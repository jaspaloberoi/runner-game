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
        
        // Make sure score sound doesn't loop
        scoreSound?.isLooping = false
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
            scoreSound?.let { player ->
                // Stop any currently playing score sound first
                if (player.isPlaying) {
                    player.pause()
                }
                
                // Disable looping - we don't want continuous playback
                player.isLooping = false
                
                // Reset position and play once
                player.seekTo(0)
                player.start()
                
                // Set a timer to stop the sound after 2 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        // Check if still playing and stop it
                        if (player.isPlaying) {
                            player.pause()
                            player.seekTo(0)
                            Log.d("SoundManager", "Score sound stopped after 2 seconds")
                        }
                    } catch (e: Exception) {
                        Log.e("SoundManager", "Error stopping score sound after timeout: ${e.message}")
                    }
                }, 2000) // 2 seconds
            }
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
            // Play a more distinct sequence to clearly indicate level completion
            soundPool?.play(scoreSoundId, 1.0f, 1.0f, 1, 0, 1.5f)
            
            // Play it again slightly higher pitched after a delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    soundPool?.play(scoreSoundId, 1.0f, 1.0f, 1, 0, 2.0f)
                } catch (e: Exception) {
                    Log.e("SoundManager", "Error playing second beep: ${e.message}")
                }
            }, 200)
            
            // And one final higher pitched sound
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    soundPool?.play(scoreSoundId, 1.0f, 1.0f, 1, 0, 2.5f)
                } catch (e: Exception) {
                    Log.e("SoundManager", "Error playing final beep: ${e.message}")
                }
            }, 400)
            
            Log.d("SoundManager", "Playing LEVEL UP sound sequence")
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing level up sound: ${e.message}")
        }
    }

    fun playGhostDeactivateSound() {
        try {
            // Play a descending sound to indicate mode deactivation
            soundPool?.play(jumpSoundId, 0.7f, 0.7f, 1, 0, 0.8f)
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    soundPool?.play(jumpSoundId, 0.6f, 0.6f, 1, 0, 0.7f)
                } catch (e: Exception) {
                    Log.e("SoundManager", "Error playing ghost deactivate sound: ${e.message}")
                }
            }, 100)
            
            Log.d("SoundManager", "Playing GHOST DEACTIVATE sound")
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing ghost deactivate sound: ${e.message}")
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