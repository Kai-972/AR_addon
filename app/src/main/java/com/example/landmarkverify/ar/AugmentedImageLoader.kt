package com.example.landmarkverify.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class AugmentedImageLoader {
    
    private companion object {
        const val TAG = "AugmentedImageLoader"
        const val IMAGE_DATABASE_NAME = "augmented_images.imgdb"
    }
    
    /**
     * Loads AugmentedImageDatabase from assets folder
     * Database should be created using arcoreimg tool:
     * arcoreimg build-db --input_image_list_path=image_list.txt --output_db_path=augmented_images.imgdb
     */
    suspend fun loadDatabase(context: Context, session: Session): AugmentedImageDatabase? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading augmented image database: $IMAGE_DATABASE_NAME")
                
                val inputStream: InputStream = context.assets.open(IMAGE_DATABASE_NAME)
                inputStream.use { stream ->
                    val database = AugmentedImageDatabase.deserialize(session, stream)
                    Log.d(TAG, "Successfully loaded database with ${database.numImages} images")
                    database
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load augmented image database: $IMAGE_DATABASE_NAME", e)
                Log.w(TAG, "Create database using: arcoreimg build-db --input_image_list_path=images.txt --output_db_path=$IMAGE_DATABASE_NAME")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading augmented image database", e)
                null
            }
        }
    }
    
    /**
     * Creates an empty AugmentedImageDatabase as fallback
     */
    suspend fun createEmptyDatabase(session: Session): AugmentedImageDatabase {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Creating empty augmented image database")
            AugmentedImageDatabase(session)
        }
    }
    
    /**
     * Validates if the database file exists in assets
     */
    fun isDatabaseAvailable(context: Context): Boolean {
        return try {
            context.assets.open(IMAGE_DATABASE_NAME).use { true }
        } catch (e: IOException) {
            Log.w(TAG, "Database file not found: $IMAGE_DATABASE_NAME")
            false
        }
    }
}
