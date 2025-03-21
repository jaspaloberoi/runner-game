package com.example.test.game

/**
 * Runner Game - Version 2.5
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
    DOUBLE_BEEP,
    GHOST_ACTIVATE,
    GHOST_DEACTIVATE
}

// Game modes
enum class GameMode {
    NORMAL,    // Yellow
    ORANGE,    // Orange mode (already implemented)
    PINK       // Pink bubble mode
}

enum class TextureType {
    CLASSIC_BRICK,
    LARGE_BLOCKS,
    SMALL_TILES,
    VERTICAL_PLANKS,
    DIAGONAL_BRICKS,
    HEXAGONAL,
    MOSAIC,
    ROUGH_STONE,
    METAL_PLATES,
    WOVEN
}

enum class ObstacleType {
    NARROW,
    NORMAL,
    WIDE,
    SPIKED
}

data class Obstacle(
    val x: Float,
    val y: Float,
    val height: Float,
    val type: ObstacleType,
    var passed: Boolean = false,
    val isMoving: Boolean = false,
    val moveSpeed: Float = 0f,
    var movingUp: Boolean = false
)

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
    
    // Game physics
    private var gravity = 0f
    private var gameSpeed = 0f
    private var baseSpeed = 0f
    
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
    private var fallSpeedMultiplier = 1.5f  // Bird falls faster by this factor
    
    // Obstacle tracking
    private var obstacleCounter = 0
    private var nextMovingObstacleIn = (8..12).random()  // Increased from 5-8 to 8-12 for even less frequent moving obstacles
    
    // Variable to hold additional random spacing between obstacles
    private var currentObstacleSpacing = 0f
    
    private var lastUpdateTime = 0L
    private var lastObstacleTime = 0L
    private var frameCount = 0
    
    private var lastHighScoreSaveTime = 0L
    
    // Mode tracking - explicitly initialize to NORMAL
    private var currentMode = GameMode.NORMAL
    private var pinkModeTimer = 0f
    private val pinkModeDuration = 5000f // 5 seconds in milliseconds
    
    // Speed multiplier
    private var speedMultiplier = 1.0f
    
    // Screen shake effect data
    private var isShaking = false
    private var shakeDuration = 0
    private var shakeIntensity = 5f
    
    init {
        Log.d("RunnerGame", "Initializing game")
        // Force normal mode in constructor with multiple safeguards
        currentMode = GameMode.NORMAL
        speedMultiplier = 1.0f
        pinkModeTimer = 0f  // Reset pink mode timer to ensure it's not active
        
        // Set initial state timestamps to prevent accidental mode activation
        lastUpdateTime = System.currentTimeMillis()
        
        Log.d("RunnerGame", "Mode forced to NORMAL in constructor")
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

    fun initialize(width: Float, height: Float) {
        Log.d("RunnerGame", "Starting initialization with width: $width, height: $height")
        if (width <= 0 || height <= 0) {
            Log.e("RunnerGame", "Invalid dimensions for initialization: $width x $height")
            return
        }
        
        // Reset mode before anything else to ensure proper initialization
        setMode(GameMode.NORMAL, "initialize")
        pinkModeTimer = 0f
        speedMultiplier = 1.0f
        
        // Clear any existing state
        obstacles.clear()
        
        screenWidth = width
        screenHeight = height
        groundHeight = screenHeight * 0.1f
        
        // Initialize physics with adjusted gravity
        gravity = screenHeight * 0.61f       // Increased from 0.405f (multiplied by 1.5 again)
        baseSpeed = screenWidth * 0.25f      // Adjusted game speed for smooth scrolling
        gameSpeed = baseSpeed
        
        // Initialize obstacle properties
        obstacleBaseWidth = screenWidth * 0.15f
        obstacleWideWidth = obstacleBaseWidth * 1.3f
        obstacleNarrowWidth = obstacleBaseWidth * 0.7f
        minObstacleHeight = screenHeight * 0.2f
        maxObstacleHeight = screenHeight * 0.4f
        
        // Initialize obstacle spacing and counters
        currentObstacleSpacing = getRandomObstacleSpacing()
        obstacleCounter = 0
        nextMovingObstacleIn = if (level <= 1) {
            (10..15).random()  // Much fewer moving obstacles at the start
        } else {
            (8..12).random()   // Fewer moving obstacles in general
        }
        Log.d("RunnerGame", "Game initialized with nextMovingObstacleIn=$nextMovingObstacleIn")
        
        highScore = loadHighScore()
        lastHighScoreSaveTime = System.currentTimeMillis()
        lastUpdateTime = System.currentTimeMillis()
        lastObstacleTime = System.currentTimeMillis()
        
        // Create initial bird
        val birdSize = screenWidth * 0.05f
        val jumpVel = screenHeight * 0.15f  // Increased base jump velocity for more noticeable effect
        bird = Bird(
            x = screenWidth * 0.2f,
            y = screenHeight * 0.4f,
            width = birdSize,
            height = birdSize,
            jumpVelocity = jumpVel,
            levelSizeFactor = 1.0f  // Explicit level size factor
        )
        
        // Add initial obstacle, ensuring it's completely off-screen
        val initialObstacle = createObstacle(false)  // Explicitly non-moving for first obstacle
        obstacles.add(initialObstacle.copy(x = screenWidth + obstacleBaseWidth))
        
        initialized = true
        isPlaying = false
        
        // Double check the mode is NORMAL
        if (currentMode != GameMode.NORMAL) {
            Log.w("RunnerGame", "WARNING: Mode is not NORMAL after initialization, forcing it again!")
            setMode(GameMode.NORMAL, "initialize-final-check")
            speedMultiplier = 1.0f
            updateBirdScale(1.0f)
        }
        
        Log.d("RunnerGame", "Game initialized with properties: gravity=$gravity, gameSpeed=$gameSpeed, obstacleBaseWidth=$obstacleBaseWidth")
        Log.d("RunnerGame", "Game initialized in mode: $currentMode")
    }

    private fun stopAllSounds() {
        if (isPlayingLevelSound) {
            soundManager.stopScoreSound()
            isPlayingLevelSound = false
        }
    }

    private fun createObstacle(forcedMoving: Boolean = false): Obstacle {
        val height = minObstacleHeight + Random.nextFloat() * (maxObstacleHeight - minObstacleHeight)
        val isHighObstacle = Random.nextBoolean()
        val y = if (isHighObstacle) {
            0f
        } else {
            screenHeight - groundHeight - height
        }
        
        // Let the caller (generateNewObstacle) determine if this should be moving
        // The forcedMoving parameter ensures we respect the counter-based system
        val isMoving = forcedMoving
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
            moveSpeed = moveSpeed
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
            // Lower levels have much more space between moving obstacles
            nextMovingObstacleIn = if (level <= 1) {
                (12..18).random()  // Extremely rare in level 1
            } else if (level <= 2) {
                (8..14).random()   // Very rare in level 2
            } else if (level <= 4) {
                (6..10).random()   // Less frequent in mid levels
            } else {
                (4..8).random()    // More frequent in higher levels
            }
            Log.d("RunnerGame", "Creating moving obstacle. Next moving obstacle in $nextMovingObstacleIn obstacles")
        }
        
        // Create and return the obstacle, passing the isMoving flag
        return createObstacle(isMoving)
    }
    
    private fun getRandomObstacleSpacing(): Float {
        // Calculate obstacle spacing based on level - more tightly packed at higher levels
        val baseSpacing = screenWidth * 0.4f  // Base spacing
        val levelFactor = 1.0f - (0.05f * (level - 1).coerceIn(0, 10))  // Decreases spacing by 5% per level, max 50%
        val randomVariance = screenWidth * Random.nextFloat() * 0.2f  // Random variance
        return baseSpacing * levelFactor + randomVariance
    }
    
    // Main update function
    private fun updateGame() {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastUpdateTime) / 1000f
        lastUpdateTime = currentTime
        
        // Use speed multiplier from current mode
        val currentSpeed = baseSpeed * speedMultiplier
        
        // Log current speed for debugging
        if (frameCount % 60 == 0) {
            Log.d("RunnerGame", "Current speed: $currentSpeed, multiplier: $speedMultiplier, mode: $currentMode")
        }
        
        // Only update obstacle generation timer when playing
        if (isPlaying) {
            frameCount++
        }
        
        if (currentMode != GameMode.PINK) { // In Pink Mode, bird position is controlled directly by slide
            // Apply gravity
            bird?.let { b ->
                b.velocityY += gravity * deltaTime
                
                // Apply current velocity
                b.y += b.velocityY * deltaTime
                
                // If bird hits bottom
                if (b.y + b.height > screenHeight * 0.9f) {
                    b.y = screenHeight * 0.9f - b.height
                    b.velocityY = 0f
                }
                
                // If bird hits top - allow it to go all the way to the top
                if (b.y < 0f) {
                    b.y = 0f
                    b.velocityY = 0f
                }
            }
        }
        
        // Update obstacle positions
        val updatedObstacles = mutableListOf<Obstacle>()
        for (obstacle in obstacles) {
            // Create new obstacle with updated position
            val newX = obstacle.x - (currentSpeed * deltaTime)
            var newY = obstacle.y
            
            // Handle moving obstacles
            if (obstacle.isMoving) {
                if (obstacle.movingUp) {
                    newY -= obstacle.moveSpeed * deltaTime
                    
                    // Check if we need to change direction
                    if (newY <= screenHeight * 0.1f) {
                        updatedObstacles.add(obstacle.copy(
                            x = newX,
                            y = newY,
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
                    if (newY + obstacle.height >= screenHeight * 0.9f) {
                        updatedObstacles.add(obstacle.copy(
                            x = newX,
                            y = newY,
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
        obstacles.removeAll { it.x + obstacleBaseWidth < 0 }
        
        // Add new obstacles
        if (obstacles.isEmpty() || obstacles.lastOrNull()?.x ?: 0f < screenWidth - currentObstacleSpacing) {
            val newObstacle = generateNewObstacle()
            obstacles.add(newObstacle)
        }
        
        // Check for collisions
        checkCollisions()
        
        // Update score
        updateScore()
        
        // Update pink mode timer
        if (currentMode == GameMode.PINK) {
            pinkModeTimer += deltaTime * 1000 // Convert to milliseconds
            if (pinkModeTimer >= pinkModeDuration) {
                setNormalMode()
            }
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
    
    private fun checkCollisions() {
        bird?.let { b ->
            // Check if bird hits the ground
            if (b.y + b.height >= screenHeight * 0.9f && currentMode != GameMode.PINK) {
                Log.d("RunnerGame", "Ground collision detected")
                isPlaying = false
                saveHighScore(score)
                stopAllSounds()
                soundManager.playCollisionSound()
                // Trigger screen shake effect
                shakeScreen()
                return
            }
            
            // Check obstacle collisions
            for (obstacle in obstacles) {
                if (checkCollision(b, obstacle)) {
                    Log.d("RunnerGame", "Obstacle collision detected")
                    isPlaying = false
                    saveHighScore(score)
                    stopAllSounds()
                    soundManager.playCollisionSound()
                    // Trigger screen shake effect
                    shakeScreen()
                    return
                }
            }
        }
    }
    
    private fun checkCollision(bird: Bird, obstacle: Obstacle): Boolean {
        // Perform bounding box collision detection
        val birdLeft = bird.x
        val birdRight = bird.x + bird.width
        val birdTop = bird.y
        val birdBottom = bird.y + bird.height
        
        val obstacleWidth = getObstacleWidth(obstacle.type)
        val obstacleLeft = obstacle.x
        val obstacleRight = obstacle.x + obstacleWidth
        val obstacleTop = obstacle.y
        val obstacleBottom = obstacle.y + obstacle.height
        
        // Check for horizontal overlap
        val horizontalOverlap = birdRight > obstacleLeft && birdLeft < obstacleRight
        
        // Check for vertical overlap
        val verticalOverlap = birdBottom > obstacleTop && birdTop < obstacleBottom
        
        // Return true if there is both horizontal and vertical overlap
        return horizontalOverlap && verticalOverlap
    }
    
    // Add more reliable scoring that's not tied to game speed
    private fun updateScore() {
        for (obstacle in obstacles) {
            if (!obstacle.passed && obstacle.x + getObstacleWidth(obstacle.type) < bird?.x ?: 0f) {
                // Mark obstacle as passed
                obstacle.passed = true
                obstaclesPassed++
                
                // Increase score
                score++
                soundManager.playScoreSound()
                
                // Level up every 10 obstacles
                if (obstaclesPassed % 10 == 0) {
                    level++
                    
                    // Remove level-based bird size scaling
                    // bird?.let { b ->
                    //     // Increase by 5% per level with a cap of 1.5x
                    //     b.levelSizeFactor = min(b.levelSizeFactor * 1.05f, 1.5f)
                    //     
                    //     // Apply proper mode scaling factors based on current mode
                    //     val modeScaleFactor = when (currentMode) {
                    //         GameMode.NORMAL -> 1.0f
                    //         GameMode.ORANGE -> 1.2f
                    //         GameMode.PINK -> 1.3f
                    //     }
                    //     updateBirdScale(modeScaleFactor)
                    // }
                    
                    // Keep bird size consistent regardless of level
                    bird?.let { b ->
                        // Reset level size factor to ensure no scaling
                        b.levelSizeFactor = 1.0f
                    }
                    
                    // Play level up sound
                    soundManager.playScoreSound()
                    levelSoundStartTime = System.currentTimeMillis()
                    isPlayingLevelSound = true
                    
                    Log.d("RunnerGame", "Level up! Now at level $level")
                }
            }
        }
        
        // Save high score every 10 seconds
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHighScoreSaveTime > 10000) {
            saveHighScore(score)
            lastHighScoreSaveTime = currentTime
        }
    }
    
    fun onTapDown() {
        // Log.d("RunnerGame", "Tap down received")
    }
    
    // Jump with configurable power
    fun jump(powerMultiplier: Float = 1.0f) {
        if (isPlaying && currentMode != GameMode.PINK) {
            // Increase jump power based on multiplier - making sure longer taps jump further
            // Apply appropriate multiplier based on mode
            val effectivePower = when (currentMode) {
                GameMode.ORANGE -> powerMultiplier * 1.2f // Even more power in orange mode
                GameMode.NORMAL -> powerMultiplier * 1.5f // 1.5x higher jumps in normal (yellow) mode
                else -> powerMultiplier
            }
            
            bird?.jump(effectivePower)
            
            if (effectivePower > 1.0f) {
                // Play super jump sound for powered jumps
                soundManager.playJumpSound()
            } else {
                // Regular jump sound
                soundManager.playJumpSound()
            }
            Log.d("RunnerGame", "Jump with power multiplier: $effectivePower (base: $powerMultiplier)")
        }
    }
    
    fun getScore(): Int = score
    
    fun getHighScore(): Int = highScore
    
    fun getBird(): Bird = bird ?: throw IllegalStateException("Bird is not initialized")
    
    fun getObstacles(): List<Obstacle> = obstacles
    
    fun reset() {
        if (!initialized) {
            Log.e("RunnerGame", "Cannot reset - game not initialized")
            return
        }
        
        // Reset all game state in a deliberate and complete order
        Log.d("RunnerGame", "Beginning complete game reset")
        
        // 1. Reset game state and flags
        isPlaying = false
        score = 0
        obstaclesPassed = 0
        obstacleCounter = 0
        nextMovingObstacleIn = (8..12).random()
        level = 1
        
        // 2. Clear existing objects
        obstacles.clear()
        
        // 3. Reset timings
        lastObstacleTime = System.currentTimeMillis()
        lastUpdateTime = System.currentTimeMillis()
        
        // 4. First reset game mode and related parameters
        setMode(GameMode.NORMAL, "reset")  // Force to normal mode
        speedMultiplier = 1.0f
        pinkModeTimer = 0f             // Ensure pink mode timer is reset
        
        // 5. Create fresh bird at the initial position with initial size
        val birdSize = screenWidth * 0.05f
        val jumpVel = screenHeight * 0.15f
        bird = Bird(
            x = screenWidth * 0.2f,
            y = screenHeight * 0.4f,
            width = birdSize,
            height = birdSize,
            jumpVelocity = jumpVel,
            levelSizeFactor = 1.0f  // Explicitly set to 1.0f for proper reset
        )
        
        // 6. Ensure bird is properly configured
        bird?.let { b ->
            b.velocityY = 0f
            b.levelSizeFactor = 1.0f  // Double check reset of level size factor
        }
        
        // 7. Call the mode setter to ensure proper state
        setNormalMode()
        
        // 8. Explicitly set bird scale
        updateBirdScale(1.0f)
        
        // 9. Add initial obstacle off-screen
        val initialObstacle = createObstacle(false)  // Explicitly non-moving for first obstacle
        obstacles.add(initialObstacle.copy(x = screenWidth + obstacleBaseWidth))
        
        // 10. Final verification check
        if (currentMode != GameMode.NORMAL) {
            Log.e("RunnerGame", "CRITICAL: Game mode is still not NORMAL after reset!")
            // Force it one more time
            setMode(GameMode.NORMAL, "reset-final-check")
            speedMultiplier = 1.0f
            updateBirdScale(1.0f)
        }
        
        Log.d("RunnerGame", "Game reset complete - bird size: ${bird?.width}x${bird?.height}, mode: $currentMode, multiplier: $speedMultiplier")
    }
    
    fun start() {
        if (!initialized) {
            Log.e("RunnerGame", "Cannot start - game not initialized")
            return
        }
        
        try {
            if (!isPlaying) {
                reset()
                
                // Completely reset all timers and state variables explicitly here
                // This ensures we always start fresh in Normal mode
                lastUpdateTime = System.currentTimeMillis()
                setMode(GameMode.NORMAL, "start")
                speedMultiplier = 1.0f
                pinkModeTimer = 0f
                
                // Explicitly call the normal mode setter to ensure proper state
                setNormalMode()
                
                // Force the bird scale to be correct
                updateBirdScale(1.0f)
                
                isPlaying = true
                Log.d("RunnerGame", "Game started in mode: $currentMode")
            }
        } catch (e: Exception) {
            Log.e("RunnerGame", "Error during start: ${e.message}", e)
            // Make sure to reset the game state even if there was an error
            isPlaying = false
        }
    }
    
    fun update() {
        if (!initialized) {
            Log.d("RunnerGame", "Update skipped - game not initialized")
            return
        }
        try {
            if (isPlaying) updateGame()
        } catch (e: Exception) {
            Log.e("RunnerGame", "Error during update: ${e.message}", e)
            // Make sure to reset the game state if there was an error
            isPlaying = false
        }
    }
    
    // Handle mode-switching logic
    fun activateOrangeMode() {
        if (!initialized || !isPlaying) return
        if (currentMode == GameMode.PINK) return // Don't downgrade from PINK to ORANGE
        
        Log.d("RunnerGame", "🔄 Mode change: ${currentMode} → ${GameMode.ORANGE} (from: activateOrangeMode)")
        currentMode = GameMode.ORANGE
        
        // Update orange mode speed multiplier to 2.0x
        speedMultiplier = 2.0f
        
        // Bird scaling already removed as requested
        
        Log.d("RunnerGame", "🟠 Orange Mode activated! Speed multiplier: $speedMultiplier")
    }
    
    fun activatePinkMode() {
        if (!initialized || !isPlaying) return
        
        Log.d("RunnerGame", "🔄 Mode change: ${currentMode} → ${GameMode.PINK} (from: activatePinkMode)")
        currentMode = GameMode.PINK
        
        // Update pink mode speed multiplier to 2.0x
        speedMultiplier = 2.0f
        
        // Bird scaling already removed as requested
        
        Log.d("RunnerGame", "🎀 Pink Mode activated! Speed multiplier: $speedMultiplier")
    }
    
    fun setNormalMode() {
        val previousMode = currentMode
        
        setMode(GameMode.NORMAL, "setNormalMode")
        // Reset speed to normal
        speedMultiplier = 1.0f
        
        // Explicitly reset bird scale to the base size (scale factor 1.0)
        updateBirdScale(1.0f)
        
        if (previousMode == GameMode.PINK) {
            // Play sound when exiting Pink Mode
            try {
                soundManager.playDoubleBeepSound()
            } catch (e: Exception) {
                Log.e("RunnerGame", "Error playing Pink Mode deactivation sound: ${e.message}", e)
            }
            Log.d("RunnerGame", "Pink Mode deactivated. Back to Normal Mode.")
        } else if (previousMode == GameMode.ORANGE) {
            Log.d("RunnerGame", "Orange Mode deactivated. Back to Normal Mode.")
        } else {
            Log.d("RunnerGame", "Normal Mode activated. Speed multiplier: $speedMultiplier")
        }
    }

    // Handle vertical slide input for Pink Mode
    fun handleSlideInput(y: Float) {
        if (currentMode == GameMode.PINK && isPlaying) {
            try {
                // Get bird's current position
                bird?.let { b ->
                    // Direct positioning with no smoothing for immediate response
                    val targetY = y - (b.height / 2) // Center the bird on the Y position
                    
                    // Apply bounds checking
                    val topLimit = 0f // Changed from 0.1f * screenHeight to allow going to the very top
                    val bottomLimit = 0.9f * screenHeight - b.height
                    
                    // Directly set position with bounds checking
                    b.y = targetY.coerceIn(topLimit, bottomLimit)
                    
                    Log.d("RunnerGame", "Pink Mode slide: Bird position set to y=${b.y}")
                }
            } catch (e: Exception) {
                Log.e("RunnerGame", "Error in handleSlideInput: ${e.message}", e)
            }
        }
    }
    
    // Getter for the mode
    fun getCurrentMode(): GameMode = currentMode
    
    // Get pink mode progress (0.0f to 1.0f)
    fun getPinkModeProgress(): Float = if (currentMode == GameMode.PINK) {
        pinkModeTimer / pinkModeDuration 
    } else 0f
    
    // Update bird scale based on mode and level
    fun updateBirdScale(modeScaleFactor: Float) {
        bird?.let { b ->
            // Base size is proportional to screen width
            val baseSize = screenWidth * 0.05f
            val levelScaleFactor = b.levelSizeFactor
            val combinedFactor = modeScaleFactor * levelScaleFactor
            
            // Calculate based on base size
            b.width = baseSize * combinedFactor
            b.height = baseSize * combinedFactor
            Log.d("RunnerGame", "Bird scale updated - width: ${b.width}, height: ${b.height}, factors: mode=$modeScaleFactor, level=$levelScaleFactor")
        }
    }

    // Get the visual bubble radius for Pink Mode - used for rendering
    fun getPinkBubbleRadius(): Float {
        return if (currentMode == GameMode.PINK && bird != null) {
            // Bubble is 40% larger than bird's width
            bird!!.width * 1.4f
        } else {
            0f
        }
    }

    // Trigger screen shake effect
    private fun shakeScreen() {
        isShaking = true
        shakeDuration = 10 // Shake for 10 frames
        Log.d("RunnerGame", "Screen shake triggered")
    }
    
    // Get current shake offset for rendering
    fun getShakeOffset(): Pair<Float, Float> {
        if (isShaking && shakeDuration > 0) {
            shakeDuration--
            val offsetX = (Math.random() * 2 - 1) * shakeIntensity
            val offsetY = (Math.random() * 2 - 1) * shakeIntensity
            
            if (shakeDuration <= 0) {
                isShaking = false
            }
            
            return Pair(offsetX.toFloat(), offsetY.toFloat())
        }
        return Pair(0f, 0f)
    }

    // Setter for the mode - with tracking
    private fun setMode(newMode: GameMode, source: String) {
        val oldMode = currentMode
        if (oldMode != newMode) {
            currentMode = newMode
            Log.d("RunnerGame", "🔄 Mode change: $oldMode → $newMode (from: $source)")
        }
    }
}

class Bird(
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var velocity: Float = 0f,
    var velocityY: Float = 0f,
    val jumpVelocity: Float,
    var levelSizeFactor: Float = 1.0f
) {
    fun jump() {
        velocity = -jumpVelocity
        velocityY = velocity
    }
    
    fun jump(powerMultiplier: Float) {
        velocity = -jumpVelocity * powerMultiplier
        velocityY = velocity
    }
} 