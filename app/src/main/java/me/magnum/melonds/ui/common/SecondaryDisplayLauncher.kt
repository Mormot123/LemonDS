package me.magnum.melonds.ui.common

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager

/**
 * Some dual screen handhelds (e.g. the Anbernic RG DS) have a top screen without touch input, and Android opens apps on that screen by default.
 * This moves the root activity onto the other (touch) display so the game list and menus can actually be used.
 */
object SecondaryDisplayLauncher {

    private const val PREF_KEY = "ui_on_secondary_display"
    private const val EXTRA_RELOCATED = "me.magnum.melonds.EXTRA_RELOCATED_TO_SECONDARY_DISPLAY"
    private val ExcludedDisplays = listOf("HiddenDisplay", "WebRTC_ScreenCapture")

    /**
     * Relaunches the given activity on the secondary display if needed.
     *
     * @return `true` if the activity was relaunched elsewhere and the caller should finish and stop setting itself up
     */
    fun relocateIfNeeded(activity: Activity): Boolean {
        try {
            if (activity.intent.getBooleanExtra(EXTRA_RELOCATED, false)) {
                return false
            }

            val enabled = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(PREF_KEY, true)
            if (!enabled) {
                return false
            }

            val currentDisplay = ContextCompat.getDisplayOrDefault(activity)
            // Only move away from the default display. If the app is already on another display, leave it there
            if (currentDisplay.displayId != Display.DEFAULT_DISPLAY) {
                return false
            }

            val target = activity.getSystemService<DisplayManager>()
                ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                ?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.name !in ExcludedDisplays && it.state == Display.STATE_ON }
                ?: return false

            val intent = Intent(activity.intent).apply {
                putExtra(EXTRA_RELOCATED, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId)
            activity.startActivity(intent, options.toBundle())
            activity.finish()
            return true
        } catch (e: Exception) {
            // If anything goes wrong, just stay on the current display
            e.printStackTrace()
            return false
        }
    }
}
