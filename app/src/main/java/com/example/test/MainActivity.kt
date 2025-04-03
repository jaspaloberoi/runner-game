package com.example.test

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.example.test.game.RunnerGame
import com.example.test.game.SoundManager
import com.example.test.game.GameScreen
import com.example.test.ui.theme.TestTheme
import androidx.compose.ui.graphics.Color

/**
 * Runner Game
 * Version 3.3
 * Features:
 * - Simple endless runner with bird character
 * - Tap to jump, hold for different modes
 * - Obstacles to avoid
 * - Score tracking
 * - Various game modes with different mechanics
 * - Themed levels that change appearance
 * - Added Blue mode with reversed gravity physics
 * - Randomized mode cycling order 
 */
class MainActivity : ComponentActivity() {
    companion object {
        const val VERSION = "3.3"
        const val VERSION_CODE = 13
        
        // Added a counter to track GameScreen instances created
        var gameScreenInstanceCount = 0
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "Creating MainActivity, about to launch GameScreen")
        
        val soundManager = SoundManager(this)
        
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                GameComposable(soundManager)
            }
        }
    }
}

@Composable
fun GameComposable(soundManager: SoundManager) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        GameScreen(context, soundManager)
    }
}