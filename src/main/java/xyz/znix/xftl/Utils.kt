package xyz.znix.xftl

import org.lwjgl.opengl.GL11
import org.newdawn.slick.Image
import xyz.znix.xftl.math.IPoint

// Make <int>.f a shorthand for <int>.toFloat(), cleaning things up a lot
val Int.f get() = toFloat()

fun Image.draw(x: Int, y: Int) = draw(x.f, y.f)
fun Image.draw(pos: IPoint) = draw(pos.x.f, pos.y.f)

fun Image.drawSection(x: Int, y: Int, width: Int, height: Int, offsetX: Int = 0, offsetY: Int = 0) {
    draw(x.f, y.f, x.f + width, y.f + height, offsetX.f, offsetY.f, offsetX + width.f, offsetY + height.f)
}

object Utils {
    /**
     * Draw something with stenciling.
     *
     * You draw something in the [stencil] function which is then used to control
     * the rendering of [drawing].
     *
     * If [mode] is [StencilMode.BLOCKING] then any non-transparent pixels drawn in the
     * [stencil] function will prevent pixels from being drawn at the same location. If
     * [mode] is [StencilMode.MASKING] then only areas with non-transparent pixels drawn
     * will appear from [drawing].
     */
    fun drawStenciled(mode: StencilMode, stencil: () -> Unit, drawing: () -> Unit) {
        // Mask out everything except the contents of the panel, where the ship may be drawn
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)
        GL11.glStencilMask(0xff)
        GL11.glEnable(GL11.GL_STENCIL_TEST)

        // Draw the mask into the stencil buffer
        // First, discard any pixel below 10% transparency:
        GL11.glEnable(GL11.GL_ALPHA_TEST)
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f)

        // Any pixel coming through will fail the stencil test, and it's value will replace the
        // zero initially in the stencil buffer
        GL11.glStencilFunc(GL11.GL_NEVER, 1, 0xFF)
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_KEEP, GL11.GL_KEEP)

        stencil()

        // Turn the alpha test off, so things draw normally again
        GL11.glDisable(GL11.GL_ALPHA_TEST)

        // Find the stencil value that allows the stenciled image to be draw. A value of
        // one only allows drawing if that pixel was stenciled, a value of zero blocks drawing
        // anywhere that was stenciled.
        val requiredValue = when (mode) {
            StencilMode.BLOCKING -> 0
            StencilMode.MASKING -> 1
        }
        GL11.glStencilFunc(GL11.GL_EQUAL, requiredValue, 0xFF)

        // Whatever is drawn does not affect the stencil mask
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)

        drawing()

        // Don't break anything else
        GL11.glDisable(GL11.GL_STENCIL_TEST)
    }

    enum class StencilMode {
        BLOCKING,
        MASKING,
    }
}
