cmake_minimum_required(VERSION 3.22.1)
project(expolibghostty CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Prebuilt libghostty-vt for this ABI (fetched into vendor/ at install time;
# see scripts/ and vendor-manifest.json).
add_library(ghostty-vt STATIC IMPORTED)
set_target_properties(ghostty-vt PROPERTIES
  IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/vendor/${ANDROID_ABI}/libghostty-vt.a)

add_library(expolibghostty SHARED src/main/cpp/ghostty_jni.cpp)

target_include_directories(expolibghostty PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/vendor/include)
# Static linking: keep GHOSTTY_API a no-op (see ghostty/vt/types.h).
target_compile_definitions(expolibghostty PRIVATE GHOSTTY_STATIC)
target_compile_options(expolibghostty PRIVATE -Wall -fvisibility=hidden)
# Android 15+ devices require 16 KB page alignment.
target_link_options(expolibghostty PRIVATE -Wl,-z,max-page-size=16384)

target_link_libraries(expolibghostty ghostty-vt log)
