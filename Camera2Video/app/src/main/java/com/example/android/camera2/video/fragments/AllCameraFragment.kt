package com.example.android.camera2.video.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.android.camera2.video.CameraActivity
import com.example.android.camera2.video.R
import com.example.android.camera2.video.Settings

class AllCameraFragment : Fragment() {
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
        return view
    }

}