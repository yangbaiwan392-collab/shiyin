package com.yangy.shiyin.play

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * 10 段图形均衡器 + 预放大 + 软限幅，单个处理器一次遍历完成。
 *
 * Media3 不自带 EQ，这里用 RBJ 音频 Cookbook 的 peaking 双二阶滤波器实现，
 * 直接在 PCM 上运算，不引入任何第三方库。
 */
class EqLimiterProcessor : BaseAudioProcessor() {

    companion object {
        /** 标准 10 段中心频率 */
        val BAND_HZ = floatArrayOf(31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        private const val BAND_COUNT = 10
        private const val Q = 1.1f
        private const val CEILING = 0.999
    }

    private class Biquad {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        fun reset() {
            x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
        }

        fun process(input: Double): Double {
            val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = input
            y2 = y1; y1 = out
            return out
        }
    }

    @Volatile private var requestedGains = FloatArray(BAND_COUNT)
    @Volatile private var requestedPreampDb = 0f
    @Volatile private var requestedEnabled = false
    @Volatile private var requestedLimiter = true
    @Volatile private var requestedBoost = 0
    @Volatile private var dirty = false

    private var bands = Array(BAND_COUNT) { arrayOfNulls<Biquad>(1) }
    private var channels = 2
    private var rate = 44100.0
    private var preampLinear = 1.0
    private var active = false
    private var limiterOn = true
    private var floatInput = false

    /** 从 UI 线程调用，系数在下一次处理时生效 */
    fun setGains(
        gainsDb: FloatArray,
        preampDb: Float,
        enabled: Boolean,
        limiterEnabled: Boolean,
        boostPercent: Int,
    ) {
        requestedGains = gainsDb.copyOf(BAND_COUNT)
        requestedPreampDb = preampDb
        requestedEnabled = enabled
        requestedLimiter = limiterEnabled
        requestedBoost = boostPercent.coerceIn(0, 100)
        dirty = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        floatInput = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!floatInput && inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate.toDouble()
        dirty = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = active

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (dirty) rebuild()
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!active) {
            val copy = replaceOutputBuffer(remaining)
            copy.put(inputBuffer.duplicate())
            copy.flip()
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val out = replaceOutputBuffer(remaining)
        if (floatInput) {
            val floats = remaining / 4
            for (i in 0 until floats) {
                var sample = inputBuffer.float.toDouble()
                sample = processSample(sample, i % channels)
                out.putFloat(sample.coerceIn(-1.0, 1.0).toFloat())
            }
        } else {
            val shorts = remaining / 2
            for (i in 0 until shorts) {
                var sample = inputBuffer.short.toDouble() / 32768.0
                sample = processSample(sample, i % channels)
                out.putShort((sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private fun processSample(sample: Double, channel: Int): Double {
        var value = sample * preampLinear
        for (band in 0 until BAND_COUNT) {
            val filter = bands[band][channel] ?: continue
            value = filter.process(value)
        }
        return if (limiterOn) softClip(value) else value.coerceIn(-CEILING, CEILING)
    }

    /** 软拐点限幅，避免提升增益后出现硬削波爆音 */
    private fun softClip(value: Double): Double {
        val abs = kotlin.math.abs(value)
        if (abs <= 0.7) return value
        val sign = if (value < 0) -1.0 else 1.0
        val knee = 0.7
        val over = abs - knee
        val compressed = knee + (1.0 - knee) * (1.0 - Math.exp(-over * 2.4))
        return sign * compressed.coerceAtMost(CEILING)
    }

    private fun rebuild() {
        dirty = false
        active = requestedEnabled
        limiterOn = requestedLimiter
        preampLinear = Math.pow(10.0, requestedPreampDb / 20.0) * (1.0 + requestedBoost / 100.0)
        bands = Array(BAND_COUNT) { band ->
            Array(channels) {
                Biquad().apply {
                    val gainDb = requestedGains.getOrElse(band) { 0f }.toDouble()
                    configurePeak(BAND_HZ[band].toDouble(), gainDb)
                }
            }
        }
    }

    private fun Biquad.configurePeak(centerHz: Double, gainDb: Double) {
        reset()
        if (centerHz >= rate / 2.0) {
            // 频率超出奈奎斯特，退化为直通
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * Math.PI * centerHz / rate
        val cw = Math.cos(w0)
        val sw = Math.sin(w0)
        val alpha = sw / (2.0 * Q)
        val b0v = 1.0 + alpha * a
        val b1v = -2.0 * cw
        val b2v = 1.0 - alpha * a
        val a0v = 1.0 + alpha / a
        val a1v = -2.0 * cw
        val a2v = 1.0 - alpha / a
        b0 = b0v / a0v
        b1 = b1v / a0v
        b2 = b2v / a0v
        a1 = a1v / a0v
        a2 = a2v / a0v
    }

    override fun onReset() {
        for (band in bands) {
            for (filter in band) {
                filter?.reset()
            }
        }
    }
}
