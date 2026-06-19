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
  void enqueue_interleaved_pcm16(const std::int16_t* samples, std::size_t frame_count);
  void clear();
  [[nodiscard]] std::size_t available_frames() const;
  [[nodiscard]] std::uint32_t playback_underruns() const;
  void set_steady_music_enabled(bool enabled);
  void set_playback_rate_multiplier(float multiplier);

 private:
  GbaApuAudioOutput() = default;

  struct StreamHolderImpl;
  StreamHolderImpl* holder_ = nullptr;
};
