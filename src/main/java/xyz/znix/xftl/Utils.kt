package xyz.znix.xftl

import org.jdom2.Element
import org.lwjgl.opengl.GL11
import org.newdawn.slick.Color
import org.newdawn.slick.Image
import xyz.znix.xftl.math.IPoint

// Make <int>.f a shorthand for <int>.toFloat(), cleaning things up a lot
val Int.f get() = toFloat()

fun Image.draw(x: Int, y: Int) = draw(x.f, y.f)
fun Image.draw(pos: IPoint) = draw(pos.x.f, pos.y.f)

fun Image.drawSection(x: Int, y: Int, width: Int, height: Int, offsetX: Int = 0, offsetY: Int = 0, colour: Color? = null) {
    draw(x.f, y.f, x.f + width, y.f + height, offsetX.f, offsetY.f,
            offsetX + width.f, offsetY + height.f, colour ?: Color.white)
}

fun Element.requireAttributeValue(name: String): String {
    return getAttributeValue(name) ?: error("Missing mandatory attribute $name on element ${this.name}")
}

fun Element.requireAttributeValueInt(name: String): Int {
    return requireAttributeValue(name).toIntOrNull() ?: error("Could not parse attribute $name as int on ${this.name}")
}

fun Element.mapChildrenText(childName: String): List<String> {
    return children.map {
        check(it.name == childName) { "Mapping child nodes to text, found unknown child ${it.name}" }
        check(it.attributes.isEmpty()) { "Mapping child nodes to text, child ${it.name} contains attributes" }
        it.textTrim
    }
}

fun Float.lerp(other: Float, proportion: Float): Float {
    val diff = other - this
    return this + diff * proportion.coerceAtLeast(0f).coerceAtMost(1f)
}

/**
 * Interpolate a colour between [this] and [other]. When [proportion] is 0 it's entirely [this], 1 is entirely [other].
 */
fun Color.lerp(other: Color, proportion: Float): Color {
    if (proportion <= 0f) return this
    if (proportion >= 1f) return other
    return Color(
            r.lerp(other.r, proportion),
            g.lerp(other.g, proportion),
            b.lerp(other.b, proportion),
            a.lerp(other.a, proportion)
    )
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
