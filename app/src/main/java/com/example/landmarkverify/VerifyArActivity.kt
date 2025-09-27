package com.example.landmarkverify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.location.LocationManager
import android.location.LocationListener
import android.location.Location
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.opengl.GLSurfaceView
import android.os.Looper
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
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
    private var locationManager: LocationManager? = null
    private var lastKnownLocation: Location? = null
    private var sessionStartTime: Long = 0
    
    // UI elements
    private lateinit var statusText: TextView
    private lateinit var sessionStateText: TextView
    private lateinit var locationText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var surfaceView: GLSurfaceView
    
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
            surfaceView = findViewById(R.id.ar_surface_view) ?: run {
                Log.e(TAG, "❌ Failed to find ar_surface_view in layout")
                finish()
                return
            }
            
            // Set up minimal GLSurfaceView for ARCore context
            setupMinimalRenderer()
            
            statusText.text = "🔄 Initializing ARCore Geospatial..."
            Log.d(TAG, "✅ UI elements initialized successfully")
            
            checkPermissionsAndInitialize()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize VerifyArActivity", e)
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        surfaceView.onResume()
        arSession?.resume()
        if (arSession != null) {
            startLocationTracking()
        }
    }
    
    override fun onPause() {
        super.onPause()
        surfaceView.onPause()
        arSession?.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
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
                
                // First check if location services are enabled
                val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                
                if (!isGpsEnabled && !isNetworkEnabled) {
                    Log.e(TAG, "❌ Location services are disabled")
                    statusText.text = "❌ Please enable location services in device settings"
                    return@launch
                }
                
                Log.d(TAG, "✅ Location services enabled - GPS: $isGpsEnabled, Network: $isNetworkEnabled")
                
                // Check internet connectivity (required for ARCore Geospatial)
                val hasInternet = checkInternetConnectivity()
                if (!hasInternet) {
                    Log.w(TAG, "⚠️ No internet connection - ARCore Geospatial requires internet")
                    statusText.text = "⚠️ ARCore Geospatial requires internet connection"
                    return@launch
                }
                
                // Start Android GPS location updates to help bootstrap ARCore
                startAndroidLocationUpdates()
                
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
                sessionStartTime = System.currentTimeMillis()
                Log.d(TAG, "ARCore session configured and resumed")
                runOnUiThread {
                    sessionStateText.text = "✅ Session Running"
                }
            }
            
            runOnUiThread {
                statusText.text = "✅ ARCore Geospatial ready - Starting location tracking..."
            }
            
            // Start location tracking with longer delay for geospatial initialization
            lifecycleScope.launch {
                delay(2000) // Give ARCore more time to initialize geospatial
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
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
            Log.d(TAG, "📍 Android GPS: ${location.latitude}, ${location.longitude} (±${location.accuracy}m)")
            runOnUiThread {
                if (arSession?.earth?.earthState != com.google.ar.core.Earth.EarthState.ENABLED) {
                    // Show Android GPS data while waiting for ARCore Geospatial
                    locationText.text = "📍 Lat: ${"%.6f".format(location.latitude)} (Android GPS)\n" +
                                      "🌐 Lng: ${"%.6f".format(location.longitude)} (Android GPS)\n" +
                                      "⛰️ Alt: ${"%.1f".format(location.altitude)}m\n" +
                                      "🧭 Provider: ${location.provider}"
                    
                    accuracyText.text = when {
                        location.accuracy <= 5.0f -> "🎯 High accuracy (${location.accuracy}m) - Android GPS"
                        location.accuracy <= 15.0f -> "✅ Good accuracy (${location.accuracy}m) - Android GPS"
                        else -> "⚠️ Fair accuracy (${location.accuracy}m) - Android GPS"
                    }
                    
                    statusText.text = "📱 Android GPS working, initializing ARCore Geospatial..."
                }
            }
        }
        
        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "📡 GPS Provider enabled: $provider")
        }
        
        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "📡 GPS Provider disabled: $provider")
        }
        
        @Deprecated("Deprecated in API level 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
            Log.d(TAG, "📡 GPS Provider status changed: $provider, status: $status")
        }
    }
    
    private fun setupMinimalRenderer() {
        // Set up a minimal OpenGL renderer for ARCore context
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                Log.d(TAG, "📱 Minimal OpenGL surface created for ARCore")
            }
            
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                Log.d(TAG, "📱 Minimal OpenGL surface changed: ${width}x${height}")
            }
            
            override fun onDrawFrame(gl: GL10?) {
                // Minimal rendering - just clear the screen
                try {
                    arSession?.let { session ->
                        val frame = session.update()
                        // Frame is updated for ARCore, but we don't draw anything
                    }
                } catch (e: Exception) {
                    Log.v(TAG, "Frame update in renderer: ${e.message}")
                }
            }
        })
        
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        Log.d(TAG, "✅ Minimal GLSurfaceView renderer set up")
    }
    
    private fun checkInternetConnectivity(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                             capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            Log.d(TAG, "🌐 Internet connectivity: $hasInternet")
            hasInternet
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet connectivity", e)
            false
        }
    }
    
    private fun startAndroidLocationUpdates() {
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Request updates from both GPS and Network providers
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1 second
                    0f,    // 0 meters
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "📡 Started GPS location updates")
            }
            
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L, // 2 seconds
                    0f,    // 0 meters  
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "📡 Started Network location updates")
            }
            
            // Get last known location immediately
            val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            val bestLocation = when {
                lastGps != null && lastNetwork != null -> {
                    if (lastGps.accuracy <= lastNetwork.accuracy) lastGps else lastNetwork
                }
                lastGps != null -> lastGps
                lastNetwork != null -> lastNetwork
                else -> null
            }
            
            bestLocation?.let {
                Log.d(TAG, "📍 Using last known location: ${it.latitude}, ${it.longitude} (±${it.accuracy}m)")
                locationListener.onLocationChanged(it)
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Location permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting Android location updates", e)
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
                var attemptCount = 0
                // Update location with dynamic delay based on GPS status
                while (arSession != null && isGeospatialSupported && !isFinishing && attemptCount < 60) { // Max 2 minutes
                    updateLocationData()
                    attemptCount++
                    
                    // Dynamic delay - faster updates initially, slower later
                    val delayMs = when {
                        attemptCount < 10 -> 1000L // First 10 seconds: check every 1s
                        attemptCount < 30 -> 2000L // Next 40 seconds: check every 2s
                        else -> 3000L // After 50 seconds: check every 3s
                    }
                    
                    delay(delayMs)
                }
                
                if (attemptCount >= 60) {
                    Log.w(TAG, "⏰ GPS timeout after 2 minutes")
                    runOnUiThread {
                        statusText.text = "⏰ GPS timeout - try moving to an open area with clear sky view"
                    }
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
            // Check if activity is still valid
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "⚠️ Activity is finishing/destroyed, stopping location updates")
                return
            }
            
            Log.v(TAG, "📱 Checking geospatial data (frame updates handled by renderer)")
            
            val earth = try {
                session.earth
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get Earth object", e)
                runOnUiThread {
                    statusText.text = "❌ Geospatial service error: ${e.localizedMessage}"
                    sessionStateText.text = "❌ Earth Error"
                }
                return
            }
            
            if (earth == null) {
                Log.w(TAG, "❌ Earth is null - geospatial not available yet. Check internet connection.")
                runOnUiThread {
                    statusText.text = "🔄 Waiting for geospatial service... (check internet)"
                    sessionStateText.text = "⏳ Waiting for Earth"
                }
                return
            }
            
            Log.v(TAG, "✅ Earth object available, checking state...")
            
            // Get current Earth state with detailed logging
            val earthState = earth.earthState
            Log.i(TAG, "🌍 Earth state: $earthState")
            
            // Also check Earth tracking state 
            val earthTrackingState = earth.trackingState
            Log.i(TAG, "🌍 Earth tracking state: $earthTrackingState")
            
            when (earthState) {
                com.google.ar.core.Earth.EarthState.ENABLED -> {
                    // Check if Earth is also tracking properly
                    if (earthTrackingState != com.google.ar.core.TrackingState.TRACKING) {
                        Log.w(TAG, "🌍 Earth enabled but not tracking yet: $earthTrackingState")
                        runOnUiThread {
                            statusText.text = "🔄 Earth enabled, waiting for tracking... ($earthTrackingState)"
                            sessionStateText.text = "🌍 Earth: $earthTrackingState"
                        }
                        return
                    }
                    
                    Log.d(TAG, "✅ Earth is ENABLED and TRACKING - getting geospatial pose")
                    
                    // SUCCESS! Get geospatial pose
                    val geospatialPose = try {
                        earth.cameraGeospatialPose
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to get camera geospatial pose", e)
                        runOnUiThread {
                            statusText.text = "❌ Location pose error: ${e.localizedMessage}"
                            sessionStateText.text = "❌ Pose Error"
                        }
                        return
                    }
                    
                    if (geospatialPose == null) {
                        Log.e(TAG, "❌ Geospatial pose is null")
                        runOnUiThread {
                            statusText.text = "❌ Geospatial pose is null"
                            sessionStateText.text = "❌ Null Pose"
                        }
                        return
                    }
                    
                    val latitude = geospatialPose.latitude
                    val longitude = geospatialPose.longitude
                    val altitude = geospatialPose.altitude
                    val horizontalAccuracy = geospatialPose.horizontalAccuracy
                    val heading = geospatialPose.heading
                    
                    Log.v(TAG, "📍 Raw ARCore pose: lat=$latitude, lng=$longitude, acc=$horizontalAccuracy")
                    
                    // Check if coordinates are valid (not zero/null location)
                    val hasValidCoordinates = latitude != 0.0 && longitude != 0.0
                    val hasReasonableAccuracy = horizontalAccuracy > 0.0f && horizontalAccuracy < 1000.0f
                    val isAccurate = horizontalAccuracy <= 10.0f && hasValidCoordinates && hasReasonableAccuracy
                    
                    // Check session runtime
                    val sessionRuntime = (System.currentTimeMillis() - sessionStartTime) / 1000
                    
                    Log.d(TAG, "📍 Location validation - Coords valid: $hasValidCoordinates, Accuracy reasonable: $hasReasonableAccuracy, Accurate: $isAccurate, Session runtime: ${sessionRuntime}s")
                    
                    runOnUiThread {
                        locationText.text = "📍 Lat: ${"%.6f".format(latitude)} (ARCore)\n" +
                                          "🌐 Lng: ${"%.6f".format(longitude)} (ARCore)\n" +
                                          "⛰️ Alt: ${"%.1f".format(altitude)}m\n" +
                                          "🧭 Heading: ${"%.1f".format(heading)}°"
                        
                        accuracyText.text = when {
                            !hasValidCoordinates -> "❌ Invalid coordinates (0,0) - ARCore"
                            !hasReasonableAccuracy -> "❌ Invalid accuracy (${horizontalAccuracy}m) - ARCore"
                            horizontalAccuracy <= 5.0f -> "🎯 High accuracy (${horizontalAccuracy}m) - ARCore"
                            horizontalAccuracy <= 10.0f -> "✅ Good accuracy (${horizontalAccuracy}m) - ARCore"
                            horizontalAccuracy <= 50.0f -> "⚠️ Fair accuracy (${horizontalAccuracy}m) - ARCore"
                            else -> "🔴 Poor accuracy (${horizontalAccuracy}m) - ARCore"
                        }
                        
                        statusText.text = when {
                            !hasValidCoordinates -> {
                                if (lastKnownLocation != null) {
                                    if (sessionRuntime < 30) {
                                        "🔄 ARCore Geospatial initializing... (${sessionRuntime}s, Android GPS: ${lastKnownLocation!!.accuracy}m)"
                                    } else {
                                        "⏰ ARCore taking longer than expected (${sessionRuntime}s) - check internet & GPS"
                                    }
                                } else {
                                    "❌ Waiting for GPS coordinates... (currently 0,0)"
                                }
                            }
                            !hasReasonableAccuracy -> "❌ Waiting for GPS accuracy data..."
                            isAccurate -> "✅ LOCATION VALIDATED - Ready for verification!"
                            horizontalAccuracy <= 50.0f -> "🔄 Improving location accuracy... (${horizontalAccuracy}m)"
                            else -> "🔄 Acquiring GPS signal... (${horizontalAccuracy}m)"
                        }
                    }
                    
                    Log.d(TAG, "📍 Location: $latitude, $longitude (±${horizontalAccuracy}m) - Valid: $hasValidCoordinates")
                    
                    // If we have invalid coordinates, provide helpful tips
                    if (!hasValidCoordinates) {
                        Log.w(TAG, "⚠️ GPS coordinates are zero - device may need clear sky view or more time")
                    }
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
                    Log.d(TAG, "🔄 Earth state: $earthState (initializing...)")
                    runOnUiThread { 
                        statusText.text = "🔄 Initializing geospatial service... ($earthState)"
                        sessionStateText.text = "🔄 Geospatial: $earthState"
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating location data", e)
            runOnUiThread { 
                statusText.text = "❌ Error getting location: ${e.localizedMessage}"
                sessionStateText.text = "❌ Error: ${e.javaClass.simpleName}"
            }
        }
    }
}
