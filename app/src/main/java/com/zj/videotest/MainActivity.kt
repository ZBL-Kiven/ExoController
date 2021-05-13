package com.zj.videotest

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.gyf.immersionbar.ImmersionBar
import com.zj.cf.managers.BaseFragmentManager
import com.zj.player.logs.VideoEventListener
import com.zj.player.logs.ZPlayerLogs
import com.zj.videotest.frg.RFeedFragment
import com.zj.videotest.frg.OtherFragment
import com.zj.videotest.frg.SingleFragment
import com.zj.views.DrawableTextView
import com.zj.webkit.CCWebView

class MainActivity : AppCompatActivity() {

    private var fragmentManager: BaseFragmentManager? = null
    private var mRFeedFragment: RFeedFragment? = null
    private var mRewardFragmentR: OtherFragment? = null
    private var mMeFragmentR: SingleFragment? = null

    private var mFeedNavView: DrawableTextView? = null
    private var mRewardNavView: DrawableTextView? = null
    private var mMeNavView: DrawableTextView? = null
    private var goldView: DrawableTextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CCWebView.onAppAttached(this, "")
        setContentView(R.layout.r_main_act_content)
        ImmersionBar.with(this).transparentStatusBar().statusBarDarkFont(true).init()
        initView()
        initFrg()
        ZPlayerLogs.setVideoEventListener(object : VideoEventListener() {
            override fun onError(e: Exception) {
                Log.e("------ ", " ================ error !!     ${e.message}")
            }

            override fun onLog(s: String, curPath: String, accessKey: String, modeName: String, params: Map<String, Any>?) {
                Log.e("------ ", s)
            }
        })
    }

    private fun initView() {
        mFeedNavView = findViewById(R.id.r_main_act_fragment_nav_btn_feed)
        mRewardNavView = findViewById(R.id.r_main_act_fragment_nav_btn_reward)
        mMeNavView = findViewById(R.id.r_main_act_fragment_nav_btn_me)
        goldView = findViewById(R.id.r_main_act_gold_view)
        goldView?.setOnClickListener {
            startActivity(Intent(this, TestActivity2::class.java))
        }
    }

    private fun initFrg() {
        mRFeedFragment = RFeedFragment()
        mRewardFragmentR = OtherFragment()
        mMeFragmentR = SingleFragment()
        fragmentManager = object : BaseFragmentManager(this, R.id.r_main_act_fragment_content, 0, listOfNotNull(mFeedNavView, mRewardNavView, mMeNavView), mRFeedFragment, mRewardFragmentR, mMeFragmentR) {
            override fun syncSelectState(selectId: String) {
                super.syncSelectState(selectId)
                if (selectId == mRFeedFragment?.fId) {

                } else {
                    //todo hide time bar
                }
            }

            override fun whenShowSameFragment(shownId: String) {
                if (shownId == mRFeedFragment?.fId) {

                }
            }
        }
    }

    override fun finish() {
        Log.e("------ ", "main activity is finishing")
        super.finish()
    }

    override fun onTrimMemory(level: Int) {
        Log.e("------ ", "main activity is onTrimMemory  $level")
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        Log.e("------ ", "main activity is destroyed")
        super.onDestroy()
    }
}
