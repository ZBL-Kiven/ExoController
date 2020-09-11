package com.zj.player.full

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.FloatRange
import androidx.annotation.LayoutRes
import androidx.core.view.children
import com.zj.player.R
import com.zj.player.anim.ZFullValueAnimator
import java.lang.IllegalArgumentException
import java.lang.ref.WeakReference
import kotlin.math.roundToInt


@Suppress("unused")
@SuppressLint("ViewConstructor")
class BaseGestureFullScreenDialog private constructor(private var controllerView: WeakReference<View>?, contentLayout: Int, private val fullMaxScreenEnable: Boolean, private val isDefaultMaxScreen: Boolean, private val defaultScreenOrientation: Int, private val onFullScreenListener: FullScreenListener?, private val onFullContentListener: FullContentListener?) : FrameLayout(WeakReference(controllerView?.get()?.context).get() ?: throw NullPointerException()) {

    companion object {
        private const val MAX_DEEP_RATIO = 0.55f

        fun showInContent(view: View, @LayoutRes contentLayout: Int, fullMaxScreenEnable: Boolean, defaultScreenOrientation: Int, onFullContentListener: FullContentListener): BaseGestureFullScreenDialog {
            return BaseGestureFullScreenDialog(WeakReference(view), contentLayout, fullMaxScreenEnable, false, defaultScreenOrientation, null, onFullContentListener)
        }

        fun showFull(view: View, defaultScreenOrientation: Int, onFullScreenListener: FullScreenListener): BaseGestureFullScreenDialog {
            return BaseGestureFullScreenDialog(WeakReference(view), -1, fullMaxScreenEnable = false, isDefaultMaxScreen = true, defaultScreenOrientation = defaultScreenOrientation, onFullScreenListener = onFullScreenListener, onFullContentListener = null)
        }
    }

    private var _width: Float = 0f
    private var _height: Float = 0f
    private val originWidth: Int = getControllerView().measuredWidth
    private val originHeight: Int = getControllerView().measuredHeight
    private var calculateUtils: RectFCalculateUtil? = null
    private var originInScreen: Point? = null
    private var curScaleOffset: Float = 1.0f
    private var isAnimRun = false
    private var scaleAnim: ZFullValueAnimator? = null
    private var isDismissing = false
    private val interpolator = DecelerateInterpolator(1.5f)
    private var isMaxFull = isDefaultMaxScreen
    private val vp: ViewGroup? = getControllerView().parent as? ViewGroup
    private val vlp: ViewGroup.LayoutParams? = getControllerView().layoutParams
    private var originViewRectF: RectF? = null
    private var contentLayoutView: View? = null
    private var backgroundView: View? = null
    private var realWindowSize = Point()
    private var screenUtil: ScreenOrientationListener? = null
    private var isScreenRotateLocked: Boolean = false
    private var curScreenRotation: RotateOrientation? = null
        set(value) {
            if (field == value && getControllerView().rotation == value?.degree) return
            field = value
            if (value == null) return
            if (value != RotateOrientation.P1) {
                getControllerView().rotation = value.degree
                val r = (value == RotateOrientation.L0 || value == RotateOrientation.L1)
                val w = _width.roundToInt()
                val h = _height.roundToInt()
                val vlp = LayoutParams(if (r) h else w, if (r) w else h)
                vlp.gravity = Gravity.CENTER
                getControllerView().layoutParams = vlp
            }
        }

    init {
        (getControllerView().context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.getRealSize(realWindowSize)
        if (!isDefaultMaxScreen && contentLayout > 0) contentLayoutView = View.inflate(getControllerView().context, contentLayout, null)
        changeSystemWindowVisibility(true)
        if (childCount > 0) removeAllViews()
        backgroundView = View(context)
        backgroundView?.setBackgroundColor(Color.BLACK)
        backgroundView?.layoutParams = ViewGroup.LayoutParams(-1, -1)
        this.addView(backgroundView, 0)
        setContent(isMaxFull)
        initListeners()
        showAnim()
    }

    private fun setContent(isMaxFull: Boolean, isResizeCalculate: Boolean = false) {
        if (!this.isMaxFull && isMaxFull) curScreenRotation = null
        this.isMaxFull = isMaxFull
        try {
            val controller = getControllerView()
            val controllerNeedAdd = (controller.parent as? ViewGroup)?.let { parent ->
                if (isMaxFull || contentLayoutView == null) {
                    if (parent != this) {
                        parent.removeView(controller); true
                    } else false
                } else if (parent != contentLayoutView) {
                    parent.removeView(controller); true
                } else false
            } ?: if (controller.parent !is ViewGroup) throw IllegalArgumentException("controllers parent is not a viewGroup ,so what's that?") else true
            if (isMaxFull || contentLayoutView == null) {
                if (contentLayoutView != null) (contentLayoutView?.parent as? ViewGroup)?.removeView(contentLayoutView)
                if (controllerNeedAdd) this@BaseGestureFullScreenDialog.addView(controller);return
            }
            contentLayoutView?.let {
                (it as? ViewGroup)?.clipChildren = false
                it.findViewById<ViewGroup>(R.id.player_gesture_full_screen_content)?.let { v ->
                    if (controllerNeedAdd) v.addView(controller, vlp)
                    v.clipChildren = false
                } ?: if (controllerNeedAdd) (it as? ViewGroup)?.addView(controller, vlp) ?: throw IllegalArgumentException("the content layout view your set is not container a view group that id`s [R.id.playerFullScreenContent] ,and your content layout is not a view group!")
                val contentNeedAdd = (it.parent as? ViewGroup)?.let { parent ->
                    if (parent != this) {
                        parent.removeView(it);true
                    } else false
                } ?: true
                if (contentNeedAdd) this@BaseGestureFullScreenDialog.addView(it)
                onFullContentListener?.onContentLayoutInflated(it)
            }
        } finally {
            if (isResizeCalculate) init()
        }
    }

    private fun showAnim() {
        fun start() {
            (getActivity()?.window?.decorView as? ViewGroup)?.addView(this, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            post {
                init()
                isAnimRun = true
                scaleAnim?.start(true)
            }
        }
        start()
    }

    private fun init() {
        initCalculate()
        updateContent(0f)
        screenRotationsChanged(true)
        setBackground(1f)
    }

    private fun initCalculate() {
        val cps = getViewPoint(vp)
        originViewRectF = RectF(cps.x, cps.y, cps.x + originWidth, cps.y + originHeight)
        val viewRectF = getWindowSize(isMaxFull)
        _width = viewRectF.right - viewRectF.left
        _height = viewRectF.bottom - viewRectF.top
        calculateUtils = RectFCalculateUtil(viewRectF, RectF(originViewRectF))
    }

    private fun dismissed() {
        onDisplayChange(false)
        curScaleOffset = 0f
        if (getControllerView().parent != null) (getControllerView().parent as? ViewGroup)?.removeView(getControllerView())
        vp?.addView(getControllerView(), vlp)
        screenUtil?.release()
        screenUtil = null
        if (scaleAnim?.isRunning == true) scaleAnim?.cancel()
        scaleAnim = null
        calculateUtils = null
        isDismissing = false
        backgroundView = null
        controllerView = null
        (getActivity()?.window?.decorView as? ViewGroup)?.removeView(this)
    }

    private fun initListeners() {
        scaleAnim = ZFullValueAnimator(object : ZFullValueAnimator.FullAnimatorListener() {

            override fun onStart() {
                initCalculate()
            }

            override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean) {
                if (isFull) {
                    updateContent(1 - duration)
                } else {
                    if (originInScreen == null) originInScreen = Point(getControllerView().scrollX, getControllerView().scrollY)
                    originInScreen?.let {
                        val sx = (it.x * (1f - duration)).roundToInt()
                        val sy = (it.y * (1f - duration)).roundToInt()
                        getControllerView().scrollTo(sx, sy)
                    }
                    val curOff = if (curScaleOffset <= 0f) 1f else curScaleOffset
                    val offset = (duration * curOff) + (1f - curOff)
                    updateContent(offset)
                }
            }

            override fun onAnimEnd(animation: Animator, isFull: Boolean) {
                isAnimRun = false
                originInScreen = null
                if (!isFull) dismissed()
                else onDisplayChange(true)
            }
        }, false).apply {
            duration = 220
        }
        screenUtil = ScreenOrientationListener(WeakReference(context)) {
            if (isMaxFull) curScreenRotation = it
        }
    }

    fun isInterruptTouchEvent(): Boolean {
        return isAnimRun || isDismissing
    }

    fun onEventEnd(formTrigDuration: Float): Boolean {
        (getControllerView().parent as? ViewGroup)?.clipChildren = true
        return isAutoScaleFromTouchEnd(formTrigDuration, true)
    }

    fun onDoubleClick() {
        if (isDefaultMaxScreen || !fullMaxScreenEnable) return
        contentLayoutView?.let {
            setContent(!isMaxFull, true)
            onFullContentListener?.onFullMaxChanged(this@BaseGestureFullScreenDialog, isMaxFull)
            if (isMaxFull) curScreenRotation = when (defaultScreenOrientation) {
                0 -> RotateOrientation.L0
                1 -> RotateOrientation.P0
                else -> null
            }
        }
    }

    fun onTracked(isStart: Boolean, offsetX: Float, offsetY: Float, easeY: Float, formTrigDuration: Float) {
        (getControllerView().parent as? ViewGroup)?.clipChildren = false
        setBackground(1f - formTrigDuration)
        followWithFinger(offsetX, offsetY)
        scaleWithOffset(easeY)
        onTracked(isStart, false, formTrigDuration)
    }

    internal fun getControllerView(): View {
        return controllerView?.get() ?: throw java.lang.NullPointerException("case the controller view is null ,so what`s your displaying wishes")
    }

    private fun updateContent(offset: Float) {
        val rect = calculateUtils?.calculate(offset)
        getFrameLayoutParams(getControllerView(), rect)
    }

    private fun followWithFinger(x: Float, y: Float) {
        if (isMaxFull) return
        getControllerView().scrollTo(x.roundToInt(), y.roundToInt())
    }

    private fun scaleWithOffset(curYOffset: Float) {
        if (isMaxFull) return
        updateContent(curYOffset)
        curScaleOffset = 1 - curYOffset
    }

    private fun isAutoScaleFromTouchEnd(curYOffset: Float, formUser: Boolean): Boolean {
        if (isMaxFull && formUser) return true
        val isScaleAuto = curYOffset <= MAX_DEEP_RATIO
        if (isScaleAuto) {
            getControllerView().scrollTo(0, 0)
            scaleWithOffset(0f)
            setBackground(1f)
            onTracked(false, isEnd = true, formTrigDuration = 0f)
        } else {
            isAnimRun = true
            changeSystemWindowVisibility(false)
            scaleAnim?.start(false)
        }
        return isScaleAuto
    }

    private fun getFrameLayoutParams(view: View, ref: RectF?) {
        if (ref == null) return
        val pl: Int = ref.left.roundToInt()
        val pt: Int = ref.top.roundToInt()
        val pr: Int = ref.right.roundToInt()
        val pb: Int = ref.bottom.roundToInt()
        val flp = LayoutParams((_width - (pl + pr)).roundToInt(), (_height - (pt + pb)).roundToInt())
        flp.setMargins(pl, pt, pr, pb)
        view.layoutParams = flp
    }

    fun dismiss() {
        screenRotationsChanged(false)
        if (getActivity()?.isFinishing == true) {
            dismissed();(getActivity()?.window?.decorView as? ViewGroup)?.removeView(this)
        } else {
            if (isDismissing) return
            isDismissing = true
            isAutoScaleFromTouchEnd(1f, false)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun changeSystemWindowVisibility(visible: Boolean) {
        val flag: Int = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        val flagSystem: Int = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        getActivity()?.let {
            val visibility = this.systemUiVisibility
            if (visible) {
                if (it.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                this.systemUiVisibility = visibility.or(flagSystem)
                it.window?.addFlags(flag)
            } else {
                this.systemUiVisibility = visibility.and(flagSystem)
                it.window?.clearFlags(flag)
            }
        }
    }

    private fun getActivity(): Activity? {
        return (context as? Activity)
    }

    private fun setBackground(@FloatRange(from = 0.0, to = 1.0) duration: Float) {
        if (isMaxFull) return
        val d = interpolator.getInterpolation(duration)
        backgroundView?.alpha = ((duration * 0.85f) + 0.15f)
        (contentLayoutView as? ViewGroup)?.let {
            it.children.forEach { cv ->
                if (cv.id != R.id.player_gesture_full_screen_content) {
                    cv.alpha = d
                }
            }
        }
    }

    private fun getWindowSize(isMaxFull: Boolean): RectF {
        return if (isMaxFull || contentLayoutView == null) {
            val rp = getViewPoint(getActivity()?.window?.decorView)
            val w = getActivity()?.window?.decorView?.width ?: 0
            val h = getActivity()?.window?.decorView?.height ?: 0
            RectF(rp.x, rp.y, rp.x + w, rp.y + h)
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
        getControllerView().rotation = 0f
        if (isRotateEnable) screenUtil?.enable() else {
            screenUtil?.disable();curScreenRotation = null
        }
    }

    fun onResume() {
        changeSystemWindowVisibility(true)
    }

    fun onStopped() {
        changeSystemWindowVisibility(false)
    }

    fun lockScreenRotation(isLock: Boolean): Boolean {
        isScreenRotateLocked = isLock
        return checkSelfScreenLockAvailable(isLock)
    }

    fun isLockedCurrent(): Boolean {
        return screenUtil?.let {
            if (!it.checkAccelerometerSystem()) true else isScreenRotateLocked
        } ?: false
    }

    private fun onTracked(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
        if (!isMaxFull) onFullContentListener?.onTrack(isStart, isEnd, formTrigDuration) ?: onFullScreenListener?.onTrack(isStart, isEnd, formTrigDuration)
    }

    private fun onDisplayChange(isShow: Boolean) {
        onFullContentListener?.onDisplayChanged(isShow) ?: onFullScreenListener?.onDisplayChanged(isShow)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        checkSelfScreenLockAvailable(isScreenRotateLocked)
        onFullContentListener?.onFocusChange(this, isMaxFull)
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