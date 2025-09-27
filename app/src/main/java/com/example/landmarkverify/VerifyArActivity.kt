package com.example.landmarkverify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.landmarkverify.ar.ArSessionManager
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.launch

class VerifyArActivity : AppCompatActivity() {
    
    private companion object {
        const val TAG = "VerifyArActivity"
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    private lateinit var statusText: TextView
    private lateinit var sessionStateText: TextView
    private lateinit var arSessionManager: ArSessionManager
    private var userRequestedInstall = false
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            initializeAr()
        } else {
            Log.w(TAG, "Required permissions not granted")
            statusText.text = "Camera and Location permissions required"
            Toast.makeText(this, "Permissions required for AR verification", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_ar)
        
        statusText = findViewById(R.id.status_text)
        sessionStateText = findViewById(R.id.session_state_text)
        arSessionManager = ArSessionManager()
        
        // Observe session state changes
        lifecycleScope.launch {
            arSessionManager.sessionState.collect { state ->
                sessionStateText.text = state.name.replace("_", " ").lowercase().capitalize()
                Log.d(TAG, "Session state changed to: $state")
            }
        }
        
        statusText.text = "Initializing AR verification..."
        checkPermissionsAndInitialize()
    }
    
    override fun onResume() {
        super.onResume()
        if (::arSessionManager.isInitialized && arSessionManager.isSessionInitialized()) {
            lifecycleScope.launch {
                arSessionManager.resumeSession()
                statusText.text = "AR Session resumed"
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (::arSessionManager.isInitialized && arSessionManager.isSessionInitialized()) {
            arSessionManager.pauseSession()
            statusText.text = "AR Session paused"
        }
    }
    
    private fun checkPermissionsAndInitialize() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            initializeAr()
        } else {
            Log.d(TAG, "Requesting permissions: ${missingPermissions.joinToString()}")
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }
    
    private fun initializeAr() {
        lifecycleScope.launch {
            try {
                statusText.text = "Checking ARCore availability..."
                
                // Check ARCore availability
                when (ArCoreApk.getInstance().checkAvailability(this@VerifyArActivity)) {
                    ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                        Log.d(TAG, "ARCore is installed and supported")
                        statusText.text = "ARCore available, initializing session..."
                        arSessionManager.initializeSession(this@VerifyArActivity)
                        statusText.text = "AR Session initialized successfully"
                    }
                    ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                    ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                        Log.d(TAG, "ARCore needs installation or update")
                        statusText.text = "Installing ARCore..."
                        requestArCoreInstallation()
                    }
                    else -> {
                        Log.e(TAG, "ARCore not supported on this device")
                        statusText.text = "ARCore not supported on this device"
                        Toast.makeText(this@VerifyArActivity, "ARCore not supported", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AR", e)
                statusText.text = "Error initializing AR: ${e.message}"
            }
        }
    }
    
    private fun requestArCoreInstallation() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    Log.d(TAG, "ARCore installation requested")
                    statusText.text = "ARCore installation requested"
                    userRequestedInstall = true
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    Log.d(TAG, "ARCore installed, initializing session")
                    lifecycleScope.launch {
                        arSessionManager.initializeSession(this@VerifyArActivity)
                        statusText.text = "AR Session initialized successfully"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting ARCore installation", e)
            statusText.text = "Error installing ARCore: ${e.message}"
        }
    }
}
