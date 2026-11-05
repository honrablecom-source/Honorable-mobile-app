package com.honorablemobile

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import app.honorable.editor.NativeWorkspaceInsets
import app.honorable.editor.PhotoSourceDecoder
import java.util.concurrent.Executors

class HonorableMediaViewerActivity : Activity() {
    private var video: VideoView? = null
    private var playback: MediaPreviewPlayback? = null
    private var photoView: ImageView? = null
    private val photoWorker=Executors.newSingleThreadExecutor()
    private val photoImage=MediaPreviewImage<Bitmap>{it.recycle()}
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
            val state = MediaPreviewPlayback(
                savedInstanceState?.takeIf { it.containsKey("previewPosition") }?.getLong("previewPosition") ?: intent.getLongExtra("timestampMs",0L),
                savedInstanceState?.getBoolean("previewPlaying", true) ?: true
            )
            playback=state
            player.setMediaController(MediaController(this).apply { setAnchorView(player) })
            player.setOnPreparedListener { media ->
                if(isFinishing || isDestroyed) return@setOnPreparedListener
                val request=state.prepared(media.duration)
                player.seekTo(request.position)
                media.isLooping=false
                if(request.start) player.start()
            }
            player.setOnCompletionListener { state.completed() }
            player.setOnErrorListener { _,_,_ -> state.failed();status.text="Media unavailable. Check access to this video.";true }
            root.addView(FrameLayout(this).apply { addView(player,FrameLayout.LayoutParams(-1,-1)) },LinearLayout.LayoutParams(-1,0,1f))
        } else {
            val view=ImageView(this).apply { setBackgroundColor(Color.WHITE);scaleType=ImageView.ScaleType.FIT_CENTER }
            photoView=view
            root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
            status.text="Loading photo preview…"
            photoWorker.execute {
                if(photoImage.isClosed())return@execute
                try {
                    val decoded=PhotoSourceDecoder.decode(this,uri,2024)
                    runOnUiThread {
                        if(isFinishing||isDestroyed||photoView!==view){decoded.recycle();return@runOnUiThread}
                        if(photoImage.publish(decoded)){
                            view.setImageBitmap(decoded)
                            status.text="Photo preview. Original unchanged."
                        }
                    }
                } catch(failure:Exception) {
                    runOnUiThread { if(!isFinishing&&!isDestroyed&&photoView===view)status.text="Photo preview unavailable. Check access or try a smaller image." }
                } catch(failure:OutOfMemoryError) {
                    runOnUiThread { if(!isFinishing&&!isDestroyed&&photoView===view)status.text="Not enough memory to preview this photo. Close other tools and try again." }
                }
            }
        }
        root.addView(status)
        setContentView(root)
        NativeWorkspaceInsets.install(this,root)
    }
    override fun onResume() {
        super.onResume()
        if(playback?.resume()==true) video?.start()
    }
    override fun onPause() {
        playback?.pause(video?.currentPosition,video?.isPlaying)
        video?.pause()
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        playback?.snapshot(video?.currentPosition,video?.isPlaying)?.let {
            outState.putLong("previewPosition",it.position)
            outState.putBoolean("previewPlaying",it.playing)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        photoView?.setImageDrawable(null)
        photoView=null
        photoImage.close()
        photoWorker.shutdown()
        video?.stopPlayback()
        super.onDestroy()
    }
}
