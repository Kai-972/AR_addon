package com.example.landmarkverify.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ImageCaptureManager {
    
    private companion object {
        const val TAG = "ImageCaptureManager"
    }
    
    /**
     * Captures current AR frame as Bitmap for processing
     */
    suspend fun captureFrameAsBitmap(image: Image): Bitmap? {
        return withContext(Dispatchers.Default) {
            try {
                when (image.format) {
                    ImageFormat.YUV_420_888 -> convertYuv420ToBitmap(image)
                    ImageFormat.NV21 -> convertNv21ToBitmap(image)
                    else -> {
                        Log.w(TAG, "Unsupported image format: ${image.format}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing frame as bitmap", e)
                null
            }
        }
    }
    
    private fun convertYuv420ToBitmap(image: Image): Bitmap? {
        try {
            val planes = image.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer  
            val vBuffer = planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            // Copy Y plane
            yBuffer.get(nv21, 0, ySize)
            
            // Interleave U and V planes for NV21 format
            val uvPixelStride = planes[1].pixelStride
            if (uvPixelStride == 1) {
                uBuffer.get(nv21, ySize, uSize)
                vBuffer.get(nv21, ySize + uSize, vSize)
            } else {
                // Handle pixel stride > 1
                var uvIndex = ySize
                for (i in 0 until uSize step uvPixelStride) {
                    nv21[uvIndex++] = vBuffer.get(i)
                    nv21[uvIndex++] = uBuffer.get(i)
                }
            }
            
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
            val jpegBytes = out.toByteArray()
            
            return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting YUV420 to bitmap", e)
            return null
        }
    }
    
    private fun convertNv21ToBitmap(image: Image): Bitmap? {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            val yuvImage = YuvImage(bytes, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
            val jpegBytes = out.toByteArray()
            
            return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting NV21 to bitmap", e)
            return null
        }
    }
    
    /**
     * Creates a downscaled version for faster processing
     */
    fun createThumbnail(bitmap: Bitmap, maxWidth: Int = 640, maxHeight: Int = 480): Bitmap {
        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )
        
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
