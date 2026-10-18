package com.yangy.shiyin.play

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import java.util.ArrayList

/** 把自定义 AudioSink 注入 ExoPlayer 的渲染器工厂 */
class ShiyinRenderersFactory(
    context: Context,
    private val sink: AudioSink,
) : DefaultRenderersFactory(context) {

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        renderers: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            sink,
            eventHandler,
            eventListener,
            renderers,
        )
    }
}

/**
 * 处理链装配。顺序：跳过静音 -> 声道混合 -> 均衡器与限幅。
 */
object PipelineFactory {

    fun buildSink(
        context: Context,
        offload: Boolean,
        monoMix: Boolean,
        skipSilence: Boolean,
        eq: EqLimiterProcessor,
    ): DefaultAudioSink {
        val processors = ArrayList<AudioProcessor>(3)
        if (skipSilence) {
            processors += SilenceSkippingAudioProcessor()
        }
        if (monoMix) {
            val mixer = ChannelMixingAudioProcessor()
            // 立体声 -> 单声道，等功率降为 0.5 避免叠加过载
            mixer.putChannelMixingMatrix(ChannelMixingMatrix(2, 1, floatArrayOf(0.5f, 0.5f)))
            mixer.putChannelMixingMatrix(ChannelMixingMatrix(1, 1, floatArrayOf(1f)))
            processors += mixer
        }
        processors += eq

        val sink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(processors.toTypedArray())
            .build()
        // 源码直通：请求硬件 offload，由设备决定是否接受，不支持时 Media3 自动回退
        sink.setOffloadMode(
            if (offload) DefaultAudioSink.OUTPUT_MODE_OFFLOAD else DefaultAudioSink.OUTPUT_MODE_PCM,
        )
        return sink
    }
}
