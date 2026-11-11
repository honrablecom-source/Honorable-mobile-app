package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

/** Android fixtures get framework/shadow state, but never app onCreate, media loading or UI display. */
internal object JvmTestAllocator {
    private val type = Class.forName("sun.misc.Unsafe")
    private val instance = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    private val allocate = type.getMethod("allocateInstance", Class::class.java)

    fun <T> allocateInstance(clazz: Class<T>): T {
        val value: Any = when {
            Activity::class.java.isAssignableFrom(clazz) -> Robolectric.buildActivity(clazz.asSubclass(Activity::class.java)).get()
            clazz == AlertDialog::class.java -> AlertDialog.Builder(RuntimeEnvironment.getApplication()).create()
            clazz == View::class.java -> View(RuntimeEnvironment.getApplication())
            else -> allocate.invoke(instance, clazz)
        }
        return clazz.cast(value)
    }
}
