# Reference repository (architecture maps)

Reviews and `docs/architecture-delta.md` cite **upstream architecture docs** that historically lived in a separate reference checkout.

This Genesis tree is a **standalone root**. Nothing here assumes a sibling checkout exists next to the Gradle project.

## Optional local path

If you keep a legacy/reference checkout on disk, set:

```bash
export COSMO_REFERENCE_REPO_ROOT="$HOME/src/cosmo-reference"   # example
```

Use that directory when opening `docs/architecture.md` or updating stack maps. If unset, use whatever location your team stores the reference docs (wiki URL, second clone, tarball).

Do **not** commit machine-specific absolute paths into Genesis docs.

## Relocation Notes

After moving a checkout, recreate generated local state instead of preserving
machine paths from the old location: `.venv-litert-convert`, `.gradle`,
`.kotlin`, `build`, `app/.cxx`, and `app/build`.

Run `scripts/verify-relocation-safety.sh` before declaring the moved checkout
ready.
