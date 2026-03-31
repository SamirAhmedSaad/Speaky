cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

# Load llama.cpp from our libs directory
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/llama.cpp build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED
        llama-android.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        common
        android
        log)
