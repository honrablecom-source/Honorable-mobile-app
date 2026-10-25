package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.*

object PhotoCreativeControls {
    fun show(activity: Activity, tool: PhotoCreativeTool, initial: PhotoCreative, commit: (PhotoCreative) -> Unit) {
        var value = initial
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        fun slider(name: String, progress: Int, maximum: Int, caption: (Int) -> String, update: (Int) -> Unit) {
            val label = TextView(activity).apply { setTextColor(Color.BLACK); text = "$name: ${caption(progress)}" }
            panel.addView(label)
            panel.addView(SeekBar(activity).apply {
                max = maximum; this.progress = progress; contentDescription = name
                progressTintList = ColorStateList.valueOf(Color.BLACK); thumbTintList = ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) {}
                    override fun onProgressChanged(bar: SeekBar, next: Int, user: Boolean) {
                        label.text = "$name: ${caption(next)}"
                        if (user) update(next)
                    }
                })
            }, LinearLayout.LayoutParams(-1, (48 * activity.resources.displayMetrics.density).toInt()))
        }
        fun signed(name: String, current: Float, update: (Float) -> Unit) =
            slider(name, (current * 100).toInt() + 100, 200, { "${it - 100}%" }, { update((it - 100) / 100f) })
        fun amount(name: String, current: Float, update: (Float) -> Unit) =
            slider(name, (current * 100).toInt(), 100, { "$it%" }, { update(it / 100f) })
        when (tool) {
            PhotoCreativeTool.VIBRANCE -> signed("Vibrance", value.vibrance) { value = value.copy(vibrance = it) }
            PhotoCreativeTool.HUE -> slider("Hue", (value.hue * 180).toInt() + 180, 360, { "${it - 180}°" }) { value = value.copy(hue = (it - 180) / 180f) }
            PhotoCreativeTool.FADE -> amount("Black lift", value.fade) { value = value.copy(fade = it) }
            PhotoCreativeTool.GRAIN -> amount("Grain", value.grain) { value = value.copy(grain = it) }
            PhotoCreativeTool.POSTERIZE -> {
                val enabled = CheckBox(activity).apply { text = "Enable posterize"; isChecked = value.posterize != 0; setTextColor(Color.BLACK) }
                panel.addView(enabled)
                var levels = value.posterize.takeIf { it != 0 } ?: 6
                enabled.setOnCheckedChangeListener { _, checked -> value = value.copy(posterize = if (checked) levels else 0) }
                slider("Levels per channel", levels - 2, 14, { "${it + 2}" }) {
                    levels = it + 2
                    if (enabled.isChecked) value = value.copy(posterize = levels)
                }
            }
            PhotoCreativeTool.THRESHOLD -> {
                val enabled = CheckBox(activity).apply { text = "Enable black and white threshold"; isChecked = value.threshold >= 0; setTextColor(Color.BLACK) }
                panel.addView(enabled)
                var level = value.threshold.takeIf { it >= 0 } ?: .5f
                enabled.setOnCheckedChangeListener { _, checked -> value = value.copy(threshold = if (checked) level else -1f) }
                amount("Threshold", level) {
                    level = it
                    if (enabled.isChecked) value = value.copy(threshold = level)
                }
            }
            PhotoCreativeTool.SPLIT_TONE -> {
                panel.addView(TextView(activity).apply { text = "Cool shadows and warm highlights"; setTextColor(Color.DKGRAY) })
                amount("Strength", value.splitTone) { value = value.copy(splitTone = it) }
            }
            PhotoCreativeTool.CHANNELS -> {
                signed("Red", value.red) { value = value.copy(red = it) }
                signed("Green", value.green) { value = value.copy(green = it) }
                signed("Blue", value.blue) { value = value.copy(blue = it) }
            }
        }
        AlertDialog.Builder(activity).setTitle(tool.title)
            .setView(ScrollView(activity).apply { addView(panel); overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS })
            .setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ -> commit(value) }.show()
    }
}
