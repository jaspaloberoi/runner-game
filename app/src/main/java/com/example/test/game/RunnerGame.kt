package com.example.test.game

/**
 * Runner Game - Version 2.2
 * 
 * Core game logic implementation with:
 * - Precise collision detection
 * - Optimized game speed for smooth scrolling
 * - Dynamic obstacle spacing with level-based difficulty adjustment
 * - Moving obstacles with balanced frequency progression based on level
 * - Enhanced bird control: longer taps = higher jumps (1x to 3x based on tap duration)
 * - Orange state power-up with enhanced jump mechanics and increased speed
 * - Color-changing bird mechanic based on tap duration
 * - Faster falling speed (1.5x) for more challenging gameplay
 * - Improved sound management with error handling
 * - Balanced early game progression
 * - Fixed state management for color transitions
 */

import android.content.Context
import android.util.Log
import kotlin.random.Random
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

enum class SoundEffectType {
    JUMP,
    COLLISION,
    SCORE,
    DOUBLE_BEEP
}

class RunnerGame(
    private val context: Context,
    private val soundManager: SoundManager
) {
    private var screenWidth = 0f
    private var screenHeight = 0f
    private var initialized = false
    
    // Game state
    var isPlaying = false
        private set
    private var score = 0
    private var highScore = 0
    private val sharedPreferences = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    
    // Game objects
    private var bird: Bird? = null
    @Volatile
    private var obstacles = mutableListOf<Obstacle>()
    @Volatile
    private var updatedObstacles = mutableListOf<Obstacle>()
    
    // Game physics
    private var gravity = 0f
    private val frameTime = 0.016f // ~60 FPS
    private var gameSpeed = 0f
    private var frameCount = 0
    
    // Obstacle properties
    private var obstacleBaseWidth = 0f
    private var obstacleWideWidth = 0f
    private var obstacleNarrowWidth = 0f
    private var minObstacleHeight = 0f
    private var maxObstacleHeight = 0f
    
    // Level properties
    private var level = 1
    private var obstaclesPassed = 0
    private var groundHeight = 0f
    private var levelSoundStartTime = 0L
    private var isPlayingLevelSound = false
    
    // Jump mechanics
    private var tapStartTime: Long = 0
    private var maxJumpMultiplier = 3.0f  // Increased from 2.0f to 3.0f for more dramatic effect
    private var fallSpeedMultiplier = 1.5f  // Bird falls faster by this factor
    private var isChargingJump = false
    private var chargeStartHeight = 0f
    private var lastChargeTime = 0L
    
    // Obstacle tracking
    private var obstacleCounter = 0
    private var nextMovingObstacleIn = (5..8).random()  // Increased from 3-6 to 5-8 for less frequent moving obstacles
    
    // Variable to hold additional random spacing between obstacles
    private var currentObstacleSpacing = 0f
    
    private var lastObstacleTime = 0L
    
    init {
        Log.d("RunnerGame", "Initializing game with screen size: $screenWidth x $screenHeight")
    }
    
    private fun loadHighScore(): Int {
        return sharedPreferences.getInt("high_score", 0)
    }
    
    private fun saveHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            sharedPreferences.edit().putInt("high_score", score).apply()
        }
    }

    fun initialize(width: Float, height: Float) {
        Log.d("RunnerGame", "Starting initialization with width: $width, height: $height")
        if (width <= 0 || height <= 0) {
            Log.e("RunnerGame", "Invalid dimensions for initialization: $width x $height")
            return
        }
        
        // Clear any existing state
        obstacles.clear()
        
        screenWidth = width
        screenHeight = height
        groundHeight = screenHeight * 0.1f
        
        // Initialize physics with adjusted gravity
        gravity = screenHeight * 0.61f       // Increased from 0.405f (multiplied by 1.5 again)
        gameSpeed = screenWidth * 0.25f      // Adjusted game speed for smooth scrolling
        
        // Initialize obstacle properties
        obstacleBaseWidth = screenWidth * 0.15f
        obstacleWideWidth = obstacleBaseWidth * 1.3f
        obstacleNarrowWidth = obstacleBaseWidth * 0.7f
        minObstacleHeight = screenHeight * 0.2f
        maxObstacleHeight = screenHeight * 0.4f
        
        // Initialize obstacle spacing
        currentObstacleSpacing = getRandomObstacleSpacing()
        
        highScore = loadHighScore()
        
        // Create initial bird
        val birdSize = screenWidth * 0.05f
        val jumpVel = screenHeight * 0.15f  // Increased base jump velocity for more noticeable effect
        bird = Bird(
            x = screenWidth * 0.2f,
            y = screenHeight * 0.4f,
            width = birdSize,
            height = birdSize,
            jumpVelocity = jumpVel,
            // Explicitly set base sizes to match initial size
            baseWidth = birdSize,
            baseHeight = birdSize
        )
        
        // Add initial obstacle, ensuring it's completely off-screen
        val initialObstacle = createObstacle()
        obstacles.add(initialObstacle.copy(x = screenWidth + obstacleBaseWidth))
        
        initialized = true
        isPlaying = false
        
        Log.d("RunnerGame", "Game initialized with properties: gravity=$gravity, gameSpeed=$gameSpeed, obstacleBaseWidth=$obstacleBaseWidth")
        Log.d("RunnerGame", "Bird created at position: (${bird?.x}, ${bird?.y}), size: ${bird?.width}x${bird?.height}, jumpVel: ${bird?.jumpVelocity}")
    }

    private fun stopAllSounds() {
        if (isPlayingLevelSound) {
            soundManager.stopScoreSound()
            isPlayingLevelSound = false
        }
    }

    private fun reset() {
        if (!initialized) {
            Log.e("RunnerGame", "Attempting to reset before initialization")
            return
        }
        
        Log.d("RunnerGame", "Reset called - isPlaying before: $isPlaying")
        
        // Stop any ongoing sounds
        stopAllSounds()
        
        level = 1
        obstaclesPassed = 0
        isPlayingLevelSound = false
        score = 0
        isPlaying = false
        
        // Initialize obstacle spacing
        currentObstacleSpacing = getRandomObstacleSpacing()
        
        Log.d("RunnerGame", "Reset - game state variables set, isPlaying: $isPlaying")
        
        // Reset bird with current scaled properties
        val birdSize = screenWidth * 0.05f
        val jumpVel = screenHeight * 0.15f   // Increased base jump velocity for more noticeable effect
        bird = Bird(
            x = screenWidth * 0.2f,
            y = screenHeight * 0.4f,
            width = birdSize,
            height = birdSize,
            jumpVelocity = jumpVel,
            // Explicitly set base sizes to match initial size
            baseWidth = birdSize,
            baseHeight = birdSize
        )
        
        // Clear obstacles and create a fresh one off-screen
        obstacles.clear()
        val initialObstacle = createObstacle()
        obstacles.add(initialObstacle.copy(x = screenWidth + obstacleBaseWidth))
        
        Log.d("RunnerGame", "Reset - bird created: ${bird != null}")
    }

    private fun createObstacle(): Obstacle {
        val height = minObstacleHeight + Random.nextFloat() * (maxObstacleHeight - minObstacleHeight)
        val isHighObstacle = Random.nextBoolean()
        val y = if (isHighObstacle) {
            0f
        } else {
            screenHeight - groundHeight - height
        }
        
        // Increase chance of moving obstacles
        val movingChance = minOf(0.2f + (level - 1) * 0.1f, 0.8f)  // Up to 80% chance at higher levels
        val isMoving = Random.nextFloat() < movingChance
        val moveSpeed = if (isMoving) {
            screenHeight * (0.2f + Random.nextFloat() * 0.3f)  // Variable movement speed
        } else {
            0f
        }
        
        return Obstacle(
            x = screenWidth + obstacleBaseWidth,
            y = y,
            height = height,
            type = getRandomObstacleType(),
            isMoving = isMoving,
            moveSpeed = moveSpeed,
            textureType = TextureType.values().random()
        )
    }

    private fun getRandomObstacleType(): ObstacleType {
        // More varied obstacle types in higher levels
        return when {
            level >= 3 && Random.nextFloat() < 0.2f -> ObstacleType.SPIKED
            Random.nextFloat() < 0.4f -> ObstacleType.NARROW
            Random.nextFloat() < 0.7f -> ObstacleType.NORMAL
            else -> ObstacleType.WIDE
        }
    }

    private fun generateNewObstacle(): Obstacle {
        obstacleCounter++
        
        // Determine if this should be a moving obstacle
        val isMoving = obstacleCounter >= nextMovingObstacleIn
        
        // If we're creating a moving obstacle, reset the counter and set the next interval
        if (isMoving) {
            obstacleCounter = 0
            // Increase the interval for next moving obstacle based on level
            // Lower levels have more space between moving obstacles
            nextMovingObstacleIn = if (level <= 2) {
                (6..10).random()  // Much less frequent in early levels
            } else if (level <= 4) {
                (5..8).random()   // Less frequent in mid levels
            } else {
                (4..7).random()   // More frequent in higher levels
            }
            Log.d("RunnerGame", "Creating moving obstacle. Next moving obstacle in $nextMovingObstacleIn obstacles")
        }
        
        // Determine obstacle type based on level - more forgiving in early levels
        val typeRandom = Random.nextFloat()
        val type = when {
            // Spiked obstacles appear only after level 3 and gradually increase in frequency
            level >= 5 && typeRandom < 0.3 -> ObstacleType.SPIKED
            level >= 3 && typeRandom < 0.15 -> ObstacleType.SPIKED
            
            // More narrow (easier) obstacles in early levels
            level <= 2 && typeRandom < 0.5 -> ObstacleType.NARROW
            typeRandom < 0.3 -> ObstacleType.NARROW
            
            // Normal obstacles are the most common type
            typeRandom < 0.8 -> ObstacleType.NORMAL
            
            // Wide obstacles are less common in early levels
            else -> ObstacleType.WIDE
        }
        
        // Determine obstacle height
        val height = minObstacleHeight + Random.nextFloat() * (maxObstacleHeight - minObstacleHeight)
        
        // Determine if obstacle comes from top or bottom
        val fromTop = Random.nextFloat() < 0.5
        val y = if (fromTop) 0f else screenHeight - groundHeight - height
        
        // Calculate move speed based on level - reduced speed in early levels
        val moveSpeedFactor = 0.15f + minOf((level - 1) * 0.05f, 0.25f)  // Starts at 15% and increases to max 40%
        val moveSpeed = screenHeight * moveSpeedFactor
        
        return Obstacle(
            x = screenWidth + obstacleBaseWidth, // Ensure obstacle starts completely off-screen
            y = y,
            height = height,
            type = type,
            isMoving = isMoving,
            moveSpeed = moveSpeed,
            movingUp = false
        )
    }

    private fun getRandomObstacleSpacing(): Float {
        // More spacing in earlier levels - increased base spacing
        val baseSpacing = screenWidth * 0.15f  // Increased from 0.05f to 0.15f (15% of screen width)
        
        // Add extra spacing for early levels
        val earlyLevelBonus = if (level <= 2) {
            screenWidth * 0.2f  // Extra 20% of screen width in first two levels
        } else if (level <= 4) {
            screenWidth * 0.1f  // Extra 10% in levels 3-4
        } else {
            0f
        }
        
        val levelAdjustedMax = 0.35f - minOf((level - 1) * 0.03f, 0.2f)  // Increased from 0.25f to 0.35f
        return baseSpacing + earlyLevelBonus + (Random.nextFloat() * screenWidth * levelAdjustedMax)
    }

    private fun updateGame() {
        if (!initialized || bird == null) {
            Log.d("RunnerGame", "Update skipped - game not initialized or bird is null")
            return
        }
        if (!isPlaying) return

        frameCount++

        try {
            // Stop level up sound after 2 seconds (reduced from 4)
            if (isPlayingLevelSound && System.currentTimeMillis() - levelSoundStartTime > 2000) {
                soundManager.stopScoreSound()
                isPlayingLevelSound = false
            }

            // Update bird position with power scaling based on level
            bird?.let { b ->
                // Scale bird's power with level
                val levelBonus = (level - 1) * 0.15f // 15% stronger per level
                val currentGravity = gravity * (1f + levelBonus)
                val currentJumpVel = b.jumpVelocity * (1f + levelBonus)

                // Log bird velocity for debugging
                if (frameCount % 10 == 0) {
                    Log.d("RunnerGame", "Bird physics - velocity: ${b.velocity}, y: ${b.y}")
                }

                // Apply gravity with different multipliers for upward and downward movement
                val gravityMultiplier = if (b.velocity < 0) 0.8f else fallSpeedMultiplier
                b.velocity += currentGravity * frameTime * gravityMultiplier

                // Limit maximum falling speed (scales with level)
                b.velocity = b.velocity.coerceIn(-currentJumpVel * 1.5f, currentGravity * 0.6f * fallSpeedMultiplier)

                b.y += b.velocity * frameTime
                
                // Check ground collision
                if (b.y >= screenHeight - groundHeight - b.height) {
                    b.y = screenHeight - groundHeight - b.height
                    isPlaying = false
                    saveHighScore(score)
                    // Stop any ongoing sounds before playing collision sound
                    stopAllSounds()
                    soundManager.playCollisionSound()
                    return
                }

                // Check ceiling collision with bounce effect
                if (b.y <= 0f) {
                    b.y = 0f
                    b.velocity = if (b.velocity < 0) -b.velocity * 0.5f else 0f
                }
            }

            // Create a new list for updated obstacles
            val newObstacles = mutableListOf<Obstacle>()

            // Update obstacles with current speed and movement
            // Use a fixed speed multiplier for smoother scrolling
            val currentSpeed = gameSpeed * (1f + minOf((level - 1) * 0.15f, 0.6f))
            
            synchronized(obstacles) {
                obstacles.filter { obstacle ->
                    obstacle.x > -obstacleBaseWidth
                }.forEach { obstacle ->
                    // Update moving obstacles
                    var newY = obstacle.y
                    if (obstacle.isMoving) {
                        // Scale movement speed based on level for smoother progression
                        val levelScaledSpeed = obstacle.moveSpeed * (0.5f + (level - 1) * 0.1f)
                        val moveAmount = levelScaledSpeed * frameTime
                        val isFromTop = obstacle.y == 0f

                        if (isFromTop) {
                            // For obstacles from top, move down and up
                            // Reduced movement range, especially in early levels
                            val maxMovementRange = screenHeight * (0.1f + minOf((level - 1) * 0.05f, 0.2f))
                            
                            if (obstacle.movingUp) {
                                newY = max(0f, newY - moveAmount)
                                if (newY <= 0f) {
                                    obstacle.movingUp = false
                                }
                            } else {
                                newY = min(maxMovementRange, newY + moveAmount)
                                if (newY >= maxMovementRange) {
                                    obstacle.movingUp = true
                                }
                            }
                        } else {
                            // For obstacles from bottom, move up and down
                            // Reduced movement range, especially in early levels
                            val movementRange = screenHeight * (0.1f + minOf((level - 1) * 0.05f, 0.2f))
                            val minY = screenHeight - groundHeight - obstacle.height - movementRange
                            val maxY = screenHeight - groundHeight - obstacle.height
                            
                            if (obstacle.movingUp) {
                                newY = max(minY, newY - moveAmount)
                                if (newY <= minY) {
                                    obstacle.movingUp = false
                                }
                            } else {
                                newY = min(maxY, newY + moveAmount)
                                if (newY >= maxY) {
                                    obstacle.movingUp = true
                                }
                            }
                        }
                    }

                    val newX = obstacle.x - (currentSpeed * frameTime)
                    newObstacles.add(
                        obstacle.copy(
                            x = newX,
                            y = newY
                        )
                    )
                }
            }

            // Generate new obstacles with more spacing and randomized gaps
            if (newObstacles.isEmpty() || 
                (newObstacles.isNotEmpty() && newObstacles.last().x < screenWidth * 0.5f)) {
                
                // Update random spacing for next obstacle
                currentObstacleSpacing = getRandomObstacleSpacing()
                
                // Create new obstacle and position it properly
                val newObstacle = generateNewObstacle()
                
                // If there's an existing obstacle, ensure proper spacing
                val xPosition = if (newObstacles.isNotEmpty()) {
                    // Place new obstacle relative to the last one plus spacing
                    val lastObstacle = newObstacles.last()
                    val lastObstacleWidth = getObstacleWidth(lastObstacle.type)
                    max(screenWidth + obstacleBaseWidth, 
                        lastObstacle.x + lastObstacleWidth + currentObstacleSpacing)
                } else {
                    // No obstacles yet, place it off-screen
                    screenWidth + obstacleBaseWidth
                }
                
                newObstacles.add(newObstacle.copy(x = xPosition))
            }

            // Check for collisions and scoring
            var hitObstacle = false
            bird?.let { b ->
                newObstacles.forEach { obstacle ->
                    if (checkCollision(b, obstacle)) {
                        hitObstacle = true
                    } else if (b.x > obstacle.x + getObstacleWidth(obstacle.type) && 
                              !obstacle.passed) {
                        obstaclesPassed++
                        score++

                        // Level up every 10 obstacles
                        if (obstaclesPassed % 10 == 0) {
                            level++
                            // Increase bird levelSizeFactor instead of directly changing width/height
                            b.levelSizeFactor *= 1.2f
                            // Apply the new size
                            updateBirdScale(1.0f)  // Pass 1.0 to just apply the level size factor

                            // Stop any previous level up sound before starting a new one
                            if (isPlayingLevelSound) {
                                soundManager.stopScoreSound()
                            }
                            
                            // Start playing level up sound
                            soundManager.playScoreSound()
                            levelSoundStartTime = System.currentTimeMillis()
                            isPlayingLevelSound = true
                        }

                        obstacle.passed = true
                    }
                }
            }

            if (hitObstacle) {
                isPlaying = false
                saveHighScore(score)
                stopAllSounds()
                soundManager.playCollisionSound()
            }

            // Update the obstacles list atomically
            synchronized(obstacles) {
                obstacles.clear()
                obstacles.addAll(newObstacles)
            }
        } catch (e: Exception) {
            Log.e("RunnerGame", "Error in updateGame: ${e.message}", e)
            // Recover from error by resetting the game
            reset()
        }
    }
    
    fun getObstacleWidth(type: ObstacleType): Float {
        return when (type) {
            ObstacleType.NARROW -> obstacleNarrowWidth
            ObstacleType.NORMAL -> obstacleBaseWidth
            ObstacleType.WIDE -> obstacleWideWidth
            ObstacleType.SPIKED -> obstacleBaseWidth
        }
    }
    
    private fun checkCollision(bird: Bird, obstacle: Obstacle): Boolean {
        val obstacleWidth = getObstacleWidth(obstacle.type)
        
        // Use full bird hitbox instead of reduced size
        val birdLeft = bird.x
        val birdRight = bird.x + bird.width
        val birdTop = bird.y
        val birdBottom = bird.y + bird.height
        
        // Use full obstacle hitbox
        val obstacleLeft = obstacle.x
        val obstacleRight = obstacle.x + obstacleWidth
        val obstacleTop = obstacle.y
        val obstacleBottom = obstacle.y + obstacle.height
        
        // Check if bird is within obstacle's x-range
        if (birdRight <= obstacleLeft || birdLeft >= obstacleRight) {
            return false
        }
        
        // Check if bird collides with obstacle
        return birdBottom >= obstacleTop && birdTop <= obstacleBottom
    }
    
    fun onTapDown() {
        tapStartTime = System.currentTimeMillis()
        
        // Store the bird's current height for charging jump
        bird?.let { b ->
            chargeStartHeight = b.y
            isChargingJump = true
            lastChargeTime = 0L
        }
        
        Log.d("RunnerGame", "Tap down detected - Game state: initialized=$initialized, isPlaying=$isPlaying, tapStartTime=$tapStartTime")
    }
    
    fun updateChargingJump(tapDuration: Long) {
        if (!isPlaying || !isChargingJump) return
        
        // Only update every 50ms to avoid too frequent updates
        if (lastChargeTime > 0 && tapDuration - lastChargeTime < 50) return
        
        lastChargeTime = tapDuration
        
        // Calculate how much to lift the bird based on tap duration
        val normalizedDuration = when {
            tapDuration < 100 -> 0f
            tapDuration > 500 -> 1f
            else -> (tapDuration - 100) / 400f
        }
        
        // Gradually lift the bird as the tap is held
        bird?.let { b ->
            // Calculate target height based on charge time
            val liftAmount = screenHeight * 0.05f * normalizedDuration
            val targetHeight = chargeStartHeight - liftAmount
            
            // Smoothly move toward target height
            b.y = max(0f, b.y - (b.y - targetHeight) * 0.2f)
            
            // Apply upward velocity proportional to charge time
            // Make this more powerful for longer taps (approaching orange state)
            val chargeVelocity = if (normalizedDuration > 0.5f) {
                // More powerful upward boost for longer taps
                -(b.jumpVelocity * 0.5f * normalizedDuration)
            } else {
                -(b.jumpVelocity * 0.3f * normalizedDuration)
            }
            
            b.velocity = min(b.velocity, chargeVelocity)
            
            Log.d("RunnerGame", "Charging jump - duration: ${tapDuration}ms, normalized: $normalizedDuration, lift: $liftAmount, velocity: ${b.velocity}")
        }
    }
    
    fun onTapUp(tapDuration: Long = 0, isOrangeState: Boolean = false) {
        if (!initialized) {
            Log.e("RunnerGame", "Tap received but game not initialized")
            return
        }

        val currentTime = System.currentTimeMillis()
        val actualTapDuration = if (tapDuration > 0) tapDuration else currentTime - tapStartTime
        
        Log.d("RunnerGame", "onTapUp called - isPlaying before: $isPlaying, tapDuration=$actualTapDuration, isOrangeState=$isOrangeState")
        
        // Reset charging jump state
        isChargingJump = false
        
        if (!isPlaying) {
            Log.d("RunnerGame", "Starting game")
            reset()  // Reset the game state
            isPlaying = true  // Set playing state to true
            Log.d("RunnerGame", "isPlaying after set: $isPlaying")
            
            // Ensure bird is properly initialized
            bird?.let { currentBird ->
                // Give initial upward velocity to make the game more playable
                currentBird.velocity = -(currentBird.jumpVelocity * 0.8f)
                soundManager.playJumpSound()
                Log.d("RunnerGame", "Initial jump on game start - velocity: ${currentBird.velocity}, bird position: (${currentBird.x}, ${currentBird.y})")
            } ?: Log.e("RunnerGame", "Bird is null after reset!")
            return
        }
        
        // Calculate jump multiplier with a more dramatic curve
        // For taps under 100ms, use base jump (1.0x)
        // For taps 100-500ms, scale from 1.0x to 3.0x
        val normalizedDuration = when {
            actualTapDuration < 100 -> 0f
            actualTapDuration > 500 -> 1f
            else -> (actualTapDuration - 100) / 400f
        }
        
        // Base jump multiplier from tap duration
        val baseDurationMultiplier = 1.0f + normalizedDuration * maxJumpMultiplier
        
        // In orange state, ALL jumps get the boost regardless of tap duration
        val isOrangeJump = isOrangeState
        val orangeMultiplierBoost = 3.25f
        
        // Apply the appropriate multiplier based on state
        val jumpMultiplier = if (isOrangeJump) {
            // Apply orange state boost to ALL jumps in orange mode
            baseDurationMultiplier + orangeMultiplierBoost
        } else {
            // Normal jump calculation based only on duration
            baseDurationMultiplier
        }
        
        Log.d("RunnerGame", "Jump calculation - tapDuration: ${actualTapDuration}ms, normalized: $normalizedDuration, final multiplier: $jumpMultiplier, isOrangeState: $isOrangeState")
        
        bird?.let { currentBird ->
            val originalVelocity = currentBird.jumpVelocity
            
            // For orange jumps, make them MUCH more powerful
            val newVelocity = if (isOrangeJump) {
                // Super powerful orange jump - with additional multiplier
                -(originalVelocity * jumpMultiplier * 1.95f)
            } else {
                // Normal jump calculation
                -(originalVelocity * jumpMultiplier)
            }
            
            // Apply the jump with the calculated multiplier - ONLY use velocity for smoother physics
            currentBird.velocity = newVelocity
            
            // REMOVED: immediate position boost that was causing the extra jump feeling
            // Now we rely only on velocity for smoother, more natural jumps
            
            // Play appropriate sound based on jump type
            if (isOrangeJump) {
                soundManager.playDoubleBeepSound() // Special sound for orange jumps
            } else {
                soundManager.playJumpSound()
            }
            
            Log.d("RunnerGame", "Bird jump - tap duration: ${actualTapDuration}ms, multiplier: $jumpMultiplier, base velocity: $originalVelocity, final velocity: $newVelocity, isOrangeJump: $isOrangeJump")
        } ?: Log.e("RunnerGame", "Bird is null during jump")
    }
    
    fun getScore(): Int = score
    fun getHighScore(): Int = highScore
    fun update() {
        if (!initialized) {
            Log.d("RunnerGame", "Update skipped - game not initialized")
            return
        }
        if (!isPlaying) {
            Log.d("RunnerGame", "Update skipped - game not playing")
            return
        }
        Log.d("RunnerGame", "Updating game - isPlaying: $isPlaying")
        updateGame()
    }
    
    // Function to handle the orange state effects
    @Suppress("UNUSED_PARAMETER")
    fun update(delta: Float = frameTime, isOrangeState: Boolean = false) {
        if (!initialized || !isPlaying) return
        
        // Apply orange state effects to the bird
        bird?.let { _ ->  // Using underscore for unused parameter
            // In orange state, focus on better jumps rather than gravity changes
            if (isOrangeState) {
                // Don't modify gravity/falling - keep normal physics
                // Just apply a boost when jumps happen (handled in onTapUp)
                
                // Increase the game speed temporarily (this part is working well)
                gameSpeed += screenWidth * 0.0026f // Increased from 0.002f by factor of 1.3
                gameSpeed = minOf(gameSpeed, screenWidth * 0.65f) // Increased from 0.5f by factor of 1.3
            } else {
                // Return game speed to normal gradually when not in orange state
                if (gameSpeed > screenWidth * 0.25f) {
                    gameSpeed -= screenWidth * 0.001f
                    gameSpeed = maxOf(gameSpeed, screenWidth * 0.25f)
                }
            }
        }
        
        // Continue with regular update
        updateGame()
    }
    
    fun getBird(): Bird {
        val birdSize = screenWidth * 0.05f
        val jumpVel = screenHeight * 0.15f  // Increased base jump velocity for more noticeable effect
        return bird ?: Bird(
            x = screenWidth * 0.2f,
            y = screenHeight * 0.4f,
            width = birdSize,
            height = birdSize,
            jumpVelocity = jumpVel
        )
    }
    fun getObstacles(): List<Obstacle> = obstacles
    fun getLevel(): Int = level

    fun applySoundEffect(effectType: SoundEffectType) {
        try {
            when (effectType) {
                SoundEffectType.JUMP -> soundManager.playJumpSound()
                SoundEffectType.COLLISION -> soundManager.playCollisionSound()
                SoundEffectType.SCORE -> soundManager.playScoreSound()
                SoundEffectType.DOUBLE_BEEP -> soundManager.playDoubleBeepSound()
            }
        } catch (e: Exception) {
            Log.e("RunnerGame", "Error playing sound effect: ${e.message}")
        }
    }

    fun start() {
        isPlaying = true
        score = 0
        bird?.x = screenWidth * 0.2f
        bird?.y = screenHeight * 0.4f
        bird?.velocity = 0f
        
        // Clear existing obstacles completely
        obstacles.clear()
        
        // Create a fresh obstacle off-screen
        val initialObstacle = createObstacle()
        obstacles.add(initialObstacle.copy(x = screenWidth + obstacleBaseWidth))
        
        lastObstacleTime = 0L
        
        // Ensure bird levelSizeFactor is reset to 1.0 when starting a new game
        bird?.levelSizeFactor = 1.0f
        updateBirdScale(1.0f)  // Reset the bird's size
        
        Log.d("RunnerGame", "Game start - bird position: (${bird?.x}, ${bird?.y}), obstacles: ${obstacles.size}")
    }

    fun updateBirdScale(scale: Float) {
        bird?.let { b ->
            // Use both the temporary scale factor AND the level-based size increase
            // This ensures we don't lose level-up size increases when transitioning from orange mode
            b.width = b.baseWidth * b.levelSizeFactor * scale
            b.height = b.baseHeight * b.levelSizeFactor * scale
        }
    }
}

data class Bird(
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var velocity: Float = 0f,
    val jumpVelocity: Float,
    // Add base size properties to track original dimensions
    val baseWidth: Float = width,
    val baseHeight: Float = height,
    // Add level-based size tracker
    var levelSizeFactor: Float = 1.0f
) {
    fun jump() {
        velocity = -jumpVelocity
    }
} 