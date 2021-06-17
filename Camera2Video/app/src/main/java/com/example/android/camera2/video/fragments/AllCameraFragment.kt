package com.example.android.camera2.video.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainer
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavArgument
import androidx.navigation.NavType
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.android.camera2.video.*
import com.example.mmsbridge.TranslateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AllCameraFragment : Fragment() {
    private lateinit var translateButton: Button
    private lateinit var showResult: Button
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = layoutInflater.inflate(R.layout.fragment_all_camera, container, false)

        val navHosts = HashMap<String, Int>().apply {
            this["0"] = R.id.fragment_container0
            this["1"] = R.id.fragment_container1
            this["2"] = R.id.fragment_container2
        }
        val enableCameras = Settings.ENABLE_CAMERAS
        for ((k, v) in navHosts) {
            if (k !in enableCameras) {
                continue
            }
            val navHostFragment = childFragmentManager.findFragmentById(v) as NavHostFragment
            val navController = navHostFragment.navController
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)

            graph.addArgument("camera_id", NavArgument.Builder()
                                            .setType(NavType.StringType)
                                            .setDefaultValue(""+k).build())
            graph.addArgument("fragment_id", NavArgument.Builder()
                .setType(NavType.IntType)
                .setDefaultValue(v).build())
            graph.startDestination = R.id.permissions_fragment
            navController.graph = graph
        }

        translateButton = view.findViewById(R.id.doTranslate)
        showResult = view.findViewById(R.id.viewResult)

        AIHelper.imageManager.callback = object : ImageManager.TranslateCallback {
            override fun onResult(result: TranslateResult) {
                enableTranslate = false
                translateResult = result

                GlobalScope.launch(Dispatchers.Main) {
                    translateButton.text = "Retranslate"
                    showResult.visibility = View.VISIBLE
                }
            }
        }
        showResult.setOnClickListener {
            requireContext().startActivity(Intent(requireContext(), ResultActivity::class.java))
        }

        translateButton.text = "Translate"
        showResult.visibility = View.GONE
        translateButton.setOnClickListener {
            translateButton.text = "Translate"
            showResult.visibility = View.GONE
            Settings.readyCameras.clear()
            enableTranslate = true
        }

        val settingsBtn = view.findViewById<ImageButton>(R.id.skip_settings)
        settingsBtn.setOnClickListener {
            Settings.SKIP_SETTINGS = !Settings.SKIP_SETTINGS
            syncSettingsBtnIcon(it as ImageButton)
        }
        syncSettingsBtnIcon(settingsBtn)
        return view
    }

    private fun syncSettingsBtnIcon(btn: ImageButton) {
        btn.setImageDrawable(if (Settings.SKIP_SETTINGS) skiSettingsIcon else showSettingIcon)
    }
    override fun onResume() {
        super.onResume()
        translateButton.text = "Translate"
        showResult.visibility = View.GONE
    }
}