package com.example.landmarkverify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.view.TextureView
import android.graphics.SurfaceTexture
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.Frame
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * CHECKPOINT 2: Core ARCore Geospatial Functionality
 * Focus: GPS location validation and position tracking ONLY
 * No fancy UI, no camera rendering - just core geospatial validation
 */
class VerifyArActivity : AppCompatActivity() {
    
    private companion object {
        const val TAG = "VerifyArActivity"
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    // Core components - ONLY what we need for geospatial
    private var arSession: Session? = null
    private var isGeospatialSupported = false
    private var userRequestedInstall = false
    
    // UI elements
    private lateinit var statusText: TextView
    private lateinit var sessionStateText: TextView
    private lateinit var locationText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var cameraTextureView: TextureView
    
    // Permission handling
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            initializeArCore()
        } else {
            Log.w(TAG, "Required permissions not granted")
            statusText.text = "Camera and Location permissions required"
            Toast.makeText(this, "Permissions required for location validation", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "VerifyArActivity onCreate started")
        
        try {
            setContentView(R.layout.activity_verify_ar)
            
            // Initialize UI with null checks
            statusText = findViewById(R.id.status_text) ?: run {
                Log.e(TAG, "❌ Failed to find status_text in layout")
                finish()
                return
            }
            sessionStateText = findViewById(R.id.session_state_text) ?: run {
                Log.e(TAG, "❌ Failed to find session_state_text in layout")
                finish()
                return
            }
            locationText = findViewById(R.id.location_text) ?: run {
                Log.e(TAG, "❌ Failed to find location_text in layout")
                finish()
                return
            }
            accuracyText = findViewById(R.id.accuracy_text) ?: run {
                Log.e(TAG, "❌ Failed to find accuracy_text in layout")
                finish()
                return
            }
            cameraTextureView = findViewById(R.id.camera_texture_view) ?: run {
                Log.e(TAG, "❌ Failed to find camera_texture_view in layout")
                finish()
                return
            }
            
            statusText.text = "🔄 Initializing ARCore Geospatial..."
            Log.d(TAG, "✅ UI elements initialized successfully")
            
            // Set up camera texture listener
            cameraTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                    Log.d(TAG, "📹 Camera surface texture available ($width x $height)")
                    // Surface is ready, now we can initialize ARCore if not already done
                }
                
                override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                    Log.d(TAG, "📹 Camera surface texture size changed ($width x $height)")
                    setupCameraTexture()
                }
                
                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                    Log.d(TAG, "📹 Camera surface texture destroyed")
                    return true
                }
                
                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {
                    // Called for each camera frame - too verbose to log
                }
            }
            
            checkPermissionsAndInitialize()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize VerifyArActivity", e)
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        arSession?.resume()
        if (arSession != null) {
            setupCameraTexture() // Ensure camera texture is set up
            startLocationTracking()
        }
    }
    
    override fun onPause() {
        super.onPause()
        arSession?.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
    }
    
    private fun checkPermissionsAndInitialize() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            initializeArCore()
        } else {
            Log.d(TAG, "Requesting permissions: ${missingPermissions.joinToString()}")
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }
    
    private fun initializeArCore() {
        lifecycleScope.launch {
            try {
                statusText.text = "Checking ARCore availability..."
                
                // Check ARCore availability
                when (ArCoreApk.getInstance().checkAvailability(this@VerifyArActivity)) {
                    ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                        Log.d(TAG, "ARCore is installed and supported")
                        createArSession()
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
                Log.e(TAG, "Error initializing ARCore", e)
                statusText.text = "Error initializing ARCore: ${e.message}"
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
                    Log.d(TAG, "ARCore installed, creating session")
                    createArSession()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting ARCore installation", e)
            statusText.text = "Error installing ARCore: ${e.message}"
        }
    }
    
    private fun createArSession() {
        try {
            Log.d(TAG, "Creating ARCore session")
            runOnUiThread {
                statusText.text = "Creating ARCore session..."
                sessionStateText.text = "Initializing..."
            }
            
            arSession = Session(this).apply {
                val config = Config(this).apply {
                    // Enable Geospatial mode - this is the CORE functionality we need
                    Log.d(TAG, "Checking geospatial mode support...")
                    if (isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
                        geospatialMode = Config.GeospatialMode.ENABLED
                        isGeospatialSupported = true
                        Log.i(TAG, "✅ Geospatial mode ENABLED successfully")
                        runOnUiThread {
                            sessionStateText.text = "✅ Geospatial Mode Enabled"
                        }
                    } else {
                        Log.e(TAG, "❌ Geospatial mode NOT SUPPORTED on this device")
                        runOnUiThread {
                            statusText.text = "❌ Geospatial mode not supported on this device"
                            sessionStateText.text = "❌ Geospatial Not Supported"
                        }
                        return
                    }
                    
                    // Configure for best geospatial performance
                    planeFindingMode = Config.PlaneFindingMode.DISABLED // We don't need plane detection
                    lightEstimationMode = Config.LightEstimationMode.DISABLED // We don't need lighting
                    focusMode = Config.FocusMode.AUTO
                }
                
                configure(config)
                resume()
                Log.d(TAG, "ARCore session configured and resumed")
                runOnUiThread {
                    sessionStateText.text = "✅ Session Running"
                }
            }
            
            runOnUiThread {
                statusText.text = "✅ ARCore Geospatial ready - Starting location tracking..."
            }
            
            // Set up camera texture for ARCore
            setupCameraTexture()
            
            // Start location tracking with a small delay to ensure session is fully ready
            lifecycleScope.launch {
                delay(1000) // Give ARCore time to initialize
                startLocationTracking()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ARCore session", e)
            runOnUiThread {
                statusText.text = "❌ Failed to create ARCore session: ${e.message}"
                sessionStateText.text = "❌ Session Failed"
            }
        }
    }
    
    private fun setupCameraTexture() {
        try {
            val session = arSession ?: return
            val surfaceTexture = cameraTextureView.surfaceTexture ?: return
            
            // Set camera texture for ARCore - this is crucial for camera access
            session.setCameraTexture(surfaceTexture)
            
            // Set up display geometry so ARCore knows the camera orientation  
            val displayRotation = windowManager.defaultDisplay.rotation
            session.setDisplayGeometry(displayRotation, cameraTextureView.width, cameraTextureView.height)
            
            Log.d(TAG, "✅ Camera texture set up for ARCore (${cameraTextureView.width}x${cameraTextureView.height})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up camera texture", e)
        }
    }
    
    private fun startLocationTracking() {
        if (arSession == null || !isGeospatialSupported) {
            Log.w(TAG, "Cannot start location tracking - session not ready or geospatial not supported")
            runOnUiThread {
                statusText.text = "❌ Geospatial not supported or session not ready"
            }
            return
        }
        
        Log.d(TAG, "Starting location tracking...")
        runOnUiThread {
            statusText.text = "🌍 Tracking your location..."
        }
        
        // Start periodic location updates - safer approach
        updateLocationPeriodically()
    }
    
    private fun updateLocationPeriodically() {
        lifecycleScope.launch {
            try {
                // Update location every 2 seconds, with safety checks
                while (arSession != null && isGeospatialSupported && !isFinishing) {
                    updateLocationData()
                    delay(2000) // Update every 2 seconds instead of 1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in location tracking", e)
                runOnUiThread {
                    statusText.text = "❌ Location tracking error: ${e.message}"
                }
            }
        }
    }
    
    private fun updateLocationData() {
        val session = arSession ?: run {
            Log.w(TAG, "❌ ARCore session is null")
            runOnUiThread { statusText.text = "❌ ARCore session not available" }
            return
        }
        
        try {
            // Update ARCore frame to get latest geospatial data
            val frame = session.update()
            val earth = session.earth
            
            if (earth == null) {
                Log.w(TAG, "❌ Earth is null - geospatial not available yet")
                runOnUiThread {
                    statusText.text = "🔄 Waiting for geospatial service..."
                }
                return
            }
            
            Log.v(TAG, "✅ Earth object available, checking state...")
            
            // Get current Earth state
            val earthState = earth.earthState
            Log.v(TAG, "Earth state: $earthState")
            
            when (earthState) {
                com.google.ar.core.Earth.EarthState.ENABLED -> {
                    // SUCCESS! Get geospatial pose
                    val geospatialPose = earth.cameraGeospatialPose
                    
                    val latitude = geospatialPose.latitude
                    val longitude = geospatialPose.longitude
                    val altitude = geospatialPose.altitude
                    val horizontalAccuracy = geospatialPose.horizontalAccuracy
                    val heading = geospatialPose.heading
                    
                    val isAccurate = horizontalAccuracy <= 10.0f // Within 10 meters
                    
                    runOnUiThread {
                        locationText.text = "📍 Lat: ${"%.6f".format(latitude)}\n" +
                                          "🌐 Lng: ${"%.6f".format(longitude)}\n" +
                                          "⛰️ Alt: ${"%.1f".format(altitude)}m\n" +
                                          "🧭 Heading: ${"%.1f".format(heading)}°"
                        
                        accuracyText.text = when {
                            horizontalAccuracy <= 5.0f -> "🎯 High accuracy (${horizontalAccuracy}m)"
                            horizontalAccuracy <= 10.0f -> "✅ Good accuracy (${horizontalAccuracy}m)"
                            else -> "⚠️ Low accuracy (${horizontalAccuracy}m)"
                        }
                        
                        statusText.text = if (isAccurate) {
                            "✅ LOCATION VALIDATED - Ready for verification!"
                        } else {
                            "🔄 Improving location accuracy... (${horizontalAccuracy}m)"
                        }
                    }
                    
                    Log.d(TAG, "📍 Location: $latitude, $longitude (±${horizontalAccuracy}m)")
                }
                
                com.google.ar.core.Earth.EarthState.ERROR_INTERNAL -> {
                    Log.e(TAG, "Earth state error: Internal error")
                    runOnUiThread { statusText.text = "❌ Internal geospatial error" }
                }
                
                com.google.ar.core.Earth.EarthState.ERROR_NOT_AUTHORIZED -> {
                    Log.e(TAG, "Earth state error: Not authorized")
                    runOnUiThread { statusText.text = "❌ Geospatial service not authorized" }
                }
                
                com.google.ar.core.Earth.EarthState.ERROR_RESOURCE_EXHAUSTED -> {
                    Log.e(TAG, "Earth state error: Resource exhausted")
                    runOnUiThread { statusText.text = "❌ Geospatial service quota exceeded" }
                }
                
                else -> {
                    Log.d(TAG, "Earth state: $earthState (initializing...)")
                    runOnUiThread { statusText.text = "🔄 Initializing geospatial service..." }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location data", e)
            runOnUiThread { statusText.text = "Error getting location: ${e.message}" }
        }
    }
}
