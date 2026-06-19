#include <jni.h>

#include "apu_audio_oboe.h"

#include "gba/core/android_core_bridge.hpp"
#include "gba/core/android_runtime.hpp"
#include "gba/core/apu.hpp"
#include "gba/core/save_state_codec.hpp"

#include "gba/core/memory_bus.hpp"

#include <algorithm>
#include <cstdint>
#include <vector>

namespace {

constexpr jint kBridgeStatusInvalid = static_cast<jint>(gba::core::AndroidBridgeStatus::invalid_argument);

jint bridge_status_to_java(gba::core::AndroidBridgeStatus status) {
  return static_cast<jint>(status);
}

jint runtime_status_to_java(gba::core::AndroidRuntimeStatus status) {
  return static_cast<jint>(status);
}

jint save_type_to_java(gba::core::GamePakSaveType type) {
  using gba::core::GamePakSaveType;
  switch (type) {
    case GamePakSaveType::none:
      return 0;
    case GamePakSaveType::sram32k:
      return 1;
    case GamePakSaveType::flash64k:
      return 2;
    case GamePakSaveType::flash128k:
      return 3;
    case GamePakSaveType::eeprom512:
      return 4;
    case GamePakSaveType::eeprom8k:
      return 5;
  }
  return 0;
}

gba::core::AndroidRuntime* as_runtime(jlong handle) {
  return reinterpret_cast<gba::core::AndroidRuntime*>(handle);
}

}  // namespace

// --- GbaCoreBridge (android_core_bridge C API) ---

extern "C" JNIEXPORT jlong JNICALL
Java_com_gba_emulator_shell_GbaCoreBridge_nativeCreate(JNIEnv*, jclass) {
  return reinterpret_cast<jlong>(gba::core::gba_android_core_create());
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_GbaCoreBridge_nativeDestroy(JNIEnv*, jclass, jlong handle) {
  gba::core::gba_android_core_destroy(reinterpret_cast<void*>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaCoreBridge_nativeReset(JNIEnv*, jclass, jlong handle) {
  return bridge_status_to_java(
      gba::core::gba_android_core_reset(reinterpret_cast<void*>(handle)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaCoreBridge_nativeLoadRom(JNIEnv* env, jclass, jlong handle,
                                                        jbyteArray rom_bytes) {
  if (rom_bytes == nullptr) {
    return kBridgeStatusInvalid;
  }
  const jsize size = env->GetArrayLength(rom_bytes);
  if (size <= 0) {
    return kBridgeStatusInvalid;
  }
  jbyte* elements = env->GetByteArrayElements(rom_bytes, nullptr);
  if (elements == nullptr) {
    return kBridgeStatusInvalid;
  }
  const auto* bytes = reinterpret_cast<const std::uint8_t*>(elements);
  const gba::core::AndroidBridgeStatus status =
      gba::core::gba_android_core_load_rom(reinterpret_cast<void*>(handle), bytes,
                                           static_cast<std::size_t>(size));
  env->ReleaseByteArrayElements(rom_bytes, elements, JNI_ABORT);
  return bridge_status_to_java(status);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_gba_emulator_shell_GbaCoreBridge_nativeRun(JNIEnv* env, jclass, jlong handle,
                                                    jint max_steps) {
  jlongArray result = env->NewLongArray(4);
  if (result == nullptr) {
    return nullptr;
  }
  jlong values[4] = {kBridgeStatusInvalid, 0, 0, 0};
  gba::core::AndroidBridgeRunResult run{};
  const gba::core::AndroidBridgeStatus status = gba::core::gba_android_core_run(
      reinterpret_cast<void*>(handle), static_cast<std::uint32_t>(max_steps), &run);
  values[0] = bridge_status_to_java(status);
  values[1] = static_cast<jlong>(run.executed_steps);
  values[2] = static_cast<jlong>(run.final_pc);
  values[3] = static_cast<jlong>(run.state_hash);
  env->SetLongArrayRegion(result, 0, 4, values);
  return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_gba_emulator_shell_GbaCoreBridge_nativeStateHash(JNIEnv*, jclass, jlong handle) {
  return static_cast<jlong>(
      gba::core::gba_android_core_state_hash(reinterpret_cast<void*>(handle)));
}

// --- GbaRuntimeBridge (AndroidRuntime) ---

extern "C" JNIEXPORT jlong JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeCreate(JNIEnv*, jclass) {
  return reinterpret_cast<jlong>(new gba::core::AndroidRuntime());
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeDestroy(JNIEnv*, jclass, jlong handle) {
  delete as_runtime(handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeReset(JNIEnv*, jclass, jlong handle) {
  if (handle == 0) {
    return runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument);
  }
  return runtime_status_to_java(as_runtime(handle)->reset());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeLoadRom(JNIEnv* env, jclass, jlong handle,
                                                           jbyteArray rom_bytes) {
  if (handle == 0 || rom_bytes == nullptr) {
    return runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument);
  }
  const jsize size = env->GetArrayLength(rom_bytes);
  if (size <= 0) {
    return runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument);
  }
  jbyte* elements = env->GetByteArrayElements(rom_bytes, nullptr);
  if (elements == nullptr) {
    return runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument);
  }
  std::vector<std::uint8_t> rom(static_cast<std::size_t>(size));
  std::copy(reinterpret_cast<const std::uint8_t*>(elements),
            reinterpret_cast<const std::uint8_t*>(elements) + size, rom.begin());
  env->ReleaseByteArrayElements(rom_bytes, elements, JNI_ABORT);
  return runtime_status_to_java(as_runtime(handle)->load_rom(rom));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeSetButtonMask(JNIEnv*, jclass, jlong handle,
                                                               jint pressed_mask) {
  if (handle == 0) {
    return runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument);
  }
  return runtime_status_to_java(
      as_runtime(handle)->set_button_mask(static_cast<std::uint16_t>(pressed_mask)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeSeedDemoEnvironment(JNIEnv*, jclass,
                                                                       jlong handle) {
  if (handle == 0) {
    return runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument);
  }
  gba::core::AndroidRuntime* runtime = as_runtime(handle);
  if (!runtime->session().memory().write16(0x05000000, 0x1234)) {
    return runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument);
  }
  runtime->session().apu().write_soundcnt_x(0x0080);
  runtime->session().apu().configure_square_channel(0, 2, 8, 4);
  [[maybe_unused]] const auto audio_seed = runtime->session().apu().tick(
      gba::core::Apu::kCpuCyclesPerAudioSample * 3U);
  return runtime_status_to_java(gba::core::AndroidRuntimeStatus::ok);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeStepFrame(JNIEnv* env, jclass, jlong handle,
                                                             jint max_steps) {
  jlongArray result = env->NewLongArray(10);
  if (result == nullptr) {
    return nullptr;
  }
  jlong values[10] = {
      runtime_status_to_java(gba::core::AndroidRuntimeStatus::invalid_argument),
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
  };
  if (handle != 0) {
    const gba::core::AndroidRuntimeFrameResult frame =
        as_runtime(handle)->step_frame(static_cast<std::uint32_t>(max_steps));
    values[0] = runtime_status_to_java(frame.status);
    values[1] = static_cast<jlong>(frame.run.executed_steps);
    values[2] = static_cast<jlong>(frame.rendered_scanlines);
    values[3] = static_cast<jlong>(frame.audio_samples);
    values[4] = static_cast<jlong>(frame.audio_underruns);
    values[5] = static_cast<jlong>(frame.state_hash);
    values[6] = static_cast<jlong>(frame.run.stop_reason);
    values[7] = static_cast<jlong>(frame.run.unsupported_steps);
    values[8] = static_cast<jlong>(frame.run.final_pc);
    values[9] = static_cast<jlong>(frame.scheduler_cycles_delta);
  }
  env->SetLongArrayRegion(result, 0, 10, values);
  return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativePixel(JNIEnv*, jclass, jlong handle, jint x,
                                                         jint y) {
  if (handle == 0) {
    return -1;
  }
  return static_cast<jint>(as_runtime(handle)->pixel(static_cast<std::uint16_t>(x),
                                                   static_cast<std::uint16_t>(y)));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeGetVideoDiagnostics(JNIEnv* env, jclass,
                                                                       jlong handle) {
  jlongArray result = env->NewLongArray(8);
  if (result == nullptr) {
    return nullptr;
  }
  jlong values[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  if (handle != 0) {
    const gba::core::AndroidRuntimeVideoDiagnostics diagnostics =
        as_runtime(handle)->video_diagnostics();
    values[0] = static_cast<jlong>(diagnostics.dispcnt);
    values[1] = diagnostics.forced_blank ? 1LL : 0LL;
    values[2] = static_cast<jlong>(diagnostics.bg_enabled_mask);
    values[3] = static_cast<jlong>(diagnostics.non_zero_pixel_count);
    values[4] = static_cast<jlong>(diagnostics.sample_rgb565);
    values[5] = static_cast<jlong>(diagnostics.unique_color_count);
    values[6] = static_cast<jlong>(diagnostics.dominant_color_ratio * 1000000.0F);
    values[7] = static_cast<jlong>(diagnostics.framebuffer_crc32);
  }
  env->SetLongArrayRegion(result, 0, 8, values);
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeCopyFramebuffer(JNIEnv* env, jclass,
                                                                   jlong handle,
                                                                   jshortArray out_pixels) {
  if (handle == 0 || out_pixels == nullptr) {
    return;
  }
  const gba::core::PpuRenderer::Framebuffer& framebuffer = as_runtime(handle)->framebuffer();
  const jsize expected =
      static_cast<jsize>(gba::core::PpuRenderer::kFramebufferPixels);
  if (env->GetArrayLength(out_pixels) != expected) {
    return;
  }
  jshort* elements = env->GetShortArrayElements(out_pixels, nullptr);
  if (elements == nullptr) {
    return;
  }
  for (jsize i = 0; i < expected; ++i) {
    elements[i] = static_cast<jshort>(framebuffer[static_cast<std::size_t>(i)]);
  }
  env->ReleaseShortArrayElements(out_pixels, elements, 0);
}

// --- Audio (Agent 3): drain last APU batch + Oboe playback ---

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeDrainAudioBatch(JNIEnv* env, jclass,
                                                                   jlong handle) {
  if (handle == 0) {
    return env->NewShortArray(0);
  }
  const std::vector<gba::core::ApuMixedSample>& batch =
      as_runtime(handle)->last_audio_batch();
  const jsize frame_count = static_cast<jsize>(batch.size());
  const jsize array_length = frame_count * 2;
  jshortArray result = env->NewShortArray(array_length);
  if (result == nullptr) {
    return nullptr;
  }
  if (array_length == 0) {
    return result;
  }
  std::vector<jshort> interleaved(static_cast<std::size_t>(array_length));
  for (jsize i = 0; i < frame_count; ++i) {
    const gba::core::ApuMixedSample& sample = batch[static_cast<std::size_t>(i)];
    interleaved[static_cast<std::size_t>(i * 2)] = sample.left;
    interleaved[static_cast<std::size_t>(i * 2 + 1)] = sample.right;
  }
  env->SetShortArrayRegion(result, 0, array_length, interleaved.data());
  return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativeStart(JNIEnv*, jclass) {
  return GbaApuAudioOutput::instance().start() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativeStop(JNIEnv*, jclass) {
  GbaApuAudioOutput::instance().stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativeEnqueueBatch(JNIEnv* env, jclass,
                                                              jshortArray pcm_interleaved) {
  if (pcm_interleaved == nullptr) {
    return;
  }
  const jsize length = env->GetArrayLength(pcm_interleaved);
  if (length <= 0 || (length % 2) != 0) {
    return;
  }
  jshort* elements = env->GetShortArrayElements(pcm_interleaved, nullptr);
  if (elements == nullptr) {
    return;
  }
  const std::size_t frame_count = static_cast<std::size_t>(length / 2);
  GbaApuAudioOutput::instance().enqueue_interleaved_pcm16(
      reinterpret_cast<const std::int16_t*>(elements), frame_count);
  env->ReleaseShortArrayElements(pcm_interleaved, elements, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativeClear(JNIEnv*, jclass) {
  GbaApuAudioOutput::instance().clear();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativeAvailableFrames(JNIEnv*, jclass) {
  return static_cast<jint>(GbaApuAudioOutput::instance().available_frames());
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativeSetSteadyMusicEnabled(JNIEnv*, jclass,
                                                                         jboolean enabled) {
  GbaApuAudioOutput::instance().set_steady_music_enabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativeSetPlaybackRateMultiplier(JNIEnv*, jclass,
                                                                           jfloat multiplier) {
  GbaApuAudioOutput::instance().set_playback_rate_multiplier(multiplier);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_ApuAudioEngine_nativePlaybackUnderruns(JNIEnv*, jclass) {
  return static_cast<jint>(GbaApuAudioOutput::instance().playback_underruns());
}

// --- GbaRuntimeBridge persistence (cartridge save + save states) ---

namespace {

constexpr jint kPersistenceOk = 0;
constexpr jint kPersistenceInvalid = 1;
constexpr jint kPersistenceNoSave = 2;
constexpr jint kPersistenceImportRejected = 3;

jbyteArray vector_to_jbyte_array(JNIEnv* env, const std::vector<std::uint8_t>& bytes) {
  if (bytes.empty()) {
    return env->NewByteArray(0);
  }
  jbyteArray array = env->NewByteArray(static_cast<jsize>(bytes.size()));
  if (array == nullptr) {
    return nullptr;
  }
  env->SetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()),
                          reinterpret_cast<const jbyte*>(bytes.data()));
  return array;
}

bool jbyte_array_to_vector(JNIEnv* env, jbyteArray array, std::vector<std::uint8_t>& out) {
  if (array == nullptr) {
    return false;
  }
  const jsize size = env->GetArrayLength(array);
  if (size < 0) {
    return false;
  }
  out.resize(static_cast<std::size_t>(size));
  if (size == 0) {
    return true;
  }
  jbyte* elements = env->GetByteArrayElements(array, nullptr);
  if (elements == nullptr) {
    return false;
  }
  std::copy(reinterpret_cast<const std::uint8_t*>(elements),
            reinterpret_cast<const std::uint8_t*>(elements) + size, out.begin());
  env->ReleaseByteArrayElements(array, elements, JNI_ABORT);
  return true;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeExportSave(JNIEnv* env, jclass,
                                                              jlong handle) {
  if (handle == 0) {
    return nullptr;
  }
  const gba::core::MemoryBus& memory = as_runtime(handle)->session().memory();
  if (!memory.has_game_pak_save()) {
    return env->NewByteArray(0);
  }
  return vector_to_jbyte_array(env, memory.export_game_pak_save());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeImportSave(JNIEnv* env, jclass, jlong handle,
                                                              jbyteArray save_bytes) {
  if (handle == 0 || save_bytes == nullptr) {
    return kPersistenceInvalid;
  }
  gba::core::MemoryBus& memory = as_runtime(handle)->session().memory();
  if (!memory.has_game_pak_save()) {
    return kPersistenceNoSave;
  }
  std::vector<std::uint8_t> bytes;
  if (!jbyte_array_to_vector(env, save_bytes, bytes)) {
    return kPersistenceInvalid;
  }
  const gba::core::GamePakSaveType type = memory.game_pak_save_type();
  if (type == gba::core::GamePakSaveType::none) {
    return kPersistenceNoSave;
  }
  return memory.import_game_pak_save(type, bytes) ? kPersistenceOk
                                                   : kPersistenceImportRejected;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeSaveState(JNIEnv* env, jclass, jlong handle) {
  if (handle == 0) {
    return nullptr;
  }
  return vector_to_jbyte_array(env,
                             gba::core::SaveStateCodec::encode(as_runtime(handle)->session()));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeLoadState(JNIEnv* env, jclass, jlong handle,
                                                             jbyteArray state_bytes) {
  if (handle == 0 || state_bytes == nullptr) {
    return static_cast<jint>(gba::core::SaveStateDecodeStatus::too_small);
  }
  std::vector<std::uint8_t> bytes;
  if (!jbyte_array_to_vector(env, state_bytes, bytes)) {
    return static_cast<jint>(gba::core::SaveStateDecodeStatus::corrupt_payload);
  }
  const gba::core::SaveStateDecodeResult result =
      gba::core::SaveStateCodec::decode_into(as_runtime(handle)->session(), bytes);
  return static_cast<jint>(result.status);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeSessionStateHash(JNIEnv*, jclass,
                                                                    jlong handle) {
  if (handle == 0) {
    return 0;
  }
  return static_cast<jlong>(as_runtime(handle)->session().state_hash());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_gba_emulator_shell_GbaRuntimeBridge_nativeGetCartridgeMetadata(JNIEnv* env, jclass,
                                                                        jlong handle) {
  if (handle == 0) {
    return nullptr;
  }
  const gba::core::MemoryBus& memory = as_runtime(handle)->session().memory();
  const std::optional<gba::core::CartridgeHeader> header = memory.game_pak_header();
  const std::optional<gba::core::GamePakSaveType> save_type =
      memory.detect_game_pak_save_type();

  jclass string_class = env->FindClass("java/lang/String");
  if (string_class == nullptr) {
    return nullptr;
  }
  jobjectArray result = env->NewObjectArray(6, string_class, nullptr);
  if (result == nullptr) {
    return nullptr;
  }

  std::string title = "Unknown";
  std::string game_code = "----";
  std::string maker_code = "--";
  if (header.has_value()) {
    auto trim_field = [](const auto& field, std::size_t length) {
      std::string text;
      for (std::size_t index = 0; index < length; ++index) {
        const char ch = static_cast<char>(field.at(index));
        if (ch == '\0') {
          break;
        }
        text.push_back(ch);
      }
      while (!text.empty() && text.back() == ' ') {
        text.pop_back();
      }
      return text;
    };
    title = trim_field(header->title, header->title.size());
    if (title.empty()) {
      title = "Unknown";
    }
    game_code = trim_field(header->game_code, header->game_code.size());
    maker_code = trim_field(header->maker_code, header->maker_code.size());
  }

  const jint save_code = save_type.has_value() ? save_type_to_java(save_type.value()) : -1;
  const bool header_valid = memory.cartridge_header_is_valid();
  const bool complement_valid = memory.cartridge_header_complement_valid();

  const jstring values[6] = {
      env->NewStringUTF(title.c_str()),
      env->NewStringUTF(game_code.c_str()),
      env->NewStringUTF(maker_code.c_str()),
      env->NewStringUTF(std::to_string(save_code).c_str()),
      env->NewStringUTF(header_valid ? "1" : "0"),
      env->NewStringUTF(complement_valid ? "1" : "0"),
  };
  for (jsize index = 0; index < 6; ++index) {
    env->SetObjectArrayElement(result, index, values[index]);
  }
  return result;
}
