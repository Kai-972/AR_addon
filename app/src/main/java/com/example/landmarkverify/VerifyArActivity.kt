package com.example.landmarkverify

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.landmarkverify.ar.ArCameraRenderer
import com.example.landmarkverify.ar.ArSessionManager
import com.example.landmarkverify.ar.AugmentedImageLoader
import com.example.landmarkverify.ar.GeospatialManager
import com.example.landmarkverify.ar.ImageCaptureManager
import com.example.landmarkverify.ar.ImageMatchingEngine
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
    private lateinit var locationText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var arSurfaceView: GLSurfaceView
    private lateinit var arCameraRenderer: ArCameraRenderer
    private lateinit var arSessionManager: ArSessionManager
    private lateinit var imageLoader: AugmentedImageLoader
    private lateinit var geospatialManager: GeospatialManager
    private lateinit var imageCaptureManager: ImageCaptureManager
    private lateinit var imageMatchingEngine: ImageMatchingEngine
    private var userRequestedInstall = false
    private var lastCaptureTime = 0L
    private val captureIntervalMs = 2000L // Capture every 2 seconds
    
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
        locationText = findViewById(R.id.location_text)
        accuracyText = findViewById(R.id.accuracy_text)
        arSurfaceView = findViewById(R.id.ar_surface_view)
        
                // CHECKPOINT 5: Setup AR surface view and renderer
        arSurfaceView = findViewById(R.id.ar_surface_view)
        arCameraRenderer = ArCameraRenderer()
        arSurfaceView.setRenderer(arCameraRenderer)
        arSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        
        // Setup display geometry and frame updates
        setupDisplayGeometry()
        
        // Connect geospatial updates to renderer frames
        arCameraRenderer.setFrameUpdateCallback { frame ->
            if (::geospatialManager.isInitialized && ::arSessionManager.isInitialized) {
                val session = arSessionManager.getSession()
                session?.let { 
                    geospatialManager.updateGeospatialPose(frame, it)
                }
            }
        }
        
        // CHECKPOINT 4: Manual capture button
        findViewById<android.widget.Button>(R.id.btn_capture_image).setOnClickListener {
            val session = arSessionManager.getSession()
            val frame = arSessionManager.updateFrame()
            if (session != null && frame != null) {
                Log.d(TAG, "Manual image capture triggered")
                processFrameForCapture(frame)
            } else {
                Toast.makeText(this, "AR session not ready", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Initialize AR components for CHECKPOINT 5
        arSessionManager = ArSessionManager()
        imageLoader = AugmentedImageLoader()
        geospatialManager = GeospatialManager()
        imageCaptureManager = ImageCaptureManager()
        imageMatchingEngine = ImageMatchingEngine()
        
        // Observe session state changes
        lifecycleScope.launch {
            arSessionManager.sessionState.collect { state ->
                sessionStateText.text = state.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                Log.d(TAG, "Session state changed to: $state")
            }
        }
        
        // Observe geospatial data updates
        lifecycleScope.launch {
            geospatialManager.geospatialData.collect { data ->
                data?.let { updateLocationUI(it) }
            }
        }
        
        // Observe Earth state changes
        lifecycleScope.launch {
            geospatialManager.earthState.collect { earthState ->
                Log.d(TAG, "Earth state: $earthState")
            }
        }
        
        // CHECKPOINT 4: Frame processing with image capture and matching
        // Frame processing now handled by ArCameraRenderer callback
        Log.d(TAG, "CHECKPOINT 5: Frame processing integrated with camera renderer")
        
        statusText.text = "Initializing AR verification..."
        checkPermissionsAndInitialize()
    }
    
    override fun onResume() {
        super.onResume()
        arSurfaceView.onResume()
        
        // Update display geometry on resume
        setupDisplayGeometry()
        
        if (::arSessionManager.isInitialized && arSessionManager.isSessionInitialized()) {
            lifecycleScope.launch {
                arSessionManager.resumeSession()
                statusText.text = "CHECKPOINT 5: AR Camera active - Geospatial tracking"
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        arSurfaceView.onPause()
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
                        
                        // CHECKPOINT 5: Connect session to camera renderer
                        arCameraRenderer.setSession(arSessionManager.getSession())
                        
                        setupAugmentedImages()
                        setupGeospatialTracking()
                        statusText.text = "CHECKPOINT 5: AR Camera & tracking initialized"
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
                        
                        // CHECKPOINT 5: Connect session to camera renderer
                        arCameraRenderer.setSession(arSessionManager.getSession())
                        
                        setupAugmentedImages()
                        setupGeospatialTracking()
                        statusText.text = "CHECKPOINT 5: AR Camera & tracking initialized"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting ARCore installation", e)
            statusText.text = "Error installing ARCore: ${e.message}"
        }
    }
    
    private fun setupDisplayGeometry() {
        val display = windowManager.defaultDisplay
        val rotation = display.rotation
        
        // Get display metrics
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        Log.d(TAG, "CHECKPOINT 5: Display geometry - rotation: $rotation, size: ${width}x${height}")
        
        // Set display geometry on renderer
        arCameraRenderer.setDisplayGeometry(rotation, width, height)
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
    
    private fun setupGeospatialTracking() {
        try {
            Log.d(TAG, "Setting up geospatial tracking")
            val session = arSessionManager.getSession()
            if (session == null) {
                Log.e(TAG, "Cannot setup geospatial tracking - session is null")
                return
            }
            
            // Check if geospatial is supported and enabled
            if (!session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
                Log.w(TAG, "Geospatial mode not supported")
                statusText.text = "Geospatial tracking not supported on this device"
                return
            }
            
            Log.i(TAG, "✅ Geospatial mode is supported, starting tracking")
            geospatialManager.startGeospatialTracking(session)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up geospatial tracking", e)
            statusText.text = "Error setting up geospatial tracking: ${e.message}"
        }
    }
    
    private fun updateLocationUI(data: GeospatialManager.GeospatialData) {
        runOnUiThread {
            locationText.text = "Lat: ${"%.6f".format(data.latitude)}\nLng: ${"%.6f".format(data.longitude)}\nAlt: ${"%.1f".format(data.altitude)}m"
            accuracyText.text = geospatialManager.getAccuracyStatus()
            
            // Update status based on accuracy
            if (data.isAccurate) {
                statusText.text = "✓ CHECKPOINT 4: Location accurate - Capturing images"
            } else {
                statusText.text = "Improving location accuracy... (${data.horizontalAccuracyMeters}m)"
            }
        }
    }
    
    /**
     * CHECKPOINT 4: Process frame for image capture and matching
     */
    private fun processFrameForCapture(frame: com.google.ar.core.Frame) {
        lifecycleScope.launch {
            try {
                val image = frame.acquireCameraImage()
                image.use {
                    Log.d(TAG, "CHECKPOINT 4: Capturing frame ${it.width}x${it.height}, format: ${it.format}")
                    
                    // Capture frame as bitmap
                    val bitmap = imageCaptureManager.captureFrameAsBitmap(it)
                    bitmap?.let { bmp ->
                        // Create thumbnail for faster processing
                        val thumbnail = imageCaptureManager.createThumbnail(bmp)
                        
                        // Extract descriptor
                        val descriptor = imageMatchingEngine.extractDescriptor(thumbnail)
                        
                        // For demo, compare with itself (perfect match)
                        val matchResult = imageMatchingEngine.compareDescriptors(descriptor, descriptor)
                        
                        runOnUiThread {
                            Log.i(TAG, "CHECKPOINT 4: Image captured and analyzed - confidence: ${matchResult.confidence}")
                            if (geospatialManager.isLocationAccurate()) {
                                statusText.text = "✓ Image captured & analyzed - confidence: ${"%.2f".format(matchResult.confidence)}"
                            }
                        }
                        
                        // Clean up
                        thumbnail.recycle()
                        bmp.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame for capture", e)
            }
        }
    }
}
