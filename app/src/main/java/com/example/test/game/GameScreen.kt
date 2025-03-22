package com.example.test.game

/**
 * Runner Game - Version 2.5
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
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime
                
                if (isInitialized) {
                    Log.d("GameScreen", "Game loop - isPlaying: ${game.isPlaying}")
                    
                    // Check if game was playing but is now stopped (collision)
                    val wasPlaying = game.isPlaying
                    game.update()
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
                    
                    // Update orange state timer (moved outside of tapping check to ensure it expires correctly)
                    if (isOrangeState) {
                        orangeStateTimer += deltaTime * 1000f // Convert to milliseconds since ORANGE_STATE_DURATION is in ms
                        Log.d("GameScreen", "🔸 Orange state timer: $orangeStateTimer, max duration: $ORANGE_STATE_DURATION")
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
                            
                            Log.d("GameScreen", "🔶 Orange state EXPIRED")
                        }
                    }
                    
                    // Handle tap input
                    if (game.isPlaying) {
                        if (isTapping) {
                            // Only update tap duration if actually tapping
                            tapDuration = System.currentTimeMillis() - tapStartTime
                            Log.d("GameScreen", "Tap time: $tapDuration")
                        } else {
                            // If not tapping, ensure that tapDuration is zero
                            // No mode activation here - moved to tap release handler
                            tapDuration = 0L
                        }
                    }
                    
                    // Update trail positions and add new ones when in orange state
                    if (isOrangeState && game.isPlaying) {
                        val birdObj = game.getBird()
                        
                        // Add new position to the trail - position it exactly at the back center of the bird
                        // This is our pivot point for the trail
                        val backOfBird = Offset(
                            birdObj.x,  // Back edge of the bird (left side)
                            birdObj.y + birdObj.height / 2  // Vertical center
                        )
                        
                        // Add new position to the trail
                        trailPositions.add(backOfBird)
                        
                        // Limit trail length - longer for a better plume effect
                        while (trailPositions.size > 30) {
                            trailPositions.removeAt(0)
                        }
                        
                        // Update last Y position for next frame's velocity calculation
                        lastBirdY = birdObj.y
                    } else if (trailPositions.isNotEmpty()) {
                        // Clear trail when not in orange state
                        trailPositions.clear()
                    }
                    
                    // Update game state
                    if (gameState == 0) {
                        if (isTapping) {
                            gameState = 1
                            game.start()
                        }
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
                if (frameCount % 60 == 0) {
                    Log.d("GameScreen", "FPS: ${1f / deltaTime}")
                }
            } catch (e: Exception) {
                Log.e("GameScreen", "Error in game loop: ${e.message}", e)
                delay(16)
            }
        }
    }
    
    // Separate effect to handle initialization
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
                Log.d("GameScreen", "Game initialized in NORMAL mode, current mode: ${game.getCurrentMode()}, tapDuration: $tapDuration")
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
                                gameState = 0
                                
                                // Force the game to normal mode
                                game.setNormalMode()
                                
                                // This will start a fresh game
                                isTapping = true
                                
                                Log.d("GameScreen", "Restarting game after collision with reset state: mode=${game.getCurrentMode()}, isOrangeState=$isOrangeState")
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
                            
                            // Calculate the actual cycle position at release time
                            val orangeTime = 400 // 0.4 seconds (increased from 300ms)
                            val greenTime = 650   // 0.65 seconds (increased slightly from 600ms)
                            val cycleTime = 900  // 0.9 seconds - full cycle time
                            
                            // Use modulo to determine the cyclic position
                            val cyclicDuration = finalTapDuration % cycleTime
                            
                            // Ensure we're not in a special mode already
                            if (game.getCurrentMode() == GameMode.NORMAL && !isOrangeState) {
                                Log.d("GameScreen", "Checking mode activation - cyclic duration: $cyclicDuration")
                                
                                if (cyclicDuration >= orangeTime && cyclicDuration < greenTime) {
                                    // Activate Orange Mode - if we're in the orange part of the cycle
                                    Log.d("GameScreen", "🔥 ORANGE STATE ACTIVATED ON RELEASE! cyclic: $cyclicDuration")
                                    isOrangeState = true
                                    orangeStateTimer = 0f
                                    
                                    // Move scale update responsibility to RunnerGame
                                    // Activate Orange Mode in game class - this will handle scaling
                                    game.activateOrangeMode()
                                    
                                    // Jump with extra power
                                    game.jump(1.2f)
                                } else if (cyclicDuration >= greenTime) {
                                    // Activate Green Mode - if we're in the green part of the cycle
                                    Log.d("GameScreen", "🌿 GREEN MODE ACTIVATED ON RELEASE! cyclic: $cyclicDuration")
                                    
                                    // Activate Green mode in game class
                                    game.activateGreenMode()
                                    
                                    // No jump for Green Mode - player controls directly with touch
                                } else {
                                    // Normal mode - Yellow part of the cycle
                                    // Basic jump with tap power proportional to tap duration
                                    val tapPower = min(2.0f, 1.0f + finalTapDuration / 600f)
                                    Log.d("GameScreen", "Normal jump with power: $tapPower")
                                    game.jump(tapPower)
                                }
                            } else {
                                // Already in a special mode
                                if (isOrangeState) {
                                    Log.d("GameScreen", "Jumping in ORANGE state")
                                    // Use higher jump multiplier for orange mode
                                    game.jump(2.0f)
                                } else if (game.getCurrentMode() == GameMode.GREEN) {
                                    Log.d("GameScreen", "In GREEN mode - not jumping")
                                    // No jump in Green mode - handled by vertical movement
                                } else {
                                    // Fallback - should not happen but just in case
                                    Log.d("GameScreen", "Unexpected state - defaulting to normal jump")
                                    game.jump(1.0f)
                                }
                            }
                            
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
            // Handle vertical drag for Green Mode
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    // Start position for vertical drag
                    // Only handle drag in Green Mode - add additional safety checks
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
            Canvas(modifier = Modifier
                .fillMaxSize()
                .offset(shakeOffset.x.dp, shakeOffset.y.dp) // Apply shake offset
            ) {
                // Update canvas size
                if (size.width > 0 && size.height > 0) {
                    canvasSize = size
                }

                try {
                    // Cache frequently used values
                    val width = size.width
                    val height = size.height
                    val groundHeight = height * 0.1f
                    
                    // Draw background
                    drawRect(
                        color = ComposeColor(0xFF87CEEB),  // Sky blue
                        size = Size(width, height)
                    )
                    
                    // Draw ground
                    drawRect(
                        color = ComposeColor(0xFF90EE90),  // Light green
                        topLeft = Offset(0f, height - groundHeight),
                        size = Size(width, groundHeight)
                    )
                    
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
                    
                    // Draw bird
                    val bird = game.getBird()
                    
                    // Draw trail when in orange state
                    if (isOrangeState && trailPositions.size > 1) {
                        // Get the bird for proper positioning
                        val birdObj = game.getBird()
                        
                        // Define the pivot point at the back center of bird
                        // Ensure it's exactly at the vertical center of the bird
                        val pivotPoint = Offset(
                            birdObj.x,  // Back edge of the bird
                            birdObj.y + (birdObj.height / 2)  // Exact vertical center
                        )
                        
                        // Calculate the bird's movement angle based on vertical velocity
                        // Compare the current position with a recent past position
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
                        
                        Log.d("GameScreen", "Trail angle: $adjustedAngle degrees")
                        
                        // Define trail colors - use vibrant orange for trail but 50% more transparent
                        val trailBaseColor = ComposeColor(1f, 0.5f, 0.1f, 0.35f)  // Original alpha was 0.7f, reduced by 50%
                        
                        // Draw the trail as a plume attached to the back of the bird
                        // Use rotation to follow the bird's movement direction
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
                                pivotPoint.y - bird.height * 0.2f  // More vertical offset for top rounding
                            )
                            
                            val control2 = Offset(
                                pivotPoint.x - plumeLength * 0.7f,
                                pivotPoint.y + bird.height * 0.2f  // More vertical offset for bottom rounding
                            )
                            
                            // Draw the plume with a Bezier curve for smooth shape
                            plumePath.cubicTo(
                                control1.x, control1.y,
                                control2.x, control2.y,
                                plumeEndPoint.x, plumeEndPoint.y
                            )
                            
                            // Draw multiple layers with steadily reducing thickness for a pointy end
                            // Use a custom function to draw the trail with vertical height control
                            val maxWidth = bird.width * 0.7f  // Width of the trail
                            val trailHeight = bird.height * 0.95f  // 95% of bird height at the start
                            val endHeight = bird.height * 0.05f  // 5% of bird height at the end
                            
                            // Calculate points along the Bezier curve for manual drawing with varying height
                            val numPoints = 35 // More points for smoother taper on longer trail
                            for (i in 0 until numPoints) {
                                val t = i / (numPoints - 1f)  // Parametric position (0 to 1)
                                
                                // Calculate position on the curve using Bezier formula
                                val mt = 1 - t
                                val mt2 = mt * mt
                                val mt3 = mt2 * mt
                                val t2 = t * t
                                val t3 = t2 * t
                                
                                // Cubic Bezier formula for center position
                                val centerX = mt3 * pivotPoint.x + 
                                        3 * mt2 * t * control1.x + 
                                        3 * mt * t2 * control2.x + 
                                        t3 * plumeEndPoint.x
                                
                                val centerY = mt3 * pivotPoint.y + 
                                        3 * mt2 * t * control1.y + 
                                        3 * mt * t2 * control2.y + 
                                        t3 * plumeEndPoint.y
                                
                                // Simple smooth height transition from start to end
                                // The cubic function gives a natural easing for the transition
                                val easingFactor = t * t * (3 - 2 * t) // Smooth step function
                                val currentHeight = trailHeight * (1 - easingFactor) + endHeight * easingFactor
                                
                                // Simple width transition - pure quadratic reduction with no bulges
                                // This ensures width only decreases as we move away from the bird
                                val widthFactor = (1 - t) * (1 - t)
                                val segmentWidth = maxWidth * widthFactor
                                
                                // Opacity also reduces toward the end for a fade-out effect
                                val alpha = 0.35f * (1 - t * 0.8f)
                                
                                // Draw vertical line (oval) for the trail segment
                                val halfHeight = currentHeight / 2
                                
                                // Draw oval (stretched circle) at each point
                                drawOval(
                                    color = trailBaseColor.copy(alpha = alpha),
                                    topLeft = Offset(centerX - segmentWidth/2, centerY - halfHeight),
                                    size = Size(segmentWidth, currentHeight)
                                )
                                
                                // Add some subtle sparkles near the start of the trail
                                if (t < 0.5f && i % 5 == 0) {
                                    val sparkleColor = ComposeColor(1f, 0.9f, 0.3f, 0.3f)
                                    val sparkleSize = segmentWidth * 0.3f
                                    
                                    // Contain sparkles within the trail height
                                    val maxVerticalOffset = halfHeight * 0.7f
                                    val offsetX = (Math.random() * segmentWidth * 0.4f - segmentWidth * 0.2f).toFloat()
                                    val offsetY = (Math.random() * maxVerticalOffset * 2 - maxVerticalOffset).toFloat()
                                    
                                    drawCircle(
                                        color = sparkleColor,
                                        radius = sparkleSize,
                                        center = Offset(centerX + offsetX, centerY + offsetY)
                                    )
                                }
                            }
                        }
                    } else {
                        Log.d("GameScreen", "❌ Not drawing trail - isOrangeState: $isOrangeState, trailPositions: ${trailPositions.size}")
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
                        
                        // Define cycle times - 0.3 seconds for each color
                        val orangeTime = 300 // 0.3 seconds
                        val greenTime = 600   // 0.6 seconds
                        val cycleTime = 900  // 0.9 seconds - full cycle time
                        
                        // Use modulo to create a repeating cycle
                        val cyclicDuration = pressDuration % cycleTime
                        
                        // Log the color cycling for debugging
                        if (frameCount % 10 == 0) {
                            Log.d("GameScreen", "Color cycling: duration=$pressDuration, cyclic=$cyclicDuration")
                        }
                        
                        // Determine color based on position in cycle
                        when {
                            cyclicDuration < orangeTime -> ComposeColor.Yellow  // First third: Yellow
                            cyclicDuration < greenTime -> ComposeColor(1f, 0.5f, 0f, 1f)  // Second third: Orange
                            else -> ComposeColor(0.0f, 0.9f, 0.2f, 1f)  // Final third: Green
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
                                color = ComposeColor(0.0f, 0.9f, 0.2f, 0.1f),  // Reduced from 0.15f to 0.1f
                                radius = bubbleRadius,
                                center = Offset(birdCenterX, birdCenterY)
                            )
                            
                            // Draw inner bubble (more transparent)
                            drawCircle(
                                color = ComposeColor(0.0f, 0.9f, 0.2f, 0.05f),  // Reduced from 0.08f to 0.05f
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
                                    color = ComposeColor(0.5f, 1.0f, 0.5f, 0.3f),  // Reduced from 0.4f to 0.3f
                                    radius = sparkleSize,
                                    center = Offset(sparkleX, sparkleY)
                                )
                            }
                            
                            // Draw bubble timer indicator
                            val greenProgress = game.getGreenModeProgress()
                            val timerArcSweepAngle = 360 * (1 - greenProgress)
                            
                            drawArc(
                                color = ComposeColor(0.0f, 0.9f, 0.2f, 0.2f),  // Reduced from 0.3f to 0.2f
                                startAngle = -90f,
                                sweepAngle = timerArcSweepAngle,
                                useCenter = false,
                                topLeft = Offset(birdCenterX - bubbleRadius, birdCenterY - bubbleRadius),
                                size = Size(bubbleRadius * 2, bubbleRadius * 2),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    
                    // Draw score and high score
                    val paint = Paint().apply {
                        color = AndroidColor.WHITE
                        textSize = 60f
                        textAlign = Paint.Align.RIGHT
                    }
                    
                    drawContext.canvas.nativeCanvas.apply {
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