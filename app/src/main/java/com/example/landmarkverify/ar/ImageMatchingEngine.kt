package com.example.landmarkverify.ar

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class ImageMatchingEngine {
    
    private companion object {
        const val TAG = "ImageMatchingEngine"
        const val HISTOGRAM_BINS = 256
        const val SIMILARITY_THRESHOLD = 0.7f
    }
    
    data class MatchResult(
        val confidence: Float,
        val isMatch: Boolean,
        val similarityScore: Float,
        val processingTimeMs: Long
    )
    
    data class ImageDescriptor(
        val histogram: FloatArray,
        val averageColor: Triple<Float, Float, Float>,
        val edgeCount: Int,
        val brightness: Float,
        val contrast: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ImageDescriptor
            return histogram.contentEquals(other.histogram)
        }
        
        override fun hashCode(): Int = histogram.contentHashCode()
    }
    
    /**
     * Extract feature descriptor from bitmap
     */
    suspend fun extractDescriptor(bitmap: Bitmap): ImageDescriptor {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                
                // Calculate histogram
                val rHist = FloatArray(HISTOGRAM_BINS)
                val gHist = FloatArray(HISTOGRAM_BINS)
                val bHist = FloatArray(HISTOGRAM_BINS)
                
                var totalR = 0f
                var totalG = 0f  
                var totalB = 0f
                var brightness = 0f
                
                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    
                    rHist[r]++
                    gHist[g]++
                    bHist[b]++
                    
                    totalR += r
                    totalG += g
                    totalB += b
                    brightness += (0.299f * r + 0.587f * g + 0.114f * b)
                }
                
                val pixelCount = pixels.size.toFloat()
                
                // Normalize histograms
                for (i in 0 until HISTOGRAM_BINS) {
                    rHist[i] = rHist[i] / pixelCount
                    gHist[i] = gHist[i] / pixelCount
                    bHist[i] = bHist[i] / pixelCount
                }
                
                // Combine RGB histograms
                val combinedHist = FloatArray(HISTOGRAM_BINS * 3)
                System.arraycopy(rHist, 0, combinedHist, 0, HISTOGRAM_BINS)
                System.arraycopy(gHist, 0, combinedHist, HISTOGRAM_BINS, HISTOGRAM_BINS)
                System.arraycopy(bHist, 0, combinedHist, HISTOGRAM_BINS * 2, HISTOGRAM_BINS)
                
                // Calculate average color
                val avgR = totalR / pixelCount
                val avgG = totalG / pixelCount
                val avgB = totalB / pixelCount
                
                // Calculate contrast (standard deviation of brightness)
                brightness /= pixelCount
                var variance = 0f
                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val pixelBrightness = 0.299f * r + 0.587f * g + 0.114f * b
                    variance += (pixelBrightness - brightness).pow(2)
                }
                val contrast = sqrt(variance / pixelCount)
                
                // Simple edge detection count
                val edgeCount = calculateEdgeCount(pixels, width, height)
                
                val processingTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Descriptor extracted in ${processingTime}ms - edges: $edgeCount, brightness: $brightness")
                
                ImageDescriptor(
                    histogram = combinedHist,
                    averageColor = Triple(avgR, avgG, avgB),
                    edgeCount = edgeCount,
                    brightness = brightness,
                    contrast = contrast
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting descriptor", e)
                // Return empty descriptor
                ImageDescriptor(
                    histogram = FloatArray(HISTOGRAM_BINS * 3),
                    averageColor = Triple(0f, 0f, 0f),
                    edgeCount = 0,
                    brightness = 0f,
                    contrast = 0f
                )
            }
        }
    }
    
    /**
     * Compare two image descriptors and return match result
     */
    suspend fun compareDescriptors(descriptor1: ImageDescriptor, descriptor2: ImageDescriptor): MatchResult {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Histogram correlation
                val histogramSimilarity = calculateHistogramCorrelation(descriptor1.histogram, descriptor2.histogram)
                
                // Color similarity
                val colorSimilarity = calculateColorSimilarity(descriptor1.averageColor, descriptor2.averageColor)
                
                // Edge similarity
                val edgeSimilarity = calculateEdgeSimilarity(descriptor1.edgeCount, descriptor2.edgeCount)
                
                // Brightness/contrast similarity
                val brightnessSimilarity = calculateBrightnessSimilarity(descriptor1.brightness, descriptor2.brightness)
                val contrastSimilarity = calculateContrastSimilarity(descriptor1.contrast, descriptor2.contrast)
                
                // Weighted combination
                val overallSimilarity = (
                    histogramSimilarity * 0.4f +
                    colorSimilarity * 0.25f +
                    edgeSimilarity * 0.15f +
                    brightnessSimilarity * 0.1f +
                    contrastSimilarity * 0.1f
                )
                
                val confidence = minOf(1.0f, overallSimilarity * 1.2f) // Boost confidence slightly
                val isMatch = confidence >= SIMILARITY_THRESHOLD
                
                val processingTime = System.currentTimeMillis() - startTime
                
                Log.d(TAG, "Match comparison: similarity=$overallSimilarity, confidence=$confidence, isMatch=$isMatch (${processingTime}ms)")
                
                MatchResult(
                    confidence = confidence,
                    isMatch = isMatch,
                    similarityScore = overallSimilarity,
                    processingTimeMs = processingTime
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error comparing descriptors", e)
                MatchResult(0f, false, 0f, System.currentTimeMillis() - startTime)
            }
        }
    }
    
    private fun calculateEdgeCount(pixels: IntArray, width: Int, height: Int): Int {
        var edgeCount = 0
        val threshold = 30
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                val centerGray = (center shr 16 and 0xFF) * 0.299f +
                               (center shr 8 and 0xFF) * 0.587f +
                               (center and 0xFF) * 0.114f
                
                val right = pixels[y * width + x + 1]
                val rightGray = (right shr 16 and 0xFF) * 0.299f +
                              (right shr 8 and 0xFF) * 0.587f +
                              (right and 0xFF) * 0.114f
                
                val bottom = pixels[(y + 1) * width + x]
                val bottomGray = (bottom shr 16 and 0xFF) * 0.299f +
                               (bottom shr 8 and 0xFF) * 0.587f +
                               (bottom and 0xFF) * 0.114f
                
                if (abs(centerGray - rightGray) > threshold || abs(centerGray - bottomGray) > threshold) {
                    edgeCount++
                }
            }
        }
        
        return edgeCount
    }
    
    private fun calculateHistogramCorrelation(hist1: FloatArray, hist2: FloatArray): Float {
        var numerator = 0f
        var sum1Sq = 0f
        var sum2Sq = 0f
        
        for (i in hist1.indices) {
            numerator += hist1[i] * hist2[i]
            sum1Sq += hist1[i] * hist1[i]
            sum2Sq += hist2[i] * hist2[i]
        }
        
        val denominator = sqrt(sum1Sq * sum2Sq)
        return if (denominator > 0) numerator / denominator else 0f
    }
    
    private fun calculateColorSimilarity(color1: Triple<Float, Float, Float>, color2: Triple<Float, Float, Float>): Float {
        val rDiff = abs(color1.first - color2.first)
        val gDiff = abs(color1.second - color2.second)
        val bDiff = abs(color1.third - color2.third)
        val maxDiff = 255f
        
        val avgDiff = (rDiff + gDiff + bDiff) / 3f
        return 1f - (avgDiff / maxDiff)
    }
    
    private fun calculateEdgeSimilarity(edges1: Int, edges2: Int): Float {
        val maxEdges = maxOf(edges1, edges2)
        return if (maxEdges > 0) {
            1f - abs(edges1 - edges2).toFloat() / maxEdges
        } else 1f
    }
    
    private fun calculateBrightnessSimilarity(brightness1: Float, brightness2: Float): Float {
        val diff = abs(brightness1 - brightness2)
        return 1f - (diff / 255f)
    }
    
    private fun calculateContrastSimilarity(contrast1: Float, contrast2: Float): Float {
        val maxContrast = maxOf(contrast1, contrast2)
        return if (maxContrast > 0) {
            1f - abs(contrast1 - contrast2) / maxContrast
        } else 1f
    }
}
