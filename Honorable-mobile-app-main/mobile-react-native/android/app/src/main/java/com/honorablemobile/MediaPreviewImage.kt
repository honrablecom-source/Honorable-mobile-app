package com.honorablemobile

/** Own one decoded resource. UI must detach its drawable before close or replacement. */
internal class MediaPreviewImage<T:Any>(private val release:(T)->Unit) {
 private var closed=false
 private var image:T?=null
 @Synchronized fun isClosed()=closed
 @Synchronized fun publish(next:T):Boolean {
  if(closed){release(next);return false}
  if(image===next)return true
  val previous=image;image=next
  if(previous!=null)release(previous)
  return true
 }
 @Synchronized fun close(){
  if(closed)return
  closed=true;val previous=image;image=null
  if(previous!=null)release(previous)
 }
}
