package com.zj.player.full

import android.view.KeyEvent
import android.view.View

internal interface FullActivityIn {
    fun isMaxFull(): Boolean
    fun isInterruptTouchEvent(): Boolean
    fun onEventEnd(formTrigDuration: Float, parseAutoScale: Boolean): Boolean
    fun onTracked(isStart: Boolean, offsetX: Float, offsetY: Float, easeY: Float, formTrigDuration: Float)
    fun onDoubleClick()
    fun lockScreenRotation(isLock: Boolean): Boolean
    fun isLockedCurrent(): Boolean
    fun close()
}

internal interface FullContentListener : FullScreenListener {

    fun onContentLayoutInflated(content: View)

    fun onFullMaxChanged(ai: FullActivityIn, isMax: Boolean)
}

internal interface FullScreenListener {

    fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?)

    fun onFocusChange(ai: FullActivityIn, isMax: Boolean)

    fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float)

    fun onKeyEvent(code: Int, event: KeyEvent): Boolean
}

enum class RotateOrientation(val degree: Float) {
    P0(0f), P1(180f), L0(270f), L1(90f);

    fun isLandSpace(): Boolean {
        return this == L0 || this == L1
    }
}