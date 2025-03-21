package com.example.test

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import com.example.test.game.RunnerGame
import com.example.test.game.SoundManager
import com.example.test.game.GameScreen
import com.example.test.ui.theme.TestTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val VERSION = "2.5"
        const val VERSION_CODE = 12
        
        // Singleton for GameScreen tracking
        private var gameScreenInstanceCount = 0
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "Creating MainActivity, about to launch GameScreen")
        
        setContent {
            val context = LocalContext.current
            // Use remember to ensure soundManager is only created once
            val soundManager = remember { SoundManager(context) }
            
            TestTheme {
                // Use a single Surface that doesn't recreate on recompositions
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Important: Wrap GameScreen in key() with a stable value to prevent recreations
                    key("stable-game-screen-v2") {
                        // Track GameScreen instances
                        gameScreenInstanceCount++
                        Log.d("MainActivity", "Creating GameScreen instance #$gameScreenInstanceCount")
                        
                        // Log before creating GameScreen
                        Log.d("MainActivity", "Starting GameScreen in NORMAL mode")
                        
                        // Create GameScreen with stable references
                        GameScreen(context = context, soundManager = soundManager)
                    }
                }
            }
        }
    }
}