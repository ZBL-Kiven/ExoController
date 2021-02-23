package com.zj.youtube

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.CallSuper
import com.zj.youtube.constance.PlayerConstants
import com.zj.youtube.options.IFramePlayerOptions
import com.zj.youtube.proctol.YouTubePlayerListener
import com.zj.youtube.utils.PendingLoadTask
import com.zj.youtube.utils.Utils
import java.lang.Exception
import kotlin.math.max
import kotlin.math.min

abstract class YoutubeDelegate(debugAble: Boolean) : YouTubePlayerListener {

    var curPath: String = ""
    var curBuffering = 0.0f
    var curPlayingDuration = 0L
    var totalDuration = 0L
    var curPlayingRate = 0f
    var curVolume = 1
    var isPageReady = false
    private var pendingIfNotReady: PendingLoadTask? = null
    protected var playerState: PlayerConstants.PlayerState = PlayerConstants.PlayerState.UNKNOWN

    init {
        if (debugAble) Utils.openDebug()
    }

    abstract fun onSeekChanged(fromUser: Boolean)

    abstract fun onSeekParsed(progress: Int, fromUser: Boolean)

    abstract fun getWebView(): WebView?

    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            HANDLE_SEEK -> onSeekParsed(it.arg1, it.arg2 == 0)
        }
        return@Handler false
    }

    private fun runWithWebView(func: (WebView) -> Unit) {
        try {
            mainThreadHandler.post {
                getWebView()?.let {
                    func(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initYoutubeScript(playerOptions: IFramePlayerOptions) {
        val ctx = getWebView()?.context ?: return
        val htmlPage = Utils.readHTMLFromUTF8File(ctx.resources.openRawResource(R.raw.youtube_player_bridge)).replace("<<injectedPlayerVars>>", playerOptions.toString())
        runWithWebView { it.loadDataWithBaseURL(playerOptions.getOrigin(), htmlPage, "text/html", "utf-8", null) }
    }

    fun loadVideoById(id: String, startSeconds: Float, suggestionQuality: String, pendingIfNotReady: PendingLoadTask) {
        if (isPageReady) {
            this.curPath = id
            onStateChange(PlayerConstants.PlayerState.LOADING)
            runWithWebView { it.loadUrl("javascript:loadVideoById(\'$id\', $startSeconds, \'$suggestionQuality\')") }
        } else {
            if (this.pendingIfNotReady?.path != pendingIfNotReady.path) {
                this.pendingIfNotReady?.let { mainThreadHandler.removeCallbacks(it) }
                this.pendingIfNotReady = pendingIfNotReady
            }
        }
    }

    fun play() {
        runWithWebView { it.loadUrl("javascript:playVideo()") }
    }

    fun pause() {
        runWithWebView { it.loadUrl("javascript:pauseVideo()") }
    }

    fun stop() {
        pendingIfNotReady = null
        playerState = PlayerConstants.PlayerState.STOP
        mainThreadHandler.removeCallbacksAndMessages(null)
    }

    fun setVolume(volumePercent: Int) {
        curVolume = volumePercent
        runWithWebView {
            if (volumePercent <= 0) it.loadUrl("javascript:mute()") else {
                it.loadUrl("javascript:unMute()")
                if (volumePercent in 0..100) it.loadUrl("javascript:setVolume($volumePercent)")
            }
        }
    }

    fun setPlaybackRate(rate: Float) {
        runWithWebView { it.loadUrl("javascript:setPlaybackRate($rate)") }
    }

    fun destroy() {
        pendingIfNotReady = null
        mainThreadHandler.removeCallbacksAndMessages(null)
        playerState = PlayerConstants.PlayerState.UNKNOWN
        runWithWebView {
            it.clearHistory()
            it.clearFormData()
            it.removeAllViews()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        curPath = ""
    }

    fun seekTo(progress: Int, fromUser: Boolean) {
        mainThreadHandler.removeMessages(HANDLE_SEEK)
        mainThreadHandler.sendMessageDelayed(Message.obtain().apply {
            what = HANDLE_SEEK
            arg1 = progress
            arg2 = if (fromUser) 0 else 1
        }, 200)
    }

    fun seekNow(progress: Int, fromUser: Boolean) {
        val seekProgress = (max(0f, min(100, progress) / 100f * max(totalDuration, 1) - 1) / 1000f).toLong()
        curPlayingDuration = seekProgress
        runWithWebView {
            it.loadUrl("javascript:seekTo($seekProgress,$fromUser)")
            onSeekChanged(fromUser)
        }
    }

    companion object {
        private const val HANDLE_SEEK = 0x165771
    }

    override fun onYouTubeIFrameAPIReady() {
        runWithWebView { it.loadUrl("javascript:hideInfo()") }
    }

    @CallSuper
    override fun onReady(totalDuration: Long) {
        this.isPageReady = true
        this.totalDuration = totalDuration * 1000L
        pendingIfNotReady?.let { mainThreadHandler.post(it) }
    }

    @CallSuper
    override fun onStateChange(state: PlayerConstants.PlayerState) {
        runWithWebView { it.loadUrl("javascript:hideInfo()") }
        Utils.log("on player state changed from $playerState to $state")
        this.playerState = state
    }

    override fun onPlaybackQualityChange(playbackQuality: PlayerConstants.PlaybackQuality) {
        Utils.log("the cur play back quality is ${playbackQuality.name}")
    }

    override fun onPlaybackRateChange(playbackRate: PlayerConstants.PlaybackRate) {
        Utils.log("the cur play back rate is ${playbackRate.name}")
        curPlayingRate = playbackRate.rate
    }

    @CallSuper
    override fun onError(error: PlayerConstants.PlayerError) {
        Utils.log("play error case: ${error.name}")
        playerState = PlayerConstants.PlayerState.UNKNOWN.setFrom(error.name)
    }

    @CallSuper
    override fun onCurrentSecond(second: Long) {
        curPlayingDuration = second * 1000L
        mainThreadHandler.post { onSeekChanged(false) }
    }

    @CallSuper
    override fun onVideoDuration(duration: Long) {
        Utils.log("on video duration parsed : $duration")
        totalDuration = duration * 1000L
        mainThreadHandler.post { onSeekChanged(false) }
    }

    @CallSuper
    override fun onVideoLoadedFraction(loadedFraction: Float) {
        curBuffering = loadedFraction
        mainThreadHandler.post { onSeekChanged(false) }
    }

    @CallSuper
    override fun onVideoUrl(videoUrl: String) {
        Utils.log("the youtube video path received : $videoUrl")
    }

    @CallSuper
    override fun onApiChange() {
    }

    fun isLoading(accurate: Boolean): Boolean {
        return if (accurate) (playerState.level == PlayerConstants.PlayerState.LOADING.level || playerState.level == PlayerConstants.PlayerState.BUFFERING.level) else playerState.level >= PlayerConstants.PlayerState.BUFFERING.level
    }

    fun isReady(accurate: Boolean): Boolean {
        return if (accurate) playerState.level == PlayerConstants.PlayerState.PREPARED.level else playerState.level >= PlayerConstants.PlayerState.PREPARED.level || playerState == PlayerConstants.PlayerState.PAUSED
    }

    fun isPlaying(accurate: Boolean): Boolean {
        return if (accurate) playerState.level == PlayerConstants.PlayerState.PLAYING.level else playerState.level >= PlayerConstants.PlayerState.PLAYING.level
    }

    fun isPause(accurate: Boolean): Boolean {
        return if (accurate) playerState == PlayerConstants.PlayerState.PAUSED else playerState.level <= PlayerConstants.PlayerState.PAUSED.level
    }

    fun isStop(accurate: Boolean): Boolean {
        return if (accurate) playerState.level == PlayerConstants.PlayerState.STOP.level else playerState.level <= PlayerConstants.PlayerState.STOP.level
    }

    fun isDestroyed(accurate: Boolean): Boolean {
        return if (accurate) playerState.level == PlayerConstants.PlayerState.UNKNOWN.level else playerState.level <= PlayerConstants.PlayerState.UNKNOWN.level
    }
}