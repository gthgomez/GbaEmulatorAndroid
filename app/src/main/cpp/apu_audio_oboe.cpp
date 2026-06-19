#include "apu_audio_oboe.h"

#include <android/log.h>
#include <oboe/Oboe.h>

#include <atomic>
#include <cstring>
#include <memory>
#include <vector>

constexpr const char* kTag = "GbaApuAudio";
// ~16 GBA frames of stereo PCM16 (~549 samples per emulated frame).
constexpr std::size_t kRingCapacityFrames = 549U * 16U;
constexpr std::size_t kRingCapacitySamples = kRingCapacityFrames * 2U;
constexpr int32_t kOboeBufferCapacityFrames = 4096;

class GbaApuRingBuffer {
 public:
  GbaApuRingBuffer() : samples_(kRingCapacitySamples, 0) {}

  void clear() {
    read_pos_.store(0, std::memory_order_release);
    write_pos_.store(0, std::memory_order_release);
  }

  [[nodiscard]] std::size_t available_frames() const {
    const std::size_t write = write_pos_.load(std::memory_order_acquire);
    const std::size_t read = read_pos_.load(std::memory_order_acquire);
    return write - read;
  }

  void set_steady_music_enabled(const bool enabled) {
    steady_music_enabled_.store(enabled, std::memory_order_release);
  }

  void enqueue_interleaved_pcm16(const std::int16_t* samples,
                                 const std::size_t frame_count) {
    if (samples == nullptr || frame_count == 0) {
      return;
    }

    std::size_t write = write_pos_.load(std::memory_order_relaxed);
    std::size_t read = read_pos_.load(std::memory_order_acquire);
    std::size_t used = write - read;

    if (used + frame_count > kRingCapacityFrames) {
      const std::size_t overflow = used + frame_count - kRingCapacityFrames;
      read_pos_.store(read + overflow, std::memory_order_release);
      read += overflow;
      used = write - read;
      if (steady_music_enabled_.load(std::memory_order_acquire)) {
        __android_log_print(ANDROID_LOG_DEBUG, kTag,
                            "steady_music_drop_oldest frames=%zu", overflow);
      }
    }

    for (std::size_t frame = 0; frame < frame_count; ++frame) {
      const std::size_t slot = (write + frame) % kRingCapacityFrames;
      samples_[slot * 2U] = samples[frame * 2U];
      samples_[slot * 2U + 1U] = samples[frame * 2U + 1U];
    }
    write_pos_.store(write + frame_count, std::memory_order_release);
  }

  std::size_t dequeue_interleaved_pcm16(std::int16_t* out_samples,
                                        const std::size_t max_frames) {
    if (out_samples == nullptr || max_frames == 0) {
      return 0;
    }

    const std::size_t write = write_pos_.load(std::memory_order_acquire);
    const std::size_t read = read_pos_.load(std::memory_order_relaxed);
    const std::size_t available = write - read;
    const std::size_t to_read = std::min(max_frames, available);

    for (std::size_t frame = 0; frame < to_read; ++frame) {
      const std::size_t slot = (read + frame) % kRingCapacityFrames;
      out_samples[frame * 2U] = samples_[slot * 2U];
      out_samples[frame * 2U + 1U] = samples_[slot * 2U + 1U];
    }
    read_pos_.store(read + to_read, std::memory_order_release);
    return to_read;
  }

  std::size_t dequeue_interleaved_pcm16_at_rate(std::int16_t* out_samples,
                                                const std::size_t out_frames,
                                                const float playback_rate,
                                                float& read_phase) {
    if (out_samples == nullptr || out_frames == 0) {
      return 0;
    }
    const float rate = playback_rate < 1.0f ? 1.0f : playback_rate;
    std::size_t produced = 0;
    for (std::size_t frame = 0; frame < out_frames; ++frame) {
      const std::size_t write = write_pos_.load(std::memory_order_acquire);
      const std::size_t read = read_pos_.load(std::memory_order_relaxed);
      if (write - read == 0) {
        break;
      }
      const std::size_t slot = read % kRingCapacityFrames;
      out_samples[frame * 2U] = samples_[slot * 2U];
      out_samples[frame * 2U + 1U] = samples_[slot * 2U + 1U];
      read_phase += rate;
      const std::size_t advance = static_cast<std::size_t>(read_phase);
      if (advance > 0) {
        read_pos_.store(read + advance, std::memory_order_release);
        read_phase -= static_cast<float>(advance);
      }
      produced += 1;
    }
    return produced;
  }

 private:
  std::vector<std::int16_t> samples_;
  alignas(64) std::atomic<std::size_t> read_pos_{0};
  alignas(64) std::atomic<std::size_t> write_pos_{0};
  std::atomic<bool> steady_music_enabled_{false};
};

class GbaApuDataCallback : public oboe::AudioStreamDataCallback {
 public:
  explicit GbaApuDataCallback(GbaApuRingBuffer& ring, std::atomic<std::uint32_t>& underruns,
                                std::atomic<float>& playback_rate)
      : ring_(ring), playback_underruns_(underruns), playback_rate_(playback_rate) {}

  oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audio_data,
                                        int32_t num_frames) override {
    (void)stream;
    auto* out = static_cast<std::int16_t*>(audio_data);
    const float rate = playback_rate_.load(std::memory_order_relaxed);
    const std::size_t read_frames = ring_.dequeue_interleaved_pcm16_at_rate(
        out, static_cast<std::size_t>(num_frames), rate, read_phase_);
    if (read_frames < static_cast<std::size_t>(num_frames)) {
      const std::size_t silence_frames =
          static_cast<std::size_t>(num_frames) - read_frames;
      std::memset(out + (read_frames * 2U), 0,
                  silence_frames * 2U * sizeof(std::int16_t));
      playback_underruns_.fetch_add(1, std::memory_order_relaxed);
    }
    return oboe::DataCallbackResult::Continue;
  }

 private:
  GbaApuRingBuffer& ring_;
  std::atomic<std::uint32_t>& playback_underruns_;
  std::atomic<float>& playback_rate_;
  float read_phase_ = 0.0f;
};

struct GbaApuAudioOutput::StreamHolderImpl {
  GbaApuRingBuffer ring;
  std::atomic<std::uint32_t> playback_underruns{0};
  std::atomic<float> playback_rate_multiplier{1.0f};
  GbaApuDataCallback callback;
  std::shared_ptr<oboe::AudioStream> stream;

  StreamHolderImpl()
      : callback(ring, playback_underruns, playback_rate_multiplier) {}
};

GbaApuAudioOutput& GbaApuAudioOutput::instance() {
  static GbaApuAudioOutput output;
  return output;
}

bool GbaApuAudioOutput::start() {
  stop();

  auto* holder = new StreamHolderImpl();
  holder_ = holder;

  oboe::AudioStreamBuilder builder;
  builder.setDirection(oboe::Direction::Output)
      ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
      ->setSharingMode(oboe::SharingMode::Exclusive)
      ->setFormat(oboe::AudioFormat::I16)
      ->setChannelCount(kChannelCount)
      ->setSampleRate(kSampleRate)
      ->setUsage(oboe::Usage::Game)
      ->setContentType(oboe::ContentType::Music)
      ->setDataCallback(&holder->callback)
      ->setBufferCapacityInFrames(kOboeBufferCapacityFrames);

  const oboe::Result open_result = builder.openStream(holder->stream);
  if (open_result != oboe::Result::OK || holder->stream == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "openStream failed: %s",
                        oboe::convertToText(open_result));
    stop();
    return false;
  }

  const oboe::Result start_result = holder->stream->requestStart();
  if (start_result != oboe::Result::OK) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "requestStart failed: %s",
                        oboe::convertToText(start_result));
    stop();
    return false;
  }

  holder->playback_underruns.store(0, std::memory_order_relaxed);
  holder->ring.clear();
  return true;
}

void GbaApuAudioOutput::stop() {
  if (holder_ == nullptr) {
    return;
  }
  StreamHolderImpl* holder = holder_;
  holder_ = nullptr;

  if (holder->stream != nullptr) {
    holder->stream->requestStop();
    holder->stream->close();
    holder->stream.reset();
  }
  delete holder;
}

void GbaApuAudioOutput::enqueue_interleaved_pcm16(const std::int16_t* samples,
                                                    const std::size_t frame_count) {
  if (holder_ == nullptr || frame_count == 0 || samples == nullptr) {
    return;
  }
  holder_->ring.enqueue_interleaved_pcm16(samples, frame_count);
}

void GbaApuAudioOutput::clear() {
  if (holder_ == nullptr) {
    return;
  }
  holder_->ring.clear();
}

std::size_t GbaApuAudioOutput::available_frames() const {
  if (holder_ == nullptr) {
    return 0;
  }
  return holder_->ring.available_frames();
}

std::uint32_t GbaApuAudioOutput::playback_underruns() const {
  if (holder_ == nullptr) {
    return 0;
  }
  return holder_->playback_underruns.load(std::memory_order_relaxed);
}

void GbaApuAudioOutput::set_steady_music_enabled(const bool enabled) {
  if (holder_ == nullptr) {
    return;
  }
  holder_->ring.set_steady_music_enabled(enabled);
}

void GbaApuAudioOutput::set_playback_rate_multiplier(const float multiplier) {
  if (holder_ == nullptr) {
    return;
  }
  const float clamped = multiplier < 1.0f ? 1.0f : multiplier;
  holder_->playback_rate_multiplier.store(clamped, std::memory_order_release);
}
