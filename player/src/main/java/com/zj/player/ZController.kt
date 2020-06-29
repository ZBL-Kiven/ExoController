package com.zj.player

import android.content.Context
import android.content.res.Resources
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.RelativeLayout.CENTER_IN_PARENT
import androidx.annotation.IntRange
import com.zj.player.UT.Controller
import com.zj.player.UT.PlayerEventController
import com.zj.player.config.VideoConfig
import java.lang.NullPointerException

/**
 * @author ZJJ on 2020/6/22.
 *
 * A controller that interacts with the user interface, player, and renderer.
 * */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ZController private constructor(private var player: ZPlayer?, viewController: Controller?) : PlayerEventController {

    private var seekProgressInterval: Long = 16
    private var videoEventListener: VideoEventListener? = null
    private var render: ZRender? = null
    private var curAccessKey: String = ""
    private var viewController: Controller? = null
        set(value) {
            if (field != null) {
                field?.onControllerBind(null)
            }
            field = value
            field?.onControllerBind(this)
        }

    init {
        this.viewController = viewController
        curAccessKey = runWithPlayer { it.setViewController(this) } ?: ""
        addRenderAndControllerView()
    }

    companion object {

        var logAble = true

        fun build(viewController: Controller): ZController {
            return build(viewController, ZPlayer(VideoConfig.create()))
        }

        fun build(viewController: Controller, config: VideoConfig): ZController {
            return build(viewController, ZPlayer(config))
        }

        fun <T : ZPlayer> build(viewController: Controller, player: T): ZController {
            return ZController(player, viewController)
        }
    }

    // Add a renderer to the video
    private fun addRenderAndControllerView() {
        if (render == null) render = ZRender(context ?: return)
        val info = viewController?.controllerInfo ?: throw NullPointerException("the controller view is required")
        val ctr = info.container
        (render?.parent as? ViewGroup)?.let {
            if (ctr != it) {
                it.removeView(render)
            }
        }
        if (ctr.width <= 0 || ctr.height <= 0) log("the controller view size is 0 , render may not to display")
        val rlp = info.layoutParams ?: getSuitParentLayoutParams(ctr)
        render?.z = Resources.getSystem().displayMetrics.density * info.zHeightDp + 0.5f
        ctr.addView(render, 0, rlp)
        log("the render view added in $ctr")
    }

    /**
     * Set an address to be played, the player will create the necessary components but will not actively play the video,
     * you need to manually call [playOrResume] or #{@link autoPlayWhenReady(true) } to play, of course, the path parameter will no longer be necessary.
     * @param autoPlay When this value is true, the video will play automatically after loading is completed.
     * */
    fun setData(url: String, autoPlay: Boolean = false) {
        log("user set new data")
        runWithPlayer { it.setData(url, autoPlay) }
    }

    /**
     * Set the minimum time difference of the automatic retrieval progress during video playback, usually in ms.
     * */
    fun setSeekInterval(interval: Long) {
        log("user set seek interval to $interval")
        this.seekProgressInterval = interval
    }

    /**
     * Set a listener for events generated during video playback.
     * */
    fun setVideoEventListener(videoEventListener: VideoEventListener?) {
        log("user set video event listener")
        this.videoEventListener = videoEventListener
    }

    /**
     * Get the current video address of the player. Local or remote.
     * */
    fun getPath(): String {
        return runWithPlayer { it.currentPlayPath() } ?: ""
    }

    /**
     * 检索视频播放位置，[i] 仅允许在 0-100 之间传递。
     * */
    fun seekTo(@IntRange(from = 0, to = 100) i: Int) {
        log("user seek the video on $i%")
        runWithPlayer { it.seekTo(i, true) }
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
    fun playOrResume(path: String = getPath()) {
        log("user call play or resume")
        if (path != getPath()) setData(path)
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

    fun isPause(): Boolean {
        log("user query cur state is pause or not")
        return runWithPlayer { it.isPause() } ?: false
    }

    fun isStop(): Boolean {
        log("user query cur state is stop or not")
        return runWithPlayer { it.isStop() } ?: true
    }

    fun isPlaying(): Boolean {
        log("user query cur state is playing or not")
        return runWithPlayer { it.isPlaying() } ?: false
    }

    /**
     * Use another View to bind to the Controller. The bound ViewController will take effect immediately and receive the method callback from the player.
     * */
    fun updateViewController(viewController: Controller) {
        if (this.viewController === viewController) return
        log("user update the view controller names ${viewController::class.java.simpleName}")
        this.viewController = viewController
        runWithPlayer { it.updateControllerState() }
        addRenderAndControllerView()
    }

    /**
     * Completely recycle a Controller, after which this instance will be completely invalid.
     * */
    fun release() {
        log("user released all player")
        (render?.parent as? ViewGroup)?.removeView(render)
        render = null
        player?.release()
        viewController?.onStop("", true)
        viewController = null
        videoEventListener = null
        curAccessKey = " - released - "
        seekProgressInterval = -1
        player = null
    }


    private fun <T> runWithPlayer(block: (ZPlayer) -> T): T? {
        return try {
            player?.let {
                block(it)
            } ?: throw NullPointerException("are you`re forgot setting a Player in to the video view controller? ,now it used the default player.")
        } catch (e: java.lang.Exception) {
            log("in VideoViewController.runWithPlayer error case: - ${e.message}")
            null
        }
    }

    override fun getProgressInterval(): Long {
        return seekProgressInterval
    }

    override fun onError(e: Exception?) {
        viewController?.onError(e)
        videoEventListener?.onError(e)
    }

    override fun getPlayerView(): ZRender? {
        return render
    }

    override fun onLog(case: String, curPath: String, accessKey: String, modeName: String) {
        videoEventListener?.onLog(case, curPath, accessKey, modeName)
    }

    override fun onLoading(path: String?, isRegulate: Boolean) {
        log("on loading ...")
        viewController?.onLoading(path, isRegulate)
    }

    override fun onPause(path: String?, isRegulate: Boolean) {
        log("on pause ...")
        viewController?.onPause(path, isRegulate)
    }

    override fun onFirstFrameRender() {
        log("the video had rendered a first frame !")
    }

    override fun onSeekChanged(seek: Int, buffered: Int, fromUser: Boolean, videoSize: Long) {
        if (fromUser) log("on seek changed to $seek")
        viewController?.onSeekChanged(seek, buffered, fromUser, videoSize)
    }

    override fun onSeekingLoading(path: String?, isRegulate: Boolean) {
        viewController?.onSeekingLoading(path)
    }

    override fun onPrepare(path: String?, videoSize: Long, isRegulate: Boolean) {
        log("on prepared ...")
        viewController?.onPrepare(path, videoSize, isRegulate)
    }

    override fun getContext(): Context? {
        return viewController?.context
    }

    override fun onPlay(path: String?, isRegulate: Boolean) {
        log("on play ...")
        viewController?.onPlay(path, isRegulate)
    }

    override fun onStop(path: String?, isRegulate: Boolean) {
        log("on stop ...")
        viewController?.onStop(path, isRegulate)
    }

    override fun onCompleted(path: String?, isRegulate: Boolean) {
        log("on completed ...")
        viewController?.onCompleted(path, isRegulate)
    }

    override fun completing(path: String?, isRegulate: Boolean) {
        log("on completing ...")
        viewController?.completing(path, isRegulate)
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

    private fun log(s: String) {
        if (logAble) onLog(s, getPath(), curAccessKey, "ZController")
    }
}