package com.example.test.game

enum class GameMode {
    NORMAL,  // Yellow - Regular jumping mode
    ORANGE,  // Orange - Speed boost and trail effect
    GREEN,   // Green - Vertical movement control
    BLUE     // Blue - Reversed gravity
}

enum class ObstacleType {
    NARROW,
    NORMAL,
    WIDE,
    SPIKED
}

enum class TextureType {
    DIAGONAL_BRICKS,
    HEXAGONAL,
    BASIC
}
