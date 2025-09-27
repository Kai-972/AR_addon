package com.example.landmarkverify.utils

import android.util.Log

object Logger {
    
    private const val DEFAULT_TAG = "LandmarkVerify"
    
    fun d(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
    }
    
    fun i(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
    }
    
    fun w(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
    }
    
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    fun v(tag: String = DEFAULT_TAG, message: String) {
        Log.v(tag, message)
    }
}
