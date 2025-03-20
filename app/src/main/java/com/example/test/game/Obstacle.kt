package com.example.test.game

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

data class Obstacle(
    val x: Float,
    var y: Float,
    val height: Float,
    val type: ObstacleType,
    var passed: Boolean = false,
    val isMoving: Boolean = false,
    var movingUp: Boolean = true,
    val moveSpeed: Float = 0f,
    val textureType: TextureType = TextureType.values().random()
)

enum class ObstacleType {
    NARROW,
    NORMAL,
    WIDE,
    SPIKED
} 