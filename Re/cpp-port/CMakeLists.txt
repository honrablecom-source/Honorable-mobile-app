cmake_minimum_required(VERSION 3.16)
project(reverend_insanity_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(X11 REQUIRED)

add_executable(ri_native src/main.cpp)
target_include_directories(ri_native PRIVATE ${X11_INCLUDE_DIR})
target_link_libraries(ri_native PRIVATE ${X11_LIBRARIES})
target_compile_options(ri_native PRIVATE -Wall -Wextra -Wpedantic)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
  target_compile_options(ri_native PRIVATE -O2)
endif()

