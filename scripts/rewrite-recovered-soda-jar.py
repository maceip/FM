#!/usr/bin/env python3
"""
Rewrite the recovered SODA jar's class file constant pools so that desugared
`j$/util/...` references become direct `java/util/...` references. The recovered
cosmo APK was built with D8 core-library desugaring, which rewrites Java 8+ JDK
references to the `j$` namespace. When we ship those classes inside the genesis
APK (rather than as desugar_jdk_libs sidecars), D8/R8 refuses to merge `j$.*`
classes alongside our app classes, and at runtime the `j$.util.concurrent.*`
references can't be resolved by `desugar_jdk_libs` (the classes pre-date Java 8
and were never part of the backport set).

This script reads the original recovered-classes.jar, walks every .class entry,
and rewrites each `CONSTANT_Utf8` entry whose UTF-8 bytes match
`j$/util/concurrent/`, `j$/util/function/`, etc. to the equivalent `java/util/...`
namespace. The 2-byte UTF-8 length prefix is updated; everything else in the
class file is preserved byte-for-byte (constant pool entries reference each
other by 1-based index, not by byte offset, so growing one Utf8 entry doesn't
shift any other reference).

Output: <out_dir>/recovered-soda.jar (the slim, j$-stripped jar that genesis
ships at app/libs/recovered-soda.jar).

Usage:
    ./scripts/rewrite-recovered-soda-jar.py \\
        <input.jar> \\
        app/libs/recovered-soda.jar

The input is the recovered SODA classes jar that ships with the cosmo
research APK. Source it from the cosmo-soda-interop side-project; the path
is environment-specific and not pinned here.
"""
import io
import os
import shutil
import struct
import sys
import tempfile
import zipfile

# (search, replacement) — must match the namespaces actually used by recovered SODA bytecode.
# These all exist natively on Android API 33+, so direct java.* resolution works at runtime.
REWRITE_RULES = [
    (b"j$/util/", b"java/util/"),
    (b"j$/time/", b"java/time/"),
    (b"j$/nio/file/", b"java/nio/file/"),
    (b"j$/lang/", b"java/lang/"),
    (b"j$/io/", b"java/io/"),
    (b"j$/net/", b"java/net/"),
    # These are synthetic helper classes from desugar_jdk_libs, not Android platform
    # classes. Route the recovered bytecode to a tiny app-owned compatibility surface.
    (b"java/util/DesugarCollections", b"com/google/research/air/cosmo/compat/DesugarCollections"),
    (b"java/util/DesugarArrays", b"com/google/research/air/cosmo/compat/DesugarArrays"),
    # Protobuf-javalite 3.x had `getGeneratedRegistry()`; in 4.x it was renamed to
    # `getEmptyRegistry()` (same signature, returns the empty extension registry). Rewrite
    # the constant-pool method-name string so the MethodRef resolves at runtime.
    (b"getGeneratedRegistry", b"getEmptyRegistry"),
    # Internal Kotlin/Desugar classes that don't have a direct java.* equivalent stay as-is;
    # if they get touched at runtime we'll see new errors and add targeted rewrites.
]

DROP_PREFIXES = [
    "com/google/research/",
    "com/google/errorprone/",
    "j$/",  # Bytecode references rewritten; the j$ class files themselves are not shipped.
    "java/",  # Same reason — JDK shadows aren't shipped from the recovered jar.
    "org/tensorflow/example/",
]

# Constant pool tag → (fixed_size_excluding_tag, has_variable_length).
# https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4
CP_TAG_SIZES = {
    1: None,   # Utf8 — variable
    3: 4,      # Integer
    4: 4,      # Float
    5: 8,      # Long  (takes 2 slots)
    6: 8,      # Double (takes 2 slots)
    7: 2,      # Class
    8: 2,      # String
    9: 4,      # Fieldref
    10: 4,     # Methodref
    11: 4,     # InterfaceMethodref
    12: 4,     # NameAndType
    15: 3,     # MethodHandle
    16: 2,     # MethodType
    17: 4,     # Dynamic
    18: 4,     # InvokeDynamic
    19: 2,     # Module
    20: 2,     # Package
}


def rewrite_class(data: bytes) -> bytes:
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        return data
    out = bytearray(data[:8])  # magic + minor_version + major_version
    cp_count = struct.unpack(">H", data[8:10])[0]
    out += data[8:10]
    pos = 10
    slot = 1
    while slot < cp_count:
        tag = data[pos]
        if tag == 1:
            length = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            text = bytes(data[pos + 3:pos + 3 + length])
            new_text = text
            for src, dst in REWRITE_RULES:
                if src in new_text:
                    new_text = new_text.replace(src, dst)
            out.append(tag)
            out += struct.pack(">H", len(new_text))
            out += new_text
            pos += 3 + length
            slot += 1
        elif tag in (5, 6):
            out += data[pos:pos + 9]
            pos += 9
            slot += 2
        else:
            sz = CP_TAG_SIZES.get(tag)
            if sz is None:
                raise ValueError(f"unknown constant pool tag {tag} at slot {slot}")
            out += data[pos:pos + 1 + sz]
            pos += 1 + sz
            slot += 1
    out += data[pos:]
    return bytes(out)


def should_drop(name: str) -> bool:
    return any(name.startswith(p) for p in DROP_PREFIXES)


def rewrite_jar(src_path: str, dst_path: str) -> None:
    classes_in = 0
    classes_out = 0
    rewrites = 0
    with zipfile.ZipFile(src_path) as zin:
        with zipfile.ZipFile(dst_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for entry in zin.infolist():
                name = entry.filename
                if should_drop(name):
                    continue
                data = zin.read(entry)
                if name.endswith(".class"):
                    classes_in += 1
                    rewritten = rewrite_class(data)
                    if rewritten != data:
                        rewrites += 1
                    zout.writestr(entry, rewritten)
                    classes_out += 1
                else:
                    zout.writestr(entry, data)
    print(f"in={classes_in} out={classes_out} rewritten={rewrites} dst={dst_path}")


def main():
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <input.jar> <output.jar>", file=sys.stderr)
        sys.exit(64)
    rewrite_jar(sys.argv[1], sys.argv[2])


if __name__ == "__main__":
    main()
