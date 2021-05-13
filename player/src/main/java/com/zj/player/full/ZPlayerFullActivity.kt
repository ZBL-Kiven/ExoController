package com.zj.player.full

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.zj.player.R
import com.zj.player.anim.ZFullValueAnimator
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.z.ZVideoView
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class ZPlayerFullActivity : AppCompatActivity(), FullActivityIn {

    companion object {
        private const val MAX_DEEP_RATIO = 0.55f
        private const val HANDLE_RESIZE_CONTROLLER = 19283
        private const val HANDLE_ORIENTATION_CHANGE = 19285
        private var config: FullScreenConfig? = null

        internal fun start(context: Context, config: FullScreenConfig) {
            ZPlayerFullActivity.config = config
            context.startActivity(Intent(context, ZPlayerFullActivity::class.java))
            (context as? Activity?)?.overridePendingTransition(0, 0)
        }
    }

    private var isMaxFull: Boolean = false
    private var _width: Float = 0f
    private var inTracked = false
    private var _height: Float = 0f
    private var calculateUtils: RectFCalculateUtil? = null
    private var originInScreen: Point? = null
    private var curScaleOffset: Float = 1.0f
    private var isAnimRun = false
    private var scaleAnim: ZFullValueAnimator? = null
    private var isDismissing = false
    private val interpolator = AccelerateInterpolator(1.5f)
    private var originViewRectF: RectF? = null
    private var contentLayoutView: View? = null
    private var realWindowSize = Point()
    private var backgroundView: View? = null
    private var screenUtil: ScreenOrientationListener? = null
    private var isScreenRotateLocked: Boolean = false
    private var fullHandler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            HANDLE_RESIZE_CONTROLLER -> {
                if (it.arg1 == 0) init(0f, false)
                try {
                    runWithControllerView { controller ->
                        val v = (controller.parent as? ViewGroup) ?: return@runWithControllerView
                        if (v.height > 0 && controller.height > v.height) {
                            controller.layoutParams.let { l -> l.height = l.height.coerceAtMost(v.height) }
                            controller.requestLayout()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            HANDLE_ORIENTATION_CHANGE -> {
                (it.obj as? RotateOrientation)?.let { o -> curScreenRotation = o }
            }
        }
        return@Handler false
    }

    private var curScreenRotation: RotateOrientation? = null
        set(value) {
            if (field == value || inTracked) return
            field = value
            if (value == null) return
            if (config?.allowReversePortrait == true || value != RotateOrientation.P1) {
                this.requestedOrientation = when (value) {
                    RotateOrientation.L0 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    RotateOrientation.L1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    RotateOrientation.P0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    RotateOrientation.P1 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config?.setActivityIn(this)
        isMaxFull = config?.isDefaultMaxScreen ?: false
        withConfig { config ->
            runWithControllerView {
                (it.context?.applicationContext?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.getRealSize(realWindowSize)
                if (!config.isDefaultMaxScreen && config.contentLayout > 0) contentLayoutView = View.inflate(it.context, config.contentLayout, null)
                val actionViews = mutableMapOf<Int, View>()
                hiddenAllChildIfNotScreenContent(contentLayoutView, actionViews)
                actionViews.forEach { (_, u) ->
                    u.alpha = 0.0f
                }
                config.preToFullMaxChange {
                    setContent(it, isMaxFull)
                }
            }
        }
        screenUtil = ScreenOrientationListener(WeakReference(this)) {
            fullHandler.removeMessages(HANDLE_ORIENTATION_CHANGE)
            if (!isScreenRotateLocked && isMaxFull) {
                fullHandler.sendMessageDelayed(Message.obtain().apply { what = HANDLE_ORIENTATION_CHANGE;obj = it }, 100)
            }
        }
    }

    private fun setContent(controller: View, isMaxFull: Boolean, isResizeCalculate: Boolean = false, isInit: Boolean = true) {
        if (this.isMaxFull && !isMaxFull && curScreenRotation?.isLandSpace() != false) curScreenRotation = RotateOrientation.P0
        this.isMaxFull = isMaxFull
        changeSystemWindowVisibility(true)
        fullHandler.removeMessages(HANDLE_RESIZE_CONTROLLER)
        try {
            if (!isMaxFull) contentLayoutView?.let {
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                if (backgroundView == null) backgroundView = View(this)
                backgroundView?.setBackgroundColor(Color.BLACK)
                (backgroundView?.parent as? ViewGroup).let { bgp ->
                    if (bgp != null && bgp != it) {
                        bgp.removeView(backgroundView)
                    }
                    if (backgroundView?.parent == null) (it as? ViewGroup)?.addView(backgroundView, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                (controller.parent as? ViewGroup).let { parent ->
                    if (parent != null && parent != it) {
                        parent.removeView(controller)
                    }
                    val vlp = config?.originLayoutParams
                    it.findViewById<ViewGroup>(R.id.player_gesture_full_screen_content)?.addView(controller, vlp)
                }
                (it.parent as? ViewGroup)?.removeView(it)
                setContentView(it)
                config?.onFullContentListener?.onContentLayoutInflated(it)
            } else {
                window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                (controller.parent as? ViewGroup)?.removeView(controller)
                setContentView(controller)
            }
        } finally {
            if (isInit) {
                initListeners()
                showAnim()
            }
            fullHandler.sendMessageDelayed(Message.obtain().apply { what = HANDLE_RESIZE_CONTROLLER;arg1 = if (isResizeCalculate && !isInit) 0 else 1 }, 100)
        }
    }

    private fun showAnim() {
        withConfig {
            init()
            fullHandler.post {
                if (it.transactionAnimDuration <= 0) {
                    initCalculate()
                    updateContent(0f)
                    setBackground(1f, true)
                    onDisplayChange(true)
                    if (it.isAnimDurationOnlyStart) it.resetDurationWithDefault()
                } else {
                    isAnimRun = true
                    startScaleAnim(true)
                }
            }
        }
    }

    private fun init(contentValue: Float = 1f, updateContent: Boolean = true) {
        initCalculate()
        if (isMaxFull || updateContent) updateContent(contentValue)
        screenRotationsChanged(true)
    }

    private fun initCalculate() {
        val cps = getViewPoint(config?.originViewGroup)
        originViewRectF = RectF(cps.x, cps.y, cps.x + (config?.originWidth ?: 0), cps.y + (config?.originHeight ?: 0))
        val viewRectF = getWindowSize(isMaxFull)
        _width = viewRectF.right - viewRectF.left
        _height = viewRectF.bottom - viewRectF.top
        calculateUtils = RectFCalculateUtil(viewRectF, originViewRectF ?: return)
    }

    private fun startScaleAnim(isFull: Boolean) {
        if (scaleAnim?.isRunning == true) scaleAnim?.end()
        scaleAnim?.duration = ((config?.transactionAnimDuration ?: 0).coerceAtLeast(0)).toLong()
        scaleAnim?.start(isFull)
    }

    private fun initListeners() {
        scaleAnim = ZFullValueAnimator(object : ZFullValueAnimator.FullAnimatorListener() {

            override fun onStart() {
                initCalculate()
            }

            override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean, obj: Any?) {
                if (isFull) {
                    updateContent(1 - duration)
                    setBackground(duration, true)
                } else {
                    setBackground(1 - duration, isFromStart = true, isDownTo = true)
                    runWithControllerView { cv ->
                        (cv.parent as? ViewGroup)?.clipChildren = false
                        if (originInScreen == null) originInScreen = Point(cv.scrollX, cv.scrollY)
                        originInScreen?.let {
                            val sx = (it.x * (1f - duration)).roundToInt()
                            val sy = (it.y * (1f - duration)).roundToInt()
                            cv.scrollTo(sx, sy)
                        }
                    }
                    val curOff = if (curScaleOffset <= 0f) 1f else curScaleOffset
                    val offset = (duration * curOff) + (1f - curOff)
                    updateContent(offset)
                }
            }

            override fun onAnimEnd(animation: Animator, isFull: Boolean, obj: Any?) {
                isAnimRun = false
                originInScreen = null
                if (!isFull) dismissed(true)
                else onDisplayChange(true)
            }
        }, false)
    }

    private fun updateContent(offset: Float) {
        runWithControllerView {
            val rect = calculateUtils?.calculate(offset)
            getFrameLayoutParams(it, rect)
        }
    }

    private fun <T> withConfig(r: (FullScreenConfig) -> T): T? {
        config?.let {
            r.invoke(it)
        } ?: dismissed()
        return null
    }

    private fun <T> runWithControllerView(i: (View) -> T): T? {
        val cv = config?.getControllerView()
        if (cv == null) {
            if (!isDismissing) dismissed()
            ZPlayerLogs.debug("case the controller view is null ,so what`s your displaying wishes")
        } else {
            return i(cv)
        }
        return null
    }

    private fun followWithFinger(x: Float, y: Float) {
        if (isMaxFull) return
        runWithControllerView { it.scrollTo(x.roundToInt(), y.roundToInt()) }
    }

    private fun scaleWithOffset(curYOffset: Float) {
        if (isMaxFull) return
        updateContent(curYOffset)
        curScaleOffset = 1 - curYOffset
    }

    private fun isAutoScaleFromTouchEnd(curYOffset: Float, fromUser: Boolean): Boolean {
        if (isMaxFull && fromUser) return true
        isDismissing = true
        val isScaleAuto = curYOffset <= MAX_DEEP_RATIO
        if (isScaleAuto) {
            runWithControllerView { it.scrollTo(0, 0) }
            scaleWithOffset(0f)
            setBackground(1f, true)
            onTracked(false, isEnd = true, formTrigDuration = 0f)
            isDismissing = false
        } else {
            config?.preToDismiss {
                isAnimRun = true
                changeSystemWindowVisibility(false)
                startScaleAnim(false)
            }
        }
        return isScaleAuto
    }

    private fun getFrameLayoutParams(view: View, ref: RectF?) {
        if (ref == null) return
        val pl: Int = ref.left.roundToInt()
        val pt: Int = ref.top.roundToInt()
        val pr: Int = ref.right.roundToInt()
        val pb: Int = ref.bottom.roundToInt()
        val w = (_width - (pl + pr)).roundToInt()
        val h = (_height - (pt + pb)).roundToInt()
        val flp = FrameLayout.LayoutParams(w, h)
        flp.setMargins(pl, pt, pr, pb)
        view.layoutParams = flp
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun changeSystemWindowVisibility(visible: Boolean) {
        val ime = ImmersionBar.with(this)
        if (visible) {
            ime.transparentStatusBar()
            if (isMaxFull || config?.translateNavigation == true) {
                ime.transparentNavigationBar().hideBar(BarHide.FLAG_HIDE_BAR)
            } else {
                ime.fullScreen(false).hideBar(BarHide.FLAG_HIDE_STATUS_BAR).navigationBarEnable(true).navigationBarColorInt(Color.BLACK)
            }
        } else {
            ime.hideBar(BarHide.FLAG_SHOW_BAR).autoDarkModeEnable(true).navigationBarEnable(false)
        }
        ime.init()
    }

    /**
     * Reduce the transparency of background and other unimportant (non-playing components) views when sliding
     * */
    private fun setBackground(@FloatRange(from = 0.0, to = 1.0) duration: Float, isFromStart: Boolean = false, isDownTo: Boolean = false) {
        if (isMaxFull) return
        val d = interpolator.getInterpolation(duration)
        if (isDownTo) {
            var cur = backgroundView?.alpha ?: 0f
            if (duration < cur) cur = duration
            backgroundView?.alpha = cur
        } else {
            backgroundView?.alpha = if (isFromStart) duration else ((duration * 0.75f) + 0.25f)
        }
        val actionViews = mutableMapOf<Int, View>()
        hiddenAllChildIfNotScreenContent(contentLayoutView, actionViews)
        actionViews.forEach { (_, u) ->
            if (d <= 0 || u.alpha >= d || !isDownTo) u.alpha = max(0f, d)
        }
    }

    /**
     * Return all other views and their parents that are not player views to [views],
     * which needs to be noted that this View is not included when view.id is not set.
     * The function considers that the View without ID is not used as a display estimate, and improves performance.
     * */
    private fun hiddenAllChildIfNotScreenContent(child: View?, views: MutableMap<Int, View>) {
        (child as? ViewGroup)?.let {
            it.children.forEach { cv ->
                if (cv.id == R.id.player_gesture_full_screen_content) {
                    removeSelfActionParent(cv, views)
                } else {
                    views[cv.id] = cv
                    hiddenAllChildIfNotScreenContent(cv, views)
                }
            }
        }
    }

    /**
     * only used by [hiddenAllChildIfNotScreenContent]
     * */
    private fun removeSelfActionParent(view: View, views: MutableMap<Int, View>) {
        if (view == contentLayoutView) return
        (view.parent as? ViewGroup)?.let {
            views.remove(it.id)
            removeSelfActionParent(it, views)
        }
    }

    private fun getWindowSize(isMaxFull: Boolean): RectF {
        return if (isMaxFull || contentLayoutView == null) {
            RectF(0f, 0f, window.attributes.width * 1.0f, window.attributes.height * 1.0f)
        } else {
            contentLayoutView?.let { cv ->
                cv.findViewById<View>(R.id.player_gesture_full_screen_content)?.let { fv ->
                    val pf = getViewPoint(fv)
                    RectF(pf.x, pf.y, pf.x + fv.width, pf.y + fv.height)
                } ?: {
                    val pf = getViewPoint(cv)
                    RectF(pf.x, pf.y, pf.x + cv.width, pf.y + cv.height)
                }.invoke()
            } ?: throw IllegalArgumentException()
        }
    }

    private fun getViewPoint(view: View?): PointF {
        val point = IntArray(2)
        view?.getLocationOnScreen(point)
        val x = point[0] * 1.0f
        val y = point[1] * 1.0f
        return PointF(x, y)
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun screenRotationsChanged(isRotateEnable: Boolean) {
        if (isRotateEnable) screenUtil?.enable() else {
            screenUtil?.disable();curScreenRotation = null
        }
    }

    override fun lockScreenRotation(isLock: Boolean): Boolean {
        isScreenRotateLocked = isLock
        return checkSelfScreenLockAvailable(isLock)
    }

    override fun isLockedCurrent(): Boolean {
        return screenUtil?.let {
            if (!it.checkAccelerometerSystem()) true else isScreenRotateLocked
        } ?: false
    }

    private fun onTracked(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
        if (!isMaxFull) config?.onFullContentListener?.onTrack(isStart, isEnd, formTrigDuration) ?: config?.onFullScreenListener?.onTrack(isStart, isEnd, formTrigDuration)
    }

    private fun onDisplayChange(isShow: Boolean) {
        config?.onFullContentListener?.onDisplayChanged(isShow, config?.payloads) ?: config?.onFullScreenListener?.onDisplayChanged(isShow, config?.payloads)
    }

    override fun isMaxFull(): Boolean {
        return isMaxFull
    }

    override fun isInterruptTouchEvent(): Boolean {
        return isAnimRun || isDismissing
    }

    override fun onEventEnd(formTrigDuration: Float, parseAutoScale: Boolean): Boolean {
        inTracked = false
        return runWithControllerView {
            (it.parent as? ViewGroup)?.clipChildren = true
            if (parseAutoScale) isAutoScaleFromTouchEnd(formTrigDuration, true) else false
        } ?: false
    }

    override fun onDoubleClick() {
        withConfig run@{ config ->
            if (config.isDefaultMaxScreen || !config.fullMaxScreenEnable) return@run
            config.preToFullMaxChange {
                contentLayoutView?.let { _ ->
                    runWithControllerView {
                        if (!isMaxFull) {
                            isScreenRotateLocked = config.defaultScreenOrientation != ZVideoView.LOCK_SCREEN_UNSPECIFIED
                            checkSelfScreenLockAvailable(isScreenRotateLocked)
                        }
                        setContent(it, !isMaxFull, true, isInit = false)
                        config.onFullContentListener?.onFullMaxChanged(this, isMaxFull)
                        if (isMaxFull) {
                            curScreenRotation = when (config.defaultScreenOrientation) {
                                0 -> RotateOrientation.L1
                                1 -> RotateOrientation.P0
                                else -> null
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onTracked(isStart: Boolean, offsetX: Float, offsetY: Float, easeY: Float, formTrigDuration: Float) {
        if (isStart) initCalculate()
        inTracked = true
        runWithControllerView {
            (it.parent as? ViewGroup)?.clipChildren = false
            setBackground(1f - formTrigDuration)
            followWithFinger(offsetX, offsetY)
            scaleWithOffset(easeY)
            onTracked(isStart, false, formTrigDuration)
        }
    }

    override fun close() {
        dismissed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (config?.onFullContentListener?.onKeyEvent(keyCode, event) == true) return true
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isAnimRun) return true else finish();return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun finish() {
        if (curScreenRotation?.isLandSpace() != false) curScreenRotation = RotateOrientation.P0
        screenRotationsChanged(false)
        if (isDismissing) return
        isAutoScaleFromTouchEnd(1f, false)
    }

    private fun dismissed(fromAnimEnd: Boolean = false) {
        try {
            scaleAnim?.let {
                if (!fromAnimEnd && it.isRunning) {
                    val toEnd = !it.isFull
                    it.end()
                    if (toEnd) return@let
                }
            }
            fullHandler.removeCallbacksAndMessages(null)
            config?.preToDismiss {
                onDisplayChange(false)
                runWithControllerView {
                    if (it.parent != null) (it.parent as? ViewGroup)?.removeView(it)
                    config?.originViewGroup?.addView(it, config?.originLayoutParams)
                }
                curScaleOffset = 0f
                screenUtil?.release()
                screenUtil = null
                scaleAnim?.cancel()
                scaleAnim = null
                calculateUtils = null
                isDismissing = false
                backgroundView = null
                config?.clear()
                config = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            super.finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fullHandler.removeMessages(HANDLE_RESIZE_CONTROLLER)
        fullHandler.sendMessageDelayed(Message.obtain().apply { what = HANDLE_RESIZE_CONTROLLER;arg1 = 0 }, 100)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isScreenRotateLocked = screenUtil?.checkAccelerometerSystem() != false || !hasFocus
        if (hasFocus) changeSystemWindowVisibility(true)
        checkSelfScreenLockAvailable(isScreenRotateLocked)
        config?.onFullContentListener?.onFocusChange(this, isMaxFull)
    }

    private fun checkSelfScreenLockAvailable(newState: Boolean): Boolean {
        val b = screenUtil?.checkAccelerometerSystem() ?: false
        var isLock = false
        return try {
            if (!b) {
                isLock = true;false
            } else {
                isLock = newState;true
            }
        } finally {
            screenUtil?.lockOrientation(isLock)
        }
    }
}