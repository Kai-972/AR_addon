package com.example.landmarkverify

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Game Selection Result Activity
 * Shows the user they've been selected for the game after AR verification
 */
class GameSelectionActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_selection)
        
        // Initialize UI elements
        val titleText = findViewById<TextView>(R.id.title_text)
        val messageText = findViewById<TextView>(R.id.message_text)
        val playButton = findViewById<Button>(R.id.play_button)
        val backButton = findViewById<Button>(R.id.back_button)
        
        // Set up click listeners
        playButton.setOnClickListener {
            // TODO: Navigate to game or next activity
            // For now, just finish this activity
            finish()
        }
        
        backButton.setOnClickListener {
            // Go back to main activity
            finish()
        }
        
        // Add some celebration animation or effects here if needed
        startCelebrationAnimation()
    }
    
    private fun startCelebrationAnimation() {
        // Simple celebration - you can enhance this later
        val titleText = findViewById<TextView>(R.id.title_text)
        titleText.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(500)
            .withEndAction {
                titleText.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(500)
                    .start()
            }
            .start()
    }
}
