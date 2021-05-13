package com.zj.player.z

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.res.Resources
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.annotation.CallSuper
import androidx.annotation.IntRange
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import com.zj.player.R
import com.zj.player.anim.ZFullValueAnimator
import com.zj.player.base.InflateInfo
import com.zj.player.base.LoadingMode
import com.zj.player.full.*
import com.zj.player.full.FullContentListener
import com.zj.player.full.FullScreenListener
import com.zj.player.full.GestureTouchListener
import com.zj.player.img.scale.ImageViewTouchEnableIn
import com.zj.player.img.scale.TouchScaleImageView
import com.zj.player.logs.BehaviorData
import com.zj.player.logs.BehaviorLogsTable
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.ut.Constance
import com.zj.player.ut.Controller
import com.zj.player.ut.PlayQualityLevel
import com.zj.player.view.QualityMenuView
import com.zj.player.view.VideoLoadingView
import com.zj.player.view.VideoRootView
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * @author ZJJ on 2020.6.16
 *
 * A user operation interface based on an instance of the framework model.
 * During use, the operation interface only needs to be concerned about what behavior events are received and make corresponding interactive responses.
 * The operation interface does not need to carry the playback controller, renderer, decoder and other components at any time, which means that it is just a simple View when you are not playing.
 * At the same time, any operation interface you define based on the Controller interface is It can be used as a container for video reception and display at any time without interruption.
 * */

@Suppress("unused", "MemberVisibilityCanBePrivate", "InflateParams", "ClickableViewAccessibility")
open class ZVideoView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, def: Int = 0) : FrameLayout(context, attributeSet, def), Controller {

    companion object {
        private const val dismissFullTools = 7817
        private const val loadingModeDelay = 7716
        var muteGlobalDefault: Boolean = false
        const val LOCK_SCREEN_UNSPECIFIED = -1
        const val LOCK_SCREEN_LANDSCAPE = 0
        const val LOCK_SCREEN_PORTRAIT = 1

        /**
         * After setting this property, all ViewController instances configured with app:useMuteGlobal in xml take effect。
         * @see muteIsUseGlobal  bind to [muteGlobalDefault]
         * */
        internal fun setGlobalMuteDefault(isMute: Boolean) {
            muteGlobalDefault = isMute
        }
    }

    private val mHandler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            dismissFullTools -> onFullTools(false)
            loadingModeDelay -> if (controller?.isLoading(true) == true) loadingView?.setMode(VideoLoadingView.DisplayMode.LOADING)
        }
        return@Handler false
    }

    protected var autoFullTools = true
    protected var autoFullInterval = 3000
    protected var loadingIgnoreInterval = 0
    protected var vPlay: View? = null
    protected var tvStart: TextView? = null
    protected var tvEnd: TextView? = null
    protected var seekBar: SeekBar? = null
    protected var seekBarSmall: SeekBar? = null
    protected var fullScreen: View? = null
    protected var qualityView: TextView? = null
    protected var speedView: TextView? = null
    protected var muteView: View? = null
    protected var lockScreen: View? = null
    private var loadingView: VideoLoadingView? = null
    protected var bottomToolsBar: LinearLayout? = null
    protected var topToolsBar: LinearLayout? = null
    protected var videoOverrideImageView: TouchScaleImageView? = null
    protected var videoOverrideImageShaderView: ImageView? = null
    protected var videoRoot: VideoRootView? = null
    protected var controller: ZController<*, *>? = null
    protected var autoPlay = false
    protected var isFull = false
    protected var isStartTrack = false
    protected var isInterruptPlayBtnAnim = true
    protected var isFullingOrDismissing = false
    protected var isTickingSeekBarFromUser: Boolean = false
    protected var fullScreenContentLayoutId: Int = -1
    protected var fullScreenSupported = false
    protected var alphaAnimViewsGroup: MutableList<View?>? = null
    protected var onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null
    protected var isDefaultMaxScreen: Boolean = false
    protected var fullMaxScreenEnable: Boolean = true
    protected var scrollXEnabled: Boolean = true
    protected var qualityEnable = Constance.qualityEnable; private set
    protected var defaultControllerVisibility = Constance.defaultControllerVisibility; private set
    protected var muteIconEnable = Constance.muteIconEnable; private set
    protected var playIconEnable = Constance.playIconEnable; private set
    protected var speedIconEnable = Constance.speedIconEnable; private set
    protected var secondarySeekBarEnable = Constance.secondarySeekBarEnable; private set
    protected var fullScreenEnable = Constance.fullScreenEnAble; private set
    protected var lockRotationEnable = Constance.lockRotationEnable; private set
    protected var keepScreenOnWhenPlaying: Boolean = false
    protected var enablePlayAnimation: Boolean = true
    protected var playAutoFullScreen = false
    protected var videoTotalTime: Long = 0L
    protected var curPlayingTime: Long = 0L
    protected var curBufferedTime: Long = 0L
    private var curSpeedIndex = 0
    private var fullScreenTransactionTime = 250
    private var fullScreenConfig: FullScreenConfig? = null
    private var muteDefault: Boolean = false
    private var muteIsUseGlobal: Boolean = false
    private var isTransactionNavigation: Boolean = false
    private var isAllowReversePortrait: Boolean = false
    open val supportedSpeedList = arrayListOf(1.0f, 1.5f, 2f)
    private var lockScreenRotation: Int = LOCK_SCREEN_UNSPECIFIED
    private var menuView: QualityMenuView? = null
    var isFullScreen = false
        private set(value) {
            if (field == value) return
            field = value
            fullScreen?.isSelected = value
        }
    var isFullMaxScreen = false; private set
    var isPlayable = true
        set(value) {
            if (value != field) {
                vPlay?.visibility = if (value && playIconEnable > 1) View.VISIBLE else GONE
                checkVisibleViews()
                field = value
            }
        }

    private var touchListener: GestureTouchListener? = object : GestureTouchListener({ isInterrupted() }) {

        override fun onEventEnd(formTrigDuration: Float, parseAutoScale: Boolean): Boolean {
            return fullScreenConfig?.onEventEnd(formTrigDuration, parseAutoScale) ?: true
        }

        override fun onClick(x: Float, y: Float) {
            if (isPlayable) onRootClick(x, y) else onRootDoubleClick(x, y)
        }

        override fun onDoubleClick(x: Float, y: Float) {
            if (isPlayable) if (isFullScreen) {
                onFullScreenViewDoubleClick(x, y)
            } else {
                onRootDoubleClick(x, y)
            }
        }

        override fun onTracked(isStart: Boolean, offsetX: Float, offsetY: Float, easeY: Float, orientation: TrackOrientation, formTrigDuration: Float) {
            fullScreenConfig?.onTracked(isStart, if (scrollXEnabled) offsetX else 0f, offsetY, easeY, formTrigDuration)
        }

        override fun onTouchActionEvent(event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean {
            return fullScreenConfig?.let {
                if (it.isFullMaxScreen() == false) this@ZVideoView.onTouchActionEvent(videoRoot, event, lastX, lastY, orientation) else false
            } ?: false
        }
    }

    init {
        initView(context, attributeSet)
        initListener()
        initSeekBar()
    }

    /**
     * config it in [R.styleable.ZVideoView]
     * */
    private fun initView(context: Context, attributeSet: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attributeSet, R.styleable.ZVideoView)
        try {
            val defaultControllerVisibility = ta.getInt(R.styleable.ZVideoView_defaultControllerVisibility, Constance.defaultControllerVisibility)
            val playIconEnable = ta.getInt(R.styleable.ZVideoView_playIconEnable, Constance.playIconEnable)
            val muteIconEnable = ta.getInt(R.styleable.ZVideoView_muteIconEnable, Constance.muteIconEnable)
            val speedIconEnable = ta.getInt(R.styleable.ZVideoView_speedIconEnable, Constance.speedIconEnable)
            val secondarySeekBarEnable = ta.getInt(R.styleable.ZVideoView_secondarySeekBarEnable, Constance.secondarySeekBarEnable)
            val fullScreenEnable = ta.getInt(R.styleable.ZVideoView_fullScreenEnable, Constance.fullScreenEnAble)
            val lockRotationEnable = ta.getInt(R.styleable.ZVideoView_lockRotationEnable, Constance.lockRotationEnable)
            qualityEnable = ta.getInt(R.styleable.ZVideoView_qualityEnable, Constance.qualityEnable)
            autoFullTools = ta.getBoolean(R.styleable.ZVideoView_autoFullTools, false)
            playAutoFullScreen = ta.getBoolean(R.styleable.ZVideoView_playAutoFullScreen, false)
            autoFullInterval = ta.getInt(R.styleable.ZVideoView_autoFullInterval, 3000)
            fullScreenTransactionTime = ta.getInt(R.styleable.ZVideoView_fullScreenTransactionTime, fullScreenTransactionTime)
            fullMaxScreenEnable = ta.getBoolean(R.styleable.ZVideoView_fullMaxScreenEnable, Constance.fullMaxScreenEnable)
            isDefaultMaxScreen = ta.getBoolean(R.styleable.ZVideoView_isDefaultMaxScreen, Constance.isDefaultMaxScreen)
            isAllowReversePortrait = ta.getBoolean(R.styleable.ZVideoView_isAllowReversePortrait, Constance.isAllowReversePortrait)
            lockScreenRotation = ta.getInt(R.styleable.ZVideoView_lockScreenRotation, LOCK_SCREEN_UNSPECIFIED)
            scrollXEnabled = ta.getBoolean(R.styleable.ZVideoView_scrollXEnabled, true)
            muteIsUseGlobal = ta.getBoolean(R.styleable.ZVideoView_useMuteGlobal, false)
            muteDefault = ta.getBoolean(R.styleable.ZVideoView_muteDefault, false)
            keepScreenOnWhenPlaying = ta.getBoolean(R.styleable.ZVideoView_keepScreenOnWhenPlaying, false)
            isTransactionNavigation = ta.getBoolean(R.styleable.ZVideoView_isTransactionNavigation, false)
            enablePlayAnimation = ta.getBoolean(R.styleable.ZVideoView_enablePlayAnimation, true)
            val view = LayoutInflater.from(context).inflate(R.layout.z_player_video_view, null, false)
            addView(view, LayoutParams(MATCH_PARENT, MATCH_PARENT))
            videoRoot = view?.findViewById(R.id.z_player_video_root)
            tvStart = view?.findViewById(R.id.z_player_video_preview_tv_start)
            tvEnd = view?.findViewById(R.id.z_player_video_preview_tv_end)
            topToolsBar = view?.findViewById(R.id.z_player_video_preview_top_bar)
            videoOverrideImageView = view?.findViewById(R.id.z_player_video_thumb)
            videoOverrideImageShaderView = view?.findViewById(R.id.z_player_video_background)
            loadingView = view?.findViewById(R.id.z_player_video_preview_vs_loading)
            seekBar = view?.findViewById(R.id.z_player_video_preview_sb)
            menuView = view?.findViewById(R.id.z_player_video_preview_v_menu)
            setPlayIconEnable(playIconEnable)
            setMuteIconEnable(muteIconEnable)
            setSpeedIconEnable(speedIconEnable)
            setFullScreenEnable(fullScreenEnable)
            setSecondarySeekBarEnable(secondarySeekBarEnable)
            setBottomToolsEnable(defaultControllerVisibility)
            setLockRotationEnable(lockRotationEnable)
            isFull = bottomToolsBar?.visibility == View.VISIBLE
            speedView?.text = context.getString(R.string.z_player_str_speed, "1")
            touchListener?.let {
                setTouchTorques(context, it)
            }
            loadingView?.let {
                loadingIgnoreInterval = ta.getInt(R.styleable.ZVideoView_loadingIgnoreTs, 0)
                val loadingBackgroundColor = ta.getColor(R.styleable.ZVideoView_loadingBackgroundColor, ContextCompat.getColor(context, R.color.z_player_color_trans_50))
                val loadingRes = ta.getResourceId(R.styleable.ZVideoView_loadingRes, R.drawable.z_player_loading_progressbar)
                val failRes = ta.getResourceId(R.styleable.ZVideoView_failRes, R.mipmap.z_player_video_loading_fail)
                val loadingIconWidth = ta.getDimension(R.styleable.ZVideoView_loadingIconWidth, 0f)
                val loadingIconHeight = ta.getDimension(R.styleable.ZVideoView_loadingIconHeight, 0f)
                val failIconWidth = ta.getDimension(R.styleable.ZVideoView_failIconWidth, 0f)
                val failIconHeight = ta.getDimension(R.styleable.ZVideoView_failIconHeight, 0f)
                val hintTextColor = ta.getColor(R.styleable.ZVideoView_hintTextColor, ContextCompat.getColor(context, R.color.z_player_color_loading))
                val refreshTextColor = ta.getColor(R.styleable.ZVideoView_refreshTextColor, ContextCompat.getColor(context, R.color.z_player_color_gray))
                val loadingText = ta.getString(R.styleable.ZVideoView_loadingText)
                val failText = ta.getString(R.styleable.ZVideoView_failText)
                val refreshHintText = ta.getString(R.styleable.ZVideoView_refreshHintText)
                val density = context.resources.displayMetrics.density
                val hintTextSize = ta.getDimension(R.styleable.ZVideoView_hintTextSize, density * 14f)
                val refreshTextSize = ta.getDimension(R.styleable.ZVideoView_refreshTextSize, density * 14f)
                it.setBackground(loadingBackgroundColor)
                it.setLoadingDrawable(loadingRes)
                it.setNoDataDrawable(failRes)
                it.setHintTextColor(hintTextColor)
                it.setRefreshTextColor(refreshTextColor)
                it.setLoadingText(if (!loadingText.isNullOrEmpty()) loadingText else context.getString(R.string.z_player_str_loading_video_progress))
                it.setFailText(if (!failText.isNullOrEmpty()) failText else context.getString(R.string.z_player_str_loading_video_error))
                it.setRefreshText(if (!refreshHintText.isNullOrEmpty()) refreshHintText else context.getString(R.string.z_player_str_loading_video_error_tint))
                if (hintTextSize > 0) it.setHintTextSize(hintTextSize)
                if (refreshTextSize > 0) it.setHintTextSize(refreshTextSize)
                it.setDrawableSize(loadingIconWidth, loadingIconHeight, failIconWidth, failIconHeight)
            }
        } catch (e: Exception) {
            log("teh attributeSet for ${this::class.java.simpleName} has some problem , with error: ${e.message}")
            if (e !is RuntimeException) e.printStackTrace()
        } finally {
            ta.recycle()
        }
        try {
            (context as? Activity)?.window?.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        } catch (e: Exception) {
            log("hardware open failed , Player rendering may be affected.\nFor example, the rendering engine used by Render is WebView or Surface-based MediaPlayer. \nYou can add <activity android:hardwareAccelerated=\"true\"> in the Manifest to try to fix it")
            e.printStackTrace()
        }
    }

    private fun setTouchTorques(context: Context, l: GestureTouchListener) {
        l.setPadding(0.05f, 0.08f)
        val density = context.resources.displayMetrics.density
        l.setTorque(32f, density * 75f + 0.5f, density * 142f + 0.5f, density * 7f + 0.5f)
    }

    private fun <T : View> getViewByDefaultConfig(id: Int, mode: Int, default: T? = null): T? {
        val v: T? = default ?: videoRoot?.findViewById(id)
        v?.visibility = if (mode < 2) View.GONE else View.VISIBLE
        v?.setTag(R.id.tag_view_enabled, mode)
        return if (mode == 0) null else v
    }

    private fun initListener() {
        vPlay?.setOnClickListener {
            log("on play btn click", BehaviorLogsTable.onPlayClick())
            onPlayClick(true)
        }

        videoRoot?.setTargetChangeListener { v, e ->
            touchListener?.updateTargetXY(v, e)
        }
        videoRoot?.setTouchInterceptor {
            (!isPlayable && (videoOverrideImageView?.scrollAble() ?: false)) && isFullScreen
        }
        videoRoot?.setOnTouchListener(touchListener)

        loadingView?.setRefreshListener {
            reload()
        }

        fullScreen?.setOnClickListener {
            onFullScreenClick(!it.isSelected, true)
        }

        speedView?.setOnClickListener {
            log("on speed btn click", BehaviorLogsTable.onSpeedClick())
            onSpeedClick()
        }

        muteView?.setOnClickListener {
            log("on mute btn click", BehaviorLogsTable.onMuteClick())
            onMuteClick(!it.isSelected)
        }

        lockScreen?.setOnClickListener {
            log("on lock screen btn click", BehaviorLogsTable.onLockScreenClick())
            onLockScreenClick(!it.isSelected)
        }

        videoOverrideImageView?.setSingleTapListener { x, y ->
            onFullScreenViewDoubleClick(x, y)
        }

        videoOverrideImageView?.setTouchEnabled(ImageViewTouchEnableIn {
            return@ImageViewTouchEnableIn !isPlayable && isFullScreen
        })
    }

    protected fun setVolume(isMute: Boolean) {
        controller?.let {
            val audioManager = context.getSystemService(Service.AUDIO_SERVICE) as? AudioManager
            val volume = if (isMute) 0 else {
                audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
            } ?: 0
            var max = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                max -= (audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0)
            }
            it.setVolume(min(volume, max).coerceAtLeast(0), max.coerceAtLeast(0))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun initSeekBar() {
        seekBar?.isEnabled = false
        seekBarSmall?.setOnTouchListener(null)
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                val seekProgress = (max(0f, min(100, p0?.progress ?: seekBar?.progress ?: 0) / 100f * max(videoTotalTime, 1) - 1)).toLong()
                if (isTickingSeekBarFromUser && p2) {
                    controller?.seekTo(seekProgress, true)
                    if (autoFullTools) mHandler.removeMessages(dismissFullTools)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                isTickingSeekBarFromUser = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                isTickingSeekBarFromUser = false
                controller?.autoPlayWhenReady(true)
                if (autoFullTools && isFull) mHandler.sendEmptyMessageDelayed(dismissFullTools, autoFullInterval.toLong())
            }
        })
        onViewInit()
    }

    override fun keepScreenOnWhenPlaying(): Boolean {
        return keepScreenOnWhenPlaying
    }

    override fun getKeepScreenOn(): Boolean {
        return keepScreenOnWhenPlaying
    }

    override fun onControllerBind(controller: ZController<*, *>?) {
        this.controller = controller
        if (controller != null) setVolume(if (muteIsUseGlobal) getMuteGlobalDefault() else getMuteDefault())
    }

    override fun onDestroy(path: String?, isRegulate: Boolean) {
        fullScreenConfig?.close()
        controller = null
    }

    override fun getControllerInfo(): InflateInfo {
        return InflateInfo(getVideoRootView(), 2)
    }

    override fun onLoading(path: String, isRegulate: Boolean) {
        if (controller?.isPlaying(true) == true) return
        checkVisibleViews() //need'nt to check with ignore visibility
        if (!isFullScreen && playAutoFullScreen) {
            fullScreen?.let { onFullScreenClick(!it.isSelected, false) }
        }
        seekBar?.isEnabled = false
        showOrHidePlayBtn(false)
        onLoadingEvent(LoadingMode.Loading)
    }

    override fun onPrepare(path: String, videoSize: Long, isRegulate: Boolean) {
        seekBar?.isEnabled = true
        this.videoTotalTime = videoSize
        tvEnd?.text = getDuration(videoSize)
        onSyncVolume()
    }

    override fun onPlay(path: String, isRegulate: Boolean) {
        onSyncVolume()
        if (!isFullScreen && playAutoFullScreen) {
            fullScreen?.let { onFullScreenClick(!it.isSelected, false) }
        }
        setOverlayViews(isShowThumb = false, isShowBackground = true, isSinkBottomShader = true)
        seekBar?.isSelected = true
        seekBar?.isEnabled = true
        isInterruptPlayBtnAnim = false
        onLoadingEvent(LoadingMode.None)
        if (!isTickingSeekBarFromUser) {
            showOrHidePlayBtn(isShow = false, withState = false, byAnim = true)
            full(false)
            seekBarSmall?.visibility = View.VISIBLE
        }
    }

    override fun onPause(path: String, isRegulate: Boolean) {
        seekBar?.isSelected = false
        showOrHidePlayBtn(true)
    }

    @CallSuper
    override fun updateCurPlayerInfo(volume: Int, speed: Float) {
        val nextState = volume <= 0
        muteView?.isSelected = nextState
        var lastMinValue = 100000f
        val realSpeed = speed.coerceAtLeast(supportedSpeedList.first())
        var curSpeedIndex = 0
        run loop@{
            supportedSpeedList.forEachIndexed { i, fl ->
                val of = abs(realSpeed - fl)
                if (of == 0f) {
                    lastMinValue = 0f
                    curSpeedIndex = i
                    return@loop
                } else if (of < lastMinValue) {
                    lastMinValue = of
                    curSpeedIndex = i
                }
            }
        }
        val curSpeed = supportedSpeedList[curSpeedIndex]
        val t = "${max(1.0f, curSpeed)}"
        speedView?.text = context.getString(R.string.z_player_str_speed, t)
    }

    override fun updateCurPlayingQuality(level: PlayQualityLevel, supportedQualities: MutableList<PlayQualityLevel>?) {
        if (supportedQualities.isNullOrEmpty()) {
            qualityView?.visibility = View.GONE
            qualityView = null
        } else {
            if (qualityView == null) qualityView = getViewByDefaultConfig(R.id.z_player_video_preview_tv_quality, qualityEnable)
            qualityView?.let {
                it.text = context.getText(level.textId)
                it.visibility = if (isDefaultMaxScreen) View.VISIBLE else View.GONE
                it.setOnClickListener {
                    menuView?.setSupportedMenusAndShow(level, supportedQualities) { l ->
                        controller?.requirePlayQuality(l)
                    }
                }
            }
        }
    }

    override fun onStop(path: String, isRegulate: Boolean) {
        reset(false, isRegulate = isRegulate, isShowPlayBtn = true)
    }

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(true, withState = false)
        full(false)
    }

    override fun onCompleted(path: String, isRegulate: Boolean) {
        if (loadingView?.visibility != View.GONE) {
            onLoadingEvent(LoadingMode.None)
            completing(path, isRegulate)
        }
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        if (isRegulate) {
            showOrHidePlayBtn(true, withState = false)
        }
        full(isFull = false, isSetNow = true, ignoreStableVisibility = true)
        onSeekChanged(0, 0, false, 0, 0)
    }

    override fun onSeekChanged(seek: Int, buffered: Long, fromUser: Boolean, played: Long, videoSize: Long) {
        if (isTickingSeekBarFromUser) return
        this.videoTotalTime = videoSize
        if (!fromUser) {
            val bf = (buffered * 1.0f / max(1, videoSize) * 100 + 0.5f).toInt()
            if (!fromUser) curPlayingTime = played
            curBufferedTime = buffered
            seekBar?.progress = seek
            seekBar?.secondaryProgress = bf
            seekBarSmall?.progress = seek
            onLoadingEvent(LoadingMode.None, true)
        }
        tvStart?.text = getDuration(played)
        tvEnd?.text = getDuration(videoSize)
    }

    override fun onSeekingLoading(path: String?) {
        if (controller?.isPlaying(true) == true) return
        isInterruptPlayBtnAnim = true
        onLoadingEvent(LoadingMode.Loading)
        showOrHidePlayBtn(false)
    }

    override fun onError(e: Exception?) {
        seekBar?.isSelected = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        onSeekChanged(0, 0, false, 0, 0)
        showOrHidePlayBtn(false)
        onLoadingEvent(LoadingMode.Fail)
    }

    internal fun clickPlayBtn(accuratePlay: Boolean) {
        if (vPlay?.isSelected != !accuratePlay) vPlay?.isSelected = !accuratePlay
        onPlayClick(false)
    }

    open fun playIfReady() {
        controller?.playOrResume()
    }

    protected fun getVideoRootView(): RelativeLayout? {
        return videoRoot
    }

    open fun getThumbView(): TouchScaleImageView? {
        return videoOverrideImageView
    }

    open fun getBackgroundView(): ImageView? {
        return videoOverrideImageShaderView
    }

    open fun getMuteDefault(): Boolean {
        return muteDefault
    }

    open fun getMuteGlobalDefault(): Boolean {
        return muteGlobalDefault
    }

    open fun setScreenContentLayout(@LayoutRes layoutId: Int, onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null) {
        this.fullScreenContentLayoutId = layoutId
        this.onFullScreenLayoutInflateListener = onFullScreenLayoutInflateListener
    }

    open fun onViewInit() {}

    @CallSuper
    open fun onFullScreenChanged(isFull: Boolean, payloads: Map<String, Any?>?) {
    }

    open fun onFullMaxScreenChanged(isFull: Boolean, fromFocusChange: Boolean) {}

    open fun onTrack(playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) {}

    open fun onTouchActionEvent(videoRoot: View?, event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean {
        return false
    }

    open fun onSyncVolume(volume: Int = controller?.getCurVolume() ?: 0) {
        if (muteIsUseGlobal) {
            val isMute = getMuteGlobalDefault()
            if (muteView?.isSelected != isMute) muteView?.isSelected = isMute;setVolume(isMute)
        } else {
            if (muteView?.isSelected != getMuteDefault()) muteView?.isSelected = getMuteDefault();setVolume(getMuteDefault())
        }
    }

    open fun onPlayClick(fromUser: Boolean) {
        if (!isPlayable) return
        controller?.let {
            vPlay?.isEnabled = false
            if (vPlay?.isSelected != true) {
                it.playOrResume()
            } else {
                it.pause()
            }
        }
    }

    open fun reload() {
        val path = controller?.getPath()
        if (path.isNullOrEmpty()) {
            onLoadingEvent(LoadingMode.Loading, isSetInNow = false, ignoreInterval = true)
            postDelayed({
                onError(NullPointerException("video path is null"))
            }, 300)
        } else controller?.playOrResume(path)
    }

    open fun onRootClick(x: Float, y: Float) {
        if (!isPlayable) {
            ZPlayerLogs.debug("on video root click , cur status is disable play")
            return
        }
        if (controller?.isLoadData() != true) {
            ZPlayerLogs.debug("on video root click , and controller is not load data")
            return
        }
        log("on root view click", BehaviorLogsTable.onRootClick())
        onFullTools(!isFull)
    }

    open fun onRootDoubleClick(x: Float, y: Float) {
        if (playAutoFullScreen) return
        log("on root view double click", BehaviorLogsTable.onRootDoubleClick())
        fullScreen?.let { onFullScreenClick(!it.isSelected, false) }
    }

    open fun onFullScreenViewDoubleClick(x: Float, y: Float) {
        log("on full screen view double click", BehaviorLogsTable.onRootDoubleClick())
        fullScreenConfig?.onDoubleClick()
    }

    private fun onFullScreenClick(nextState: Boolean, fromUser: Boolean, payloads: Map<String, Any?>? = null) {
        log("on full screen", BehaviorLogsTable.onFullScreen())
        if (!isFullingOrDismissing) {
            isFullingOrDismissing = true
            onFullScreen(nextState, this.onFullScreenClick(Transaction(fromUser, fullScreenTransactionTime, true, payloads)))
        }
    }

    fun fullScreen(isFull: Boolean, fromUser: Boolean, payloads: Map<String, Any?>? = null) {
        this.fullScreen(isFull, fromUser, fullScreenTransactionTime, true, payloads)
    }

    fun fullScreen(isFull: Boolean, fromUser: Boolean, transactionTime: Int, isStartOnly: Boolean, payloads: Map<String, Any?>? = null) {
        log("on override method full screen", BehaviorLogsTable.onChildFullScreen())
        if (!isFullingOrDismissing) {
            isFullingOrDismissing = true
            onFullScreen(isFull, this.onFullScreenClick(Transaction(fromUser, transactionTime, isStartOnly, payloads)))
        }
    }

    open fun onFullScreenClick(transaction: Transaction): Transaction {
        return transaction
    }

    open fun onSpeedClick() {
        resendAutoFullScreenAction()
        if (controller?.isReady() == true) {
            val next = supportedSpeedList[++curSpeedIndex % supportedSpeedList.size]
            requestNewSpeed(next)
        }
    }

    open fun requestNewSpeed(nextSpeed: Float) {
        controller?.setSpeed(nextSpeed)
    }

    open fun onMuteClick(nextState: Boolean) {
        resendAutoFullScreenAction()
        try {
            if (muteIsUseGlobal) muteGlobalDefault = nextState
            muteDefault = nextState
            setVolume(nextState)
        } catch (e: Exception) {
            error("the mute click to $nextState failed , trying to check the system audio manager as device type: ${Build.MANUFACTURER}")
        }
    }

    open fun onLockScreenClick(nextState: Boolean) {
        resendAutoFullScreenAction()
        if (!lockScreenRotate(nextState)) Toast.makeText(context, R.string.z_player_str_screen_locked_tint, Toast.LENGTH_SHORT).show()
    }

    @CallSuper
    open fun removeView(tag: Any?, nullAbleView: WeakReference<View?>? = null) {
        getVideoRootView()?.let { v ->
            v.post {
                val view = v.findViewWithTag<View>(tag)
                if (view != null) getVideoRootView()?.removeView(view)
                else try {
                    getVideoRootView()?.removeView(nullAbleView?.get())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    open fun containsOverlayView(tag: Any?, view: WeakReference<View?>?): Boolean {
        getVideoRootView()?.let { rv ->
            return rv.findViewWithTag<View>(tag) != null || (view?.get()?.parent == rv)
        }
        return false
    }

    open fun addOverlayView(tag: Any, view: WeakReference<View?>?, paramsBuilder: ((RelativeLayout.LayoutParams) -> RelativeLayout.LayoutParams)? = null) {
        addOverlayView(tag, (getVideoRootView()?.childCount ?: 0) * 1.0f, view, paramsBuilder)
    }

    /**
     * For information about [zPoint], see [ZVideoView.addViewWithZPoint].
     * The difference is that calling this method provides a stable width and height under various conditions,
     * suitable for covering the full layout of the covered view, and can follow the full screen animation zoom
     * */
    @CallSuper
    open fun addOverlayView(tag: Any, zPoint: Float, view: WeakReference<View?>?, paramsBuilder: ((RelativeLayout.LayoutParams) -> RelativeLayout.LayoutParams)? = null) {

        fun generateLp(invoke: (RelativeLayout.LayoutParams) -> Unit) {
            getThumbView()?.let {
                it.post {
                    val match = MATCH_PARENT
                    var ivW: Int? = null
                    var ivH: Int? = null
                    if (it.drawable != null) try {
                        val bounds = it.getRealBounds()
                        ivW = bounds[0]
                        ivH = bounds[1]
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if ((ivW ?: 0) <= 0 || (ivH ?: 0) <= 0) {
                        ivW = it.width
                        ivH = it.height
                    }
                    val width = if (isFullScreen) ivW ?: match else width
                    val height = if (isFullScreen) ivH ?: match else height
                    val lp = RelativeLayout.LayoutParams(width, height)
                    lp.addRule(RelativeLayout.CENTER_IN_PARENT)
                    invoke(lp)
                }
            }
        }
        generateLp {
            val nlp = paramsBuilder?.invoke(it) ?: it
            addViewWithZPoint(tag, view, zPoint, nlp)
        }
    }

    /**
     * @param zPoint This value will determine the Z-axis position after trying to be added to the Controller,that is, the height.
     * [zPoint] can be passed in one A larger value to ensure that the View is always at the top of the control,
     * of course, additional calls [RelativeLayout.bringChildToFront] or [View.bringToFront] will only take effect when the layout is stable
     * if current [zPoint] less tha 6 ,the adding view may below of the video controller children.
     * */
    @CallSuper
    open fun addViewWithZPoint(tag: Any?, view: WeakReference<View?>?, zPoint: Float, nlp: RelativeLayout.LayoutParams? = null) {
        getVideoRootView()?.let { rv ->
            rv.post {
                var isAdd = true
                val vp = view?.get()?.parent as? ViewGroup
                rv.findViewWithTag<View>(tag)?.let { exi ->
                    if (vp != rv) vp?.removeView(exi)
                    else isAdd = false
                } ?: vp?.let {
                    if (it != rv) it.removeView(view.get())
                    else isAdd = false
                }
                if (isAdd) {
                    view?.get()?.tag = tag
                    view?.get()?.setTag(R.id.tag_view_point_z, zPoint)
                    if (zPoint <= 0 || rv.childCount <= 0) {
                        rv.addView(view?.get() ?: return@post, 0, nlp)
                    } else {
                        view?.get()?.let { v ->
                            v.translationZ = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, zPoint, resources.displayMetrics)
                            rv.addView(v, nlp)
                        }
                    }
                }
            }
        }
    }

    /**
     * even though you called it and set the lock to FALSE ,
     * it also merge with the system screen rotate settings.
     * @return the status form this operation
     * */
    @CallSuper
    open fun lockScreenRotate(isLock: Boolean): Boolean {
        return if (fullScreenConfig?.lockScreenRotation(isLock) == true) {
            lockScreen?.isSelected = isLock
            true
        } else false
    }

    /**
     * If the event is consumed, return true, otherwise false
     * */
    open fun onFullKeyEvent(code: Int, event: KeyEvent): Boolean {
        return false
    }

    open fun onToolsBarChanged(isFullExpand: Boolean, isResetNow: Boolean) {}

    open fun onPreToDismissFullScreen(agree: () -> Unit) {
        agree.invoke()
    }

    open fun onPreToFullMaxScreen(agree: () -> Unit) {
        agree.invoke()
    }

    protected fun getDuration(mediaDuration: Long): String {
        val duration = mediaDuration / 1000
        val minute = duration / 60
        val second = duration % 60
        return String.format("${if (minute < 10) "0%d" else "%d"}:${if (second < 10) "0%d" else "%d"}", minute, second)
    }

    protected fun showOrHidePlayBtn(isShow: Boolean, withState: Boolean = false, byAnim: Boolean = false) {
        if ((isShow && playIconEnable < 2) || (!isPlayable && isShow)) return
        vPlay?.let {
            if (!it.isSelected && controller?.isPlaying(true) == true) {
                it.isSelected = true
            }
            if (it.isSelected && controller?.isPause() == true) {
                it.isSelected = false
            }
            var isNeedSetFreePlayBtn = true
            try {
                if (!withState) {
                    it.isSelected = !isShow
                    if (isShow && it.visibility == View.VISIBLE && it.tag == null) return
                }
                if (withState && it.tag != null) return
                if (isShow && it.tag == 0) return
                if (!isShow && it.tag == 1) return
                isNeedSetFreePlayBtn = false
                if (!byAnim || enablePlayAnimation) {
                    it.tag = if (isShow) 0 else 1
                    val start = if (isShow) 0.0f else 1.0f
                    val end = if (isShow) 1.0f else 0.0f
                    it.alpha = start
                    if (isShow) it.visibility = View.VISIBLE
                    it.animation?.cancel()
                    it.clearAnimation()
                    it.animate()?.alpha(end)?.setDuration(Constance.ANIMATE_DURATION)?.withEndAction {
                        it.alpha = end
                        it.visibility = if (isShow) View.VISIBLE else View.GONE
                        it.animation = null
                        it.tag = null
                        it.isEnabled = true
                    }?.start()
                } else {
                    it.visibility = if (isShow) View.VISIBLE else View.GONE
                    it.isEnabled = true
                }
            } finally {
                if (isNeedSetFreePlayBtn) it.isEnabled = true
            }
        }
    }

    protected fun full(isFull: Boolean, isSetNow: Boolean = false, ignoreStableVisibility: Boolean = !isFull) {
        if (autoFullTools) mHandler.removeMessages(dismissFullTools)
        if (isSetNow || !isPlayable) {
            anim?.end()
            bottomToolsBar?.let {
                it.clearAnimation()
                it.visibility = if (isFull) VISIBLE else GONE
            }
            topToolsBar?.let {
                hideTopViews(false, 1f, isFull, ignoreStableVisibility)
            }
            seekBarSmall?.visibility = GONE
        } else {
            if (anim?.isRunning == true || isFull == this.isFull) return
            bottomToolsBar?.let {
                this.isFull = isFull
                it.clearAnimation()
                anim?.start(isFull, ignoreStableVisibility)
                log("on tools bar hidden ${!isFull}", BehaviorLogsTable.onToolsBarShow(isFull))
            }
            if (isFull) seekBarSmall?.visibility = View.GONE
        }
        onToolsBarChanged(isFull, isSetNow)
        if (autoFullTools && isFull) mHandler.sendEmptyMessageDelayed(dismissFullTools, autoFullInterval.toLong())
    }

    protected fun setOverlayViews(isShowThumb: Boolean, isShowBackground: Boolean, isSinkBottomShader: Boolean) {
        log("on overlay views visibility  thumb = $isShowThumb  bg = $isShowBackground", BehaviorLogsTable.thumbImgVisible(isShowThumb, isShowBackground))
        videoOverrideImageView?.visibility = if (isShowThumb) View.VISIBLE else View.GONE
        videoOverrideImageShaderView?.visibility = if (isShowBackground) View.VISIBLE else View.GONE
        videoOverrideImageShaderView?.z = if (isSinkBottomShader) 0f else Resources.getSystem().displayMetrics.density * 3 + 0.5f
    }

    private var anim: ZFullValueAnimator? = null
        get() {
            if (field == null) field = ZFullValueAnimator(fullListener)
            field?.duration = Constance.ANIMATE_DURATION
            field?.interpolator = AccelerateDecelerateInterpolator()
            return field
        }

    private val fullListener = object : ZFullValueAnimator.FullAnimatorListener() {

        override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean, obj: Any?) {
            if (checkActIsFinished()) return
            bottomToolsBar?.let {
                val toolsBottomHeight = it.measuredHeight * 1.0f
                if (isFull && it.translationY == 0f) {
                    it.alpha = 0f
                    it.translationY = toolsBottomHeight
                }
                val d = if (isFull) duration else -duration
                val bottomTrans = d * toolsBottomHeight
                var bTranslateY = it.translationY
                bTranslateY -= bottomTrans
                it.translationY = bTranslateY
                it.alpha += d
                if (isFull && it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
            }
            topToolsBar?.let {
                hideTopViews(true, duration, isFull, (obj as? Boolean?) ?: false)
            }
        }

        override fun onAnimEnd(animation: Animator, isFull: Boolean, obj: Any?) {
            if (checkActIsFinished()) return
            bottomToolsBar?.let {
                val toolsBottomHeight = (it.measuredHeight) * 1.0f
                it.translationY = if (isFull) 0f else toolsBottomHeight
                it.alpha = if (isFull) 1f else 0f
                if (!isFull) it.visibility = View.GONE
                if (!isFull && !isStartTrack && (controller?.isPlaying() == true || controller?.isPause(true) == true)) seekBarSmall?.visibility = View.VISIBLE
            }
            topToolsBar?.let {
                hideTopViews(false, if (isFull) 1f else 0f, isFull, (obj as? Boolean?) ?: false)
            }
        }
    }

    private val fullScreenListener = object : FullScreenListener {
        override fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?) {
            this@ZVideoView.onDisplayChanged(isShow, payloads)
        }

        override fun onFocusChange(ai: FullActivityIn, isMax: Boolean) {
            this@ZVideoView.onFocusChanged(ai, isMax, false)
        }

        override fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
            onTracked(isStart, isEnd, formTrigDuration)
        }

        override fun onKeyEvent(code: Int, event: KeyEvent): Boolean {
            return onFullKeyEvent(code, event)
        }
    }

    private val fullContentListener = object : FullContentListener {
        override fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?) {
            this@ZVideoView.onDisplayChanged(isShow, payloads)
        }

        override fun onContentLayoutInflated(content: View) {
            onFullScreenLayoutInflateListener?.invoke(content)
        }

        override fun onFullMaxChanged(ai: FullActivityIn, isMax: Boolean) {
            lockScreen?.visibility = if (isFull && isPlayable && isMax) View.VISIBLE else GONE
            qualityView?.visibility = if (isFull && isPlayable && isMax) View.VISIBLE else GONE
            this@ZVideoView.onFocusChanged(ai, isMax, false)
        }

        override fun onFocusChange(ai: FullActivityIn, isMax: Boolean) {
            this@ZVideoView.onFocusChanged(ai, isMax, true)
        }

        override fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
            onTracked(isStart, isEnd, formTrigDuration)
        }

        override fun onKeyEvent(code: Int, event: KeyEvent): Boolean {
            return onFullKeyEvent(code, event)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun onFullScreen(full: Boolean, transaction: Transaction) {
        if (!full) {
            lockScreen?.visibility = GONE
            qualityView?.visibility = GONE
            fullScreenConfig?.close()
        } else {
            if (fullScreenConfig == null) fullScreenConfig = object : FullScreenConfig() {
                override fun getControllerView(): View? {
                    return getVideoRootView()
                }
            }
            fullScreenConfig?.defaultOrientation(lockScreenRotation)?.allowReversePortrait(isAllowReversePortrait)?.transactionNavigation(isTransactionNavigation)?.transactionAnimDuration(transaction.transactionTime, transaction.isStartOnly, fullScreenTransactionTime)?.setPreDismissInterceptor { onPreToDismissFullScreen(it) }?.setPreFullMaxChangeInterceptor { onPreToFullMaxScreen(it) }?.payLoads(transaction.payloads)
            fullScreenConfig?.let { config ->
                if (isDefaultMaxScreen) {
                    lockScreen?.visibility = View.VISIBLE
                    qualityView?.visibility = View.VISIBLE
                    config.withFullMaxScreen(fullScreenListener)
                } else {
                    config.withFullContentScreen(fullScreenContentLayoutId, fullMaxScreenEnable, fullContentListener)
                }
                config.start(context)
            }
        }
        lockScreenRotate(lockScreenRotation != LOCK_SCREEN_UNSPECIFIED)
    }

    private var lastIsFull = false
    private fun onTracked(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float, resumeFullState: Boolean = true) {
        if (isPlayable) {
            if (isStart) {
                lastIsFull = this.isFull
                isStartTrack = true
            }
            if (isEnd) {
                isStartTrack = false
            }
            if (isStart) {
                if (this.isFull) {
                    full(false, isSetNow = false, ignoreStableVisibility = true)
                    showOrHidePlayBtn(isShow = false, withState = true)
                } else {
                    hideTopViews(false, 1.0f, isFull = false, ignoreStableVisibility = true)
                }
            }
            if (isEnd) {
                if (lastIsFull && resumeFullState) {
                    full(true, isSetNow = false, ignoreStableVisibility = false)
                    if (!isInterruptPlayBtnAnim) showOrHidePlayBtn(isShow = true, withState = true)
                } else {
                    hideTopViews(false, 1.0f, isFull = false, ignoreStableVisibility = false)
                }
                lastIsFull = false
            }
        }
        onTrack(isPlayable, isStart, isEnd, formTrigDuration)
    }

    @CallSuper
    open fun reset(isNow: Boolean, isRegulate: Boolean, isShowPlayBtn: Boolean, isShowThumb: Boolean = true, isShowBackground: Boolean = true, isSinkBottomShader: Boolean = false) {
        vPlay?.isEnabled = isShowPlayBtn
        onLoadingEvent(LoadingMode.None, true)
        setOverlayViews(isShowThumb, isShowBackground, isSinkBottomShader)
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        videoTotalTime = 0L
        curPlayingTime = 0L
        curBufferedTime = 0L
        onSeekChanged(0, 0, false, 0, 0)
        if (isRegulate) showOrHidePlayBtn(isShowPlayBtn, withState = false)
        full(false, isSetNow = isNow, ignoreStableVisibility = false)
        muteView?.isSelected = if (muteIsUseGlobal) getMuteGlobalDefault() || getMuteDefault() else getMuteDefault()
        qualityView?.visibility = View.GONE
    }

    private fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?) {
        log("on full screen $isShow", BehaviorLogsTable.onFullscreen(isShow))
        isFullScreen = isShow
        lockScreen?.visibility = GONE
        qualityView?.visibility = GONE
        if (!isShow) {
            isFullMaxScreen = false
            fullScreenConfig = null
            lockScreen?.isSelected = false
            this@ZVideoView.onTracked(isStart = false, isEnd = true, formTrigDuration = 1.0f, resumeFullState = resumeToolsWhenFullscreenChanged())
        }
        isFullingOrDismissing = false
        if (!isShow && playAutoFullScreen) {
            controller?.stopNow(withNotify = true, isRegulate = true)
        }
        onFullScreenChanged(isShow, payloads)
    }

    private fun onFocusChanged(ai: FullActivityIn, isMax: Boolean, fromFocusChange: Boolean) {
        log("on focus changed  fullMaxScreen = $isMax", BehaviorLogsTable.onFullMaxScreen(isMax))
        this@ZVideoView.isFullMaxScreen = isMax
        if (isMax) lockScreen?.isSelected = ai.isLockedCurrent()
        onFullMaxScreenChanged(isMax, fromFocusChange)
    }

    protected fun setMuteIsGlobal(isGlobal: Boolean) {
        muteIsUseGlobal = isGlobal
    }

    protected fun checkActIsFinished(): Boolean {
        return (context as? Activity)?.isFinishing == true
    }

    private fun onLoadingEvent(loadingMode: LoadingMode, isSetInNow: Boolean = false, ignoreInterval: Boolean = false) {
        mHandler.removeMessages(loadingModeDelay)
        loadingView?.let {
            when (loadingMode) {
                LoadingMode.None -> it.setMode(VideoLoadingView.DisplayMode.NONE, isSetInNow)
                LoadingMode.Loading -> if (isSetInNow || loadingIgnoreInterval <= 0 || ignoreInterval) it.setMode(VideoLoadingView.DisplayMode.LOADING, isSetInNow)
                else mHandler.sendEmptyMessageDelayed(loadingModeDelay, loadingIgnoreInterval.toLong())
                LoadingMode.Fail -> it.setMode(VideoLoadingView.DisplayMode.NO_DATA, isSetInNow)
            }
        }
    }

    private fun onFullTools(isFull: Boolean) {
        if (isTickingSeekBarFromUser) return
        controller?.let {
            if (!isInterruptPlayBtnAnim) {
                showOrHidePlayBtn(isFull, true)
            }
            full(isFull, false, ignoreStableVisibility = false)
        }
    }

    private fun hideTopViews(inDuration: Boolean, duration: Float, isFull: Boolean, ignoreStableVisibility: Boolean) {
        topToolsBar?.forEach {
            if (checkDisableView(it) && (ignoreStableVisibility || checkVisibleView(it))) {
                if (inDuration) {
                    if (isFull && it.alpha == 1.0f) it.alpha = 0f
                    val d = if (isFull) duration else -duration
                    it.alpha += d
                    if (isFull && it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
                } else {
                    it.alpha = duration
                    it.visibility = if (isFull) VISIBLE else GONE
                }
            }
        }
    }

    private fun checkVisibleViews() {
        topToolsBar?.forEach {
            if (!checkDisableView(it)) return@forEach
            checkVisibleView(it)
        }
    }

    private fun checkDisableView(it: View): Boolean {
        return if (!isPlayable || (it == lockScreen || it == qualityView) && !isFullMaxScreen) {
            it.visibility = View.GONE
            false
        } else true
    }

    private fun checkVisibleView(it: View): Boolean {
        val mod = (it.getTag(R.id.tag_view_enabled) as? Int?) ?: 0
        if ((mod >= 3 && !isStartTrack) || mod == 0) {
            if (mod == 3 && it.visibility != View.VISIBLE || it.alpha < 1.0f) {
                it.alpha = 1.0f
                it.visibility = View.VISIBLE
            }
            if (mod == 0 && it.visibility != View.GONE) it.visibility = View.GONE
            return false
        }
        return true
    }

    private fun resendAutoFullScreenAction() {
        if (autoFullTools) mHandler.removeMessages(dismissFullTools)
        if (autoFullTools && isFull) mHandler.sendEmptyMessageDelayed(dismissFullTools, autoFullInterval.toLong())
    }

    private fun log(s: String, bd: BehaviorData? = null) {
        controller?.recordLogs(s, "BaseViewController", bd)
    }

    protected fun recordLogs(s: String, modeName: String, vararg params: Pair<String, Any>) {
        if (Constance.CORE_LOG_ABLE) ZPlayerLogs.onLog(s, controller?.getPath() ?: "", "", modeName, *params)
    }

    fun getCurSpeed(): Float {
        return controller?.getCurSpeed() ?: 1.0f
    }

    fun getCurVolume(): Int {
        return controller?.getCurVolume() ?: 0
    }

    fun isPause(accurate: Boolean = false): Boolean {
        return controller?.isPause(accurate) ?: false
    }

    fun isStop(accurate: Boolean = false): Boolean {
        return controller?.isStop(accurate) ?: true
    }

    fun isPlaying(accurate: Boolean = false): Boolean {
        return controller?.isPlaying(accurate) ?: false
    }

    fun isReady(accurate: Boolean = false): Boolean {
        return controller?.isReady(accurate) ?: false
    }

    fun isLoading(accurate: Boolean = false): Boolean {
        return controller?.isLoading(accurate) ?: false
    }

    fun isLoadData(): Boolean {
        return controller?.isLoadData() ?: false
    }

    fun isDestroyed(accurate: Boolean = false): Boolean {
        return controller?.isDestroyed(accurate) ?: true
    }

    open fun setPlayIconEnable(@IntRange(from = 0, to = 3) mode: Int) {
        this.playIconEnable = mode;vPlay = getViewByDefaultConfig(R.id.z_player_video_preview_iv_play, mode, vPlay)
    }

    open fun setMuteIconEnable(@IntRange(from = 0, to = 3) mode: Int) {
        this.muteIconEnable = mode;muteView = getViewByDefaultConfig(R.id.z_player_video_preview_iv_mute, mode, muteView)
    }

    open fun setSpeedIconEnable(@IntRange(from = 0, to = 3) mode: Int) {
        this.speedIconEnable = mode;speedView = getViewByDefaultConfig(R.id.z_player_video_preview_tv_speed, mode, speedView)
    }

    open fun setSecondarySeekBarEnable(@IntRange(from = 0, to = 3) mode: Int) {
        this.secondarySeekBarEnable = mode;seekBarSmall = getViewByDefaultConfig(R.id.z_player_video_preview_sb_small, mode, seekBarSmall)
    }

    open fun setFullScreenEnable(@IntRange(from = 0, to = 3) mode: Int) {
        this.fullScreenEnable = mode;fullScreen = getViewByDefaultConfig(R.id.z_player_video_preview_iv_full_screen, mode, fullScreen)
    }

    open fun setLockRotationEnable(@IntRange(from = 0, to = 3) mode: Int) {
        this.lockRotationEnable = mode;lockScreen = getViewByDefaultConfig(R.id.z_player_video_preview_iv_lock_screen, mode, lockScreen)
    }

    open fun setBottomToolsEnable(@IntRange(from = 0, to = 3) mode: Int) {
        this.defaultControllerVisibility = mode;bottomToolsBar = getViewByDefaultConfig(R.id.z_player_video_preview_tools_bar, mode, bottomToolsBar)
    }

    open fun resumeToolsWhenFullscreenChanged(): Boolean {
        return true
    }

    private fun isInterrupted(): Boolean {
        return if ((menuView?.visibility ?: View.GONE) != View.GONE) {
            menuView?.visibility = View.GONE;true
        } else fullScreenConfig?.isInterruptTouchEvent() ?: false
    }
}