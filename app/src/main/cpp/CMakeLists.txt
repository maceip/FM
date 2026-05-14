cmake_minimum_required(VERSION 3.22.1)

project(cosmo_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_library(log_lib log)
find_library(android_lib android)

# ---------------------------------------------------------------------------
# BERT gatekeeper JNI
# ---------------------------------------------------------------------------

add_library(bert_gatekeeper_jni SHARED
    bert_gatekeeper_jni.cc
)

target_link_libraries(bert_gatekeeper_jni
    dl
    ${log_lib}
)

# ---------------------------------------------------------------------------
# Raw-audio gatekeeper LiteRT JNI
# ---------------------------------------------------------------------------

add_library(audio_gatekeeper_jni SHARED
    audio_gatekeeper_jni.cc
)

target_link_libraries(audio_gatekeeper_jni
    dl
    ${log_lib}
)

# ---------------------------------------------------------------------------
# SODA JNI recovered-runtime compatibility layer
#
# The genesis stub at soda/soda_jni.cc is no longer compiled: app/src/main/jniLibs/
# now ships the recovered libsoda_dev_jni.so (the real cosmo SODA engine, ~50 MB).
# That file is the production target loaded by the recovered SODA Java surface in
# app/libs/recovered-soda.jar. The .cc source is preserved as reference for the
# stable JNI entry-point names; if you ever need a pure-stub build, re-enable the
# add_library() block below.
# ---------------------------------------------------------------------------

# add_library(soda_dev_jni SHARED soda/soda_jni.cc)
# target_compile_options(soda_dev_jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden -Wall -Wextra -Wno-unused-parameter)
# target_link_libraries(soda_dev_jni ${log_lib} ${android_lib})
# set_target_properties(soda_dev_jni PROPERTIES LINK_FLAGS "-Wl,--exclude-libs,ALL")
