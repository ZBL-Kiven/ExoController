package com.zj.videotest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zj.player.z.ZController
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.feed.data.DataType
import com.zj.videotest.ytb.YtbContentChecker
import com.zj.webkit.CCWebView
import kotlinx.android.synthetic.main.test_act_2_content.*
import kotlinx.android.synthetic.main.test_act_content.*

class TestActivity2 : AppCompatActivity() {

    private var controller: ZController<*, *>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CCWebView.onAppAttached(this, "")
        setContentView(R.layout.test_act_2_content)
        controller = VideoControllerPlayers.getOrCreatePlayerWithVc(mVideoView) { DataType.VIDEO }
        controller?.playOrResume("http://vjs.zencdn.net/v/oceans.mp4")
    }

    override fun onResume() {
        super.onResume()
        controller?.playOrResume()
    }

    override fun onPause() {
        super.onPause()
        controller?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoControllerPlayers.stopVideo()
    }
}