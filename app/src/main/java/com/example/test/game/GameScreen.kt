package com.example.test.game

/**
 * Runner Game - Version 3.1
 * 
 * Features:
 * - Smooth scrolling with key-based Canvas recomposition
 * - Precise collision detection with full hitboxes
 * - Screen shake effect on collision
 * - Optimized rendering for better performance
 * - Moving obstacles appear every 3-6 obstacles
 * - Enhanced bird control: longer taps = higher jumps (1x to 3x based on tap duration)
 * - Orange state power-up activated by holding tap for more than 0.2 seconds
 * - Super-powered jumps and increased game speed in orange state
 * - Color-changing bird mechanic based on tap duration and state
 * - Faster falling speed (1.5x) for more challenging gameplay
 * - Visual tap duration indicator
 * - Bug fixes for color transitions and state management
 * - Level cycling from 1-4 with themed backgrounds
 * - Improved mode activation logic
 */

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Path
import kotlin.math.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.example.test.MainActivity
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.rotate
import android.content.Context
import android.graphics.Paint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlin.random.Random
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

// Color helper functions
fun ComposeColor.darker(factor: Float): ComposeColor {
    return ComposeColor(
        red = red * (1 - factor),
        green = green * (1 - factor),
        blue = blue * (1 - factor),
        alpha = alpha
    )
}

fun ComposeColor.lighter(factor: Float): ComposeColor {
    return ComposeColor(
        red = red + (1 - red) * factor,
        green = green + (1 - green) * factor,
        blue = blue + (1 - blue) * factor,
        alpha = alpha
    )
}

@Composable
fun GameScreen(
    context: Context,
    soundManager: SoundManager
) {
    // Log once at entry but don't do this on every recomposition
    LaunchedEffect(true) {
        Log.d("GameScreen", "Creating GameScreen composable once")
    }
    
    // Initialize with proper time values to prevent accidental Green mode
    val initialTime = System.currentTimeMillis()
    
    // Create a single game instance using remember to keep it alive across recompositions
    // Important: Use a stable key structure for remember, not mutableState that changes
    val game = remember(Unit) { 
        RunnerGame(context, soundManager).also {
            // Reset the game instance completely before use
            try {
                // Force to normal mode immediately
                it.setNormalMode()
                
                // Reset all key parameters
                val currentMode = it.getCurrentMode()
                if (currentMode != GameMode.NORMAL) {
                    Log.e("GameScreen", "CRITICAL: Game created in non-normal mode: $currentMode")
                }
                
                Log.d("GameScreen", "Created stable RunnerGame instance in NormalMode. Current mode: ${it.getCurrentMode()}")
            } catch (e: Exception) {
                Log.e("GameScreen", "Error initializing game instance: ${e.message}", e)
            }
        }
    }
    
    // Use mutableStateOf for reactive state values
    var score by remember { mutableStateOf(0) }
    var highScore by remember { mutableStateOf(0) }
    
    // Load initial high score from the game's SharedPreferences
    LaunchedEffect(Unit) {
        try {
            highScore = game.getHighScore()
            Log.d("GameScreen", "Successfully loaded high score from game: $highScore")
        } catch (e: Exception) {
            Log.e("GameScreen", "Error loading high score: ${e.message}", e)
        }
    }
    
    var shakeOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    var shakeDuration by remember { mutableStateOf(0) }
    var isShaking by remember { mutableStateOf(false) }
    
    // Bird trail and orange state
    var isOrangeState by remember { mutableStateOf(false) }
    var orangeStateTimer by remember { mutableStateOf(0f) }
    var tapStartTime by remember { mutableStateOf(initialTime) } // Initialize with current time
    var tapDuration by remember { mutableStateOf(0L) } // Initialize to zero
    var lastBirdY by remember { mutableStateOf(0f) }
    val trailPositions = remember { mutableStateListOf<Offset>() }
    
    // Track the transition from orange to normal mode while tapping
    var isTransitioningFromOrangeState by remember { mutableStateOf(false) }
    
    // Constants
    val ORANGE_STATE_DURATION = 5000f  // 5 seconds in milliseconds
    
    // Game state
    var isTapping by remember { mutableStateOf(false) } // Initialize to not tapping
    var gameState by remember { mutableStateOf(0) }
    var lastFrameTime by remember { mutableStateOf(System.nanoTime()) }
    var frameCount by remember { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // This state is used to force Canvas recomposition
    var frameKey by remember { mutableStateOf(0) }
    
    // Green mode drag handling
    var isGreenDragging by remember { mutableStateOf(false) }
    var greenInitialTouchY by remember { mutableStateOf(0f) }
    var greenInitialBirdY by remember { mutableStateOf(0f) }
    
    // Weather effect parameters
    var clouds = remember { mutableStateListOf<Triple<Offset, Float, Float>>() } // Position, size, opacity
    var raindrops = remember { mutableStateListOf<Pair<Offset, Float>>() } // Position and size
    var stars = remember { mutableStateListOf<Triple<Offset, Float, Float>>() } // Position, size, twinkle factor
    var sunRays = remember { mutableStateListOf<Triple<Float, Float, Float>>() } // Angle, length, opacity
    
    // Weather theme initialization
    LaunchedEffect(Unit) {
        // Initialize clouds for level 1 with better distribution
        for (i in 0 until 10) {
            // Distribute clouds across different heights in the sky
            val x = (Math.random() * 1000).toFloat()
            // Calculate height distribution (0% to 80% of screen height)
            val y = (50 + Math.random() * 400).toFloat() // More distributed vertically
            // Make clouds bigger and more consistent
            val size = (60f + Math.random() * 30f).toFloat() // Increased base size from 30f to 60f
            val opacity = (0.7f + Math.random() * 0.3f).toFloat() // Higher minimum opacity
            clouds.add(Triple(Offset(x, y), size, opacity))
        }
        
        // Initialize sun rays for level 2 (sunset)
        for (i in 0 until 20) {
            val angle = (i * (360 / 20)).toFloat() // Evenly spaced rays
            val length = (80f + Math.random() * 50f).toFloat()
            val opacity = (0.4f + Math.random() * 0.5f).toFloat()
            sunRays.add(Triple(angle, length, opacity))
        }
        
        // Initialize stars for level 4 (night sky) - distribute throughout the entire sky
        for (i in 0 until 150) {
            val x = (Math.random() * 1000).toFloat()
            // Make sure stars cover the ENTIRE sky height - using 90% of screen height
            val y = (Math.random() * 600).toFloat() // Will be adjusted once we know actual canvas size
            val size = (1f + Math.random() * 3f).toFloat()
            val twinkleFactor = Math.random().toFloat() // Used for twinkling effect
            stars.add(Triple(Offset(x, y), size, twinkleFactor))
        }
    }
    
    // Update weather effects
    LaunchedEffect(Unit) {
        while (true) {
            val level = game.getLevel()
            val canvasWidth = canvasSize.width
            val canvasHeight = canvasSize.height
            
            // Level 1: Moving clouds in clear sky
            if (level == 1) {
                val updatedClouds = mutableListOf<Triple<Offset, Float, Float>>()
                
                clouds.forEach { cloud ->
                    val (pos, size, opacity) = cloud
                    val newX = (pos.x + 0.3f) % (canvasWidth + 200) // Slow cloud movement
                    if (newX > canvasWidth) {
                        // Cloud moved off-screen, reposition to left side
                        updatedClouds.add(Triple(Offset(-100f, pos.y), size, opacity))
                    } else {
                        updatedClouds.add(Triple(Offset(newX, pos.y), size, opacity))
                    }
                }
                
                clouds.clear()
                clouds.addAll(updatedClouds)
            }
            
            // Level 3: Rain with dark clouds
            if (level == 3) {
                // Add new raindrops
                if (raindrops.size < 200) {
                    val x = (Math.random() * canvasWidth).toFloat()
                    val y = (Math.random() * 50).toFloat() // Start at top
                    val size = (10f + Math.random() * 20f).toFloat()
                    raindrops.add(Pair(Offset(x, y), size))
                }
                
                // Update existing raindrops
                val toRemove = mutableListOf<Pair<Offset, Float>>()
                val updated = mutableListOf<Pair<Offset, Float>>()
                
                // Calculate the ground position - raindrops should stop there
                val groundY = canvasHeight - canvasHeight * 0.1f // Ground height is 10% of canvas height
                
                raindrops.forEach { drop ->
                    val (pos, size) = drop
                    val newY = pos.y + 12f // Rain speed
                    
                    // Check if the raindrop would hit the ground
                    if (newY > groundY) {
                        // Remove the raindrop when it hits the ground
                        toRemove.add(drop)
                    } else {
                        updated.add(Pair(Offset(pos.x, newY), size))
                    }
                }
                
                raindrops.removeAll(toRemove)
                raindrops.clear()
                raindrops.addAll(updated)
            }
            
            // Level 4: Twinkling stars in night sky
            if (level == 4) {
                val updatedStars = mutableListOf<Triple<Offset, Float, Float>>()
                
                // If stars list is empty or has less than 150 stars, reinitialize stars across the entire sky
                if (stars.isEmpty() || stars.size < 150 || canvasHeight > 0 && stars[0].first.y > canvasHeight * 0.5f) {
                    stars.clear()
                    for (i in 0 until 150) {
                        val x = (Math.random() * canvasWidth).toFloat()
                        // Distribute stars across the ENTIRE sky (0% to 90% of screen height)
                        val y = (Math.random() * canvasHeight * 0.9f).toFloat()
                        val size = (1f + Math.random() * 3f).toFloat()
                        val twinkleFactor = Math.random().toFloat()
                        stars.add(Triple(Offset(x, y), size, twinkleFactor))
                    }
                    Log.d("GameScreen", "Reinitialized stars across entire sky height: ${canvasHeight}")
                }
                
                stars.forEach { star ->
                    val (pos, size, twinkleFactor) = star
                    
                    // Create twinkling effect by varying the twinkle factor
                    val newTwinkleFactor = if (Math.random() < 0.05) {
                        // 5% chance to change twinkling state each frame
                        (Math.random()).toFloat()
                    } else {
                        twinkleFactor
                    }
                    
                    updatedStars.add(Triple(pos, size, newTwinkleFactor))
                }
                
                stars.clear()
                stars.addAll(updatedStars)
            }
            
            // Short delay before next update
            delay(16) // ~60fps
        }
    }
    
    // Function to trigger screen shake
    fun startScreenShake() {
        isShaking = true
        shakeDuration = 10 // Shake for 10 frames
    }

    // Game update loop
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val currentTime = System.nanoTime()
                val deltaTimeSeconds = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime
                
                if (isInitialized) {
                    // Log status consistently at regular intervals
                    if (frameCount % 60 == 0) {
                        Log.d("GameScreen", "Game loop - isPlaying: ${game.isPlaying}, gameState: $gameState, frame: $frameCount")
                        val bird = game.getBird()
                        Log.d("GameScreen", "Bird position: (${bird.x}, ${bird.y}), velocity: ${bird.velocityY}")
                        Log.d("GameScreen", "Obstacles count: ${game.getObstacles().size}")
                    }
                    
                    // Check if game was playing but is now stopped (collision)
                    val wasPlaying = game.isPlaying
                    
                    // Only update the game if it's in "playing" state (gameState = 1)
                    if (gameState == 1) {
                        game.update() // Ensure this gets called every frame to update game state
                    }
                    
                    if (wasPlaying && !game.isPlaying) {
                        startScreenShake()
                        // Reset tapping state when game ends
                        isTapping = false
                        // Reset orange state when game ends
                        isOrangeState = false
                        orangeStateTimer = 0f
                        isTransitioningFromOrangeState = false
                        tapDuration = 0L
                        tapStartTime = System.currentTimeMillis() // Reset tap start time
                        
                        // Reset any active game modes
                        game.setNormalMode()
                        
                        // Log that collision handling completed successfully
                        Log.d("GameScreen", "Collision detected and handled - game stopped")
                    }
                    
                    // Update orange state timer
                    if (isOrangeState) {
                        orangeStateTimer += deltaTimeSeconds * 1000f // Convert to milliseconds since ORANGE_STATE_DURATION is in ms
                        if (frameCount % 30 == 0) {
                            Log.d("GameScreen", "Orange state timer: $orangeStateTimer, max duration: $ORANGE_STATE_DURATION")
                        }
                        if (orangeStateTimer >= ORANGE_STATE_DURATION) {
                            isOrangeState = false
                            orangeStateTimer = 0f
                            
                            // Reset game mode to normal
                            game.setNormalMode()
                            
                            // Set transition flag if tap is still being held
                            if (isTapping) {
                                isTransitioningFromOrangeState = true
                                // Force a recalculation of tap duration to update the color
                                tapDuration = 0L
                                tapStartTime = System.currentTimeMillis()
                            } else {
                                isTransitioningFromOrangeState = false
                            }
                            
                            Log.d("GameScreen", "Orange state EXPIRED")
                        }
                    }
                    
                    // Handle tap input
                    if (game.isPlaying) {
                        if (isTapping) {
                            // Only update tap duration if actually tapping
                            tapDuration = System.currentTimeMillis() - tapStartTime
                            if (frameCount % 30 == 0) {
                                Log.d("GameScreen", "Tap time: $tapDuration")
                            }
                        } else {
                            // If not tapping, ensure that tapDuration is zero
                            tapDuration = 0L
                        }
                    }
                    
                    // Update trail positions when in orange state
                    if (isOrangeState && game.isPlaying) {
                        val birdObj = game.getBird()
                        
                        // Add new position to the trail
                        val backOfBird = Offset(
                            birdObj.x,
                            birdObj.y + birdObj.height / 2
                        )
                        
                        trailPositions.add(backOfBird)
                        
                        // Limit trail length
                        while (trailPositions.size > 30) {
                            trailPositions.removeAt(0)
                        }
                        
                        // Update last Y position for next frame's velocity calculation
                        lastBirdY = birdObj.y
                    } else if (trailPositions.isNotEmpty()) {
                        // Clear trail when not in orange state
                        trailPositions.clear()
                    }
                    
                    // Update score
                    score = game.getScore().toInt()
                    if (score > highScore) {
                        highScore = score
                    }
                    
                    // Update screen shake effect
                    if (isShaking) {
                        if (shakeDuration > 0) {
                            shakeOffset = Offset(
                                (Math.random() * 20 - 10).toFloat(),
                                (Math.random() * 20 - 10).toFloat()
                            )
                            shakeDuration--
                        } else {
                            isShaking = false
                            shakeOffset = Offset(0f, 0f)
                        }
                    }
                    
                    // Increment frame key to force Canvas redraw
                    frameKey++
                }
                
                val frameTime = (System.nanoTime() - currentTime) / 1_000_000f
                val targetFrameTime = 16.666f  // Target 60 FPS
                if (frameTime < targetFrameTime) {
                    delay((targetFrameTime - frameTime).toLong())
                }
                
                frameCount++
            } catch (e: Exception) {
                Log.e("GameScreen", "Error in game loop: ${e.message}", e)
                delay(16)
            }
        }
    }
    
    // Modified initialization process to ensure the game starts correctly
    LaunchedEffect(canvasSize) {
        if (!isInitialized && canvasSize.width > 0 && canvasSize.height > 0) {
            try {
                Log.d("GameScreen", "Initializing game with size: ${canvasSize.width} x ${canvasSize.height}")
                game.initialize(canvasSize.width, canvasSize.height)
                
                // Explicitly set all state variables to default values
                isOrangeState = false
                orangeStateTimer = 0f
                
                // Reset tap state
                val safeInitialTime = System.currentTimeMillis()
                tapStartTime = safeInitialTime
                tapDuration = 0L
                isTapping = false
                
                // Force the game to Normal mode multiple times to ensure it sticks
                game.setNormalMode()
                
                // Double check game mode after initialization and force if needed
                val currentMode = game.getCurrentMode()
                if (currentMode != GameMode.NORMAL) {
                    Log.w("GameScreen", "WARNING: Game mode is $currentMode after initialization, forcing NORMAL!")
                    game.setNormalMode()
                }
                
                // Force another check to be absolutely sure
                if (game.getCurrentMode() != GameMode.NORMAL) {
                    Log.e("GameScreen", "CRITICAL: Still not in NORMAL mode after forced reset!")
                }
                
                isInitialized = true
                gameState = 0 // Set to waiting for tap
                Log.d("GameScreen", "Game initialized in NORMAL mode, current mode: ${game.getCurrentMode()}, waiting for tap to start")
                
            } catch (e: Exception) {
                Log.e("GameScreen", "Error initializing game: ${e.message}", e)
            }
        }
    }
    
    // When app is paused or resumed, update the game accordingly
    DisposableEffect(Unit) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                // Pause the game when app goes to background
                Log.d("GameScreen", "App paused - pausing game")
                // This ensures we reset to normal mode when paused to prevent mode getting stuck
                if (game.isPlaying) {
                    game.setNormalMode()
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("GameScreen", "App resumed")
                // Check current mode when app returns to foreground to ensure it's in the expected state
                val currentMode = game.getCurrentMode()
                if (currentMode != GameMode.NORMAL) {
                    Log.w("GameScreen", "Game resumed in non-normal mode: $currentMode - resetting to normal")
                    game.setNormalMode()
                }
            }
        }
        
        // Register the observer
        lifecycleOwner?.lifecycle?.addObserver(observer)
        
        // Cleanup when disposed
        onDispose {
            Log.d("GameScreen", "Disposing GameScreen resources")
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }
    
    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { _ ->
                    if (isInitialized) {
                        Log.d("GameScreen", "Tap down detected at ${System.currentTimeMillis()}")
                        
                        // If the game is over, restart the game on tap
                        if (!game.isPlaying) {
                            try {
                                // Completely reset all state variables
                                isOrangeState = false
                                orangeStateTimer = 0f
                                isTransitioningFromOrangeState = false
                                
                                // Reset tap timing - critical for proper mode detection
                                val safeTime = System.currentTimeMillis()
                                tapStartTime = safeTime
                                tapDuration = 0L
                                
                                // Reset game state 
                                gameState = 1  // Set to 1 to start the game immediately
                                
                                // Force the game to normal mode
                                game.setNormalMode()
                                
                                // Start the game immediately
                                isTapping = true
                                game.start()
                                
                                Log.d("GameScreen", "Game started explicitly - gameState: $gameState, isPlaying: ${game.isPlaying}")
                            } catch (e: Exception) {
                                Log.e("GameScreen", "Error restarting game: ${e.message}", e)
                            }
                        } else {
                            // Normal tap behavior for ongoing game
                            tapStartTime = System.currentTimeMillis()
                            isTapping = true
                            game.onTapDown()
                        }
                        
                        // Wait for release
                        val released = tryAwaitRelease()
                        
                        // Stop tracking tap duration
                        isTapping = false
                        
                        // Reset transition flag when releasing tap
                        isTransitioningFromOrangeState = false
                        
                        if (released && game.isPlaying) {
                            Log.d("GameScreen", "Tap up detected at ${System.currentTimeMillis()}")
                            
                            // Store final tap duration for mode activation
                            val finalTapDuration = tapDuration
                            Log.d("GameScreen", "Final tap duration on release: $finalTapDuration")
                            
                            // MODE ACTIVATION ENGINE - THIS IS A CRITICAL BLOCK
                            // -----------------------------------------------
                            // Calculate the actual cycle position at release time
                            val orangeTimeStart = 300 // 0.3 seconds
                            val orangeTimeEnd = 600   // 0.6 seconds
                            val greenTimeStart = 600  // 0.6 seconds
                            val greenTimeEnd = 900    // 0.9 seconds
                            val cycleTime = 900       // 0.9 seconds - full cycle time
                            
                            // Use modulo to determine the cyclic position
                            val cyclicDuration = finalTapDuration % cycleTime
                            
                            // LOG DETAILED DIAGNOSTICS
                            Log.d("GameScreen", "MODE ENGINE - cyclic: $cyclicDuration, " +
                                  "orangeStart: $orangeTimeStart, orangeEnd: $orangeTimeEnd, " +
                                  "greenStart: $greenTimeStart, greenEnd: $greenTimeEnd")
                            
                            // We can only activate special modes from NORMAL mode
                            if (game.getCurrentMode() == GameMode.NORMAL) {
                                // DETERMINE MODE BASED ON CURRENT COLOR AT RELEASE TIME
                                when {
                                    // YELLOW MODE - either at start of cycle or after completing a full cycle
                                    cyclicDuration < orangeTimeStart -> {
                                        Log.d("GameScreen", "🟡 YELLOW MODE - cyclicDuration: $cyclicDuration (< $orangeTimeStart)")
                                        // Normal jump with tap power proportional to tap duration
                                        val tapPower = min(2.0f, 1.0f + finalTapDuration / 600f)
                                        game.jump(tapPower)
                                    }
                                    
                                    // ORANGE MODE - between orangeTimeStart and orangeTimeEnd
                                    cyclicDuration >= orangeTimeStart && cyclicDuration < orangeTimeEnd -> {
                                        Log.d("GameScreen", "🟠 ORANGE MODE ACTIVATED - cyclicDuration: $cyclicDuration")
                                        isOrangeState = true
                                        orangeStateTimer = 0f
                                        game.activateOrangeMode()
                                        game.jump(1.2f) // Jump with extra power
                                    }
                                    
                                    // GREEN MODE - between greenTimeStart and greenTimeEnd
                                    cyclicDuration >= greenTimeStart && cyclicDuration < greenTimeEnd -> {
                                        Log.d("GameScreen", "🟢 GREEN MODE ACTIVATED - cyclicDuration: $cyclicDuration")
                                        isOrangeState = false
                                        orangeStateTimer = 0f
                                        game.activateGreenMode()
                                        // No jump for Green Mode - player controls directly with touch
                                    }
                                    
                                    // YELLOW MODE AGAIN - if we somehow exceed the cycle time
                                    else -> {
                                        Log.d("GameScreen", "🟡 YELLOW MODE (after cycle) - cyclicDuration: $cyclicDuration (>= $greenTimeEnd)")
                                        // Normal jump with tap power proportional to tap duration
                                        val tapPower = min(2.0f, 1.0f + finalTapDuration / 600f)
                                        game.jump(tapPower)
                                    }
                                }
                            } else if (game.getCurrentMode() == GameMode.ORANGE) {
                                // Already in Orange mode, just jump with the orange mode power
                                game.jump(2.0f)
                            } else if (game.getCurrentMode() == GameMode.GREEN) {
                                // Already in Green mode - no jump
                                Log.d("GameScreen", "Maintaining GREEN mode - no jump")
                            }
                            // -----------------------------------------------
                            // END OF MODE ACTIVATION ENGINE
                            
                            // Reset tap duration after processing the release action
                            tapDuration = 0L
                        }
                    } else {
                        Log.d("GameScreen", "Tap ignored - game not initialized")
                    }
                }
            )
        }
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    // Only handle drag in Green Mode
                    val currentGameMode = game.getCurrentMode()
                    if (currentGameMode == GameMode.GREEN && game.isPlaying) {
                        // Store the initial touch position for reference
                        greenInitialTouchY = offset.y
                        isGreenDragging = true
                        
                        // Store the bird's initial position
                        greenInitialBirdY = game.getBird().y
                        
                        Log.d("GameScreen", "Green Mode: Drag start at y=${offset.y}, bird at ${greenInitialBirdY}")
                    } else {
                        // Log why we're not handling the drag
                        Log.d("GameScreen", "Not handling drag - mode: $currentGameMode, isPlaying: ${game.isPlaying}")
                    }
                },
                onDragEnd = {
                    // End of drag
                    if (game.getCurrentMode() == GameMode.GREEN && game.isPlaying) {
                        // Reset dragging state
                        isGreenDragging = false
                        Log.d("GameScreen", "Green Mode: Drag ended")
                    }
                },
                onDragCancel = {
                    // Drag canceled
                    if (game.getCurrentMode() == GameMode.GREEN && game.isPlaying) {
                        // Reset dragging state
                        isGreenDragging = false
                        Log.d("GameScreen", "Green Mode: Drag canceled")
                    }
                },
                onVerticalDrag = { change, _ ->
                    // Add double safety check for green mode
                    val isGreenMode = game.getCurrentMode() == GameMode.GREEN
                    if (isGreenMode && game.isPlaying && isGreenDragging) {
                        try {
                            // Relative movement based on initial positions
                            val dragDelta = change.position.y - greenInitialTouchY
                            
                            // Apply the movement based on drag amount directly
                            val bird = game.getBird()
                            
                            // Calculate new position based on the initial bird position plus drag delta
                            val newY = greenInitialBirdY + dragDelta
                            
                            // Feed position directly to game for bird control
                            game.handleSlideInput(newY + bird.height/2)
                            
                            Log.d("GameScreen", "Green Mode: Vertical drag delta=${dragDelta}, current Y=${bird.y}")
                        } catch (e: Exception) {
                            Log.e("GameScreen", "Error in vertical drag handler: ${e.message}", e)
                        }
                    } else if (isGreenDragging && !isGreenMode) {
                        // We somehow got out of Green mode while dragging
                        isGreenDragging = false
                        Log.d("GameScreen", "Canceling drag - no longer in Green Mode")
                    }
                }
            )
        }
    ) {
        // Use frameKey as a key to force recomposition of Canvas on each frame
        key(frameKey) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = shakeOffset.x.dp, y = shakeOffset.y.dp)
            ) {
                // Update canvas size if needed
                if (size.width > 0 && size.height > 0) {
                    canvasSize = size
                }
                
                try {
                    // Get canvas dimensions
                    val width = size.width
                    val height = size.height
                    val groundHeight = height * 0.1f
                    
                    // Draw background based on level theme
                    val level = game.getLevel()
                    val skyColor = when (level) {
                        1 -> ComposeColor(0xFF87CEEB) // Light blue for clear sky
                        2 -> ComposeColor(0xFFFAB3A3) // Light pastel salmon color for better contrast with orange bird
                        3 -> ComposeColor(0xFF3A608B) // Slightly lighter rainy blue
                        4 -> ComposeColor(0xFF1A2030) // Slightly lighter night blue for better contrast
                        else -> ComposeColor(0xFF87CEEB) // Default sky blue
                    }
                    
                    // Draw ground based on level theme
                    val groundColor = when (level) {
                        1 -> ComposeColor(0xFF006400) // Darker green for level 1 to contrast with bird
                        2 -> ComposeColor(0xFF8B4513) // Darker brown for sunset
                        3 -> ComposeColor(0xFF004000) // Even darker green for rain level
                        4 -> ComposeColor(0xFF001800) // Very dark green for night level
                        else -> ComposeColor(0xFF90EE90) // Default light green
                    }
                    
                    // Draw background
                    drawRect(
                        color = skyColor,
                        size = Size(width, height)
                    )
                    
                    // Draw ground
                    drawRect(
                        color = groundColor,
                        topLeft = Offset(0f, height - groundHeight),
                        size = Size(width, groundHeight)
                    )
                    
                    // Draw level-specific weather effects
                    when (level) {
                        1 -> {
                            // Level 1: Clouds in clear sky
                            clouds.forEach { (pos, size, opacity) ->
                                // Draw main cloud body
                                drawCircle(
                                    color = ComposeColor(1f, 1f, 1f, opacity),
                                    radius = size,
                                    center = pos
                                )
                                
                                // Draw smaller cloud puffs
                                drawCircle(
                                    color = ComposeColor(1f, 1f, 1f, opacity),
                                    radius = size * 0.7f,
                                    center = Offset(pos.x + size * 0.6f, pos.y)
                                )
                                
                                drawCircle(
                                    color = ComposeColor(1f, 1f, 1f, opacity),
                                    radius = size * 0.6f,
                                    center = Offset(pos.x - size * 0.6f, pos.y)
                                )
                                
                                drawCircle(
                                    color = ComposeColor(1f, 1f, 1f, opacity),
                                    radius = size * 0.5f,
                                    center = Offset(pos.x, pos.y - size * 0.4f)
                                )
                            }
                        }
                        2 -> {
                            // Level 2: Sunset with nice sun
                            val sunCenter = Offset(width * 0.3f, height * 0.2f)
                            
                            // Draw sun glow (outer)
                            drawCircle(
                                color = ComposeColor(1f, 0.5f, 0.1f, 0.2f),
                                radius = 120f,
                                center = sunCenter
                            )
                            
                            // Draw sun glow (middle)
                            drawCircle(
                                color = ComposeColor(1f, 0.6f, 0.2f, 0.4f),
                                radius = 90f,
                                center = sunCenter
                            )
                            
                            // Draw sun (inner)
                            drawCircle(
                                color = ComposeColor(1f, 0.8f, 0.2f, 0.9f),
                                radius = 70f,
                                center = sunCenter
                            )
                            
                            // Draw sun rays
                            sunRays.forEach { (angle, length, opacity) ->
                                val angleRadians = angle * (PI.toFloat() / 180f)
                                val startX = sunCenter.x + 70f * cos(angleRadians)
                                val startY = sunCenter.y + 70f * sin(angleRadians)
                                val endX = sunCenter.x + (70f + length) * cos(angleRadians)
                                val endY = sunCenter.y + (70f + length) * sin(angleRadians)
                                
                                drawLine(
                                    color = ComposeColor(1f, 0.7f, 0.2f, opacity),
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY),
                                    strokeWidth = 3f
                                )
                            }
                            
                            // Draw sunset clouds
                            for (i in 0 until 3) {
                                val cloudY = height * 0.35f + i * 40f
                                val cloudWidth = width * (0.5f + i * 0.1f)
                                val cloudHeight = 20f + i * 10f
                                
                                drawOval(
                                    color = ComposeColor(0.9f, 0.5f, 0.4f, 0.7f - i * 0.15f),
                                    topLeft = Offset(width * 0.1f - i * 20f, cloudY),
                                    size = Size(cloudWidth, cloudHeight)
                                )
                            }
                        }
                        3 -> {
                            // Level 3: Rain with dark clouds
                            
                            // Draw multiple smaller rain clouds instead of one big one
                            for (i in 0 until 6) {
                                val xOffset = (i * width / 5f) % width
                                val yVariation = (i % 3) * 40f
                                val y = 40f + yVariation
                                val size = 70f - (i % 3) * 10f
                                val cloudOpacity = 0.7f + (i % 3) * 0.1f
                                
                                // Draw main cloud body
                                drawCircle(
                                    color = ComposeColor(0.3f, 0.3f, 0.4f, cloudOpacity),
                                    radius = size,
                                    center = Offset(xOffset, y)
                                )
                                
                                // Draw additional puffs for each cloud
                                drawCircle(
                                    color = ComposeColor(0.3f, 0.3f, 0.4f, cloudOpacity),
                                    radius = size * 0.7f,
                                    center = Offset(xOffset + size * 0.5f, y + 10f)
                                )
                                
                                drawCircle(
                                    color = ComposeColor(0.3f, 0.3f, 0.4f, cloudOpacity),
                                    radius = size * 0.6f,
                                    center = Offset(xOffset - size * 0.4f, y + 5f)
                                )
                            }
                            
                            // Draw rain
                            raindrops.forEach { (pos, size) ->
                                drawLine(
                                    color = ComposeColor(0.8f, 0.8f, 1f, 0.7f), // Increased opacity for better visibility
                                    start = pos,
                                    end = Offset(pos.x - 3f, pos.y + size), // Make rain slightly thicker
                                    strokeWidth = 2.5f // Slightly thicker rain
                                )
                            }
                        }
                        4 -> {
                            // Level 4: Night sky with twinkling stars
                            stars.forEach { (pos, size, twinkleFactor) ->
                                // Use twinkle factor to determine brightness
                                val brightness = 0.5f + twinkleFactor * 0.5f
                                
                                // Draw star as a small circle with varying brightness
                                drawCircle(
                                    color = ComposeColor(brightness, brightness, brightness * 0.9f, brightness),
                                    radius = size,
                                    center = pos
                                )
                                
                                // Add a glow effect for some stars
                                if (twinkleFactor > 0.7f && size > 2f) {
                                    drawCircle(
                                        color = ComposeColor(brightness, brightness, brightness * 0.9f, 0.3f),
                                        radius = size * 2f,
                                        center = pos
                                    )
                                }
                            }
                            
                            // Draw a larger moon in the night sky (2x larger)
                            val moonCenter = Offset(width * 0.8f, height * 0.2f)
                            val moonRadius = 100f // Increased from 50f to 100f (2x larger)
                            
                            // Draw main moon body with slight yellow tint
                            drawCircle(
                                color = ComposeColor(0.95f, 0.95f, 0.9f, 0.95f),
                                radius = moonRadius,
                                center = moonCenter
                            )
                            
                            // Draw moon crater shadows with more detailed design
                            // Large crater with shadow
                            drawCircle(
                                color = ComposeColor(0.75f, 0.75f, 0.7f, 0.6f),
                                radius = moonRadius * 0.25f,
                                center = Offset(moonCenter.x - moonRadius * 0.4f, moonCenter.y - moonRadius * 0.2f)
                            )
                            
                            // Medium crater with shadow
                            drawCircle(
                                color = ComposeColor(0.8f, 0.8f, 0.75f, 0.5f),
                                radius = moonRadius * 0.2f,
                                center = Offset(moonCenter.x + moonRadius * 0.3f, moonCenter.y + moonRadius * 0.3f)
                            )
                            
                            // Small crater with shadow
                            drawCircle(
                                color = ComposeColor(0.85f, 0.85f, 0.8f, 0.4f),
                                radius = moonRadius * 0.15f,
                                center = Offset(moonCenter.x + moonRadius * 0.45f, moonCenter.y - moonRadius * 0.35f)
                            )
                            
                            // Add some smaller craters
                            drawCircle(
                                color = ComposeColor(0.83f, 0.83f, 0.78f, 0.5f),
                                radius = moonRadius * 0.1f,
                                center = Offset(moonCenter.x - moonRadius * 0.15f, moonCenter.y + moonRadius * 0.42f)
                            )
                            
                            drawCircle(
                                color = ComposeColor(0.87f, 0.87f, 0.82f, 0.4f),
                                radius = moonRadius * 0.07f,
                                center = Offset(moonCenter.x - moonRadius * 0.38f, moonCenter.y - moonRadius * 0.45f)
                            )
                            
                            // Add a subtle glow around the moon
                            drawCircle(
                                color = ComposeColor(0.9f, 0.9f, 0.85f, 0.15f),
                                radius = moonRadius * 1.3f,
                                center = moonCenter
                            )
                        }
                    }
                    
                    // Draw obstacles with simplified textures for better performance
                    game.getObstacles().forEach { obstacle ->
                        try {
                            val obstacleWidth = game.getObstacleWidth(obstacle.type)
                            val baseColor = when (obstacle.type) {
                                ObstacleType.NARROW -> ComposeColor(0xFF2E7D32)  // Medium green
                                ObstacleType.NORMAL -> ComposeColor(0xFF1B5E20)  // Dark green
                                ObstacleType.WIDE -> ComposeColor(0xFF388E3C)    // Forest green
                                ObstacleType.SPIKED -> ComposeColor(0xFF8B0000)  // Dark red
                            }

                            // Draw base rectangle
                            drawRect(
                                color = baseColor,
                                topLeft = Offset(obstacle.x, obstacle.y),
                                size = Size(obstacleWidth, obstacle.height)
                            )

                            // Add simple texture (just a border)
                            drawRect(
                                color = baseColor.darker(0.3f),
                                topLeft = Offset(obstacle.x, obstacle.y),
                                size = Size(obstacleWidth, obstacle.height),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )

                            // Draw spikes if needed
                            if (obstacle.type == ObstacleType.SPIKED) {
                                drawSpikes(obstacle, obstacleWidth)
                            }
                        } catch (e: Exception) {
                            Log.e("GameScreen", "Error drawing obstacle: ${e.message}", e)
                        }
                    }
                    
                    // Get the bird
                    val bird = game.getBird()
                    
                    // Draw trail when in orange state
                    if (isOrangeState && trailPositions.size > 1) {
                        // Define the pivot point at the back center of bird
                        val pivotPoint = Offset(
                            bird.x,  // Back edge of the bird
                            bird.y + (bird.height / 2)  // Exact vertical center
                        )
                        
                        // Calculate the bird's movement angle based on vertical velocity
                        val recentPastIndex = max(0, trailPositions.size - 5)
                        val recentPosition = trailPositions[recentPastIndex]
                        
                        // Calculate angle based on vertical movement
                        val verticalDelta = pivotPoint.y - recentPosition.y
                        val horizontalDelta = 100f  // Fixed horizontal distance for stability
                        
                        // Calculate angle in radians, then convert to degrees
                        val angleRadians = atan2(verticalDelta, horizontalDelta)
                        val angleDegrees = angleRadians * (180f / PI.toFloat())
                        
                        // Adjust angle to limit excessive tilt
                        val maxAngle = 35f
                        val adjustedAngle = angleDegrees.coerceIn(-maxAngle, maxAngle)
                        
                        // Define trail colors - use vibrant orange for trail
                        val trailBaseColor = ComposeColor(1f, 0.5f, 0.1f, 0.35f)
                        
                        // Draw the trail as a plume attached to the back of the bird
                        rotate(adjustedAngle, pivotPoint) {
                            // Create a path for the plume
                            val plumePath = Path()
                            
                            // Start at the pivot point (back of bird)
                            plumePath.moveTo(pivotPoint.x, pivotPoint.y)
                            
                            // Calculate control points for a fluid plume shape
                            val plumeLength = bird.width * 4f  // Make it exactly 4x the bird width
                            
                            // Calculate end point of plume (far from bird)
                            val plumeEndPoint = Offset(
                                pivotPoint.x - plumeLength,
                                pivotPoint.y
                            )
                            
                            // Control points for the plume curve - adjust for rounded edges
                            val control1 = Offset(
                                pivotPoint.x - plumeLength * 0.3f,
                                pivotPoint.y - bird.height * 0.2f
                            )
                            
                            val control2 = Offset(
                                pivotPoint.x - plumeLength * 0.7f,
                                pivotPoint.y + bird.height * 0.2f
                            )
                            
                            // Draw the plume with a Bezier curve for smooth shape
                            plumePath.cubicTo(
                                control1.x, control1.y,
                                control2.x, control2.y,
                                plumeEndPoint.x, plumeEndPoint.y
                            )
                            
                            // Draw trail with varying height
                            val maxWidth = bird.width * 0.7f
                            val trailHeight = bird.height * 0.95f
                            val endHeight = bird.height * 0.05f
                            
                            // Calculate points along the Bezier curve
                            val numPoints = 35
                            for (i in 0 until numPoints) {
                                val t = i / (numPoints - 1f)
                                
                                // Calculate position using Bezier formula
                                val mt = 1 - t
                                val mt2 = mt * mt
                                val mt3 = mt2 * mt
                                val t2 = t * t
                                val t3 = t2 * t
                                
                                val centerX = mt3 * pivotPoint.x + 
                                        3 * mt2 * t * control1.x + 
                                        3 * mt * t2 * control2.x + 
                                        t3 * plumeEndPoint.x
                                
                                val centerY = mt3 * pivotPoint.y + 
                                        3 * mt2 * t * control1.y + 
                                        3 * mt * t2 * control2.y + 
                                        t3 * plumeEndPoint.y
                                
                                // Calculate height and width at this point
                                val easingFactor = t * t * (3 - 2 * t)
                                val currentHeight = trailHeight * (1 - easingFactor) + endHeight * easingFactor
                                val widthFactor = (1 - t) * (1 - t)
                                val segmentWidth = maxWidth * widthFactor
                                
                                // Draw oval segment
                                val halfHeight = currentHeight / 2
                                drawOval(
                                    color = trailBaseColor.copy(alpha = 0.35f * (1 - t * 0.8f)),
                                    topLeft = Offset(centerX - segmentWidth/2, centerY - halfHeight),
                                    size = Size(segmentWidth, currentHeight)
                                )
                            }
                        }
                    }
                    
                    // Calculate bird color based on state
                    val birdColor = if (isOrangeState) {
                        // When in orange state, use pure orange
                        ComposeColor(1f, 0.5f, 0f, 1f)
                    } else if (game.getCurrentMode() == GameMode.GREEN) {
                        // Green mode has its own color
                        ComposeColor(0.0f, 0.9f, 0.2f, 1f)
                    } else if (isTapping && game.isPlaying) {
                        // Calculate the tap duration since start
                        val pressDuration = System.currentTimeMillis() - tapStartTime
                        
                        // DISPLAY COLORS CYCLE - mirrors the activation thresholds exactly
                        val orangeTime = 300 // 0.3 seconds - MUST MATCH the activation engine above
                        val greenTime = 600  // 0.6 seconds - MUST MATCH the activation engine above
                        val cycleTime = 900  // 0.9 seconds - MUST MATCH the activation engine above
                        
                        // Use modulo to create a repeating cycle
                        val cyclicDuration = pressDuration % cycleTime
                        
                        // Determine color based on position in cycle
                        when {
                            cyclicDuration < orangeTime -> ComposeColor.Yellow      // First third: Yellow (0-299ms)
                            cyclicDuration < greenTime -> ComposeColor(1f, 0.5f, 0f, 1f)  // Second third: Orange (300-599ms)
                            else -> ComposeColor(0.0f, 0.9f, 0.2f, 1f)             // Final third: Green (600-899ms)
                        }
                    } else {
                        // Not tapping - always default to yellow in normal mode
                        ComposeColor.Yellow
                    }
                    
                    // Draw bird as a square to match hitbox
                    drawRect(
                        color = birdColor,
                        topLeft = Offset(bird.x + bird.visualOffsetX, bird.y),  // Apply visual offset for shake effect
                        size = Size(bird.width, bird.height)
                    )

                    // Add eye for visual appeal
                    drawCircle(
                        color = ComposeColor.Black,
                        radius = bird.width / 6,
                        center = Offset(bird.x + bird.visualOffsetX + bird.width * 0.7f, bird.y + bird.height * 0.4f)  // Apply visual offset for shake effect
                    )
                    
                    // Draw green bubble effect in Green Mode
                    if (game.getCurrentMode() == GameMode.GREEN) {
                        val bubbleRadius = game.getGreenBubbleRadius()
                        if (bubbleRadius > 0) {
                            // Calculate center of bird for bubble
                            val birdCenterX = bird.x + bird.visualOffsetX + bird.width / 2  // Apply visual offset
                            val birdCenterY = bird.y + bird.height / 2
                            
                            // Draw outer bubble (more transparent)
                            drawCircle(
                                color = ComposeColor(0.0f, 0.9f, 0.2f, 0.1f),
                                radius = bubbleRadius,
                                center = Offset(birdCenterX, birdCenterY)
                            )
                            
                            // Draw inner bubble (more transparent)
                            drawCircle(
                                color = ComposeColor(0.0f, 0.9f, 0.2f, 0.05f),
                                radius = bubbleRadius * 0.8f,
                                center = Offset(birdCenterX, birdCenterY)
                            )
                            
                            // Draw shimmer effect (small sparkles)
                            val currentTime = System.currentTimeMillis()
                            val shimmerSeed = (currentTime / 100).toInt() // Changes every 100ms
                            val shimmerRandom = Random(shimmerSeed)
                            
                            repeat(5) {
                                val angle = shimmerRandom.nextFloat() * 2 * PI.toFloat()
                                val distance = shimmerRandom.nextFloat() * bubbleRadius * 0.9f
                                val sparkleX = birdCenterX + cos(angle) * distance
                                val sparkleY = birdCenterY + sin(angle) * distance
                                val sparkleSize = bubbleRadius * (0.05f + shimmerRandom.nextFloat() * 0.05f)
                                
                                drawCircle(
                                    color = ComposeColor(0.5f, 1.0f, 0.5f, 0.3f),
                                    radius = sparkleSize,
                                    center = Offset(sparkleX, sparkleY)
                                )
                            }
                            
                            // Draw bubble timer indicator
                            val greenProgress = game.getGreenModeProgress()
                            val timerArcSweepAngle = 360 * (1 - greenProgress)
                            
                            drawArc(
                                color = ComposeColor(0.0f, 0.9f, 0.2f, 0.2f),
                                startAngle = -90f,
                                sweepAngle = timerArcSweepAngle,
                                useCenter = false,
                                topLeft = Offset(birdCenterX - bubbleRadius, birdCenterY - bubbleRadius),
                                size = Size(bubbleRadius * 2, bubbleRadius * 2),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    
                    // Draw level number on the top left
                    val paint = Paint().apply {
                        color = AndroidColor.WHITE
                        textSize = 60f
                        textAlign = Paint.Align.LEFT
                    }
                    
                    drawContext.canvas.nativeCanvas.apply {
                        drawText("Level: $level", 50f, 100f, paint)
                        
                        // Draw score and high score on the right side
                        paint.textAlign = Paint.Align.RIGHT
                        drawText("Score: $score", width - 50f, 100f, paint)
                        drawText("High Score: $highScore", width - 50f, 170f, paint)
                        
                        // Draw game over text
                        if (!game.isPlaying && gameState > 0) {
                            paint.apply {
                                textSize = 80f
                                textAlign = Paint.Align.CENTER
                            }
                            drawText("Game Over", width / 2f, height / 2f, paint)
                            paint.textSize = 40f
                            drawText("Tap to play again", width / 2f, height / 2f + 80f, paint)
                        }
                        
                        // Draw tap to start text
                        if (gameState == 0) {
                            paint.apply {
                                textSize = 60f
                                textAlign = Paint.Align.CENTER
                            }
                            drawText("Tap to start", width / 2f, height / 2f, paint)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GameScreen", "Error in Canvas drawing: ${e.message}", e)
                }
            }
        }
    }
    
    // Draw game version only (remove duplicated score, high score, and tap to start)
    if (!game.isPlaying) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Version ${MainActivity.VERSION}",
                color = ComposeColor.Black,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun DrawScope.drawOptimizedTexture(
    textureType: TextureType,
    baseColor: ComposeColor,
    brickX: Float,
    brickY: Float,
    brickWidth: Float,
    brickHeight: Float,
    detailLevel: Int
) {
    when (textureType) {
        TextureType.DIAGONAL_BRICKS -> {
            if (detailLevel == 0) {
                drawRect(
                    color = baseColor.darker(0.2f),
                    topLeft = Offset(brickX, brickY),
                    size = Size(brickWidth, brickHeight)
                )
                drawLine(
                    color = baseColor.lighter(0.2f),
                    start = Offset(brickX, brickY),
                    end = Offset(brickX + brickWidth, brickY + brickHeight),
                    strokeWidth = 5f
                )
            }
        }
        TextureType.HEXAGONAL -> {
            if (detailLevel <= 1) {
                val centerX = brickX + brickWidth / 2
                val centerY = brickY + brickHeight / 2
                val radius = minOf(brickWidth, brickHeight) / 2
                drawPath(
                    path = createHexagonPath(centerX, centerY, radius),
                    color = baseColor.darker(0.2f)
                )
            }
        }
        else -> {
            // Basic 3D effect for other textures
            drawRect(
                color = baseColor.darker(0.2f),
                topLeft = Offset(brickX, brickY),
                size = Size(brickWidth, brickHeight)
            )
            if (detailLevel == 0) {
                drawRect(
                    color = baseColor.lighter(0.2f),
                    topLeft = Offset(brickX + 2, brickY + 2),
                    size = Size(brickWidth - 4, brickHeight - 4)
                )
            }
        }
    }
}

private fun createHexagonPath(centerX: Float, centerY: Float, radius: Float): Path {
    return Path().apply {
        moveTo(centerX + radius * cos(0f), centerY + radius * sin(0f))
        for (i in 1..6) {
            val angle = i * (2 * PI / 6).toFloat()
            lineTo(centerX + radius * cos(angle), centerY + radius * sin(angle))
        }
        close()
    }
}

private fun DrawScope.drawSpikes(obstacle: Obstacle, obstacleWidth: Float) {
    val spikeHeight = 20.dp.toPx()
    val spikeWidth = 10.dp.toPx()
    val spikesPerObstacle = (obstacleWidth / spikeWidth).toInt()
    val spikeSpacing = obstacleWidth / spikesPerObstacle
    
    val isTopObstacle = obstacle.y == 0f
    val spikeY = if (isTopObstacle) {
        obstacle.y + obstacle.height
    } else {
        obstacle.y - spikeHeight
    }
    
    repeat(spikesPerObstacle) { i ->
        val path = Path().apply {
            val x = obstacle.x + i * spikeSpacing
            if (isTopObstacle) {
                moveTo(x, spikeY)
                lineTo(x + spikeWidth / 2, spikeY + spikeHeight)
                lineTo(x + spikeWidth, spikeY)
            } else {
                moveTo(x, spikeY + spikeHeight)
                lineTo(x + spikeWidth / 2, spikeY)
                lineTo(x + spikeWidth, spikeY + spikeHeight)
            }
        }
        drawPath(path = path, color = ComposeColor.Red.copy(alpha = 0.9f))
    }
} 