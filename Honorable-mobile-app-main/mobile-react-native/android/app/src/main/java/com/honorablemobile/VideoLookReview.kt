package com.honorablemobile

/** Saved-look API retained over the shared synchronous action lease. */
internal class VideoLookReview : VideoActionReview() {
    companion object {
        fun requireAvailable(catalog: VideoColorLookCatalog, look: NamedVideoColorLook) {
            require(catalog.looks.any { it == look }) { "Saved look changed. Open saved looks again." }
        }
    }
}
