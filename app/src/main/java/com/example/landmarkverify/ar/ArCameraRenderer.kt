package com.example.landmarkverify.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CHECKPOINT 5: AR Camera Renderer
 * Provides OpenGL context required for ARCore camera and pose tracking
 */
class ArCameraRenderer : ArRenderer {
    
    private companion object {
        const val TAG = "ArCameraRenderer"
        
        // Vertex shader for background camera texture
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """
        
        // Fragment shader using external texture (required for AR camera)
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_CameraTexture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_CameraTexture, v_TexCoord);
            }
        """
    }
    
    private var session: Session? = null
    private var displayRotation = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    
    // OpenGL shader program and buffers
    private var cameraProgram = 0
    private var cameraPositionAttribute = 0
    private var cameraTexCoordAttribute = 0  
    private var cameraTextureUniform = 0
    private var cameraVertexBuffer: FloatBuffer? = null
    
    // Camera texture
    private var cameraTextureId = 0
    
    // Full screen quad vertices (position + texture coordinates)
    // Adjusted for proper camera orientation
    private val CAMERA_VERTICES = floatArrayOf(
        -1.0f, -1.0f, 1.0f, 1.0f, // Bottom-left -> Bottom-right (rotated)
         1.0f, -1.0f, 1.0f, 0.0f, // Bottom-right -> Top-right
        -1.0f,  1.0f, 0.0f, 1.0f, // Top-left -> Bottom-left  
         1.0f,  1.0f, 0.0f, 0.0f  // Top-right -> Top-left
    )
    
    override fun setSession(session: Session?) {
        this.session = session
        Log.d(TAG, "Session set: ${session != null}, camera texture ID: $cameraTextureId")
        
        // Set camera texture immediately if available
        if (session != null && cameraTextureId != 0) {
            try {
                session.setCameraTextureName(cameraTextureId)
                Log.d(TAG, "Camera texture name set immediately")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set camera texture name immediately", e)
            }
        }
    }
    
    override fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        displayRotation = rotation
        surfaceWidth = width
        surfaceHeight = height
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "CHECKPOINT 5: AR Camera surface created")
        
        try {
            // Log OpenGL version info
            val version = GLES20.glGetString(GLES20.GL_VERSION)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
            Log.d(TAG, "OpenGL Version: $version")
            Log.d(TAG, "OpenGL Renderer: $renderer")
            Log.d(TAG, "OpenGL Vendor: $vendor")
            
            // Clear color to black
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            
            // Create camera shader program
            createCameraShaderProgram()
            
            // Create vertex buffer for full-screen quad
            createVertexBuffer()
            
            // Generate camera texture
            createCameraTexture()
            
            Log.d(TAG, "CHECKPOINT 5: AR Camera renderer initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Failed to initialize AR Camera renderer", e)
            throw RuntimeException("OpenGL initialization failed", e)
        }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "CHECKPOINT 5: Surface changed to ${width}x${height}")
        GLES20.glViewport(0, 0, width, height)
        
        surfaceWidth = width
        surfaceHeight = height
        
        // Update session display geometry
        session?.setDisplayGeometry(displayRotation, width, height)
    }
    
    // Frame update callback for geospatial tracking
    private var frameUpdateCallback: ((Frame) -> Unit)? = null
    
    override fun setFrameUpdateCallback(callback: (Frame) -> Unit) {
        frameUpdateCallback = callback
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        val currentSession = session ?: return
        
        try {
            // Set camera texture name for ARCore
            currentSession.setCameraTextureName(cameraTextureId)
            
            // Update and render camera background
            val frame = currentSession.update()
            frame?.let { 
                renderCameraBackground()
                // Notify activity for geospatial updates
                frameUpdateCallback?.invoke(it)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDrawFrame", e)
        }
    }
    
    private fun createCameraShaderProgram() {
        try {
            Log.d(TAG, "Creating camera shader program...")
            
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            if (vertexShader == 0) {
                throw RuntimeException("Failed to load vertex shader")
            }
            
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            if (fragmentShader == 0) {
                throw RuntimeException("Failed to load fragment shader")
            }
            
            cameraProgram = GLES20.glCreateProgram()
            if (cameraProgram == 0) {
                throw RuntimeException("Failed to create GL program")
            }
            
            GLES20.glAttachShader(cameraProgram, vertexShader)
            GLES20.glAttachShader(cameraProgram, fragmentShader)
            GLES20.glLinkProgram(cameraProgram)
            
            // Check program linking
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(cameraProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val errorLog = GLES20.glGetProgramInfoLog(cameraProgram)
                GLES20.glDeleteProgram(cameraProgram)
                throw RuntimeException("Program linking failed: $errorLog")
            }
            
            // Get attribute and uniform locations
            cameraPositionAttribute = GLES20.glGetAttribLocation(cameraProgram, "a_Position")
            cameraTexCoordAttribute = GLES20.glGetAttribLocation(cameraProgram, "a_TexCoord")
            cameraTextureUniform = GLES20.glGetUniformLocation(cameraProgram, "u_CameraTexture")
            
            Log.d(TAG, "Camera shader program created successfully: $cameraProgram")
            Log.d(TAG, "Attribute locations - position: $cameraPositionAttribute, texCoord: $cameraTexCoordAttribute")
            Log.d(TAG, "Uniform location - texture: $cameraTextureUniform")
            
            // Clean up shaders (they're now linked into the program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera shader program", e)
            throw e
        }
    }
    
    private fun createVertexBuffer() {
        cameraVertexBuffer = ByteBuffer.allocateDirect(CAMERA_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(CAMERA_VERTICES)
        cameraVertexBuffer?.position(0)
    }
    
    private fun createCameraTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        
        Log.d(TAG, "Camera texture created: $cameraTextureId")
    }
    
    private fun renderCameraBackground() {
        // Use camera shader program
        GLES20.glUseProgram(cameraProgram)
        
        // Enable vertex attributes
        GLES20.glEnableVertexAttribArray(cameraPositionAttribute)
        GLES20.glEnableVertexAttribArray(cameraTexCoordAttribute)
        
        // Bind vertex buffer and set attributes
        cameraVertexBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glVertexAttribPointer(cameraPositionAttribute, 2, GLES20.GL_FLOAT, false, 16, buffer)
            buffer.position(2)
            GLES20.glVertexAttribPointer(cameraTexCoordAttribute, 2, GLES20.GL_FLOAT, false, 16, buffer)
        }
        
        // Bind camera texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(cameraTextureUniform, 0)
        
        // Draw camera background
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // Disable attributes
        GLES20.glDisableVertexAttribArray(cameraPositionAttribute)
        GLES20.glDisableVertexAttribArray(cameraTexCoordAttribute)
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader of type: $type")
            return 0
        }
        
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // Check for compilation errors
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            val errorLog = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation failed for type $type:")
            Log.e(TAG, "Error: $errorLog")
            Log.e(TAG, "Shader source:")
            Log.e(TAG, shaderCode)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $errorLog")
        }
        
        Log.d(TAG, "Shader compiled successfully: type=$type, id=$shader")
        return shader
    }
}
