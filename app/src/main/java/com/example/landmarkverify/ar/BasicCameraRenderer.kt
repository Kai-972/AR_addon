package com.example.landmarkverify.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CHECKPOINT 2: Basic Camera Renderer (Fallback)
 * Simple renderer that just clears the screen - used when ArCameraRenderer fails
 */
class BasicCameraRenderer : ArRenderer {
    
    private companion object {
        const val TAG = "BasicCameraRenderer"
    }
    
    private var session: Session? = null
    private var frameUpdateCallback: ((Frame) -> Unit)? = null
    
    fun setSession(session: Session?) {
        this.session = session
        Log.d(TAG, "Session set: ${session != null}")
    }
    
    fun setFrameUpdateCallback(callback: (Frame) -> Unit) {
        frameUpdateCallback = callback
    }
    
    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        Log.d(TAG, "Display geometry set: ${width}x${height}, rotation: $rotation")
        session?.setDisplayGeometry(rotation, width, height)
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "CHECKPOINT 2: Basic Camera surface created")
        
        // Simple black background
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        
        Log.d(TAG, "CHECKPOINT 2: Basic Camera renderer initialized")
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "Surface changed to ${width}x${height}")
        GLES20.glViewport(0, 0, width, height)
        
        // Update session display geometry
        session?.setDisplayGeometry(0, width, height)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Clear screen with dark background
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        val currentSession = session ?: return
        
        try {
            // Update ARCore frame (no camera background rendering)
            val frame = currentSession.update()
            frame?.let { 
                // Notify activity for geospatial updates
                frameUpdateCallback?.invoke(it)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDrawFrame", e)
        }
    }
}