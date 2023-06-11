package xyz.znix.xftl

import xyz.znix.xftl.rendering.Image

class AnimationSpec(
    val sheet: Animations.SpriteSheetSpec,
    val name: String,
    val x: Int, val y: Int,
    val length: Int,
    val time: Float
) {
    val totalTime: Float get() = time * length

    fun startSingle() = startSingle(1f, false)

    fun startLooping() = FTLAnimation(this, true, 1f, false)

    fun startSingle(speed: Float, backwards: Boolean): FTLAnimation {
        return FTLAnimation(this, false, speed, backwards)
    }

    fun spriteAt(i: Int): Image {
        if (i >= length) throw IndexOutOfBoundsException(i)
        return sheet.sheet.getSprite(x + i, y)
    }
}
