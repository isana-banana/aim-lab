package com.isanabanana.aim

/** Position, rotation (degrees), and scale in virtual-world coordinates. */
data class Transform(
    var x: Float = 0f,
    var y: Float = 0f,
    var rotation: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f
)
