package com.example.landmarkverify.constants

object GeospatialConstants {
    // Geospatial accuracy thresholds
    const val GEO_ACCURACY_METERS = 10.0
    const val HIGH_ACCURACY_METERS = 5.0
    const val MIN_POSE_CONFIDENCE = 0.5 // Medium confidence (0.0 to 1.0 scale)
    
    // Earth state descriptions
    const val EARTH_STATE_ENABLED_DESC = "Geospatial enabled"
    const val EARTH_STATE_DISABLED_DESC = "Geospatial disabled"
    const val EARTH_STATE_ERROR_INTERNAL_DESC = "Internal error"
    const val EARTH_STATE_ERROR_NOT_AUTHORIZED_DESC = "Not authorized"
    const val EARTH_STATE_ERROR_RESOURCE_EXHAUSTED_DESC = "Resource exhausted"
    
    // UI update intervals
    const val POSE_UPDATE_INTERVAL_MS = 100L
    const val UI_UPDATE_INTERVAL_MS = 500L
}
