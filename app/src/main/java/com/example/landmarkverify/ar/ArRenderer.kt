package com.example.landmarkverify.ar

import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.Session

/**
 * Common interface for AR renderers
 */
interface ArRenderer : GLSurfaceView.Renderer {
    fun setSession(session: Session?)
    fun setFrameUpdateCallback(callback: (Frame) -> Unit)
    fun setDisplayGeometry(rotation: Int, width: Int, height: Int)
}
