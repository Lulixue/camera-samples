package com.example.android.camera2.video.fragments

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.preference.*
import com.example.android.camera2.video.R
import com.example.android.camera2.video.Settings
import java.lang.StringBuilder

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsPreferences())
            .commit()

        val continueTo = view.findViewById<View>(R.id.continueTo)
        continueTo.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                SettingsFragmentDirections.actionSettingsToPermission())
        }
        if (Settings.SKIP_SETTINGS) {
            continueTo.performClick()
        }


    }
}

class SettingsPreferences : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting_references, rootKey)

        val cameraManager =
            requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val skipSettings = findPreference<SwitchPreference>(Settings.KEY_SKIP_SETTINGS)!!
        skipSettings.isChecked = Settings.SKIP_SETTINGS

        val enableCameras = findPreference<MultiSelectListPreference>(Settings.KEY_ENABLE_CAMERAS)!!
        enableCameras.summaryProvider = cameraSummaryProvider

        val legacyCameras = findPreference<MultiSelectListPreference>(Settings.KEY_LEGACY_CAMERAS)!!
        legacyCameras.summaryProvider = cameraSummaryProvider

        val cameraResolution = findPreference<ListPreference>(Settings.KEY_RESOLUTION)!!
        val list = SelectorFragment.enumerateVideoCameras(cameraManager)
        val values = mutableListOf<String>()

        list.forEach {
            val res = "${it.size.width}x${it.size.height}"
            if (!values.contains(res)) {
                values.add(res)
            }
        }
        cameraResolution.entries = values.toTypedArray()
        cameraResolution.entryValues = values.toTypedArray()
        cameraResolution.value = Settings.RESOLUTION

    }

    private val cameraSummaryProvider = Preference.SummaryProvider<MultiSelectListPreference> {
        val sb = StringBuilder()
        for (k in it.values) {
            sb.append(k)
            sb.append(",")
        }
        if (sb.isNotEmpty()) {
            sb.deleteCharAt(sb.length-1)
        }
        sb.toString()
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        val value = sharedPreferences.all[key]!!
        Log.d("Settings", "key: $key, value: $value")
        when (key) {
            Settings.KEY_RESOLUTION -> Settings.mmkv.putString(key, value.toString())
            Settings.KEY_CAMERA_FPS -> Settings.mmkv.putInt(key, value.toString().toInt())
            Settings.KEY_ENABLE_AI_ANALYZE, Settings.KEY_SKIP_SETTINGS,
                Settings.KEY_DEV_BOARD -> Settings.mmkv.putBoolean(key, value.toString().toBoolean())
            Settings.KEY_ENABLE_CAMERAS -> Settings.mmkv.putStringSet(key,  value as Set<String>)
            Settings.KEY_LEGACY_CAMERAS -> Settings.mmkv.putStringSet(key,  value as Set<String>)
        }
    }
}