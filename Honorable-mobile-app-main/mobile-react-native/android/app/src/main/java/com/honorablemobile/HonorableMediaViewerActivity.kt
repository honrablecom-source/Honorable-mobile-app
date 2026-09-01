package com.honorablemobile

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView

class HonorableMediaViewerActivity:Activity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;val uri=Uri.parse(intent.getStringExtra("uri")?:run{finish();return});if(intent.getStringExtra("kind")=="VIDEO"){val video=VideoView(this);video.setBackgroundColor(Color.BLACK);video.setVideoURI(uri);video.setMediaController(MediaController(this).apply{setAnchorView(video)});video.setOnPreparedListener{player->intent.getLongExtra("timestampMs",0).takeIf{it>0}?.let{video.seekTo(it.toInt())};player.isLooping=false;video.start()};setContentView(video,ViewGroup.LayoutParams(-1,-1))}else{val image=ImageView(this).apply{setBackgroundColor(Color.BLACK);scaleType=ImageView.ScaleType.FIT_CENTER;setImageURI(uri);setOnClickListener{finish()}};setContentView(image,ViewGroup.LayoutParams(-1,-1))}}
}
