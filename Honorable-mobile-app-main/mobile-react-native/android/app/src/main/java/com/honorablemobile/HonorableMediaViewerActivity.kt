package com.honorablemobile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import app.honorable.editor.NativeWorkspaceInsets

class HonorableMediaViewerActivity : Activity() {
    private var video: VideoView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = Uri.parse(intent.getStringExtra("uri") ?: run { finish(); return })
        val isVideo = intent.getStringExtra("kind") == "VIDEO"
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = if(Build.VERSION.SDK_INT >= 26) Color.WHITE else Color.BLACK
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if(Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(12),dp(4),dp(12),dp(8)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(this).apply {
            text = "‹"; contentDescription = "Close media preview"; setTextColor(Color.BLACK)
            background = GradientDrawable().apply { setColor(Color.WHITE);setStroke(dp(1).coerceAtLeast(1),Color.BLACK);cornerRadius=dp(8).toFloat() }
            setOnClickListener { finish() }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(TextView(this).apply { text = if(isVideo) "Video / Preview" else "Photo / Preview"; textSize=17f;setTextColor(Color.BLACK);setPadding(dp(12),0,0,0) },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)
        val status = TextView(this).apply { setTextColor(Color.DKGRAY);textSize=12f;setPadding(0,dp(8),0,dp(8));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        if(isVideo) {
            val player = VideoView(this).apply { setBackgroundColor(Color.WHITE);setVideoURI(uri) }
            video=player
            player.setMediaController(MediaController(this).apply { setAnchorView(player) })
            player.setOnPreparedListener { media ->
                intent.getLongExtra("timestampMs",0L).takeIf { it > 0 }?.let { player.seekTo(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
                media.isLooping=false;player.start()
            }
            player.setOnErrorListener { _,_,_ -> status.text="Media unavailable. Check access to this video.";true }
            root.addView(FrameLayout(this).apply { addView(player,FrameLayout.LayoutParams(-1,-1)) },LinearLayout.LayoutParams(-1,0,1f))
        } else {
            root.addView(ImageView(this).apply { setBackgroundColor(Color.WHITE);scaleType=ImageView.ScaleType.FIT_CENTER;setImageURI(uri) },LinearLayout.LayoutParams(-1,0,1f))
        }
        root.addView(status)
        setContentView(root)
        NativeWorkspaceInsets.install(this,root)
    }
    override fun onPause() { video?.pause();super.onPause() }
    override fun onDestroy() { video?.stopPlayback();super.onDestroy() }
}
