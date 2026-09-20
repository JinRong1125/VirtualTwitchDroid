package com.example.virtualtwitchdroid.feature.stream

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-scoped owner of the single [ExoPlayer] instance and of the "what is currently playing"
 * session. Living above navigation (a Hilt `@Singleton`, held by the Activity), it lets the video
 * outlive the stream destination: when the user minimizes, the stream screen is popped but this
 * controller keeps the same [exoPlayer] playing, and the floating [StreamMiniPlayer] simply
 * re-attaches its own surface to it — no stop, no reload.
 *
 * It is a *thin* holder: HLS-URL resolution still belongs to [StreamViewModel], which calls [bind]
 * once its URL resolves. Media is (re)loaded only when the channel actually changes, so expanding
 * the mini-player back to fullscreen (which re-enters the stream screen and re-resolves a fresh
 * signed URL for the *same* channel) never restarts playback.
 */
@Singleton
class PlayerController @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** Built lazily so unit tests / previews that never play don't need a media stack. */
    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            // Surface hard playback failures (e.g. the network dropped mid-stream and the next HLS
            // segment couldn't load) so the UI can show a black retry window over the frozen video.
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    _playbackError.value = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // A successful (re)prepare that reaches READY clears the error window.
                    if (playbackState == Player.STATE_READY) _playbackError.value = false
                }
            })
        }
    }

    data class Session(val channelLogin: String, val viewers: Int, val minimized: Boolean = false)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** Xtra-style quality menu. Each caps the adaptive video height; AUTO removes the cap. */
    enum class VideoQuality(@StringRes val labelRes: Int, val maxHeight: Int) {
        AUTO(R.string.quality_auto, Int.MAX_VALUE),
        P720(R.string.quality_720, 720),
        P480(R.string.quality_480, 480),
        P360(R.string.quality_360, 360),
        P160(R.string.quality_160, 160),
    }

    private val _quality = MutableStateFlow(VideoQuality.AUTO)
    val quality: StateFlow<VideoQuality> = _quality.asStateFlow()

    /** True after ExoPlayer reported a playback error; drives the black retry window. */
    private val _playbackError = MutableStateFlow(false)
    val playbackError: StateFlow<Boolean> = _playbackError.asStateFlow()

    /**
     * Arms recovery from a playback error: clears the error window and forgets [loadedChannel] so
     * the paired [StreamViewModel.retry] — which resolves a *fresh* signed HLS URL (the old one
     * expires during a long outage) — makes the next [bind] actually reload.
     *
     * It deliberately does NOT re-prepare the current media: that stale URL is likely dead, so a
     * `prepare()` here would just re-raise [onPlayerError] and flash the dismissed error back, and
     * its result would be discarded by the fresh [bind] anyway (which reloads because loadedChannel
     * is now null). The single fresh reload is the whole recovery. Must run before [StreamViewModel.retry]
     * so the reload isn't guarded out.
     */
    fun retryPlayback() {
        _playbackError.value = false
        loadedChannel = null
    }

    /** Changes the rendered resolution live via ExoPlayer's adaptive track constraints (no reload). */
    fun setQuality(q: VideoQuality) {
        _quality.value = q
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, q.maxHeight)
            .build()
    }

    /** The channel whose media is currently loaded into the player (guards against reloads). */
    private var loadedChannel: String? = null

    /** True while playback was auto-paused because the app went to the background. */
    private var resumeOnForeground = false

    /**
     * Called by the fullscreen stream screen when its HLS URL resolves. Marks this channel as the
     * active (maximized) session and loads the media, but only if the channel changed — so
     * re-entering the same channel keeps the running playback untouched.
     */
    fun bind(channelLogin: String, viewers: Int, hlsPlaylistUrl: String) {
        val current = _session.value
        _session.value = if (current?.channelLogin == channelLogin) {
            current.copy(viewers = viewers, minimized = false)
        } else {
            Session(channelLogin, viewers)
        }
        if (loadedChannel != channelLogin) {
            loadedChannel = channelLogin
            _playbackError.value = false
            exoPlayer.setMediaItem(MediaItem.fromUri(hlsPlaylistUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    /** Dock the running stream into the floating corner window over the app. */
    fun minimize() = _session.update { it?.copy(minimized = true) }

    /** Expand the floating window back to the fullscreen stream screen. */
    fun maximize() = _session.update { it?.copy(minimized = false) }

    /** Close the mini-player / stop playback entirely. */
    fun close() {
        _session.value = null
        loadedChannel = null
        resumeOnForeground = false
        _quality.value = VideoQuality.AUTO
        _playbackError.value = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun setMuted(muted: Boolean) {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun setPaused(paused: Boolean) {
        exoPlayer.playWhenReady = !paused
    }

    /** Pause when the app is backgrounded (but not when entering system PiP, which never stops). */
    fun onEnterBackground() {
        if (_session.value != null && exoPlayer.playWhenReady) {
            resumeOnForeground = true
            exoPlayer.playWhenReady = false
        }
    }

    /** Resume only if we were the ones who paused it for the background. */
    fun onEnterForeground() {
        if (resumeOnForeground) {
            resumeOnForeground = false
            exoPlayer.playWhenReady = true
        }
    }

    /** Frees the media stack; call when the Activity is finishing for good. */
    fun release() {
        loadedChannel = null
        _session.value = null
        exoPlayer.release()
    }
}
