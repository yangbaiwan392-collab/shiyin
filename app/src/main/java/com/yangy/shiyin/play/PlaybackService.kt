package com.yangy.shiyin.play

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yangy.shiyin.MainActivity
import com.yangy.shiyin.app
import kotlinx.coroutines.launch
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context

/**
 * 后台播放服务。
 *
 * 只有一条播放内核（ExoPlayer + MediaCodec）：常见格式直接硬解，
 * 通知栏、锁屏、线控、均衡器行为一致。
 */
class PlaybackService : MediaSessionService() {

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val eq = EqLimiterProcessor()

    override fun onCreate() {
        super.onCreate()
        val settings = kotlinx.coroutines.runBlocking { app.settings.current() }

        val sink = PipelineFactory.buildSink(
            context = this,
            offload = settings.outputPath == com.yangy.shiyin.data.model.OutputPath.DIRECT,
            monoMix = settings.monoMix,
            skipSilence = settings.skipSilence,
            eq = eq,
        )
        eq.setGains(settings.eqGains, settings.preampDb, settings.eqOn, settings.limiterOn, settings.volumeBoostPercent)

        val dataSourceFactory = DefaultDataSource.Factory(this)

        val exo = ExoPlayer.Builder(this)
            .setRenderersFactory(ShiyinRenderersFactory(this, sink))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(5_000)
            .build()
        exo.playbackParameters = androidx.media3.common.PlaybackParameters(settings.speed, 1f)
        player = exo

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(contentIntent)
            .build()

        // 设置变化热更新：均衡器、限幅、音量增强、直通模式，无需重启播放
        serviceScope.launch {
            app.settings.settings.collect { next ->
                eq.setGains(next.eqGains, next.preampDb, next.eqOn, next.limiterOn, next.volumeBoostPercent)
                sink.setOffloadMode(
                    if (next.outputPath == com.yangy.shiyin.data.model.OutputPath.DIRECT) {
                        DefaultAudioSink.OUTPUT_MODE_OFFLOAD
                    } else {
                        DefaultAudioSink.OUTPUT_MODE_PCM
                    },
                )
                player?.playbackParameters = androidx.media3.common.PlaybackParameters(next.speed, 1f)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
