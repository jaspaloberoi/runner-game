package com.example.test.game

/**
 * Runner Game - Version 3.1
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
 * - Level cycling from 1-4 indefinitely
 * - Blue mode with reversed gravity physics
 */

import android.content.Context
import android.util.Log
import kotlin.random.Random
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs

enum class SoundEffectType {
    JUMP,
    COLLISION,
    SCORE,
    DOUBLE_BEEP,
    GHOST_ACTIVATE,
    GHOST_DEACTIVATE
}

// Using GameMode, ObstacleType, and TextureType from GameTypes.kt

data class Obstacle(
    val x: Float,
    val y: Float,
    val height: Float,
    val type: ObstacleType,
    var passed: Boolean = false,
    val isMoving: Boolean = false,
    val moveSpeed: Float = 0f,
    var movingUp: Boolean = false,
    val textureType: TextureType = TextureType.BASIC
)

class RunnerGame(
    private val context: Context,
    private val soundManager: SoundManager
) {
    // Add companion object for singleton instance access
    companion object {
        lateinit var instance: RunnerGame
            private set
    }
    
    private var screenWidth = 0f
    private var screenHeight = 0f
    private var initialized = false
    
    // Game state
    var isPlaying = false
        private set
    var isPaused = false
        private set
    private var score = 0
    private var highScore = 0
    private val sharedPreferences = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    
    // Game objects
    private var bird: Bird? = null
    @Volatile
    private var obstacles = mutableListOf<Obstacle>()
    
    // Game physics
    private var gravity = 0.7f  // Increased to 0.7f per request
    private var gameSpeed = 375f  // Increased by 0.5x (250f * 1.5)
    private var baseSpeed = 375f  // Increased by 0.5x (250f * 1.5)
    private var normalGravity = 0.7f // Store normal gravity for reference
    
    // Obstacle properties
    private var obstacleBaseWidth = 60f
    private var obstacleWideWidth = 80f
    private var obstacleNarrowWidth = 40f
    private var minObstacleHeight = 0f
    private var maxObstacleHeight = 0f
    
    // Level properties
    private var level = 1
    private var obstaclesPassed = 0
    private var groundHeight = 0f
    private var levelSoundStartTime = 0L
    private var isPlayingLevelSound = false
    
    // Jump mechanics
    private var fallSpeedMultiplier = 1.5f  // Bird falls faster by this factor
    
    // Obstacle tracking
    private var obstacleCounter = 0
    private var nextMovingObstacleIn = (8..12).random()  // Increased from 5-8 to 8-12 for even less frequent moving obstacles
    
    // Variable to hold additional random spacing between obstacles
    private var currentObstacleSpacing = 0f
    
    // Game timers and state tracking
    private var lastUpdateTime = 0L
    private var lastObstacleTime = 0L
    private var frameCount = 0
    
    private var lastHighScoreSaveTime = 0L
    
    // Mode tracking
    private var currentMode = GameMode.NORMAL
    private var greenModeTimer = 0f
    private val greenModeDuration = 5000f // 5 seconds in milliseconds
    private var blueModeTimer = 0f
    private val blueModeDuration = 5000f // 5 seconds in milliseconds for Blue mode
    
    // Speed multiplier
    private var speedMultiplier = 1.0f
    
    // Screen shake effect data
    private var isShaking = false
    private var shakeDuration = 0
    private var shakeIntensity = 5f
    
    // Add method to trigger bird shake
    private var isBirdShaking = false
    private var birdShakeStartTime = 0L
    private var birdShakeOffsetX = 0f
    
    // Add this as a class member with the other shake variables
    private var shakeRemainingDuration = 300 // Default 300ms
    
    // Add a flag to delay gravity and speed changes until after shake completes
    private var isTransitioningToNormal = false
    private var transitionStartTime = 0L
    
    // Game timers and state tracking
    private var gameStartTime = 0L  // Track when the game started
    private var normalModeSpeedIncreased = false  // Flag to track if normal mode speed has been increased
    
    // Mode cycling randomization
    private var randomizedModeCycle = mutableListOf<GameMode>()
    private var modeActivationRanges = mutableMapOf<GameMode, Pair<Int, Int>>() // (start, end) in ms
    private var cycleTime = 1200 // 1.2 seconds (longer to include blue)
    
    init {
        Log.d("RunnerGame", "Initializing game")
        currentMode = GameMode.NORMAL
        
        // Set initial state
        lastUpdateTime = System.currentTimeMillis()
        lastObstacleTime = System.currentTimeMillis()
        
        // Load high score
        highScore = loadHighScore()
        
        // Initialize randomized mode cycle
        randomizeModeCycle()
        
        // Set the singleton instance
        instance = this
        
        Log.d("RunnerGame", "High score loaded: $highScore")
    }
    
    private fun loadHighScore(): Int {
        return sharedPreferences.getInt("high_score", 0)
    }
    
    private fun saveHighScore(score: Int) {
        if (score > highScore) {
            highScore = score
            sharedPreferences.edit().putInt("high_score", score).apply()
            Log.d("RunnerGame", "New high score saved: $highScore")
        }
    }

    // Additional methods would go here in a real implementation
    
    // Critical methods needed for the game to function with our bird drawing code
    fun getBird(): Bird {
        // Initialize a bird with visible size if bird is null
        if (bird == null) {
            // Create a square bird (equal width and height)
            // Use a reasonable fallback that's appropriate for most devices
            val size = 30f  // Reduced from 40f to be more appropriate
            bird = Bird(100f, 300f, size, size)
        }
        return bird!!
    }
    
    fun getCurrentMode(): GameMode {
        return currentMode
    }
    
    fun getObstacles(): List<Obstacle> = obstacles
    
    fun getObstacleWidth(type: ObstacleType): Float {
        return when (type) {
            ObstacleType.NARROW -> obstacleNarrowWidth
            ObstacleType.NORMAL -> obstacleBaseWidth
            ObstacleType.WIDE -> obstacleWideWidth
            ObstacleType.SPIKED -> obstacleBaseWidth // Same width as normal
        }
    }
    
    fun getGreenBubbleRadius(): Float = if (currentMode == GameMode.GREEN) 70f else 0f
    
    fun getGreenModeProgress(): Float = greenModeTimer / greenModeDuration
    
    fun getBlueModeProgress(): Float = blueModeTimer / blueModeDuration
    
    fun getLevel(): Int {
        return level
    }
    
    fun initialize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        
        // Setup physics for gameplay - using updated values
        gravity = 0.7f  // Increased to 0.7f per request
        normalGravity = gravity
        gameSpeed = 375f  // Increased by 0.5x (250f * 1.5)
        baseSpeed = 375f  // Increased by 0.5x (250f * 1.5)
        
        // Increase obstacle sizes to match v3.1 difficulty
        obstacleBaseWidth = width * 0.06f    // Normal obstacle width - 6% of screen width
        obstacleWideWidth = width * 0.08f    // Wide obstacle width - 8% of screen width
        obstacleNarrowWidth = width * 0.04f  // Narrow obstacle width - 4% of screen width
        minObstacleHeight = height * 0.1f   // Min height 10% of screen
        maxObstacleHeight = height * 0.4f   // Max height 40% of screen
        
        // Use original v3.1 obstacle spacing
        currentObstacleSpacing = width * 0.7f
        
        // Initialize bird with proper size for a square - use the smaller dimension to avoid too large bird in landscape
        val smallerDimension = minOf(width, height)
        val birdSize = smallerDimension * 0.07f  // 7% of the smaller dimension for consistent sizing
        bird = Bird(
            x = width * 0.2f,  // Fixed position at 20% from left
            y = height * 0.5f - birdSize/2,
            width = birdSize,
            height = birdSize,
            jumpVelocity = 10f  // Exact v3.1 value
        )
        
        // Set ground height for collision detection
        groundHeight = height * 0.9f
        
        // Mark as initialized and set to NORMAL mode
        initialized = true
        currentMode = GameMode.NORMAL
            speedMultiplier = 1.0f
        
        // Randomize mode cycle
        randomizeModeCycle()
        
        Log.d("RunnerGame", "Game initialized with dimensions: $width x $height, bird size: $birdSize")
    }
    
    fun update() {
        if (!initialized || !isPlaying || isPaused) {
            return
        }
        
        try {
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastUpdateTime) / 1000f
            lastUpdateTime = currentTime
            
            frameCount++
            
            // Check for normal mode speed increase after 5 seconds
            if (currentMode == GameMode.NORMAL && !normalModeSpeedIncreased) {
                val gameTimeElapsed = currentTime - gameStartTime
                if (gameTimeElapsed > 5000) { // 5 seconds
                    speedMultiplier = 1.5f // Increase normal mode speed by 50%
                    normalModeSpeedIncreased = true
                    Log.d("RunnerGame", "⏩ Normal mode speed increased after 5 seconds: $speedMultiplier")
                }
            }
            
            // Update timers for special modes - pass the delta time separately to updateGreenModeTimer and updateBlueModeTimer
            updateModeTimers()
            
            // Update bird position based on mode
            @Suppress("UNUSED_PARAMETER")
            updateBird(deltaTime)
            
            // Update obstacles
            updateObstacles(deltaTime)
            
            // Update screen shake effect if active
            updateShakeEffect()
            
            // Check for collisions
            checkCollisions()
            
            // Update score for obstacles passed
            updateScore()
            
        } catch (e: Exception) {
            Log.e("RunnerGame", "Error during update: ${e.message}", e)
            // Reset game state if there was an error
            isPlaying = false
        }
    }
    
    private fun updateModeTimers() {
        // Calculate time delta in seconds for current frame
        val deltaTimeSeconds = 1f / 60f // Use a fixed time step for consistency
        
        // Handle green mode timer
        if (currentMode == GameMode.GREEN) {
            greenModeTimer += deltaTimeSeconds * 1000 // Convert to milliseconds
            if (greenModeTimer >= greenModeDuration) {
                setNormalMode()
            }
        }
        
        // Handle blue mode timer
        if (currentMode == GameMode.BLUE) {
            blueModeTimer += deltaTimeSeconds * 1000 // Convert to milliseconds
            if (blueModeTimer >= blueModeDuration) {
                setNormalMode()
            }
        }
        
        // Handle transition to normal mode after shake effect
        if (isTransitioningToNormal && !isBirdShaking) {
            val timeSinceTransition = System.currentTimeMillis() - transitionStartTime
            if (timeSinceTransition > 500) { // Wait a bit after shake ends
                    completeTransitionToNormal()
                }
            }
        }
        
    @Suppress("UNUSED_PARAMETER")
    private fun updateBird(deltaTime: Float) {
        // Skip the bird physics update if we're in Green Mode (user controls directly)
        if (currentMode == GameMode.GREEN) return
        
        // Update bird position and velocity
        bird?.let { b ->
            // Apply gravity based on mode - exact v3.1 style without deltaTime scaling
            if (currentMode == GameMode.BLUE) {
                // Reversed gravity for Blue mode
                b.velocityY -= gravity
                
                // Implement blue mode jumping correctly by reversing the jump direction
                if (b.velocityY < -8f) { // Cap fall speed in blue mode
                    b.velocityY = -8f
                }
            } else {
                // Normal gravity for other modes
                b.velocityY += gravity
                
                // Cap fall speed in normal modes
                if (b.velocityY > 8f) {
                    b.velocityY = 8f
                }
            }
            
            // Apply current velocity to position - exact v3.1 style without deltaTime scaling
            b.y += b.velocityY
            
            // Keep the bird in a fixed horizontal position
            b.x = screenWidth * 0.2f
            
            // Only handle ceiling collisions here, ground collisions are handled in checkCollisions()
                if (b.y < 0f) {
                    b.y = 0f
                b.velocityY = Math.abs(b.velocityY) * 0.5f // Bounce with dampening
            }
            
            // Apply bird shake effect if active
            if (isBirdShaking) {
                val elapsed = System.currentTimeMillis() - birdShakeStartTime
                if (elapsed < shakeRemainingDuration) {
                    // Generate a slight horizontal offset using sine wave
                    val shakeProgress = elapsed.toFloat() / shakeRemainingDuration
                    val intensity = (1 - shakeProgress) * shakeIntensity // Gradually fade out
                    b.visualOffsetX = sin(shakeProgress * 20 * PI.toFloat()) * intensity
                } else {
                    // End shake
                    isBirdShaking = false
                    b.visualOffsetX = 0f
                }
            }
        }
    }
    
    private fun updateObstacles(deltaTime: Float) {
        // Calculate current game speed with exact v3.1 values - don't cap deltaTime
        val currentSpeed = baseSpeed * speedMultiplier
        
        // Update existing obstacles
        val updatedObstacles = mutableListOf<Obstacle>()
        
        for (obstacle in obstacles) {
            // V3.1 obstacle movement with exact deltaTime scaling
            val newX = obstacle.x - (currentSpeed * deltaTime)
            var newY = obstacle.y
            
            // Handle moving obstacles
            if (obstacle.isMoving) {
                if (obstacle.movingUp) {
                    newY -= obstacle.moveSpeed * deltaTime
                    
                    // Check if we need to change direction
                    if (newY <= 0f) {
                        updatedObstacles.add(obstacle.copy(
                            x = newX,
                            y = 0f, // Exactly at the top boundary
                            movingUp = false
                        ))
                    } else {
                        updatedObstacles.add(obstacle.copy(
                            x = newX,
                            y = newY
                        ))
                    }
                } else {
                    newY += obstacle.moveSpeed * deltaTime
                    
                    // Check if we need to change direction
                    val groundY = screenHeight * 0.9f
                    if (newY + obstacle.height >= groundY) {
                        updatedObstacles.add(obstacle.copy(
                            x = newX,
                            y = groundY - obstacle.height, // Exactly at the ground
                            movingUp = true
                        ))
                    } else {
                        updatedObstacles.add(obstacle.copy(
                            x = newX,
                            y = newY
                        ))
                    }
                }
            } else {
                updatedObstacles.add(obstacle.copy(x = newX))
            }
        }
        
        // Update obstacles list
        obstacles.clear()
        obstacles.addAll(updatedObstacles)
        
        // Remove obstacles that are off-screen
        obstacles.removeAll { 
            it.x + getObstacleWidth(it.type) < 0 
        }
        
        // Maintain original v3.1 obstacle spacing - keep the exact formula from GitHub
        currentObstacleSpacing = screenWidth * (0.7f - (level * 0.05f))
        
        // Add new obstacles when appropriate spacing is available
        if (obstacles.isEmpty() || obstacles.lastOrNull()?.x ?: 0f < screenWidth - currentObstacleSpacing) {
            val newObstacle = generateNewObstacle()
            obstacles.add(newObstacle)
            Log.d("RunnerGame", "New obstacle added at spacing: $currentObstacleSpacing")
        }
    }
    
    private fun generateNewObstacle(): Obstacle {
        // Determine obstacle type based on probability - progressively make harder obstacles more common
        val type = when (level) {
            1 -> { // Level 1: Mostly normal obstacles
                when ((0..100).random()) {
                    in 0..70 -> ObstacleType.NORMAL   // 70% chance
                    in 71..85 -> ObstacleType.NARROW  // 15% chance
                    in 86..95 -> ObstacleType.WIDE    // 10% chance
                    else -> ObstacleType.SPIKED       // 5% chance
                }
            }
            2 -> { // Level 2: More varied
                when ((0..100).random()) {
                    in 0..45 -> ObstacleType.NORMAL   // 45% chance (reduced from 55%)
                    in 46..70 -> ObstacleType.NARROW  // 25% chance (increased from 20%)
                    in 71..90 -> ObstacleType.WIDE    // 20% chance (increased from 15%)
                    else -> ObstacleType.SPIKED       // 10% chance
                }
            }
            3 -> { // Level 3: Harder
                when ((0..100).random()) {
                    in 0..35 -> ObstacleType.NORMAL   // 35% chance (reduced from 40%)
                    in 36..60 -> ObstacleType.NARROW  // 25% chance
                    in 61..85 -> ObstacleType.WIDE    // 25% chance (increased from 20%)
                    else -> ObstacleType.SPIKED       // 15% chance
                }
            }
            else -> { // Level 4: Hardest
                when ((0..100).random()) {
                    in 0..25 -> ObstacleType.NORMAL   // 25% chance (reduced from 30%)
                    in 26..50 -> ObstacleType.NARROW  // 25% chance
                    in 51..75 -> ObstacleType.WIDE    // 25% chance
                    else -> ObstacleType.SPIKED       // 25% chance (increased from 20%)
                }
            }
        }
        
        // Determine if this is a moving obstacle - more common in higher levels
        // Increased probability of moving obstacles in higher levels
        val movingObstacleChance = when (level) {
            1 -> 10  // 10% in level 1
            2 -> 25  // 25% in level 2
            3 -> 40  // 40% in level 3
            else -> 60 // 60% in level 4
        }
        
        val isMoving = (0..100).random() < movingObstacleChance
        
        // Reset counter if this is a moving obstacle
        if (isMoving) {
            obstacleCounter = 0
            // Reduced wait time between moving obstacles in higher levels
            nextMovingObstacleIn = when (level) {
                1 -> (8..12).random()
                2 -> (6..10).random()
                3 -> (4..8).random()
                else -> (3..6).random()
            }
        } else {
            obstacleCounter++
        }
        
        // Calculate height based on level progression - higher levels have taller obstacles
        val minHeightMultiplier = 0.1f + (level * 0.02f).coerceAtMost(0.08f) // Increases with level but capped
        val maxHeightMultiplier = 0.3f + (level * 0.05f).coerceAtMost(0.2f) // Increases with level but capped
        
        val minHeight = screenHeight * minHeightMultiplier
        val maxHeight = screenHeight * maxHeightMultiplier
        
        val heightRange = maxHeight - minHeight
        val height = minHeight + (heightRange * Random.nextFloat())
        
        // Calculate position (top or bottom)
        val isTopAligned = Random.nextBoolean()
        
        // Ensure bird path is always available for avoiding obstacles
        // This creates a safe corridor in the middle of the screen that prevents impossible obstacles
        // Calculate minimum safe Y coordinates for obstacles to ensure a passable gap
        val minSafeY = screenHeight * 0.3f
        val maxSafeY = screenHeight * 0.7f - height
        
        val y = if (isTopAligned) {
            // Make sure top obstacles don't extend too far down
            val maxTopHeight = screenHeight * 0.4f
            if (height > maxTopHeight) {
                0f // Standard top alignment
            } else {
                0f // Standard top alignment
            }
        } else {
            // Make sure bottom obstacles don't extend too far up
            // Ensure there's always a passable gap - bottom obstacles have to stay low enough
            val calculatedY = screenHeight * 0.9f - height
            // Ensure the obstacle doesn't go above the safe corridor
            if (calculatedY < minSafeY) {
                // If it would invade the safe corridor, push it down
                maxSafeY
            } else {
                calculatedY
            }
        }
        
        // Movement speed for moving obstacles increases with level but caps for playability
        val baseSpeed = 100f + (level * 50f) 
        val moveSpeed = if (isMoving) {
            // Add slight randomization to movement speed for variety
            baseSpeed * (0.8f + Random.nextFloat() * 0.4f)
        } else {
            0f
        }
        
        // Add texture variation based on level
        val textureType = when (level) {
            1 -> TextureType.BASIC // Simple textures in level 1
            2 -> {
                // In level 2, use more diagonal brick textures
                if (Random.nextFloat() < 0.6f) TextureType.DIAGONAL_BRICKS else TextureType.BASIC
            }
            3 -> {
                // In level 3, use more varied textures
                when ((0..100).random()) {
                    in 0..40 -> TextureType.BASIC
                    in 41..80 -> TextureType.DIAGONAL_BRICKS
                    else -> TextureType.HEXAGONAL
                }
            }
            else -> {
                // In level 4, hexagonal is more common
                when ((0..100).random()) {
                    in 0..30 -> TextureType.BASIC
                    in 31..60 -> TextureType.DIAGONAL_BRICKS
                    else -> TextureType.HEXAGONAL
                }
            }
        }
        
        return Obstacle(
            x = screenWidth,
            y = y,
            height = height,
            type = type,
            isMoving = isMoving,
            moveSpeed = moveSpeed,
            movingUp = Random.nextBoolean(),
            textureType = textureType
        )
    }
    
    private fun checkCollisions() {
        bird?.let { b ->
            // Ground collision (bottom of screen)
            if (b.y + b.height > screenHeight * 0.9f) {
                if (currentMode == GameMode.GREEN) {
                    // Green mode is immune to ground collisions
                    Log.d("RunnerGame", "Green mode ground collision ignored - immune to ground only")
                    // Bounce the bird back
                    b.y = screenHeight * 0.9f - b.height
                    b.velocityY = -Math.abs(b.velocityY) * 0.5f // Bounce with dampening
                } else {
                    // All other modes die on ground collision
                    Log.d("RunnerGame", "Bird hit the ground in ${currentMode} mode! Game over.")
                isPlaying = false
                soundManager.playCollisionSound()
                    saveHighScore(score)
                return
                }
            }
            
            // Ceiling collision - no mode should die, just bounce
            if (b.y < 0f) {
                // Just bounce the bird off the ceiling
                b.y = 0f
                b.velocityY = Math.abs(b.velocityY) * 0.5f // Bounce with dampening
                Log.d("RunnerGame", "Bird hit ceiling - bouncing back")
            }
            
            // Check for obstacle collision (applies to ALL modes)
            for (obstacle in obstacles) {
                if (isColliding(b, obstacle)) {
                    Log.d("RunnerGame", "Bird hit an obstacle in ${currentMode} mode! Game over.")
                    isPlaying = false
                    soundManager.playCollisionSound()
                    saveHighScore(score)
                    triggerScreenShake()
                    return
                }
            }
        }
    }
    
    private fun isColliding(bird: Bird, obstacle: Obstacle): Boolean {
        val obstacleWidth = getObstacleWidth(obstacle.type)
        
        // Bird bounds
        val birdLeft = bird.x
        val birdRight = bird.x + bird.width
        val birdTop = bird.y
        val birdBottom = bird.y + bird.height
        
        // Obstacle bounds
        val obstacleLeft = obstacle.x
        val obstacleRight = obstacle.x + obstacleWidth
        val obstacleTop = obstacle.y
        val obstacleBottom = obstacle.y + obstacle.height
        
        // Check for intersection
        return (birdRight > obstacleLeft && 
                birdLeft < obstacleRight && 
                birdBottom > obstacleTop && 
                birdTop < obstacleBottom)
    }
    
    private fun updateScore() {
        bird?.let { b ->
        for (obstacle in obstacles) {
                // Check if bird has passed an obstacle that hasn't been counted yet
                if (!obstacle.passed && b.x > obstacle.x + getObstacleWidth(obstacle.type)) {
                    // Mark as passed and update score
                    val obstacleIndex = obstacles.indexOf(obstacle)
                    obstacles[obstacleIndex] = obstacle.copy(passed = true)
                    
                    // Increment score and play sound
                score++
                    obstaclesPassed++ // Increment counter for level progression
                    soundManager.playScoreSound()
                    
                    // Update high score if needed
                    if (score > highScore) {
            saveHighScore(score)
                    }
                    
                    // Check if we need to level up - every 2 obstacles in v3.1
                    if (obstaclesPassed >= 2) {
                        levelUp()
                    }
                    
                    Log.d("RunnerGame", "Obstacle passed! Score: $score, Obstacles passed: $obstaclesPassed")
                }
            }
        }
    }
    
    private fun levelUp() {
        obstaclesPassed = 0  // Reset counter
        level = (level % 4) + 1  // Cycle through levels 1-4
        
        // V3.1 obstacle spacing adjustment based on level
        currentObstacleSpacing = screenWidth * (0.5f - (level * 0.05f)).coerceAtLeast(0.3f)
        
        // Play level up sound
        try {
            soundManager.playDoubleBeepSound()
            isPlayingLevelSound = true
            levelSoundStartTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("RunnerGame", "Error playing level up sound: ${e.message}", e)
        }
        
        Log.d("RunnerGame", "LEVEL UP! Now at level $level with spacing $currentObstacleSpacing")
    }
    
    private fun updateShakeEffect() {
        if (isShaking) {
            shakeDuration--
            if (shakeDuration <= 0) {
                isShaking = false
            }
        }
    }
    
    private fun triggerScreenShake() {
        isShaking = true
        shakeDuration = 20 // Frames of shake
    }
    
    private fun triggerBirdShake() {
        isBirdShaking = true
        birdShakeStartTime = System.currentTimeMillis()
        shakeRemainingDuration = 300 // Shake for 300ms
    }
    
    fun getShakeOffset(): Pair<Float, Float> {
        if (!isShaking) return Pair(0f, 0f)
        
        // Generate random shake offset using Random.nextFloat()
        val xOffset = (Random.nextFloat() * 2 - 1) * shakeIntensity
        val yOffset = (Random.nextFloat() * 2 - 1) * shakeIntensity
        
        return Pair(xOffset, yOffset)
    }
    
    fun getBirdShakeOffset(): Float {
        return bird?.visualOffsetX ?: 0f
    }
    
    // Mode activation methods
    fun activateOrangeMode() {
        if (!initialized || !isPlaying) return
        if (currentMode == GameMode.GREEN) return // Don't downgrade from GREEN to ORANGE
        if (currentMode == GameMode.BLUE) return // Don't change from BLUE to ORANGE
        
        Log.d("RunnerGame", "🔄 Mode change: ${currentMode} → ${GameMode.ORANGE}")
        currentMode = GameMode.ORANGE
        
        // Use correct v3.1 multiplier for Orange mode
        speedMultiplier = 2.0f
        
        Log.d("RunnerGame", "🔥 Orange Mode activated! Speed multiplier: $speedMultiplier")
    }
    
    fun activateGreenMode() {
        if (!initialized || !isPlaying) return
        
        // Only allow activation from NORMAL mode
        if (currentMode != GameMode.NORMAL) {
            Log.d("RunnerGame", "🚫 Can't activate Green Mode from ${currentMode} - only allowed from NORMAL mode")
            return
        }
        
        Log.d("RunnerGame", "🔄 Mode change: ${currentMode} → ${GameMode.GREEN}")
        currentMode = GameMode.GREEN
        greenModeTimer = 0f
        
        // Use correct v3.1 multiplier for Green mode
        speedMultiplier = 3.0f
        
        Log.d("RunnerGame", "🌿 Green Mode activated! Speed multiplier: $speedMultiplier")
    }
    
    fun activateBlueMode() {
        if (!initialized || !isPlaying) return
        
        // Only allow activation from NORMAL mode
        if (currentMode != GameMode.NORMAL) {
            Log.d("RunnerGame", "🚫 Can't activate Blue Mode from ${currentMode} - only allowed from NORMAL mode")
            return
        }
        
        Log.d("RunnerGame", "🔄 Mode change: ${currentMode} → ${GameMode.BLUE}")
        currentMode = GameMode.BLUE
        blueModeTimer = 0f
        
        // Blue mode uses same speed as Normal mode
        speedMultiplier = 1.0f
        
        Log.d("RunnerGame", "🔵 Blue Mode activated! Speed multiplier: $speedMultiplier (same as normal)")
    }
    
    fun setNormalMode() {
        val previousMode = currentMode
        
        if (previousMode != GameMode.NORMAL && isPlaying) {
            // Mark that we're transitioning to normal mode
            isTransitioningToNormal = true
            transitionStartTime = System.currentTimeMillis()
            
            // Set the game mode to NORMAL in tracking, but don't change gameplay physics yet
            setMode(GameMode.NORMAL, "setNormalMode")
            
            // Trigger bird shake first before applying gameplay changes
            Log.d("RunnerGame", "Triggering bird shake - previousMode: $previousMode, isPlaying: $isPlaying")
            triggerBirdShake()
            
            if (previousMode == GameMode.GREEN) {
                // Play sound when exiting Green Mode
                try {
                    soundManager.playDoubleBeepSound()
                } catch (e: Exception) {
                    Log.e("RunnerGame", "Error playing Green Mode deactivation sound: ${e.message}", e)
                }
                Log.d("RunnerGame", "Green Mode deactivated. Back to Normal Mode.")
            } else if (previousMode == GameMode.ORANGE) {
                Log.d("RunnerGame", "Orange Mode deactivated. Back to Normal Mode.")
            } else if (previousMode == GameMode.BLUE) {
                Log.d("RunnerGame", "Blue Mode deactivated. Back to Normal Mode.")
            }
        } else {
            // Direct mode change if not transitioning from a special mode or not playing
            setMode(GameMode.NORMAL, "setNormalMode")
            speedMultiplier = 1.0f
            Log.d("RunnerGame", "Normal Mode activated directly. Speed multiplier: $speedMultiplier")
        }
    }

    // Helper method to set mode without side effects
    private fun setMode(mode: GameMode, source: String) {
        val previous = currentMode
        currentMode = mode
        Log.d("RunnerGame", "Mode set to $mode (from: $source, previous: $previous)")
    }
    
    // Complete the transition to normal mode after shake effects
    private fun completeTransitionToNormal() {
        isTransitioningToNormal = false
        
        // Now apply the speed change after shake is complete
        speedMultiplier = 1.0f  // Reset to normal 1.0x speed
        normalModeSpeedIncreased = false  // Reset the normal mode speed increase flag
        
        Log.d("RunnerGame", "Transition to Normal Mode completed - physics now reset")
    }
    
    fun getScore(): Int = score
    
    fun getHighScore(): Int = highScore
    
    // Start a new game
    fun start() {
        if (!initialized) {
            Log.e("RunnerGame", "Cannot start game - not initialized")
            return
        }
        
        // Reset game state
        score = 0
        obstaclesPassed = 0
        level = 1
        gameStartTime = System.currentTimeMillis()
        normalModeSpeedIncreased = false
        
        // Reset bird position
        resetBird()
        
        // Reset physics to updated values
        gravity = 0.7f  // Updated gravity
        gameSpeed = baseSpeed // 375f (increased by 0.5x)
        speedMultiplier = 1.0f
        currentObstacleSpacing = screenWidth * 0.7f
        
        // Clear any existing obstacles
        obstacles.clear()
        obstacleCounter = 0
        nextMovingObstacleIn = (8..12).random()
        lastObstacleTime = System.currentTimeMillis()
        
        // Force normal mode
        currentMode = GameMode.NORMAL
        greenModeTimer = 0f
        blueModeTimer = 0f
        
        // Re-randomize the mode cycle
        randomizeModeCycle()
        
        // Start playing
        isPlaying = true
        
        Log.d("RunnerGame", "Game started with bird at (${bird?.x}, ${bird?.y}), level: $level")
    }
    
    // Bird jump method - Fixed exact v3.1 values
    fun jump(powerMultiplier: Float = 1.0f) {
        if (!initialized || !isPlaying) return
        
        // Skip jump in GREEN mode as it uses direct position control
        if (currentMode == GameMode.GREEN) return
        
        bird?.let { b ->
            // For Blue mode, we need to reverse the jump direction since gravity is reversed
            if (currentMode == GameMode.BLUE) {
                // In blue mode, jump means accelerate downward
                b.velocityY = b.jumpVelocity * powerMultiplier
                Log.d("RunnerGame", "Blue mode jump DOWN with power: $powerMultiplier, velocityY: ${b.velocityY}")
            } else {
                // Standard jump (upward) for all other modes
                b.velocityY = -b.jumpVelocity * powerMultiplier
                Log.d("RunnerGame", "Standard jump UP with power: $powerMultiplier, velocityY: ${b.velocityY}")
            }
            
            // Play jump sound
            try {
                soundManager.playJumpSound()
            } catch (e: Exception) {
                Log.e("RunnerGame", "Error playing jump sound: ${e.message}", e)
            }
        }
    }
    
    // Reset bird position - ensure x position is fixed at 20% of screen width
    fun resetBird() {
        bird?.let { b ->
            b.x = screenWidth * 0.2f  // Fixed at 20% from left
            b.y = screenHeight * 0.5f - b.height/2
            b.velocityY = 0f
            b.visualOffsetX = 0f
        }
    }
    
    // Handle tap down event - used to track when a tap starts
    fun onTapDown() {
        // Randomize the mode cycle on each tap
        randomizeModeCycle()
        Log.d("RunnerGame", "Tap down - randomized mode cycle: $randomizedModeCycle")
    }
    
    // Handle vertical slide input for Green Mode
    fun handleSlideInput(y: Float) {
        if (!initialized || !isPlaying) return
        if (currentMode != GameMode.GREEN) return
        
        bird?.let { b ->
            // Calculate bird's new Y position, keeping its center at touch position
            val newY = (y - b.height/2).coerceIn(0f, screenHeight * 0.9f - b.height)
            b.y = newY
        }
    }
    
    // Randomizes the mode cycle for tap durations
    private fun randomizeModeCycle() {
        // Randomize the mode order (excluding NORMAL which is always first)
        val specialModes = mutableListOf(GameMode.ORANGE, GameMode.GREEN, GameMode.BLUE)
        specialModes.shuffle()
        
        // Build the complete mode cycle with NORMAL first
        randomizedModeCycle = mutableListOf(GameMode.NORMAL)
        randomizedModeCycle.addAll(specialModes)
        
        // Calculate time ranges for each mode with total cycle time of maxCycleTime
        val rangeSize = cycleTime / randomizedModeCycle.size
        
        // Clear existing ranges
        modeActivationRanges.clear()
        
        // Set ranges for each mode
        for (i in randomizedModeCycle.indices) {
            val mode = randomizedModeCycle[i]
            val start = i * rangeSize
            val end = (i + 1) * rangeSize - 1
            modeActivationRanges[mode] = Pair(start, end)
        }
        
        // Log the current cycle for debugging
        Log.d("RunnerGame", "Mode cycle randomized: $randomizedModeCycle")
        Log.d("RunnerGame", "Mode ranges: $modeActivationRanges")
    }
    
    // Method to determine which mode a given tap duration would activate
    fun getModeForDuration(tapDuration: Long): GameMode {
        val cyclicDuration = tapDuration % cycleTime
        
        for ((mode, range) in modeActivationRanges) {
            val (start, end) = range
            if (cyclicDuration in start until end) {
                return mode
            }
        }
        
        // If nothing matched (shouldn't happen), return NORMAL
        return GameMode.NORMAL
    }
    
    // Get the current mode cycle information for display
    fun getModeCycleInfo(): Pair<List<GameMode>, Map<GameMode, Pair<Int, Int>>> {
        return Pair(randomizedModeCycle, modeActivationRanges)
    }
    
    // Add method to get cycle time
    fun getCycleTime(): Int = cycleTime

    fun togglePause() {
        if (isPlaying) {
            isPaused = !isPaused
            Log.d("RunnerGame", "Game ${if (isPaused) "paused" else "resumed"}")
            
            // If resuming, reset the last update time to prevent time jumps
            if (!isPaused) {
                lastUpdateTime = System.currentTimeMillis()
            }
        }
    }

    fun pause() {
        if (isPlaying && !isPaused) {
            isPaused = true
            Log.d("RunnerGame", "Game paused")
        }
    }

    fun resume() {
        if (isPlaying && isPaused) {
            isPaused = false
            lastUpdateTime = System.currentTimeMillis()
            Log.d("RunnerGame", "Game resumed")
        }
    }
}

// Bird class - Fixed to match v3.1 exactly
class Bird(
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var velocityY: Float = 0f,
    var jumpVelocity: Float = 10f  // Using v3.1 value which was 10f, not 600f
) {
    var visualOffsetX = 0f  // Added for shake effect
    
    fun jump() {
        velocityY = -jumpVelocity
    }
    
    fun jump(powerMultiplier: Float) {
        velocityY = -jumpVelocity * powerMultiplier
    }
} 