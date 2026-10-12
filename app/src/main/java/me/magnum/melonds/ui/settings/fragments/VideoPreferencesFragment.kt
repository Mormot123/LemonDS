package me.magnum.melonds.ui.settings.fragments

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import javax.inject.Inject

@AndroidEntryPoint
class VideoPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    private companion object {
        const val GLES_3_2 = 0x30002
    }

    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager, directoryAccessValidator) }
    @Inject lateinit var uriPermissionManager: UriPermissionManager
    @Inject lateinit var directoryAccessValidator: DirectoryAccessValidator
    @Inject lateinit var layoutsRepository: LayoutsRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val softwareRendererPreferences = mutableListOf<Preference>()
    private val openGlRendererPreferences = mutableListOf<Preference>()
    private val computeRendererPreferences = mutableListOf<Preference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_video, rootKey)

        softwareRendererPreferences.apply {
            add(findPreference("enable_threaded_rendering")!!)
        }

        openGlRendererPreferences.apply {
            add(findPreference("video_internal_resolution")!!)
        }

        computeRendererPreferences.apply {
            add(findPreference("video_internal_resolution")!!)
        }

        val rendererPreference = findPreference<ListPreference>("video_renderer")!!

        val activityManager = requireContext().getSystemService<ActivityManager>()

        rendererPreference.apply {
            val deviceGlesVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
            if (deviceGlesVersion >= GLES_3_2) {
                // Check if the device has a Qualcomm chipset. Only Adreno GPUs are supported for now
                val isAdrenoGpu = Build.HARDWARE.lowercase() == "qcom"
                if (!isAdrenoGpu) {
                    val computeRenderOptionIndex = rendererPreference.entryValues.indexOfFirst { it == VideoRenderer.COMPUTE.name.lowercase() }
                    rendererPreference.entries = rendererPreference.entries.filterIndexed { index, _ ->
                        index != computeRenderOptionIndex
                    }.toTypedArray()
                    rendererPreference.entryValues = rendererPreference.entryValues.filterIndexed { index, _ ->
                        index != computeRenderOptionIndex
                    }.toTypedArray()
                }

                setOnPreferenceChangeListener { _, newValue ->
                    onRendererPreferenceChanged(newValue as String)
                    true
                }
            } else {
                // GLES 3.2 is not supported. Remove the preference
                isVisible = false
            }
        }

        onRendererPreferenceChanged(rendererPreference.value)
    }

    private fun onRendererPreferenceChanged(rendererValue: String) {
        val newRenderer = enumValueOfIgnoreCase<VideoRenderer>(rendererValue)
        when (newRenderer) {
            VideoRenderer.SOFTWARE -> {
                computeRendererPreferences.forEach { it.isVisible = false }
                openGlRendererPreferences.forEach { it.isVisible = false }
                softwareRendererPreferences.forEach { it.isVisible = true }
            }
            VideoRenderer.OPENGL -> {
                softwareRendererPreferences.forEach { it.isVisible = false }
                computeRendererPreferences.forEach { it.isVisible = false }
                openGlRendererPreferences.forEach { it.isVisible = true }
            }
            VideoRenderer.COMPUTE -> {
                softwareRendererPreferences.forEach { it.isVisible = false }
                openGlRendererPreferences.forEach { it.isVisible = false }
                computeRendererPreferences.forEach { it.isVisible = true }
            }
        }
    }

    override fun getTitle(): String {
        return getString(R.string.category_video)
    }
}