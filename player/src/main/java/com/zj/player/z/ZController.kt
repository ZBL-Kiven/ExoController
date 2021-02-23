package com.zj.player.z

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.RelativeLayout.CENTER_IN_PARENT
import androidx.annotation.IntRange
import com.zj.player.base.BasePlayer
import com.zj.player.base.BaseRender
import com.zj.player.ut.Constance.CORE_LOG_ABLE
import com.zj.player.ut.Controller
import com.zj.player.ut.PlayerEventController
import com.zj.player.logs.BehaviorData
import com.zj.player.logs.BehaviorLogsTable
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.ut.PlayStateChangeListener
import java.lang.IllegalArgumentException
import java.lang.NullPointerException

/**
 * @author ZJJ on 2020/6/22.
 *
 * A controller that interacts with the user interface, player, and renderer.
 * */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ZController<P : BasePlayer<R>, R : BaseRender> internal constructor(private var player: P?, private var renderCls: Class<R>, viewController: Controller?) : PlayerEventController<R> {

    private var seekProgressInterval: Long = 16
    private var curAccessKey: String = ""
    private var render: R? = null
    private var isPausedByLifecycle = false
    private var isIgnoreNullControllerGlobal = false
    private var playingStateListener: PlayStateChangeListener? = null
    private var internalPlayingStateListener: PlayStateChangeListener? = null
    private var viewController: Controller? = null
        set(value) {
            if (field != null) {
                field?.onControllerBind(null)
            }
            field = value
            field?.onControllerBind(this)
            isIgnoreNullControllerGlobal = value == null
        }

    init {
        this.viewController = viewController
        curAccessKey = runWithPlayer { it.setController(this) } ?: ""
        withRenderAndControllerView(false)
    }

    companion object {
        private const val releaseKey = " - released - "
    }

    private fun withRenderAndControllerView(needed: Boolean): Controller? {
        try {
            val c = getController()
            if (c == null) {
                (render?.parent as? ViewGroup)?.removeView(render)
                stopNow(false, isRegulate = false)
                return null
            }
            val info = c.controllerInfo ?: throw NullPointerException("the controller view is required")
            val ctr = info.container ?: throw NullPointerException("the view controller post a null container parent , which the renderer add to?")
            if (render == null) render = BaseRender.create(ctr.context ?: throw NullPointerException("context should not be null!"), renderCls)
            render?.let { r ->
                r.init(this)
                val parent = (r.parent as? ViewGroup) ?: if (r.parent != null) throw IllegalArgumentException("the renderer added in and without a viewGroup?") else null
                if (parent == ctr) {
                    return@withRenderAndControllerView c
                } else {
                    if (parent != null) {
                        parent.removeView(r);log("the render view has removed form $parent")
                        if (!needed) return@withRenderAndControllerView null
                    }
                }
            } ?: throw NullPointerException("the renderer created but not found ? where is changing it?")
            if (ctr.measuredWidth <= 0 || ctr.measuredHeight <= 0) log("the controller view size is 0 , render may not to display")
            val rlp = info.layoutParams ?: getSuitParentLayoutParams(ctr)
            render?.z = Resources.getSystem().displayMetrics.density * info.zHeightDp + 0.5f
            ctr.addView(render, 0, rlp)
            log("the render view added in $ctr")
            return c
        } catch (e: Exception) {
            stopNow(false, isRegulate = true)
            ZPlayerLogs.onError(e)
        }
        return null
    }

    /**
     * Set an address to be played, the player will create the necessary components but will not actively play the video,
     * you need to manually call [playOrResume] or #{@link autoPlayWhenReady(true) } to play, of course, the path parameter will no longer be necessary.
     * @param autoPlay When this value is true, the video will play automatically after loading is completed.
     * */
    fun setData(url: String, autoPlay: Boolean = false, callId: Any? = null) {
        log("user set new data", BehaviorLogsTable.setNewData(url, callId, autoPlay))
        runWithPlayer { it.setData(url, autoPlay, callId) }
    }

    /**
     * Set the minimum time difference of the automatic retrieval progress during video playback, usually in ms.
     * */
    fun setSeekInterval(interval: Long) {
        log("user set seek interval to $interval")
        this.seekProgressInterval = interval
    }

    /**
     * Get the current video address of the player. Local or remote.
     * */
    fun getPath(): String {
        return runWithPlayer { it.currentPlayPath() } ?: ""
    }

    /**
     * Get the current video playing back call id.
     * */
    fun getCallId(): Any? {
        return runWithPlayer { it.currentCallId() }
    }

    /**
     * Retrieve the video playback position, [i] can only between 0-100.
     * */
    fun seekTo(@IntRange(from = 0, to = 100) i: Int, fromUser: Boolean) {
        log("user seek the video on $i%")
        runWithPlayer { it.seekTo(i, fromUser) }
    }

    /**
     * On/Off No matter what the current state of the player is, when the first frame of the video is loaded, it will start to play automatically.
     * */
    fun autoPlayWhenReady(autoPlay: Boolean) {
        log("user set auto play when ready")
        runWithPlayer { it.autoPlay(autoPlay) }
    }

    /**
     * Call this method to start automatic playback after the player processes playable frames.
     * */
    fun playOrResume(path: String = getPath(), callId: Any? = null) {
        log("user call play or resume")
        if (path != getPath()) setData(path, false, callId)
        runWithPlayer { it.play() }
    }

    fun pause() {
        log("user call pause")
        runWithPlayer { it.pause() }
    }

    fun stop() {
        log("user call stop")
        runWithPlayer { it.stop() }
    }

    fun stopNow(withNotify: Boolean = false, isRegulate: Boolean = false) {
        log("user call stop --now")
        runWithPlayer { it.stopNow(withNotify, isRegulate) }
    }

    fun setSpeed(s: Float) {
        log("user set speed to $s")
        runWithPlayer { it.setSpeed(s) }
    }

    fun setVolume(volume: Int, maxVolume: Int) {
        log("user set volume to $volume")
        runWithPlayer { it.setVolume(volume, maxVolume) }
    }

    fun isPause(accurate: Boolean = false): Boolean {
        log("user query cur state is pause or not")
        return runWithPlayer { it.isPause(accurate) } ?: false
    }

    fun isStop(accurate: Boolean = false): Boolean {
        log("user query cur state is stop or not")
        return runWithPlayer { it.isStop(accurate) } ?: true
    }

    fun isPlaying(accurate: Boolean = false): Boolean {
        log("user query cur state is playing or not")
        return runWithPlayer { it.isPlaying(accurate) } ?: false
    }

    fun isReady(accurate: Boolean = false): Boolean {
        log("user query cur state is ready or not")
        return runWithPlayer { it.isReady(accurate) } ?: false
    }

    fun isLoading(accurate: Boolean = false): Boolean {
        log("user query cur state is loading or not")
        return runWithPlayer { it.isLoading(accurate) } ?: false
    }

    fun isLoadData(): Boolean {
        log("user query cur state is  loaded data")
        return runWithPlayer { it.isLoadData() } ?: false
    }

    fun isDestroyed(accurate: Boolean = false): Boolean {
        log("user query cur state is destroy or not")
        return runWithPlayer { it.isDestroyed(accurate) } ?: true
    }

    fun getCurVolume(): Int {
        return player?.getVolume() ?: 0
    }

    fun getCurSpeed(): Float {
        return player?.getSpeed() ?: 1f
    }

    fun isDefaultPlayerType(): Boolean {
        return checkPlayerType(ZVideoPlayer::class.java)
    }

    fun isDefaultRendererType(): Boolean {
        return checkRendererType(ZRender::class.java)
    }

    fun checkPlayerType(cls: Class<*>): Boolean {
        return player?.let { cls == it::class.java } ?: false
    }

    fun checkRendererType(cls: Class<*>): Boolean {
        return render?.let { cls == it::class.java } ?: false
    }

    /**
     * Use another View to bind to the Controller. The bound ViewController will take effect immediately and receive the method callback from the player.
     * */
    fun updateViewController(viewController: Controller?) {
        this.viewController = viewController
        if (viewController != null) {
            if (this.viewController != viewController) {
                withRenderAndControllerView(false)
                log("user update the view controller names ${viewController::class.java.simpleName}")
                syncPlayerState()
            }
        } else {
            withRenderAndControllerView(true)
        }
    }

    fun syncPlayerState() {
        if (viewController != null) {
            runWithPlayer { it.updateControllerState() }
        }
    }

    fun setOnPlayingStateChangedListener(l: PlayStateChangeListener) {
        this.playingStateListener = l
    }

    /**
     * recycle a Controller in Completely, after which this instance will be invalid.
     * */
    fun release() {
        isPausedByLifecycle = false
        (render?.parent as? ViewGroup)?.removeView(render)
        render?.release()
        render = null
        player?.stopNow(false, isRegulate = false)
        player?.release()
        viewController?.let {
            it.onStop("", true)
            it.onDestroy("", true)
        }
        viewController = null
        player = null
        seekProgressInterval = -1
        curAccessKey = releaseKey
    }


    private fun <T> runWithPlayer(throwMust: Boolean = true, block: (P) -> T): T? {
        return try {
            player?.let {
                block(it) ?: return@runWithPlayer null
            } ?: {
                if (curAccessKey != releaseKey) {
                    throw NullPointerException("are you forgot setting a Player in to the video view controller? ,now it used the default player.")
                } else null
            }.invoke()
        } catch (e: java.lang.Exception) {
            if (throwMust) ZPlayerLogs.onError("in VideoViewController.runWithPlayer error case: - ${e.message}")
            null
        }
    }

    override fun getProgressInterval(): Long {
        return seekProgressInterval
    }

    override fun getPlayerView(): R? {
        return render
    }

    override fun getContext(): Context? {
        return getController()?.context
    }

    override fun keepScreenOnWhenPlaying(): Boolean {
        return getController()?.keepScreenOnWhenPlaying() ?: true
    }

    override fun onFirstFrameRender() {
        log("the video had rendered a first frame !")
    }

    override fun onSeekChanged(seek: Int, buffered: Int, fromUser: Boolean, played: Long, videoSize: Long) {
        if (fromUser) log("on seek changed to $seek")
        withRenderAndControllerView(true)?.onSeekChanged(seek, buffered, fromUser, played, videoSize)
    }

    override fun onSeekingLoading(path: String?, isRegulate: Boolean) {
        log("on video seek loading ...", BehaviorLogsTable.controllerState("onSeekLoading", getCallId(), getPath()))
        onPlayingStateChanged(false, "buffering")
        withRenderAndControllerView(true)?.onSeekingLoading(path)
    }

    override fun onLoading(path: String?, isRegulate: Boolean) {
        log("on video loading ...", BehaviorLogsTable.controllerState("loading", getCallId(), getPath()))
        onPlayingStateChanged(false, "loading")
        withRenderAndControllerView(true)?.onLoading(path, isRegulate)
    }

    override fun onPrepare(path: String?, videoSize: Long, isRegulate: Boolean) {
        log("on video prepare ...", BehaviorLogsTable.controllerState("onPrepare", getCallId(), getPath()))
        onPlayingStateChanged(false, "prepared")
        withRenderAndControllerView(true)?.onPrepare(path, videoSize, isRegulate)
    }

    override fun onPlay(path: String?, isRegulate: Boolean) {
        log("on video playing ...", BehaviorLogsTable.controllerState("onPlay", getCallId(), getPath()))
        onPlayingStateChanged(true, "play")
        checkIsMakeScreenOn(true)
        withRenderAndControllerView(true)?.onPlay(path, isRegulate)
    }

    override fun onPause(path: String?, isRegulate: Boolean) {
        log("on video loading ...", BehaviorLogsTable.controllerState("onPause", getCallId(), getPath()))
        onPlayingStateChanged(false, "pause")
        checkIsMakeScreenOn(false)
        withRenderAndControllerView(true)?.onPause(path, isRegulate)
    }

    override fun onStop(notifyStop: Boolean, path: String?, isRegulate: Boolean) {
        log("on video stop ...", BehaviorLogsTable.controllerState("onStop", getCallId(), getPath()))
        onPlayingStateChanged(false, "stop")
        checkIsMakeScreenOn(false)
        val c = withRenderAndControllerView(false)
        if (notifyStop) c?.onStop(path, isRegulate)
    }

    override fun completing(path: String?, isRegulate: Boolean) {
        log("on video completing ...", BehaviorLogsTable.controllerState("completing", getCallId(), getPath()))
        onPlayingStateChanged(false, "completing")
        withRenderAndControllerView(true)?.completing(path, isRegulate)
    }

    override fun onCompleted(path: String?, isRegulate: Boolean) {
        log("on video completed ...", BehaviorLogsTable.controllerState("onCompleted", getCallId(), getPath()))
        onPlayingStateChanged(false, "completed")
        checkIsMakeScreenOn(false)
        withRenderAndControllerView(false)?.onCompleted(path, isRegulate)
    }

    override fun onError(e: Exception?) {
        withRenderAndControllerView(false)?.onError(e)
        onPlayingStateChanged(false, "error")
        ZPlayerLogs.onError(e, true)
    }

    override fun onPlayerInfo(volume: Int, speed: Float) {
        log("on video upload player info ...", BehaviorLogsTable.controllerState("onUploadPlayerInfo", getCallId(), getPath()))
        withRenderAndControllerView(false)?.updateCurPlayerInfo(volume, speed)
    }

    private fun getSuitParentLayoutParams(v: ViewGroup): ViewGroup.LayoutParams {
        return when (v) {
            is FrameLayout -> FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                this.gravity = Gravity.CENTER
            }
            is RelativeLayout -> RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply {
                this.addRule(CENTER_IN_PARENT)
            }
            is LinearLayout -> LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                this.gravity = Gravity.CENTER
            }
            else -> ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    fun getController(): Controller? {
        return viewController
    }

    private fun onPlayingStateChanged(isPlaying: Boolean, desc: String) {
        try {
            playingStateListener?.onState(isPlaying, desc, this)
        } catch (e: Throwable) {
            playingStateListener?.onStateInvokeError(e)
        }
        internalPlayingStateListener?.onState(isPlaying, desc, this)
    }

    private fun log(s: String, bd: BehaviorData? = null) {
        recordLogs(s, "ZController", bd)
    }

    internal fun recordLogs(s: String, modeName: String, bd: BehaviorData? = null) {
        if (CORE_LOG_ABLE) ZPlayerLogs.onLog(s, getPath(), curAccessKey, modeName, bd)
    }

    internal fun bindInternalPlayStateListener(l: PlayStateChangeListener) {
        this.internalPlayingStateListener = l
    }

    /**
     * Keep screen on
     * */
    @Suppress("DEPRECATION") val stableFlag = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
    @Suppress("DEPRECATION") val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON

    private fun checkIsMakeScreenOn(isScreen: Boolean) {
        try {
            (context as? Activity)?.let { act ->
                act.runOnUiThread {
                    act.window?.let {
                        if (keepScreenOnWhenPlaying()) {
                            if (isScreen) {
                                it.addFlags(flag or stableFlag)
                            } else {
                                it.clearFlags(flag)
                            }
                        }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}