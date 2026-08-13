cmake_minimum_required(VERSION 3.22.1)
project(gbaemulator)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 16 KB page-size alignment for Play (Android 15+). Hoist to shared linker flags so
# libc++_shared and other shared deps inherit the same LOAD segment alignment.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Project_Android/GbaEmulatorAndroid/app/src/main/cpp -> ../../../../../GBA_Emulator
set(GBA_EMULATOR_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../GBA_Emulator")
set(GBA_CORE_DIR "${GBA_EMULATOR_ROOT}/src/core")
set(GBA_INCLUDE_DIR "${GBA_EMULATOR_ROOT}/include")

if(NOT EXISTS "${GBA_EMULATOR_ROOT}/include/gba/core/android_core_bridge.hpp")
    message(FATAL_ERROR "GBA_Emulator core not found at ${GBA_EMULATOR_ROOT}")
endif()

set(GBA_CORE_SOURCES
    ${GBA_CORE_DIR}/android_core_bridge.cpp
    ${GBA_CORE_DIR}/android_runtime.cpp
    ${GBA_CORE_DIR}/arm7tdmi.cpp
    ${GBA_CORE_DIR}/apu.cpp
    ${GBA_CORE_DIR}/bios.cpp
    ${GBA_CORE_DIR}/core_scheduler.cpp
    ${GBA_CORE_DIR}/core_session.cpp
    ${GBA_CORE_DIR}/dma_controller.cpp
    ${GBA_CORE_DIR}/interrupt_controller.cpp
    ${GBA_CORE_DIR}/io_registers.cpp
    ${GBA_CORE_DIR}/keypad.cpp
    ${GBA_CORE_DIR}/memory_bus.cpp
    ${GBA_CORE_DIR}/ppu_background.cpp
    ${GBA_CORE_DIR}/ppu_renderer.cpp
    ${GBA_CORE_DIR}/ppu_sprites.cpp
    ${GBA_CORE_DIR}/ppu_timing.cpp
    ${GBA_CORE_DIR}/save_state_codec.cpp
    ${GBA_CORE_DIR}/timers.cpp
    ${GBA_CORE_DIR}/wait_state_control.cpp
)

find_package(oboe REQUIRED CONFIG)

add_library(gbaemulator SHARED
    apu_audio_oboe.cpp
    gba_jni.cpp
    ${GBA_CORE_SOURCES}
)

target_include_directories(gbaemulator PRIVATE
    ${GBA_INCLUDE_DIR}
    ${CMAKE_CURRENT_LIST_DIR}
)

target_compile_options(gbaemulator PRIVATE
    -Wall
    -Wextra
    -O3
)

target_link_libraries(gbaemulator PRIVATE log oboe::oboe)

target_link_options(gbaemulator PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
