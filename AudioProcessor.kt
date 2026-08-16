package com.worm.livevoicefx

import kotlin.math.*

object AudioProcessor {
    // معاملات الترشيح (IIR) للـ Bass و Treble
    private var prevLow: Float = 0f
    private var prevHigh: Float = 0f

    // مخزن مؤقت للصدى (Reverb)
    private val reverbBuffer = FloatArray(24000) // تأخير 0.5 ثانية بمعدل 48k
    private var reverbIndex = 0

    fun process(
        input: ShortArray,
        bassGain: Float,      // 0.5 إلى 4.0
        trebleGain: Float,    // 0.5 إلى 4.0
        compressorAmount: Float, // 0.0 إلى 1.0
        reverbMix: Float,     // 0.0 إلى 0.8
        mixWithTrack: ShortArray? = null // المقطع الصوتي المدمج
    ): ShortArray {
        val output = ShortArray(input.size)
        val sampleRate = 44100f
        val alpha = 0.35f // تردد القطع (~200Hz)

        for (i in input.indices) {
            var sample = input[i].toFloat() / 32768f

            // 1. فلتر Bass (تمرير منخفض + رفع الكسب)
            val lowPass = alpha * sample + (1 - alpha) * prevLow
            prevLow = lowPass
            val bassBoosted = lowPass * bassGain

            // 2. فلتر Treble (تمرير عالي + رفع الكسب)
            val highPass = sample - lowPass
            prevHigh = highPass
            val trebleBoosted = highPass * trebleGain

            // 3. دمج Bass + Treble
            var processed = (bassBoosted + trebleBoosted).coerceIn(-1f, 1f)

            // 4. الضاغط (Compressor) - معادلة RMS لحظية
            if (compressorAmount > 0.01f) {
                val rms = sqrt((processed * processed).coerceAtLeast(0.0001f))
                val threshold = 0.3f + (1 - compressorAmount) * 0.5f
                var gain = 1f
                if (rms > threshold) {
                    gain = threshold / rms + (1 - threshold) * 0.3f
                }
                processed *= gain.coerceIn(0.3f, 1.5f)
            }

            // 5. الصدى (Reverb) - تأخير مع تغذية مرتدة
            if (reverbMix > 0.01f) {
                val delayIndex = (reverbIndex - 16000 + reverbBuffer.size) % reverbBuffer.size
                val delayed = reverbBuffer[delayIndex]
                reverbBuffer[reverbIndex] = processed + delayed * 0.5f
                reverbIndex = (reverbIndex + 1) % reverbBuffer.size
                processed = processed * (1 - reverbMix) + delayed * reverbMix
            }

            // 6. خلط المقطع الصوتي الخارجي (إن وجد)
            mixWithTrack?.let { track ->
                if (i < track.size) {
                    val trackSample = track[i].toFloat() / 32768f
                    processed = (processed + trackSample * 0.7f).coerceIn(-1f, 1f)
                }
            }

            output[i] = (processed * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }
}
