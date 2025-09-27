package com.example.landmarkverify.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArSessionManager {
    
    private companion object {
        const val TAG = "ArSessionManager"
    }
    
    private var session: Session? = null
    private var isGeospatialSupported = false
    
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.NOT_INITIALIZED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    
    private val _frameUpdates = MutableStateFlow<Frame?>(null)
    val frameUpdates: StateFlow<Frame?> = _frameUpdates.asStateFlow()
    
    enum class SessionState {
        NOT_INITIALIZED,
        INITIALIZING,
        INITIALIZED,
        PAUSED,
        ERROR
    }
    
    suspend fun initializeSession(context: Context) {
        try {
            _sessionState.value = SessionState.INITIALIZING
            Log.d(TAG, "Initializing ARCore session")
            
            session = Session(context).apply {
                val sessionRef = this
                val config = Config(this).apply {
                    // Enable Geospatial if supported
                    Log.d(TAG, "Checking geospatial mode support...")
                    if (sessionRef.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
                        geospatialMode = Config.GeospatialMode.ENABLED
                        isGeospatialSupported = true
                        Log.i(TAG, "✅ Geospatial mode ENABLED successfully")
                    } else {
                        Log.w(TAG, "❌ Geospatial mode NOT SUPPORTED on this device")
                        isGeospatialSupported = false
                    }
                    
                    // Configure session for best performance
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    focusMode = Config.FocusMode.AUTO
                }
                
                configure(config)
                resume()
                Log.d(TAG, "ARCore session configured and resumed")
            }
            
            _sessionState.value = SessionState.INITIALIZED
            Log.d(TAG, "ARCore session initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ARCore session", e)
            _sessionState.value = SessionState.ERROR
            throw e
        }
    }
    
    suspend fun resumeSession() {
        try {
            session?.let { 
                it.resume()
                _sessionState.value = SessionState.INITIALIZED
                Log.d(TAG, "ARCore session resumed")
                
                // Start frame processing loop
                startFrameUpdates()
            } ?: run {
                Log.w(TAG, "Attempted to resume null session")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume ARCore session", e)
            _sessionState.value = SessionState.ERROR
        }
    }
    
    fun pauseSession() {
        try {
            session?.pause()
            _sessionState.value = SessionState.PAUSED
            Log.d(TAG, "ARCore session paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause ARCore session", e)
        }
    }
    
    fun closeSession() {
        try {
            session?.close()
            session = null
            _sessionState.value = SessionState.NOT_INITIALIZED
            Log.d(TAG, "ARCore session closed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close ARCore session", e)
        }
    }
    
    fun updateFrame(): Frame? {
        return try {
            session?.update()?.also { frame ->
                _frameUpdates.value = frame
                Log.v(TAG, "Frame updated - tracking state: ${frame.camera.trackingState}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update frame", e)
            null
        }
    }
    
    fun isSessionInitialized(): Boolean = session != null && _sessionState.value == SessionState.INITIALIZED
    
    fun isGeospatialEnabled(): Boolean = isGeospatialSupported
    
    fun getSession(): Session? = session
    
    private fun startFrameUpdates() {
        // Frame updates will be handled by the activity's render loop
        // This is a placeholder for future frame processing
        Log.d(TAG, "Frame updates started")
    }
}
