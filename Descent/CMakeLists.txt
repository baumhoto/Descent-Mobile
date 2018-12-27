cmake_minimum_required(VERSION 3.6)
set (CMAKE_C_FLAGS "-Wno-error=format-security -DANDROID_NDK -DNASM -DDISABLE_IMPORTGL -DOGLES -DNETWORK -std=c99")
include_directories(../lib)
file(GLOB_RECURSE SRC_FILES ../2d/* ../3d/* ../cfile/* ../fix/* ../iff/* ../io/* ../main/* ../mem/* ../misc/* ../texmap/* ../ui/* ../vecmat/* src/main/jni/*)

add_library (Descent SHARED ${SRC_FILES})
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
include_directories(${ANDROID_SYSROOT}/usr/include/arm-linux-androideabi)
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
include_directories(${ANDROID_SYSROOT}/usr/include/aarch64-linux-android)
else()
include_directories(${ANDROID_SYSROOT}/usr/include/arm-linux-androideabi)
endif()
set_target_properties(Descent PROPERTIES LINKER_LANGUAGE C)

find_library(GLESv1_CM-lib GLESv1_CM)
find_library(EGL-lib EGL)
find_library(dl-lib dl)
find_library(log-lib log)
find_library(android-lib android)
find_library(OpenSLES-lib OpenSLES)

target_link_libraries(Descent
${GLESv1_CM-lib}
${EGL-lib}
${dl-lib}
${log-lib}
${android-lib}
${OpenSLES-lib})