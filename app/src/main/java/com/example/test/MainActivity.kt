package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import com.example.test.game.RunnerGame
import com.example.test.game.SoundManager
import com.example.test.game.GameScreen
import com.example.test.ui.theme.TestTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val VERSION = "2.2"
        const val VERSION_CODE = 11
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val soundManager = remember { SoundManager(context) }
            
            TestTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    GameScreen(context = context, soundManager = soundManager)
                }
            }
        }
    }
}