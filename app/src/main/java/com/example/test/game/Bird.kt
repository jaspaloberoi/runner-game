class Bird(val initialX: Float, val initialY: Float) {
    var x: Float = initialX
    var y: Float = initialY
    var velocityY: Float = 0f
    var rotation: Float = 0f
    var width: Float = 50f
    var height: Float = 50f
    var scale: Float = 1f
    
    fun reset() {
        x = initialX
        y = initialY
        velocityY = 0f
        rotation = 0f
        scale = 1f
    }
} 