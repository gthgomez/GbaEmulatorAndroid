cmake_minimum_required(VERSION 3.22.1)
project(gbaemulator)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 16 KB page-size alignment for Play (Android 15+). Hoist to shared linker flags so
# libc++_shared and other shared deps inherit the same LOAD segment alignment.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Core resolution order (GA01): the pinned checkout produced by
# tools/bootstrap-core.py is the qualified source; everything else is an
# explicit development override and is marked NOT QUALIFIED.
#
# 1. GBA_EMULATOR_ROOT env var (set by CI from tools/bootstrap-core.py output,
#    or by a developer).
# 2. Repo-local bootstrapped dependency dir: <repo>/deps/GBA_Emulator
#    (created by `python tools/bootstrap-core.py`, gitignored).
# 3. Sibling repo layout (C:\Workspace\Project_Android: app/src/main/cpp ->
#    ../../../../../GBA_Emulator) — development override, NOT QUALIFIED.
set(GBA_CORE_LOCK "${CMAKE_CURRENT_LIST_DIR}/../../../../core.lock.json")

# Read the locked commit from core.lock.json (single source of the pin).
set(GBA_CORE_LOCKED_COMMIT "")
if(EXISTS "${GBA_CORE_LOCK}")
    file(READ "${GBA_CORE_LOCK}" GBA_CORE_LOCK_TEXT)
    string(REGEX MATCH "\"commit\"[ \t]*:[ \t]*\"([0-9a-f]+)\"" _m "${GBA_CORE_LOCK_TEXT}")
    if(CMAKE_MATCH_1)
        set(GBA_CORE_LOCKED_COMMIT "${CMAKE_MATCH_1}")
    endif()
endif()

function(gba_check_lock_commit core_root qualifier)
    if(GBA_CORE_LOCKED_COMMIT STREQUAL "")
        message(FATAL_ERROR "core.lock.json missing or unreadable at ${GBA_CORE_LOCK}; cannot qualify the core pin")
    endif()
    set(_marker "${core_root}/.core-lock-verified")
    if(NOT EXISTS "${_marker}")
        message(FATAL_ERROR
            "${qualifier} core at ${core_root} has no lock verification marker "
            "(${_marker}). Run `python tools/bootstrap-core.py` to produce a "
            "qualified core checkout, or set GBA_CORE_OVERRIDE for an "
            "unqualified development build.")
    endif()
    file(READ "${_marker}" _marker_commit)
    string(STRIP "${_marker_commit}" _marker_commit)
    if(NOT _marker_commit STREQUAL GBA_CORE_LOCKED_COMMIT)
        message(FATAL_ERROR
            "core at ${core_root} was verified at commit ${_marker_commit} but "
            "core.lock.json pins ${GBA_CORE_LOCKED_COMMIT}. Re-run "
            "`python tools/bootstrap-core.py` after the lock changes; refusing "
            "a qualified build against a different core revision.")
    endif()
endfunction()

set(GBA_CORE_QUALIFIED FALSE)
if(DEFINED ENV{GBA_EMULATOR_ROOT})
    set(GBA_EMULATOR_ROOT "$ENV{GBA_EMULATOR_ROOT}")
    if(DEFINED ENV{GBA_CORE_OVERRIDE})
        message(WARNING
            "GBA_EMULATOR_ROOT=${GBA_EMULATOR_ROOT} is a development override "
            "(GBA_CORE_OVERRIDE set): core pin is NOT QUALIFIED against core.lock.json.")
    else()
        gba_check_lock_commit("${GBA_EMULATOR_ROOT}" "GBA_EMULATOR_ROOT")
        set(GBA_CORE_QUALIFIED TRUE)
    endif()
elseif(EXISTS "${CMAKE_CURRENT_LIST_DIR}/../../../../deps/GBA_Emulator/.git")
    set(GBA_EMULATOR_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../deps/GBA_Emulator")
    gba_check_lock_commit("${GBA_EMULATOR_ROOT}" "Bootstrapped dependency dir")
    set(GBA_CORE_QUALIFIED TRUE)
else()
    set(GBA_EMULATOR_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../GBA_Emulator")
    message(WARNING
        "Using sibling core ${GBA_EMULATOR_ROOT} as a development override; "
        "it is NOT QUALIFIED against core.lock.json. Run "
        "`python tools/bootstrap-core.py` for a qualified, lock-verified core.")
endif()

message(STATUS "GBA core root: ${GBA_EMULATOR_ROOT} (qualified=${GBA_CORE_QUALIFIED})")
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
