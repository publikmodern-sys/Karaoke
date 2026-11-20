package com.example.audio

object AudioConstants {
    const val SAMPLE_RATE = 44100
    const val BACKING_CHANNELS = 2
    const val VOCAL_CHANNELS = 1
    const val BYTES_PER_SAMPLE = 2
    const val STEREO_FRAME_BYTES = BACKING_CHANNELS * BYTES_PER_SAMPLE // 4 bytes
}
