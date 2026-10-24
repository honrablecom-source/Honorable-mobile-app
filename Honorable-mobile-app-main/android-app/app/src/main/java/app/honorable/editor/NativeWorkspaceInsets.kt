package app.honorable.editor

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Keep editor/viewer chrome clear of system bars and cutouts on edge-to-edge Android. */
object NativeWorkspaceInsets {
    fun install(activity: Activity, root: View) {
        val left = root.paddingLeft; val top = root.paddingTop
        val right = root.paddingRight; val bottom = root.paddingBottom
        if (Build.VERSION.SDK_INT >= 30) activity.window.setDecorFitsSystemWindows(false)
        else activity.window.decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            } else {
                view.setPadding(left + insets.systemWindowInsetLeft, top + insets.systemWindowInsetTop,
                    right + insets.systemWindowInsetRight, bottom + insets.systemWindowInsetBottom)
            }
            insets
        }
        root.post { root.requestApplyInsets() }
    }
}
