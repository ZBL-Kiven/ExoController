package com.zj.videotest.ytb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import com.zj.webkit.CCWebView
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class CCYtbWebView(context: Context, private val ytbWebBridge: YtbWebBridge) : CCWebView<YtbWebBridge>(context) {

    override val javaScriptClient: YtbWebBridge; get() = ytbWebBridge
    override val webDebugEnable: Boolean; get() = true

    override fun getDefaultVideoPoster(): Bitmap? {
        val result = super.getDefaultVideoPoster()
        return result ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val h1 = (parent as? ViewGroup)?.height ?: h
        super.onSizeChanged(w, min((w * 9f / 16f).roundToInt(), h1), ow, oh)
    }
}