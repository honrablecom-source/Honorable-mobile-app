package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class MediaPreviewImageTest {
 private class Resource {var releases=0}
 @Test fun keepsPublishedImageUntilCloseAndClosesOnce(){
  val owner=MediaPreviewImage<Resource>{it.releases++};val image=Resource()
  assertFalse(owner.isClosed());assertTrue(owner.publish(image));assertEquals(0,image.releases)
  owner.close();owner.close();assertTrue(owner.isClosed());assertEquals(1,image.releases)
 }
 @Test fun lateDecodeAfterCloseIsReleasedWithoutPublication(){
  val owner=MediaPreviewImage<Resource>{it.releases++};owner.close();val image=Resource()
  assertFalse(owner.publish(image));assertEquals(1,image.releases)
 }
 @Test fun replacementReleasesPreviousResourceButNotTheCurrentOne(){
  val owner=MediaPreviewImage<Resource>{it.releases++};val first=Resource();val next=Resource()
  owner.publish(first);owner.publish(next);assertEquals(1,first.releases);assertEquals(0,next.releases)
  owner.close();assertEquals(1,first.releases);assertEquals(1,next.releases)
 }
 @Test fun publishingTheSameOwnedResourceDoesNotReleaseItEarly(){
  val owner=MediaPreviewImage<Resource>{it.releases++};val image=Resource()
  owner.publish(image);owner.publish(image);assertEquals(0,image.releases)
  owner.close();assertEquals(1,image.releases)
 }
}
