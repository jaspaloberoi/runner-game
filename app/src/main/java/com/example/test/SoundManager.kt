package com.example.test

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.Gdx

class SoundManager {
    private var jumpSound: Sound? = null
    private var doubleBeepSound: Sound? = null

    init {
        try {
            jumpSound = Gdx.audio.newSound(Gdx.files.internal("jump.wav"))
            doubleBeepSound = Gdx.audio.newSound(Gdx.files.internal("double_beep.wav"))
        } catch (e: Exception) {
            // Handle sound loading errors gracefully
            println("Error loading sounds: ${e.message}")
        }
    }

    fun playJump() {
        try {
            jumpSound?.play()
        } catch (e: Exception) {
            println("Error playing jump sound: ${e.message}")
        }
    }

    fun playDoubleBeep() {
        try {
            doubleBeepSound?.play()
        } catch (e: Exception) {
            println("Error playing double beep sound: ${e.message}")
        }
    }

    fun dispose() {
        try {
            jumpSound?.dispose()
            doubleBeepSound?.dispose()
        } catch (e: Exception) {
            println("Error disposing sounds: ${e.message}")
        }
    }
} 