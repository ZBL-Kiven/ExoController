package com.zj.player.full

import android.view.View

interface FullContentListener : FullScreenListener {

    fun onContentLayoutInflated(dialog: BaseGestureFullScreenDialog, content: View)

    fun onFullMaxChanged(dialog: BaseGestureFullScreenDialog, isMax: Boolean)
}

interface FullScreenListener {

    fun onDisplayChanged(dialog: BaseGestureFullScreenDialog, isShow: Boolean)

    fun onFocusChange(dialog: BaseGestureFullScreenDialog, isMax: Boolean)

    fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float)
}

enum class RotateOrientation(val degree: Float) {
    P0(0f), P1(180f), L0(270f), L1(90f)
}