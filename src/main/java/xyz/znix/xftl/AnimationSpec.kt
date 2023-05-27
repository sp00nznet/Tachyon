package xyz.znix.xftl

import org.newdawn.slick.Animation
import org.newdawn.slick.Image
import java.lang.reflect.Field

class AnimationSpec(
    val sheet: Animations.SpriteSheetSpec,
    val name: String,
    val x: Int, val y: Int,
    val length: Int,
    val time: Float
) {
    val totalTime: Float get() = time * length

    fun start() = start(1f, false)

    fun start(tmult: Float, backwards: Boolean): Animation {
        val anim = Animation(sheet.sheet, x, y, x + length - 1, y, true, (tmult * 1000 * time).toInt(), false)

        // Horrid solution since this is inaccessible, but what else can we do?
        if (backwards) {
            val frames = ANIMATION_FRAMES.get(anim) as ArrayList<*>
            frames.reverse()
        }

        return anim
    }

    /**
     * It would appear some weapon animations run half as fast as what's set in their XML. No idea why, and
     * it doesn't happen on crew animations (or at least rock putting out fire, which was the one I tested).
     */
    fun startHalfSpeed() = start(2f, false)

    fun spriteAt(i: Int): Image {
        if (i >= length) throw IndexOutOfBoundsException(i)
        return sheet.sheet.getSprite(x + i, y)
    }

    companion object {
        @JvmStatic
        private val ANIMATION_FRAMES: Field

        init {
            val af = Animation::class.java.getDeclaredField("frames")
            af.isAccessible = true
            ANIMATION_FRAMES = af
        }
    }
}
