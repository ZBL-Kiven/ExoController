package com.zj.videotest.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.zj.loading.BaseLoadingView
import com.zj.loading.DisplayMode
import com.zj.player.base.LoadingMode
import com.zj.player.controller.BaseListVideoController
import com.zj.player.img.ImgLoader
import com.zj.videotest.R

class CCVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseListVideoController(c, attr, def) {

    override fun onImgGot(path: String, type: ImgLoader.ImgType, tag: String, e: Exception?) {
        val thumb = getThumbView() ?: return
        val background = getBackgroundView() ?: return
        when (type) {
            ImgLoader.ImgType.IMG -> {
                Glide.with(thumb).asBitmap().skipMemoryCache(true).fitCenter().load(path).into(thumb)
                Glide.with(background).asBitmap().load(path).centerCrop().thumbnail(0.35f).transform(RenderScriptBlur(background.context, 12)).into(background)
            }
            ImgLoader.ImgType.GIF -> {
                Glide.with(thumb).asGif().skipMemoryCache(true).fitCenter().load(path).into(thumb)
                Glide.with(background).asBitmap().load(path).centerCrop().thumbnail(0.35f).transform(RenderScriptBlur(background.context, 12)).into(background)
            }
        }
    }

    fun stopOrResumeGif(stop: Boolean) {
        val thumb = getThumbView() ?: return
        (thumb.drawable as? GifDrawable)?.let {
            if (stop) it.stop() else it.start()
        }
    }

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(false, withState = false)
        full(false)
    }

    override fun onFullKeyEvent(code: Int, event: KeyEvent): Boolean {

        return true
    }
}