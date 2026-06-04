#pragma once

#include <cstddef>
#include <cstdint>

// Oboe playback for stereo PCM16 @ 32,768 Hz (GBA APU contract).
class GbaApuAudioOutput {
 public:
  static constexpr int kSampleRate = 32768;
  static constexpr int kChannelCount = 2;

  static GbaApuAudioOutput& instance();

  bool start();
  void stop();
  void write_interleaved_pcm16(const std::int16_t* samples, std::size_t frame_count);
  [[nodiscard]] std::uint32_t playback_underruns() const;

 private:
  GbaApuAudioOutput() = default;

  struct StreamHolderImpl;
  StreamHolderImpl* holder_ = nullptr;
};
