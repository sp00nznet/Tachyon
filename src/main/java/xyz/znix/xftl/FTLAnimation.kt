package xyz.znix.xftl

import org.newdawn.slick.Color
import org.newdawn.slick.Image
import org.newdawn.slick.Renderable

class FTLAnimation(val spec: AnimationSpec, var loop: Boolean, val speed: Float, val backwards: Boolean) : Renderable {
    val width: Int = spec.spriteAt(0).width
    val height: Int = spec.spriteAt(0).height

    var timer: Float = 0f
        set(value) {
            field = value
            updateImage()
        }

    val isStopped: Boolean get() = timer >= duration && !loop

    val frameCount: Int get() = spec.length

    var isPaused: Boolean = false

    var frame: Int = -1
        private set

    var duration: Float = spec.totalTime
        set(value) {
            field = value
            updateImage()
        }

    lateinit var currentFrame: Image
        private set

    init {
        // Initialise the current index and frame
        updateImage()
    }

    fun update(dt: Float) {
        if (isPaused) {
            return
        }

        timer += dt * speed

        if (timer >= duration) {
            when {
                loop -> timer -= duration
                else -> timer = duration
            }
        }
    }

    private fun updateImage() {
        val effectiveTimer = if (backwards) duration - timer else timer
        val progress = effectiveTimer / duration // 0-1 progress through the animation
        frame = (frameCount * progress).toInt().coerceIn(0 until frameCount)

        currentFrame = spec.spriteAt(frame)
    }

    override fun draw(x: Float, y: Float) {
        draw(x, y, Color.white)
    }

    override fun draw(x: Float, y: Float, filter: Color) {
        draw(x, y, width.f, height.f, filter)
    }

    override fun draw(x: Float, y: Float, width: Float, height: Float) {
        draw(x, y, width, height, Color.white)
    }

    override fun draw(x: Float, y: Float, width: Float, height: Float, filter: Color) {
        currentFrame.draw(x, y, width, height, filter)
    }
}
