package com.zj.videotest.feed

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zj.img.ImageLoader
import com.zj.videotest.feed.data.FeedDataIn
import com.zj.player.ZController
import com.zj.player.config.VideoConfig
import com.zj.player.list.BaseListVideoController
import com.zj.player.list.ListVideoAdapterDelegate
import com.zj.videotest.BuildConfig
import com.zj.videotest.R
import com.zj.videotest.videos.CCVideoController
import com.zj.views.list.adapters.AnimationAdapter
import com.zj.views.list.holders.BaseViewHolder
import java.lang.ref.WeakReference

class FeedContentAdapter<T : FeedDataIn> : AnimationAdapter<T>(R.layout.r_main_fg_feed_item) {

    init {
        setFirstOnly(false)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        adapterDelegate?.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterDelegate?.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onViewDetachedFromWindow(holder: BaseViewHolder) {
        super.onViewDetachedFromWindow(holder)
        adapterDelegate?.onViewDetachedFromWindow(holder)
    }

    override fun bindData(holder: BaseViewHolder?, p: Int, d: T?, pl: MutableList<Any>?) {
        adapterDelegate?.bindData(holder, p, d, pl)
    }

    private var adapterDelegate: ListVideoAdapterDelegate<T, BaseListVideoController, BaseViewHolder>? = object : ListVideoAdapterDelegate<T, BaseListVideoController, BaseViewHolder>(this@FeedContentAdapter) {
        override fun createZController(vc: BaseListVideoController): ZController {
            return ZController.build(vc, VideoConfig.create().setCacheEnable(true).setDebugAble(BuildConfig.DEBUG).setCacheFileDir("feed/videos").updateMaxCacheSize(200L * 1024 * 1024))
        }

        override fun getViewController(holder: BaseViewHolder?): BaseListVideoController? {
            return holder?.getView<CCVideoController>(R.id.r_main_fg_feed_item_vc)
        }

        override fun getItem(p: Int): T? {
            return this@FeedContentAdapter.getItem(p)
        }

        override fun getPathAndLogsCallId(d: T?): Pair<String, Any?>? {
            return Pair(d?.getVideoPath() ?: "", d?.getSourceId())
        }

        override fun onBindData(holder: BaseViewHolder?, p: Int, d: T?, vc: BaseListVideoController, pl: MutableList<Any>?) {
            onBindAdapterData(holder, p, d, vc, pl)
        }
    }

    private fun onBindAdapterData(holder: BaseViewHolder?, p: Int, d: T?, vc: BaseListVideoController, pl: MutableList<Any>?) {
        context?.let { ctx ->
            holder?.let { h ->
                val avatarPath = d?.getAvatarPath() ?: ""
                val imgPath = d?.getImagePath() ?: ""
                val videoWidth = d?.getViewWidth() ?: 1
                val videoHeight = d?.getViewHeight() ?: 1
                h.getView<ImageView>(R.id.r_main_fg_feed_item_iv_avatar)?.let {
                    it.post { loadAvatar(ctx, avatarPath, it) }
                    it.setOnClickListener { avatarClicked(d, p) }
                }
                h.getView<TextView>(R.id.r_main_fg_feed_item_tv_nickname)?.text = d?.getNickname()
                h.getView<TextView>(R.id.r_main_fg_feed_item_tv_desc)?.text = d?.getDesc()
                h.getView<TextView>(R.id.r_main_fg_feed_item_tv_claps)?.text = "${d?.getClapsCount() ?: 0}"
                h.getView<View>(R.id.r_main_fg_feed_item_ll_claps)?.setOnClickListener {
                    clap(d, p)
                }
                vc.post {
                    val maxWidth = holder.itemView.width
                    val maxHeight = holder.itemView.height
                    loadThumbImage(ctx, videoWidth, videoHeight, maxWidth, maxHeight, imgPath, vc)
                }
                vc.setScreenContentLayout(R.layout.r_main_video_details_content) { v ->
                    v.findViewById<ImageView>(R.id.r_main_fg_list_iv_avatar)?.let {
                        loadAvatar(vc.context, d?.getAvatarPath() ?: "", it)
                    }
                }
            }
        }
    }

    private fun clap(d: T?, p: Int) {

    }

    private fun avatarClicked(d: T?, p: Int) {

    }

    private fun loadAvatar(context: Context, url: String, iv: ImageView) {
        //        val options = RequestOptions().centerCrop().transform(CircleCrop())
        //        val width = if (iv.width <= 0) 1 else iv.width
        //        val height = if (iv.height <= 0) 1 else iv.height
        //        Glide.with(context).load(url).override(width, height).apply(options).into(iv)
    }

    private fun loadThumbImage(ctx: Context, videoWidth: Int, videoHeight: Int, maxWidth: Int, maxHeight: Int, imgPath: String, vc: BaseListVideoController) {
        ImageLoader.load(ctx, videoWidth, videoHeight, maxWidth, maxHeight, 0.5f, 0.5f, "feed", imgPath, "") {
            Log.e("----- ", "33333   ${System.currentTimeMillis()}")
            it.withCtx(WeakReference(ctx))?.centerCrop()?.blur(90)?.crossFade()?.memoryEnable(false)?.into(vc.getBackgroundView())
            it.withCtx(WeakReference(ctx))?.thumbnail(0.7f)?.crossFade()?.memoryEnable(false)?.into(vc.getThumbView())
        }
    }

    override fun getAnimators(p0: View?): Array<Animator> {
        if (p0 == null) return arrayOf()
        val anim = ObjectAnimator.ofFloat(p0, "alpha", 0.0f, 1.0f).setDuration(300)
        return arrayOf(anim)
    }

    fun release() {
        adapterDelegate?.release()
        adapterDelegate = null
    }
}