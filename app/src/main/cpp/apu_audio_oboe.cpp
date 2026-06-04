#include "apu_audio_oboe.h"

#include <android/log.h>
#include <oboe/Oboe.h>

#include <atomic>
#include <cstring>
#include <memory>

namespace {

constexpr const char* kTag = "GbaApuAudio";

}  // namespace

struct GbaApuAudioOutput::StreamHolderImpl {
  std::shared_ptr<oboe::AudioStream> stream;
  std::atomic<std::uint32_t> playback_underruns{0};
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
      ->setContentType(oboe::ContentType::Music);

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

void GbaApuAudioOutput::write_interleaved_pcm16(const std::int16_t* samples,
                                                const std::size_t frame_count) {
  if (holder_ == nullptr || holder_->stream == nullptr || frame_count == 0 ||
      samples == nullptr) {
    return;
  }

  const oboe::ResultWithValue<int32_t> written = holder_->stream->write(
      samples, static_cast<int32_t>(frame_count), 0);
  if (!written) {
    holder_->playback_underruns.fetch_add(1, std::memory_order_relaxed);
    return;
  }
  if (static_cast<std::size_t>(written.value()) < frame_count) {
    holder_->playback_underruns.fetch_add(1, std::memory_order_relaxed);
  }
}

std::uint32_t GbaApuAudioOutput::playback_underruns() const {
  if (holder_ == nullptr) {
    return 0;
  }
  return holder_->playback_underruns.load(std::memory_order_relaxed);
}
