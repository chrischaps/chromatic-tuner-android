package com.chrischappelear.tuner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrischappelear.tuner.ui.TunerScreen
import com.chrischappelear.tuner.ui.theme.ChromaticTunerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TunerViewModel by viewModels()

    /** True once the user has denied the permission in a way we can no longer re-ask. */
    private var permanentlyDenied by mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermission()
        permanentlyDenied = !granted &&
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ChromaticTunerTheme {
                // Collection stops when the activity is stopped, which releases the microphone.
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
                val reference by viewModel.reference.collectAsStateWithLifecycle()

                TunerScreen(
                    uiState = uiState,
                    settings = settings,
                    reference = reference,
                    hasPermission = hasPermission,
                    permissionPermanentlyDenied = permanentlyDenied,
                    onRequestPermission = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    onOpenAppSettings = ::openAppSettings,
                    onTuningSelected = viewModel::setTuning,
                    onA4Changed = viewModel::setA4,
                    onSaveCustomTuning = viewModel::saveCustomTuning,
                    onDeleteCustomTuning = viewModel::deleteCustomTuning,
                    onPlayReference = viewModel::playReference
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The permission may have been granted or revoked from system settings.
        viewModel.refreshPermission()
    }

    override fun onStop() {
        super.onStop()
        // A tone shouldn't follow the user out of the app, but it can ring through a rotation.
        if (!isChangingConfigurations) viewModel.stopReference()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }
}
