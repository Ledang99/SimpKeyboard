cmake_minimum_required(VERSION 3.22.1)

project(jni_latinime LANGUAGES CXX)

add_library(
    jni_latinime
    SHARED
    ../native/jni/com_android_inputmethod_latin_BinaryDictionary.cpp
    ../native/src/dictionary.cpp
    ../native/src/char_utils.cpp
)

target_include_directories(
    jni_latinime
    PRIVATE
    ../native/src
)

target_compile_features(jni_latinime PRIVATE cxx_std_17)
