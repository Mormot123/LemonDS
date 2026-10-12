package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import androidx.preference.SeekBarPreference
import me.magnum.melonds.R
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider

class AudioPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    override fun getTitle() = getString(R.string.category_audio)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_audio, rootKey)
        val volumePreference = findPreference<SeekBarPreference>("volume")!!

        updateVolumePreferenceSummary(volumePreference, volumePreference.value)

        volumePreference.setOnPreferenceChangeListener { _, newValue ->
            updateVolumePreferenceSummary(volumePreference, newValue as Int)
            true
        }
    }

    private fun updateVolumePreferenceSummary(volumePreference: SeekBarPreference, volume: Int) {
        val volumePercentage = (volume / volumePreference.max.toFloat() * 100f).toInt()
        volumePreference.summary = getString(R.string.volume_percentage, volumePercentage)
    }
}
