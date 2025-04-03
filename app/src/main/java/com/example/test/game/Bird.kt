class Bird(
    val initialX: Float, 
    val initialY: Float,
    var width: Float = 50f,
    var height: Float = 50f,
    var jumpVelocity: Float = 10f
) {
    var x: Float = initialX
    var y: Float = initialY
    var velocityY: Float = 0f
    var rotation: Float = 0f
    var scale: Float = 1f
    var visualOffsetX: Float = 0f
    
    // Secondary constructor for backward compatibility
    constructor(x: Float, y: Float, width: Float, height: Float) : 
        this(x, y, width, height, 10f)
    
    fun reset() {
        x = initialX
        y = initialY
        velocityY = 0f
        rotation = 0f
        scale = 1f
        visualOffsetX = 0f
    }
} 