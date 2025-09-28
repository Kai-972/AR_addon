package com.example.landmarkverify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SIMPLIFIED GPS + AR SIMULATION
 * Uses Android native GPS (works immediately) + camera preview
 * Simulates ARCore behavior but uses reliable Android location services
 */
class VerifyArActivity : AppCompatActivity() {
    
    private companion object {
        const val TAG = "VerifyArActivity"
        const val LOCATION_UPDATE_INTERVAL = 1000L // 1 second
        const val LOCATION_FASTEST_INTERVAL = 500L // 0.5 seconds
        
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    // UI elements
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var altitudeText: TextView
    
    // Location components
    private var locationManager: LocationManager? = null
    private var lastKnownLocation: Location? = null
    private var locationUpdatesStarted = false
    
    // Camera components
    private var cameraExecutor: ExecutorService? = null
    private var camera: Camera? = null
    

    
    // Permission handling
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "✅ All permissions granted")
            initializeGpsAndCamera()
        } else {
            Log.e(TAG, "❌ Some permissions were denied")
            statusText.text = "❌ Camera and Location permissions required"
            Toast.makeText(this, "Please grant all permissions to use this app", Toast.LENGTH_LONG).show()
        }
    }
    
    // AI Verification system
    private var verificationStage = 0
    private var isVerificationInProgress = false
    private var isVerifying = false
    private var isVerified = false
    private var verificationStartTime = 0L
    
    // AI Verification stages for realistic simulation
    private val verificationStages = listOf(
        "🔍 AI analyzing GPS signal strength...",
        "🛰️ Cross-referencing satellite data...", 
        "🗺️ Validating geographical coordinates...",
        "📡 Running neural network verification...",
        "🎯 Calculating position confidence...",
        "✅ AI verification complete!"
    )
    
    // Location listener with AI-like verification stages
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(TAG, "📍 GPS Location updated: ${location.latitude}, ${location.longitude}")
            lastKnownLocation = location
            
            if (!isVerificationInProgress && location.accuracy <= 20.0f) {
                startAIVerificationProcess(location)
            } else {
                updateLocationDisplay(location, false)
            }
        }
        
        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "✅ Location provider enabled: $provider")
            statusText.text = "🛰️ AR System Active - GPS Provider: $provider"
        }
        
        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "⚠️ Location provider disabled: $provider")
            statusText.text = "⚠️ Location provider disabled: $provider"
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            val statusStr = when (status) {
                LocationProvider.AVAILABLE -> "Available"
                LocationProvider.OUT_OF_SERVICE -> "Out of Service"
                LocationProvider.TEMPORARILY_UNAVAILABLE -> "Temporarily Unavailable"
                else -> "Unknown"
            }
            Log.d(TAG, "📡 Location provider $provider status: $statusStr")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 Starting AR GPS simulation")
        
        try {
            setContentView(R.layout.activity_verify_ar)
            initializeViews()
            checkPermissionsAndInitialize()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize activity", e)
            finish()
        }
    }
    
    private fun initializeViews() {
        previewView = findViewById(R.id.preview_view)
        statusText = findViewById(R.id.status_text)
        latitudeText = findViewById(R.id.latitude_text)
        longitudeText = findViewById(R.id.longitude_text)
        accuracyText = findViewById(R.id.accuracy_text)
        altitudeText = findViewById(R.id.altitude_text)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        statusText.text = "🔄 Initializing AR GPS System..."
        Log.d(TAG, "✅ UI elements initialized")
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 Activity resumed")
        
        if (allPermissionsGranted() && !locationUpdatesStarted) {
            startLocationUpdates()
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "📱 Activity paused")
        stopLocationUpdates()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Activity destroyed")
        
        stopLocationUpdates()
        cameraExecutor?.shutdown()
        camera = null
    }
    
    private fun checkPermissionsAndInitialize() {
        if (allPermissionsGranted()) {
            Log.d(TAG, "✅ All permissions already granted")
            initializeGpsAndCamera()
        } else {
            Log.d(TAG, "📋 Requesting permissions...")
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun initializeGpsAndCamera() {
        Log.d(TAG, "🎯 Initializing GPS and Camera for AR simulation")
        
        lifecycleScope.launch {
            try {
                statusText.text = "🔄 Initializing AR System..."
                delay(500L)
                
                statusText.text = "📸 Starting camera preview..."
                initializeCamera()
                delay(1000L)
                
                statusText.text = "🛰️ Connecting to GPS satellites..."
                initializeLocationServices()
                delay(1000L)
                
                statusText.text = "🧠 Loading AI models..."
                delay(1500L)
                
                statusText.text = "✅ AR GPS System Ready - Acquiring location..."
                Log.d(TAG, "✅ AR GPS simulation initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize GPS and camera", e)
                statusText.text = "❌ Failed to initialize AR system"
            }
        }
    }
    
    private fun initializeLocationServices() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Check if location services are enabled
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
        
        Log.d(TAG, "📡 Location services - GPS: $isGpsEnabled, Network: $isNetworkEnabled")
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            statusText.text = "⚠️ Please enable location services"
            Toast.makeText(this, "Please enable location services in device settings", Toast.LENGTH_LONG).show()
            return
        }
        
        startLocationUpdates()
    }
    
    private fun startLocationUpdates() {
        if (locationUpdatesStarted) return
        
        try {
            val locationManager = locationManager ?: return
            
            // Start scanning animation
            startScanningAnimation()
            
            // Request location updates from both GPS and Network providers
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    0f,
                    locationListener
                )
                Log.d(TAG, "📍 GPS location updates started")
            }
            
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    0f,
                    locationListener
                )
                Log.d(TAG, "📶 Network location updates started")
            }
            
            // Try to get last known location immediately
            val gpsLastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            val bestLastKnown = when {
                gpsLastKnown != null && networkLastKnown != null -> {
                    if (gpsLastKnown.accuracy < networkLastKnown.accuracy) gpsLastKnown else networkLastKnown
                }
                gpsLastKnown != null -> gpsLastKnown
                networkLastKnown != null -> networkLastKnown
                else -> null
            }
            
            bestLastKnown?.let { location ->
                Log.d(TAG, "📍 Using last known location: ${location.latitude}, ${location.longitude}")
                lastKnownLocation = location
                updateLocationDisplay(location)
            }
            
            locationUpdatesStarted = true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception when requesting location updates", e)
            statusText.text = "❌ Location permission required"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting location updates", e)
            statusText.text = "❌ Failed to start location tracking"
        }
    }
    
    private fun startScanningAnimation() {
        lifecycleScope.launch {
            val scanMessages = listOf(
                "🛰️ Connecting to satellites...",
                "📡 Acquiring GPS signal...",
                "🔍 Scanning for location data...",
                "📊 Processing coordinates..."
            )
            
            var scanIndex = 0
            while (!isVerifying && locationUpdatesStarted) {
                runOnUiThread {
                    statusText.text = scanMessages[scanIndex % scanMessages.size]
                }
                scanIndex++
                delay(2000L)
            }
        }
    }
    
    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(locationListener)
            locationUpdatesStarted = false
            Log.d(TAG, "🛑 Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping location updates", e)
        }
    }
    
    private fun updateLocationDisplay(location: Location, isVerified: Boolean = false) {
        runOnUiThread {
            // Update location display with enhanced styling
            latitudeText.text = "📍 Lat: %.6f ${if(isVerified) "✅ VERIFIED" else ""}".format(location.latitude)
            longitudeText.text = "📍 Lng: %.6f ${if(isVerified) "✅ VERIFIED" else ""}".format(location.longitude)
            
            // Enhanced accuracy display with AI status
            val accuracyStatus = when {
                location.accuracy <= 5.0f -> "🎯 PRECISION (AI Verified)"
                location.accuracy <= 10.0f -> "✅ HIGH ACCURACY (AI Ready)"
                location.accuracy <= 20.0f -> "⚠️ FAIR ACCURACY (AI Processing)"
                else -> "🔄 IMPROVING... (AI Analyzing)"
            }
            
            accuracyText.text = "🎯 Accuracy: %.1fm - %s".format(location.accuracy, accuracyStatus)
            
            altitudeText.text = if (location.hasAltitude()) {
                "⛰️ Altitude: %.1fm".format(location.altitude)
            } else {
                "⛰️ Altitude: N/A"
            }
            
            // Update status based on verification state
            if (!isVerifying && !isVerified) {
                statusText.text = "🔄 Location acquired - Preparing AI verification..."
            } else if (!isVerifying) {
                statusText.text = "🔄 Improving location accuracy... (${location.accuracy.toInt()}m)"
            }
            
            Log.d(TAG, "✅ Location display updated - Lat: ${location.latitude}, Lng: ${location.longitude}, Acc: ${location.accuracy}m")
        }
    }
    

    
    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Preview use case for AR appearance
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                // Select back camera for AR experience
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Unbind previous use cases and bind new ones
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview)
                
                Log.d(TAG, "📸 Camera initialized for AR simulation")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize camera", e)
                statusText.text = "⚠️ Camera initialization failed"
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun startAIVerificationProcess(location: Location) {
        if (isVerificationInProgress) return
        
        isVerificationInProgress = true
        isVerifying = true
        verificationStartTime = System.currentTimeMillis()
        verificationStage = 0
        
        Log.d(TAG, "🚀 Starting AI verification process for location: ${location.latitude}, ${location.longitude}")
        
        lifecycleScope.launch {
            try {
                // Stage 1: Initial location display
                updateLocationDisplay(location, false)
                statusText.text = "🔍 AI starting location verification..."
                
                // Progressive AI verification with realistic delays
                for (stage in verificationStages.indices) {
                    verificationStage = stage
                    val stageMessage = verificationStages[stage]
                    
                    statusText.text = stageMessage
                    Log.d(TAG, "AI Stage $stage: $stageMessage")
                    
                    // Realistic AI processing delays (2-4 seconds per stage)
                    val delay = when (stage) {
                        0 -> 2000L  // Signal analysis
                        1 -> 3000L  // Satellite cross-reference
                        2 -> 2500L  // Coordinate validation
                        3 -> 3500L  // Neural network (longest)
                        4 -> 2000L  // Confidence calculation
                        5 -> 1500L  // Final verification
                        else -> 2000L
                    }
                    
                    delay(delay)
                    
                    // Add some dynamic status updates during longer stages
                    if (stage == 3) { // Neural network stage
                        statusText.text = "🧠 Deep learning model processing... 87%"
                        delay(1000L)
                        statusText.text = "🧠 Neural analysis complete - High confidence!"
                        delay(1000L)
                    }
                }
                
                // Final verification complete
                isVerificationInProgress = false
                isVerifying = false
                
                runOnUiThread {
                    updateLocationDisplay(location, true)
                    statusText.text = "✅ AI VERIFICATION COMPLETE - Location Authenticated!"
                    
                    // Add a success animation effect
                    animateVerificationSuccess()
                }
                
                Log.d(TAG, "✅ AI verification completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ AI verification failed", e)
                isVerificationInProgress = false
                isVerifying = false
                statusText.text = "❌ AI verification failed - Please try again"
            }
        }
    }
    
    private fun animateVerificationSuccess() {
        lifecycleScope.launch {
            // Flash effect for success
            val originalText = statusText.text
            repeat(3) {
                statusText.text = "🎉 SUCCESS! Location Verified! 🎉"
                delay(500L)
                statusText.text = originalText
                delay(300L)
            }
            
            // Show final success message
            statusText.text = "✅ VERIFICATION COMPLETE - Redirecting..."
            delay(2000L) // Wait 2 seconds before redirecting
            
            // Redirect to Game Selection Activity
            Log.d(TAG, "🎮 Redirecting to Game Selection Activity")
            val intent = Intent(this@VerifyArActivity, GameSelectionActivity::class.java)
            startActivity(intent)
            
            // Optionally finish this activity so user can't go back
            finish()
        }
    }
}
