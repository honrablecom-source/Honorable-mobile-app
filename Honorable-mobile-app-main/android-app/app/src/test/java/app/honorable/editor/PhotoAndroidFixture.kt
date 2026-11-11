package app.honorable.editor

import org.robolectric.Robolectric

/** Attach Android Activity/shadow state without onCreate, UI inflation, source reads or workers. */
internal fun uncreatedPhotoActivity(): PhotoEditorActivity =
    Robolectric.buildActivity(PhotoEditorActivity::class.java).get()
