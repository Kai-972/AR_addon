package com.example.landmarkverify.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArRenderer : GLSurfaceView.Renderer {
    
    private companion object {
        const val TAG = "ArRenderer"
        
        // Simple vertex shader for background camera
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """
        
        // Simple fragment shader for background camera
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
    }
    
    private var session: Session? = null
    private var frame: Frame? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    
    // OpenGL program and buffer objects
    private var program = 0
    private var positionAttribute = 0
    private var texCoordAttribute = 0
    private var textureUniform = 0
    private var vertexBuffer: FloatBuffer? = null
    
    // Background camera texture
    private var backgroundTextureId = 0
    
    fun setSession(session: Session?) {
        this.session = session
    }
    
    fun updateFrame(frame: Frame?) {
        this.frame = frame
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated")
        
        // Set clear color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // Create shader program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        // Get attribute/uniform locations
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
        
        // Create vertex buffer for full-screen quad
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f, 1.0f,  // Bottom-left
             1.0f, -1.0f, 1.0f, 1.0f,  // Bottom-right
            -1.0f,  1.0f, 0.0f, 0.0f,  // Top-left
             1.0f,  1.0f, 1.0f, 0.0f   // Top-right
        )
        
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer?.position(0)
        
        // Generate texture for camera background
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        backgroundTextureId = textures[0]
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: ${width}x${height}")
        GLES20.glViewport(0, 0, width, height)
        surfaceWidth = width
        surfaceHeight = height
        
        // Update session display geometry
        session?.setDisplayGeometry(0, width, height)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        val currentSession = session ?: return
        val currentFrame = frame ?: return
        
        try {
            // Update session
            currentSession.setCameraTextureName(backgroundTextureId)
            
            // Use shader program
            GLES20.glUseProgram(program)
            
            // Enable attributes
            GLES20.glEnableVertexAttribArray(positionAttribute)
            GLES20.glEnableVertexAttribArray(texCoordAttribute)
            
            // Set vertex data
            vertexBuffer?.let { buffer ->
                buffer.position(0)
                GLES20.glVertexAttribPointer(positionAttribute, 2, GLES20.GL_FLOAT, false, 16, buffer)
                buffer.position(2)
                GLES20.glVertexAttribPointer(texCoordAttribute, 2, GLES20.GL_FLOAT, false, 16, buffer)
            }
            
            // Bind texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId)
            GLES20.glUniform1i(textureUniform, 0)
            
            // Draw quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            // Disable attributes
            GLES20.glDisableVertexAttribArray(positionAttribute)
            GLES20.glDisableVertexAttribArray(texCoordAttribute)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDrawFrame", e)
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
}
