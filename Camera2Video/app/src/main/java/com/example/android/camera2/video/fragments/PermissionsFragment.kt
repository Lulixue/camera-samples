/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.video.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera2.video.AIHelper
import com.example.android.camera2.video.R
import com.example.android.camera2.video.Settings

private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO)

/**
 * This [Fragment] requests permissions and, once granted, it will navigate to the next fragment
 */
class PermissionsFragment : Fragment() {
    private val args: PermissionsFragmentArgs by navArgs()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasPermissions(requireContext())) {
            AIHelper.instance.initAI()
            // If permissions have already been granted, proceed
            if (!Settings.TRIPLE_CAMERA_DEV) {
                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    PermissionsFragmentDirections.actionPermissionsToSelector())
            } else {
                    if (args.cameraId == null) {
                        Navigation.findNavController(requireActivity(), R.id.fragment_container)
                            .navigate(
                                PermissionsFragmentDirections.actionPermissionsToAllCamera()
                            )
                    } else {
                        if (args.cameraId in Settings.CAMERA_USE_LEGACY) {
                            Navigation.findNavController(requireActivity(), args.fragmentId)
                                .navigate(
                                    PermissionsFragmentDirections.actionPermissionsToCameraLegacy(
                                        args.cameraId!!,
                                        Settings.DEFAULT_CAMERA_WIDTH,
                                        Settings.DEFAULT_CAMERA_HEIGHT,
                                        Settings.DEFAULT_CAMERA_FPS
                                    )
                                )
                        } else {
                            Navigation.findNavController(requireActivity(), args.fragmentId)
                                .navigate(
                                    PermissionsFragmentDirections.actionPermissionsToCamera(
                                        args.cameraId!!,
                                        Settings.DEFAULT_CAMERA_WIDTH,
                                        Settings.DEFAULT_CAMERA_HEIGHT,
                                        Settings.DEFAULT_CAMERA_FPS
                                    )
                                )
                        }
                    }
                }
        } else {
            // Request camera-related permissions
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Takes the user to the success fragment when permission is granted
                if (!Settings.TRIPLE_CAMERA_DEV) {
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(
                            PermissionsFragmentDirections.actionPermissionsToSelector()
                        )
                } else {
                   Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                        PermissionsFragmentDirections.actionPermissionsToAllCamera())
                }
            } else {
                Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
