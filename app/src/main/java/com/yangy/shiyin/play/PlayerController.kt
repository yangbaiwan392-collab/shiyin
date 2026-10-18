package com.yangy.shiyin.play

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import com.yangy.shiyin.app
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.model.PlayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@UnstableApi
data class PlayUiState(
    val connected: Boolean = false,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val mode: PlayMode = PlayMode.SEQUENTIAL,
    val speed: Float = 1f,
) {
    val current: Track? get() = queue.getOrNull(index)
    val hasQueue: Boolean get() = queue.isNotEmpty()
}

/** UI 与 PlaybackService 之间唯一的通道 */
@UnstableApi
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(PlayUiState())
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private var queue: List<Track> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let { syncFromPlayer(it) }
            attachArtwork()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.value = _state.value.copy(speed = playbackParameters.speed)
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                val result = future.get()
                controller = result
                result.addListener(listener)
                _state.value = _state.value.copy(connected = true)
                syncFromPlayer(result)
                startTicker()
            }
        }, DirectExecutor)
    }

    fun disconnect() {
        ticker?.cancel()
        sleepJob?.cancel()
        controller?.let { c ->
            c.removeListener(listener)
            c.release()
        }
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    /** 用给定的曲目列表替换队列并从第 startIndex 首开始播 */
    fun play(tracks: List<Track>, startIndex: Int = 0, autoplay: Boolean = true) {
        queue = tracks
        _state.value = _state.value.copy(queue = tracks, index = startIndex)
        val player = controller ?: run { connect(); return }
        val items = tracks.map { it.toMediaItem() }
        player.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        player.prepare()
        if (autoplay) player.play()
        syncFromPlayer(player)
        attachArtwork()
    }

    fun append(ids: List<Long>) {
        val player = controller ?: return
        val start = queue.size
        scope.launch {
            val added = ArrayList<Track>()
            for (id in ids) {
                context.app.library.trackById(id)?.let { added += it }
            }
            if (added.isEmpty()) return@launch
            queue = queue + added
            player.addMediaItems(added.map { it.toMediaItem() })
            _state.value = _state.value.copy(queue = queue)
        }
    }

    fun togglePlay() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun seekToIndex(index: Int) {
        val player = controller ?: return
        if (index in queue.indices) player.seekTo(index, 0)
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()

    fun removeAt(index: Int) {
        val player = controller ?: return
        if (index !in queue.indices) return
        queue = queue.minusElement(queue[index])
        player.removeMediaItem(index)
        _state.value = _state.value.copy(queue = queue)
    }

    fun moveItem(from: Int, to: Int) {
        val player = controller ?: return
        if (from !in queue.indices || to !in queue.indices) return
        val mutable = queue.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        queue = mutable
        player.moveMediaItem(from, to)
        _state.value = _state.value.copy(queue = queue)
    }

    fun cycleMode(): PlayMode {
        val nextMode = _state.value.mode.next()
        applyMode(nextMode)
        return nextMode
    }

    fun applyMode(mode: PlayMode) {
        val player = controller ?: return
        when (mode) {
            PlayMode.SEQUENTIAL -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = false
            }
            PlayMode.REPEAT_ALL -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
            }
            PlayMode.REPEAT_ONE -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
            }
            PlayMode.SHUFFLE -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = true
            }
        }
        _state.value = _state.value.copy(mode = mode)
    }

    fun setSpeed(speed: Float) {
        val player = controller ?: return
        val clamped = speed.coerceIn(0.25f, 3f)
        player.playbackParameters = androidx.media3.common.PlaybackParameters(clamped, 1f)
        _state.value = _state.value.copy(speed = clamped)
    }

    /** 睡眠定时器：minutes<=0 表示取消；到点先淡出再暂停 */
    fun startSleepTimer(minutes: Int, fadeOut: Boolean) {
        sleepJob?.cancel()
        if (minutes <= 0) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            if (fadeOut) {
                val player = controller
                var volume = player?.volume ?: 1f
                while (volume > 0.02f) {
                    volume -= 0.02f
                    player?.volume = volume.coerceAtLeast(0f)
                    delay(120)
                }
                controller?.volume = 1f
            }
            controller?.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
    }

    /** 把内嵌封面注入当前媒体项，通知栏与系统播放控件才会显示封面而非占位图 */
    private var artworkJob: Job? = null

    private fun attachArtwork() {
        val player = controller ?: return
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val track = queue.getOrNull(index) ?: return
        if (item.mediaMetadata.artworkData != null) return
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val bytes = com.yangy.shiyin.data.source.embeddedArtBytes(context, track.uri) ?: return@launch
            val current = controller ?: return@launch
            if (current.currentMediaItemIndex != index) return@launch
            val existing = current.currentMediaItem ?: return@launch
            if (existing.mediaMetadata.artworkData != null) return@launch
            val updated = existing.buildUpon()
                .setMediaMetadata(
                    existing.mediaMetadata.buildUpon()
                        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build(),
                )
                .build()
            runCatching { current.replaceMediaItem(index, updated) }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val player = controller
                if (player != null) {
                    val position = player.currentPosition.coerceAtLeast(0)
                    val duration = if (player.duration > 0) player.duration else queue.getOrNull(player.currentMediaItemIndex)?.durationMs ?: 0
                    _state.value = _state.value.copy(
                        positionMs = position,
                        durationMs = duration,
                        bufferedMs = player.bufferedPosition,
                    )
                    persistResume(position)
                }
                delay(500)
            }
        }
    }

    private var lastPersist = 0L

    private fun persistResume(position: Long) {
        val now = System.currentTimeMillis()
        if (now - lastPersist < 5_000) return
        val track = queue.getOrNull(controller?.currentMediaItemIndex ?: -1) ?: return
        lastPersist = now
        scope.launch { context.app.library.saveResume(track.id, position) }
    }

    private fun syncFromPlayer(player: Player) {
        val index = player.currentMediaItemIndex
        val track = queue.getOrNull(index)
        _state.value = _state.value.copy(
            connected = true,
            index = index,
            isPlaying = player.isPlaying,
            queue = queue,
            durationMs = if (player.duration > 0) player.duration else track?.durationMs ?: 0,
            positionMs = player.currentPosition.coerceAtLeast(0),
        )
        if (player.playbackState == Player.STATE_READY && track != null) {
            scope.launch { context.app.library.markPlayed(track.id) }
        }
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setGenre(genre)
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .build(),
        )
        .build()

    private object DirectExecutor : java.util.concurrent.Executor {
        override fun execute(command: Runnable) = command.run()
    }
}
