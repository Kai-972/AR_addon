package com.example.landmarkverify.ar

import android.util.Log
import com.google.ar.core.Earth
import com.google.ar.core.Frame
import com.google.ar.core.GeospatialPose
import com.google.ar.core.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GeospatialManager {
    
    private companion object {
        const val TAG = "GeospatialManager"
        const val GEO_ACCURACY_METERS = 10.0
        const val MIN_POSE_CONFIDENCE = 0.5 // Medium confidence threshold
    }
    
    private val _geospatialPose = MutableStateFlow<GeospatialPose?>(null)
    val geospatialPose: StateFlow<GeospatialPose?> = _geospatialPose.asStateFlow()
    
    private val _earthState = MutableStateFlow<Int>(-1) // -1 = unknown, 0 = disabled, 1 = enabled
    val earthState: StateFlow<Int> = _earthState.asStateFlow()
    
    private val _isGeospatialReady = MutableStateFlow(false)
    val isGeospatialReady: StateFlow<Boolean> = _isGeospatialReady.asStateFlow()
    
    data class GeospatialData(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val heading: Double,
        val horizontalAccuracyMeters: Float,
        val poseConfidence: Float,
        val isAccurate: Boolean
    )
    
    private val _geospatialData = MutableStateFlow<GeospatialData?>(null)
    val geospatialData: StateFlow<GeospatialData?> = _geospatialData.asStateFlow()
    
    fun startGeospatialTracking(session: Session?) {
        session?.let {
            Log.d(TAG, "Starting geospatial tracking")
            Log.d(TAG, "Session geospatial mode: ${it.config.geospatialMode}")
            
            val earth = it.earth
            if (earth != null) {
                val earthStateValue = try {
                    val state = earth.earthState
                    Log.d(TAG, "Raw Earth state: $state")
                    state.ordinal
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting earth state", e)
                    -1
                }
                _earthState.value = earthStateValue
                Log.d(TAG, "Earth state ordinal: $earthStateValue")
            } else {
                Log.w(TAG, "Earth is null - geospatial not supported")
                _earthState.value = 0 // disabled
            }
        } ?: Log.w(TAG, "Cannot start geospatial tracking - session is null")
    }
    
    fun updateGeospatialPose(frame: Frame, session: Session) {
        try {
            val earth = session.earth
            
            if (earth == null) {
                Log.w(TAG, "Earth is null in updateGeospatialPose")
                return
            }
            
            val currentEarthState = try {
                val state = earth.earthState
                Log.v(TAG, "Current Earth state: $state")
                state.ordinal
            } catch (e: Exception) {
                Log.e(TAG, "Error getting earth state in update", e)
                -1
            }
            _earthState.value = currentEarthState
            
            Log.d(TAG, "Earth state in update: $currentEarthState")
            
            when (currentEarthState) {
                1 -> { // ENABLED state (typically ordinal 1)
                    val cameraGeospatialPose = earth.cameraGeospatialPose
                    _geospatialPose.value = cameraGeospatialPose
                    
                    val data = GeospatialData(
                        latitude = cameraGeospatialPose.latitude,
                        longitude = cameraGeospatialPose.longitude,
                        altitude = cameraGeospatialPose.altitude,
                        heading = cameraGeospatialPose.heading,
                        horizontalAccuracyMeters = cameraGeospatialPose.horizontalAccuracy.toFloat(),
                        poseConfidence = cameraGeospatialPose.orientationYawAccuracy.toFloat(),
                        isAccurate = cameraGeospatialPose.horizontalAccuracy <= GEO_ACCURACY_METERS.toFloat() &&
                                   cameraGeospatialPose.orientationYawAccuracy >= MIN_POSE_CONFIDENCE.toFloat()
                    )
                    
                    _geospatialData.value = data
                    _isGeospatialReady.value = data.isAccurate
                    
                    Log.v(TAG, "Geospatial pose - Lat: ${data.latitude}, Lng: ${data.longitude}, " +
                            "Accuracy: ${data.horizontalAccuracyMeters}m, Confidence: ${data.poseConfidence}")
                }
                2 -> { // ERROR_INTERNAL (typically ordinal 2)
                    Log.e(TAG, "Earth state error: Internal error")
                    _isGeospatialReady.value = false
                }
                3 -> { // ERROR_NOT_AUTHORIZED (typically ordinal 3)
                    Log.e(TAG, "Earth state error: Not authorized")
                    _isGeospatialReady.value = false
                }
                4 -> { // ERROR_RESOURCE_EXHAUSTED (typically ordinal 4)
                    Log.e(TAG, "Earth state error: Resource exhausted")
                    _isGeospatialReady.value = false
                }
                else -> {
                    Log.d(TAG, "Earth state: $currentEarthState")
                    _isGeospatialReady.value = false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating geospatial pose", e)
            _isGeospatialReady.value = false
        }
    }
    
    fun getCurrentLocation(): GeospatialData? = _geospatialData.value
    
    fun isLocationAccurate(): Boolean = _geospatialData.value?.isAccurate ?: false
    
    fun getAccuracyStatus(): String {
        val data = _geospatialData.value ?: return "No location data"
        return when {
            data.horizontalAccuracyMeters <= 5.0 -> "High accuracy (${data.horizontalAccuracyMeters}m)"
            data.horizontalAccuracyMeters <= GEO_ACCURACY_METERS -> "Good accuracy (${data.horizontalAccuracyMeters}m)"
            else -> "Low accuracy (${data.horizontalAccuracyMeters}m)"
        }
    }
}
