package com.example.test.game

/**
 * Runner Game - Version 2.2
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
    var game by remember { mutableStateOf(RunnerGame(context, soundManager)) }
    var score by remember { mutableStateOf(0) }
    var highScore by remember { mutableStateOf(0) }
    var shakeOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    var shakeDuration by remember { mutableStateOf(0) }
    var isShaking by remember { mutableStateOf(false) }
    
    // Bird trail and orange state
    var isOrangeState by remember { mutableStateOf(false) }
    var orangeStateTimer by remember { mutableStateOf(0f) }
    var tapStartTime by remember { mutableStateOf(0L) }
    var tapDuration by remember { mutableStateOf(0L) }
    var lastBirdY by remember { mutableStateOf(0f) }
    val trailPositions = remember { mutableStateListOf<Offset>() }
    
    // Track the transition from orange to normal mode while tapping
    var isTransitioningFromOrangeState by remember { mutableStateOf(false) }
    
    // Constants
    val ORANGE_STATE_DURATION = 5000f  // 5 seconds in milliseconds
    
    // Game state
    var isTapping by remember { mutableStateOf(false) }
    var gameState by remember { mutableStateOf(0) }
    var lastFrameTime by remember { mutableStateOf(System.nanoTime()) }
    var frameCount by remember { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // This state is used to force Canvas recomposition
    var frameKey by remember { mutableStateOf(0) }
    
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
                    game.update(deltaTime, isOrangeState)
                    if (wasPlaying && !game.isPlaying) {
                        startScreenShake()
                        // Reset tapping state when game ends
                        isTapping = false
                        // Reset orange state when game ends
                        isOrangeState = false
                        orangeStateTimer = 0f
                        isTransitioningFromOrangeState = false
                        tapDuration = 0L
                        // Reset bird scale when game ends
                        game.updateBirdScale(1.0f)
                    }
                    
                    // Update orange state timer (moved outside of tapping check to ensure it expires correctly)
                    if (isOrangeState) {
                        orangeStateTimer += deltaTime * 1000f // Convert to milliseconds since ORANGE_STATE_DURATION is in ms
                        Log.d("GameScreen", "🔸 Orange state timer: $orangeStateTimer, max duration: $ORANGE_STATE_DURATION")
                        if (orangeStateTimer >= ORANGE_STATE_DURATION) {
                            isOrangeState = false
                            orangeStateTimer = 0f
                            // Reset bird scale when orange mode expires
                            game.updateBirdScale(1.0f)
                            
                            // Set transition flag if tap is still being held
                            if (isTapping) {
                                isTransitioningFromOrangeState = true
                                // Force a recalculation of tap duration to update the color
                                tapStartTime = System.currentTimeMillis() - tapDuration
                                Log.d("GameScreen", "🔄 Transitioning from orange state while tapping")
                                
                                // Add additional measures to force color update
                                // This attempts to break any stale color calculation
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
                            tapDuration = System.currentTimeMillis() - tapStartTime
                            Log.d("GameScreen", "Tap time: $tapDuration")
                            
                            // Remove orange state activation from here - only show color change while pressing
                            // Just track if the tap is long enough to trigger orange mode on release
                            // Don't actually enter orange mode yet
                                
                            // Update bird's jump based on tap duration
                            if (tapDuration > 100) {
                                game.updateChargingJump(tapDuration)
                            }
                        } else {
                            // Check for orange state activation on release
                            if (tapDuration > 200 && !isOrangeState) {
                                isOrangeState = true
                                orangeStateTimer = 0f
                                game.applySoundEffect(SoundEffectType.DOUBLE_BEEP)
                                
                                // Apply scale increase ONLY when entering orange mode (not during tap)
                                val orangeScale = 1.2f
                                game.updateBirdScale(orangeScale)
                                
                                Log.d("GameScreen", "🔥 ORANGE STATE ACTIVATED ON RELEASE! tapDuration: $tapDuration")
                            } else {
                                // Reset to normal scale when orange mode not activated
                                if (!isOrangeState) {
                                    game.updateBirdScale(1.0f)
                                }
                            }
                            
                            tapDuration = 0L
                            Log.d("GameScreen", "Tap released, resetting tapDuration")
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
                isInitialized = true
            } catch (e: Exception) {
                Log.e("GameScreen", "Error initializing game: ${e.message}", e)
            }
        }
    }
    
    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { _ ->
                    if (isInitialized) {
                        Log.d("GameScreen", "Tap down detected at ${System.currentTimeMillis()}")
                        
                        // If the game is over, make sure orange state variables are reset
                        if (!game.isPlaying) {
                            isOrangeState = false
                            orangeStateTimer = 0f
                            isTransitioningFromOrangeState = false
                            tapDuration = 0L
                        }
                        
                        tapStartTime = System.currentTimeMillis()
                        
                        // Start tracking tap duration
                        isTapping = true
                        
                        // Call game's onTapDown
                        game.onTapDown()
                        
                        // Wait for release
                        val released = tryAwaitRelease()
                        
                        // Stop tracking tap duration
                        isTapping = false
                        
                        // Reset transition flag when releasing tap
                        isTransitioningFromOrangeState = false
                        
                        if (released) {
                            Log.d("GameScreen", "Tap up detected at ${System.currentTimeMillis()}")
                            // Pass the orange state information to onTapUp
                            if (isOrangeState) {
                                Log.d("GameScreen", "Jumping in ORANGE state")
                            }
                            game.onTapUp(tapDuration, isOrangeState)
                        }
                    } else {
                        Log.d("GameScreen", "Tap ignored - game not initialized")
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
                    } else if (isTapping && game.isPlaying) {
                        val currentTime = System.currentTimeMillis()
                        val currentTapDuration = currentTime - tapStartTime
                        
                        // Scale from 0 to 1 based on the new calculation (100-500ms)
                        val normalizedDuration = (currentTapDuration.coerceIn(100L, 500L) - 100L) / 400f
                        
                        // DON'T update bird scale during tap - color change only during charging
                        // This prevents double scaling when entering orange mode
                        // game.updateBirdScale(scale) - REMOVED
                        
                        // Transition from yellow to orange as tap duration increases (visual feedback only)
                        ComposeColor(
                                red = 1f,
                            green = 1f - (normalizedDuration * 0.5f),  // Only reduce to 0.5 for orange
                                blue = 0f,
                                alpha = 1f
                            )
                        } else {
                        // Reset transition flag if we're not tapping anymore
                        isTransitioningFromOrangeState = false
                        ComposeColor.Yellow
                        }
                        
                    // Draw bird as a square to match hitbox (no need for orangeSizeMultiplier here anymore)
                    // Since we're handling the scaling in updateBirdScale
                        drawRect(
                            color = birdColor,
                            topLeft = Offset(bird.x, bird.y),
                            size = Size(bird.width, bird.height)
                        )
                        
                        // Add eye for visual appeal
                        drawCircle(
                        color = ComposeColor.Black,
                            radius = bird.width / 6,
                            center = Offset(bird.x + bird.width * 0.7f, bird.y + bird.height * 0.4f)
                        )
                    
                    // Draw score and high score
                    val paint = Paint().apply {
                        color = AndroidColor.WHITE
                        textSize = 60f
                        textAlign = Paint.Align.LEFT
                    }
                    
                    drawContext.canvas.nativeCanvas.apply {
                        drawText("Score: $score", 50f, 100f, paint)
                        drawText("High Score: $highScore", 50f, 170f, paint)
                        
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