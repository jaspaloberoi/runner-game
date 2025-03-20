package com.example.test.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.util.Log
import com.example.test.R
import kotlin.random.Random

class SoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var jumpSoundId = 0
    private var collisionSoundId = 0
    private var scoreSoundId = 0
    private var jumpSound: MediaPlayer? = null
    private var scoreSound: MediaPlayer? = null
    private var collisionSound: MediaPlayer? = null

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attributes)
            .build()

        jumpSoundId = soundPool?.load(context, R.raw.jump, 1) ?: 0
        collisionSoundId = soundPool?.load(context, R.raw.collision, 1) ?: 0
        scoreSoundId = soundPool?.load(context, R.raw.score, 1) ?: 0

        // Initialize sound effects
        jumpSound = MediaPlayer.create(context, R.raw.jump)
        scoreSound = MediaPlayer.create(context, R.raw.score)
        collisionSound = MediaPlayer.create(context, R.raw.collision)
        
        // Set score sound to loop
        scoreSound?.isLooping = true
    }

    fun playJumpSound() {
        try {
            jumpSound?.let { player ->
                if (player.isPlaying) {
                    player.seekTo(0)
                } else {
                    player.start()
                }
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing jump sound: ${e.message}")
        }
    }

    fun playCollisionSound() {
        try {
            collisionSound?.start()
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing collision sound: ${e.message}")
        }
    }

    fun playScoreSound() {
        try {
            scoreSound?.start()
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing score sound: ${e.message}")
        }
    }

    fun stopScoreSound() {
        try {
            scoreSound?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    player.seekTo(0)
                }
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error stopping score sound: ${e.message}")
        }
    }

    fun playDoubleBeepSound() {
        try {
            // Use the jump sound as a fallback since double beep doesn't exist
            playJumpSound()
            // Play it a second time with a slight delay to create a "double" effect
            // Use a handler to avoid blocking the UI thread
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    playJumpSound()
                } catch (e: Exception) {
                    Log.e("SoundManager", "Error playing second beep: ${e.message}")
                }
            }, 100)
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing double beep sound: ${e.message}")
        }
    }

    fun release() {
        try {
            soundPool?.release()
            
            jumpSound?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
            
            scoreSound?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
            
            collisionSound?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
            
            soundPool = null
            jumpSound = null
            scoreSound = null
            collisionSound = null
        } catch (e: Exception) {
            Log.e("SoundManager", "Error releasing sound resources: ${e.message}")
        }
    }
} 