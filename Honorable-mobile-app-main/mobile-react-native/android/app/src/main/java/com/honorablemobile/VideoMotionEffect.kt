package com.honorablemobile

import android.graphics.Matrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation

/** Shared composition effect: picture and clip title move together; timed captions stay anchored. */
@UnstableApi
internal class VideoMotionEffect(clips:List<VideoSegment>):MatrixTransformation{
    private val timeline=VideoMotionTimeline(clips)
    private val identity=clips.all{it.motion.isIdentity}
    override fun getMatrix(presentationTimeUs:Long)=Matrix().apply{setValues(timeline.at(presentationTimeUs).matrixValues())}
    // A start frame can be identity while subsequent frames move. Never infer this from time zero.
    override fun isNoOp(inputWidth:Int,inputHeight:Int)=identity
}
