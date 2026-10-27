cmake_minimum_required(VERSION 3.10)
project(arkmenu)
set(CMAKE_CXX_STANDARD 17)
add_library(arkmenu SHARED arkmenu.cpp)
target_compile_options(arkmenu PRIVATE -O2 -fvisibility=hidden)
# Dobby: build it for arm64-v8a and point this at libdobby.a (or add it as a subdirectory).
add_library(dobby STATIC IMPORTED)
set_target_properties(dobby PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libdobby.a)
target_link_libraries(arkmenu dobby log)
