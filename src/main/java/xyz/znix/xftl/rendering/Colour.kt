package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL11

// This is largely copied from Slick's colour class, under the BSD licence.
class Colour(var r: Float, var g: Float, var b: Float, var a: Float) {
    constructor(r: Float, g: Float, b: Float) : this(r, g, b, 1f)

    constructor(red: Int, green: Int, blue: Int) : this(red, green, blue, 255)
    constructor(red: Int, green: Int, blue: Int, alpha: Int) : this(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    constructor(other: Colour) : this(other.r, other.g, other.b, other.a)

    /**
     * Bind this colour to the GL context
     */
    fun bind() {
        GL11.glColor4f(r, g, b, a)
    }

    @Suppress("unused")
    companion object {
        @JvmField
        val transparent = Color(0.0f, 0.0f, 0.0f, 0.0f)

        @JvmField
        val white = Color(1f, 1f, 1f, 1f)

        @JvmField
        val yellow: Color = Color(1f, 1f, 0f, 1f)

        @JvmField
        val red: Color = Color(1f, 0f, 0f, 1f)

        @JvmField
        val blue: Color = Color(0f, 0f, 1f, 1f)

        @JvmField
        val green: Color = Color(0f, 1f, 0f, 1f)

        @JvmField
        val black: Color = Color(0f, 0f, 0f, 1f)

        @JvmField
        val gray = Color(0.5f, 0.5f, 0.5f, 1f)

        @JvmField
        val cyan: Color = Color(0f, 1f, 1f, 1f)

        @JvmField
        val darkGray = Color(0.3f, 0.3f, 0.3f, 1f)

        @JvmField
        val lightGray = Color(0.7f, 0.7f, 0.7f, 1f)

        @JvmField
        val pink = Color(255, 175, 175, 255)

        @JvmField
        val orange = Color(255, 200, 0, 255)

        @JvmField
        val magenta = Color(255, 0, 255, 255)
    }
}

// Migration aid
typealias Color = Colour
