package com.example.test

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport

class GameScreen(
    private val game: RunnerGame,
    private val soundManager: SoundManager
) : Screen, InputProcessor {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(800f, 480f, camera)
    private val renderer = ShapeRenderer()
    private val font = BitmapFont()
    private val batch = SpriteBatch()
    private var isTapping = false
    private var tapStartTime = 0L
    private var lastTapTime = 0L
    private var tapDuration = 0f
    private var jumpMultiplier = 1f
    private var isOrangeState = false
    private var orangeStateTimer = 0f
    private val ORANGE_STATE_DURATION = 5f
    private val orangeTrail = mutableListOf<Vector2>()
    private val MAX_TRAIL_LENGTH = 30
    private var birdColor = Color.YELLOW

    override fun show() {
        Gdx.input.inputProcessor = this
    }

    override fun render(delta: Float) {
        // Set background color based on orange state - DRAMATIC CHANGE
        if (isOrangeState) {
            // BRIGHT ORANGE BACKGROUND for visibility testing
            Gdx.gl.glClearColor(0.8f, 0.4f, 0f, 1f)
        } else {
            // Normal black background
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Log orange state on every frame for debugging
        Gdx.app.log("STATE", "isOrangeState: $isOrangeState")

        // Update game state
        game.update(delta, isOrangeState)

        // Update orange state timer
        if (isOrangeState) {
            orangeStateTimer += delta
            Gdx.app.log("DEBUG", "Orange state timer: $orangeStateTimer / $ORANGE_STATE_DURATION seconds")
            
            if (orangeStateTimer >= ORANGE_STATE_DURATION) {
                isOrangeState = false
                orangeStateTimer = 0f
                Gdx.app.log("DEBUG", "Orange state expired after $ORANGE_STATE_DURATION seconds")
            }
        }

        // Update trail
        if (isOrangeState) {
            orangeTrail.add(Vector2(game.bird.x, game.bird.y))
            if (orangeTrail.size > MAX_TRAIL_LENGTH) {
                orangeTrail.removeAt(0)
            }
        } else {
            orangeTrail.clear()
        }

        // Bird color logic - extremely simplified
        birdColor = if (isOrangeState) {
            Color.ORANGE  // Use built-in ORANGE constant for consistency
        } else {
            Color.YELLOW
        }

        // Begin rendering
        camera.update()
        renderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        // Draw bird with a MUCH larger size to make color more visible
        renderer.begin(ShapeRenderer.ShapeType.Filled)
        renderer.color = birdColor
        renderer.circle(game.bird.x, game.bird.y, 40f)  // MUCH larger for visibility
        renderer.end()

        // Draw orange trail with higher opacity
        if (isOrangeState && orangeTrail.size > 1) {
            renderer.begin(ShapeRenderer.ShapeType.Line)
            renderer.color = Color.RED  // Use RED for contrast with orange background
            for (i in 0 until orangeTrail.size - 1) {
                renderer.line(orangeTrail[i], orangeTrail[i + 1])
            }
            renderer.end()
        }

        // Draw score
        batch.begin()
        font.draw(batch, "Score: ${game.score}", 20f, 460f)
        batch.end()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun pause() {
        // Handle pause
    }

    override fun resume() {
        // Handle resume
    }

    override fun hide() {
        // Clean up resources
    }

    override fun dispose() {
        renderer.dispose()
        font.dispose()
        batch.dispose()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        // LOUD DEBUGGING
        Gdx.app.log("TOUCH", "!!!!!!!!!!!!! TOUCH DOWN DETECTED !!!!!!!!!!!!!!")
        
        if (!game.isGameOver && !game.isGameStarted) {
            game.startGame()
            Gdx.app.log("TOUCH", "Game started")
        }
        
        if (!game.isGameOver) {
            isTapping = true
            tapStartTime = System.currentTimeMillis()
            lastTapTime = tapStartTime
            
            // SIMPLIFIED: Immediately activate orange state on any tap for testing
            Gdx.app.log("TOUCH", "Setting isOrangeState = true")
            isOrangeState = true
            orangeStateTimer = 0f
            
            // Try to play sound
            try {
                soundManager.playDoubleBeep()
                Gdx.app.log("SOUND", "Played double beep sound")
            } catch (e: Exception) {
                Gdx.app.log("SOUND", "ERROR playing sound: ${e.message}")
            }
            
            Gdx.app.log("TOUCH", "Orange state activated! Current state: $isOrangeState")
        }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!game.isGameOver) {
            isTapping = false
        }
        return true
    }

    // Additional InputProcessor methods
    override fun keyDown(keycode: Int): Boolean = false
    override fun keyUp(keycode: Int): Boolean = false
    override fun keyTyped(character: Char): Boolean = false
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!game.isGameOver) {
            isTapping = false
        }
        return true
    }
} 