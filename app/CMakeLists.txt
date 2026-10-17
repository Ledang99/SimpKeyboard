cmake_minimum_required(VERSION 3.22.1)
project(PCKeyboardNative)

add_library(
        jni_pckeyboard
        SHARED
        src/main/cpp/org_pocketworkstation_pckeyboard_BinaryDictionary.cpp
        src/main/cpp/dictionary.cpp
        src/main/cpp/char_utils.cpp
)

find_library(log-lib log)

target_compile_options(jni_pckeyboard PRIVATE
        -Wno-deprecated-declarations
        -Wno-unused-parameter
        -Wno-unused-variable
)

target_link_libraries(jni_pckeyboard ${log-lib})
