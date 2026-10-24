package app.honorable.editor

import org.junit.Assert.fail
import org.junit.Test

class PhotoDestinationSafetyTest {
    @Test fun aliasesCannotTruncateOriginalEvenWithDifferentUris() {
        try { PhotoDestinationSafety.requireSeparate("content://a", "content://b", 1, 10, 1, 10); fail("Alias accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun sameUriAndUnknownIdentitiesFailClosed() {
        try {PhotoDestinationSafety.requireSeparate("content://a","content://a",1,10,1,20);fail("Same URI accepted")}
        catch (_: IllegalArgumentException) {}
        try {PhotoDestinationSafety.requireSeparate("content://a","content://b",1,0,1,20);fail("Unknown identity accepted")}
        catch (_: IllegalArgumentException) {}
    }
    @Test fun separateFilesOnSameOrDifferentDevicesAreAllowed() {
        PhotoDestinationSafety.requireSeparate("content://a","content://b",1,10,1,20)
        PhotoDestinationSafety.requireSeparate("content://a","content://b",1,10,2,10)
    }
}
