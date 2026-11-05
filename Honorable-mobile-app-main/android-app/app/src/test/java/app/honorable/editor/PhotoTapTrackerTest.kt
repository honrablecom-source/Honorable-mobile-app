package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoTapTrackerTest {
 @Test fun oneDownAndNearbyUpIsAcceptedOnlyOnce(){val t=PhotoTapTracker(8f);t.down(10f,20f,0);assertTrue(t.up(12f,23f,0));assertFalse(t.up(12f,23f,0))}
 @Test fun toleranceUsesRadialDistanceIncludingBoundary(){
  val t=PhotoTapTracker(5f);t.down(0f,0f,2);assertTrue(t.up(3f,4f,2));t.down(0f,0f,2);assertFalse(t.up(3.1f,4f,2))
 }
 @Test fun dragCannotBecomeTapByReturningToItsStartingPoint(){val t=PhotoTapTracker(8f);t.down(20f,20f,0);t.move(50f,20f,0);t.move(20f,20f,0);assertFalse(t.up(20f,20f,0))}
 @Test fun changedPointerCancelsInsteadOfSelectingWithAnotherFinger(){val t=PhotoTapTracker(8f);t.down(20f,20f,1);t.move(20f,20f,2);assertFalse(t.up(20f,20f,1));t.down(0f,0f,1);assertFalse(t.up(0f,0f,2))}
 @Test fun explicitCancellationAllowsOnlyAFreshGesture(){val t=PhotoTapTracker(8f);t.down(0f,0f,0);t.cancel();assertFalse(t.up(0f,0f,0));t.down(1f,1f,0);assertTrue(t.up(1f,1f,0))}
 @Test fun missingDownInvalidPointerAndNonfiniteCoordinatesFailClosed(){
  val t=PhotoTapTracker(8f);assertFalse(t.up(0f,0f,0));t.down(0f,0f,-1);assertFalse(t.up(0f,0f,-1))
  t.down(Float.NaN,0f,0);assertFalse(t.up(0f,0f,0));t.down(0f,0f,0);t.move(Float.POSITIVE_INFINITY,0f,0);assertFalse(t.up(0f,0f,0))
  t.down(0f,0f,0);assertFalse(t.up(0f,Float.NEGATIVE_INFINITY,0))
 }
 @Test fun zeroToleranceAcceptsOnlyTheSamePoint(){val t=PhotoTapTracker(0f);t.down(2f,3f,0);assertTrue(t.up(2f,3f,0));t.down(2f,3f,0);assertFalse(t.up(2.001f,3f,0))}
 @Test fun invalidToleranceIsRejectedAndLargeFiniteMovementDoesNotOverflow(){
  for(slop in listOf(-1f,Float.NaN,Float.POSITIVE_INFINITY))try{PhotoTapTracker(slop);fail("Invalid slop accepted")}catch(expected:IllegalArgumentException){}
  val t=PhotoTapTracker(Float.MAX_VALUE);t.down(-Float.MAX_VALUE,0f,0);assertFalse(t.up(Float.MAX_VALUE,0f,0));t.down(0f,0f,0);assertTrue(t.up(Float.MAX_VALUE,0f,0))
 }
}
