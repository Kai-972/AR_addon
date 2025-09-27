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
import com.example.landmarkverify.ar.AugmentedImageLoader
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Config
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
    private lateinit var imageLoader: AugmentedImageLoader
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
        imageLoader = AugmentedImageLoader()
        
        // Observe session state changes
        lifecycleScope.launch {
            arSessionManager.sessionState.collect { state ->
                sessionStateText.text = state.name.replace("_", " ").lowercase().capitalize()
                Log.d(TAG, "Session state changed to: $state")
            }
        }
        
        // Observe frame updates for image detection
        lifecycleScope.launch {
            arSessionManager.frameUpdates.collect { frame ->
                frame?.let { processFrameForImageDetection(it) }
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
                        setupAugmentedImages()
                        statusText.text = "AR Session initialized with image database"
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
                        setupAugmentedImages()
                        statusText.text = "AR Session initialized with image database"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting ARCore installation", e)
            statusText.text = "Error installing ARCore: ${e.message}"
        }
    }
    
    private suspend fun setupAugmentedImages() {
        try {
            val session = arSessionManager.getSession()
            if (session == null) {
                Log.w(TAG, "Cannot setup augmented images - session is null")
                return
            }
            
            Log.d(TAG, "Setting up augmented image database")
            statusText.text = "Loading image database..."
            
            // Check if database exists
            if (!imageLoader.isDatabaseAvailable(this)) {
                Log.w(TAG, "Augmented image database not found, using empty database")
                statusText.text = "No image database found - see assets/README_augmented_image_db.txt"
                val emptyDatabase = imageLoader.createEmptyDatabase(session)
                attachDatabaseToSession(session, emptyDatabase)
                return
            }
            
            // Load database from assets
            val database = imageLoader.loadDatabase(this, session)
            if (database != null) {
                attachDatabaseToSession(session, database)
                Log.d(TAG, "Augmented image database loaded successfully with ${database.numImages} images")
                statusText.text = "Image database loaded: ${database.numImages} images"
            } else {
                Log.e(TAG, "Failed to load augmented image database")
                statusText.text = "Failed to load image database"
                // Use empty database as fallback
                val emptyDatabase = imageLoader.createEmptyDatabase(session)
                attachDatabaseToSession(session, emptyDatabase)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up augmented images", e)
            statusText.text = "Error loading image database: ${e.message}"
        }
    }
    
    private fun attachDatabaseToSession(session: com.google.ar.core.Session, database: com.google.ar.core.AugmentedImageDatabase) {
        try {
            val config = Config(session).apply {
                augmentedImageDatabase = database
                // Keep existing geospatial configuration
                if (session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
                    geospatialMode = Config.GeospatialMode.ENABLED
                }
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                focusMode = Config.FocusMode.AUTO
            }
            
            session.configure(config)
            Log.d(TAG, "Session reconfigured with augmented image database")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach database to session", e)
        }
    }
    
    private fun processFrameForImageDetection(frame: com.google.ar.core.Frame) {
        try {
            val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
            
            for (augmentedImage in updatedAugmentedImages) {
                when (augmentedImage.trackingState) {
                    com.google.ar.core.TrackingState.PAUSED -> {
                        // Image was detected but tracking is paused
                        Log.d(TAG, "Image detection paused: ${augmentedImage.name}")
                    }
                    com.google.ar.core.TrackingState.TRACKING -> {
                        // Image is being tracked successfully
                        Log.i(TAG, "Image detected and tracking: ${augmentedImage.name}")
                        Log.d(TAG, "Image pose: ${augmentedImage.centerPose}")
                        Log.d(TAG, "Image extent: ${augmentedImage.extentX} x ${augmentedImage.extentZ}")
                        
                        // Update UI with detection info
                        runOnUiThread {
                            statusText.text = "✓ Detected: ${augmentedImage.name} (tracking)"
                        }
                    }
                    com.google.ar.core.TrackingState.STOPPED -> {
                        // Image tracking has stopped
                        Log.d(TAG, "Image tracking stopped: ${augmentedImage.name}")
                        runOnUiThread {
                            statusText.text = "Image tracking lost: ${augmentedImage.name}"
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for image detection", e)
        }
    }
}
