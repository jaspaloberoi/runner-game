package com.example.test

import com.badlogic.gdx.math.Vector2

class RunnerGame {
    var isGameStarted = false
    var isGameOver = false
    var score = 0
    val bird = Bird()
    private val soundManager = SoundManager()

    companion object {
        const val GRAVITY = 800f
        const val JUMP_VELOCITY = 400f
    }

    fun update(delta: Float, isOrangeState: Boolean) {
        if (!isGameStarted || isGameOver) return

        // Update bird physics
        bird.velocity.y += GRAVITY * delta
        bird.y += bird.velocity.y * delta

        // Apply orange state effects
        if (isOrangeState) {
            bird.velocity.y = maxOf(bird.velocity.y, -JUMP_VELOCITY * 1.5f)  // Increased upward velocity
            bird.velocity.x = minOf(bird.velocity.x + 50f * delta, 200f)  // Increased horizontal speed
        } else {
            bird.velocity.x = minOf(bird.velocity.x + 20f * delta, 100f)  // Normal horizontal speed
        }

        // Update score
        score++
    }

    fun startGame() {
        isGameStarted = true
        isGameOver = false
        score = 0
        bird.reset()
    }

    fun jump(jumpMultiplier: Float = 1f) {
        if (!isGameStarted || isGameOver) return
        
        // Apply jump with multiplier
        bird.velocity.y = -JUMP_VELOCITY * jumpMultiplier
        soundManager.playJump()
    }
}

class Bird {
    var x = 100f
    var y = 240f
    val velocity = Vector2(0f, 0f)

    fun reset() {
        x = 100f
        y = 240f
        velocity.set(0f, 0f)
    }
} 