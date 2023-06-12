package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL11
import org.newdawn.slick.Color
import xyz.znix.xftl.math.Matrix3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A custom graphics class, whose functions generally match that of [org.newdawn.slick.Graphics].
 *
 * See it's JavaDoc for descriptions of these methods.
 */
class Graphics {
    var lineWidth: Float = 1f

    // TODO switch everything over to the colour variable
    var color: Color
        get() = colour
        set(value) {
            colour = value
        }

    var colour: Color = Color.white

    // The stack of transforms from pushTransform/popTransform calls.
    private val transformStack = ArrayList<Matrix3f>()

    // The 'live' transform that we're currently rendering with.
    // This is NOT on transformStack until pushTransform is called.
    private var transform = Matrix3f()

    fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        Texture.unbind()
        colour.bind()
        GL11.glBegin(GL11.GL_QUADS)

        glVertexTransformed(x, y)
        glVertexTransformed(x, y + height)
        glVertexTransformed(x + width, y + height)
        glVertexTransformed(x + width, y)

        GL11.glEnd()
    }

    fun drawRect(x: Float, y: Float, widthMinusOne: Float, heightMinusOne: Float) {
        // I don't think the line width is supposed to affect rectangles?
        GL11.glLineWidth(1f)

        Texture.unbind()
        colour.bind()
        GL11.glBegin(GL11.GL_LINE_LOOP)

        // Offset the line points by 0.5, so int values fall in the middle
        // of a pixel, ensuring it gets rasterised into the correct place.
        val ox = x + 0.5f
        val oy = y + 0.5f

        glVertexTransformed(ox, oy)
        glVertexTransformed(ox, oy + heightMinusOne)
        glVertexTransformed(ox + widthMinusOne, oy + heightMinusOne)
        glVertexTransformed(ox + widthMinusOne, oy)

        GL11.glEnd()
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        // Use rectangles to draw straight lines, copiedd from Slick's graphics class.
        // This ensures that the vast majority of lines won't vary between OpenGL
        // implementations, which can vary by upto one pixel.
        // Also, it means we won't have to change all our current line-rendering
        // stuff, since this works the same.
        val rectWidth = lineWidth - 1
        if (x1 == x2) {
            val yMin = min(y1, y2)
            val height = abs(y1 - y2)
            val step = 1f
            fillRect(x1 - (rectWidth / 2f), yMin - (rectWidth / 2f), rectWidth + step, height + rectWidth + step)
            return
        } else if (y1 == y2) {
            val xMin = min(x1, x2)
            val width = abs(x1 - x2)
            val step = 1f
            fillRect(xMin - (rectWidth / 2.0f), y1 - (rectWidth / 2.0f), width + rectWidth + step, rectWidth + step)
            return
        }

        Texture.unbind()
        colour.bind()
        GL11.glLineWidth(lineWidth)

        GL11.glBegin(GL11.GL_LINES)
        glVertexTransformed(x1, y1)
        glVertexTransformed(x2, y2)
        GL11.glEnd()
    }

    fun fillOval(x: Float, y: Float, width: Float, height: Float) {
        fillArc(x, y, width, height, 50, 0f, 360f)
    }

    fun drawOval(x: Float, y: Float, width: Float, height: Float) {
        drawArc(x, y, width, height, 50, 0f, 360f)
    }

    fun pushTransform() {
        transformStack.add(Matrix3f(transform))
    }

    fun popTransform() {
        transform = transformStack.removeAt(transformStack.size - 1)
    }

    fun translate(x: Float, y: Float) {
        // Do transform = transform*translate which means we're effectively
        // using a translation matrix which is an identity matrix, aside from
        // its right-hand column. That then gets dot-product-ed into the
        // top and middle row of the transform matrix to get the new right-hand
        // column of the transform matrix.
        transform.m02 = x * transform.m00 + y * transform.m01 + transform.m02
        transform.m12 = x * transform.m10 + y * transform.m11 + transform.m12
    }

    /**
     * Rotates subsequent drawing by [degrees], around the point at [pointX],[pointY].
     */
    fun rotate(pointX: Float, pointY: Float, degrees: Float) {
        // Translate so the rotation point is at the origin
        translate(pointX, pointY)

        val angle = Math.toRadians(degrees.toDouble()).toFloat()

        // Effectively, we build a 2x2 matrix to use for our rotations. We can
        // trivially expand it by assuming all but the bottom-right entries of
        // the 3rd row and column are zero, since we're not applying a translation.
        // In case you're not familiar with transform matrices, we're effectively
        // translating the x and y unit vectors in our post-rotation space
        // into the pre-rotation space.
        val m00 = cos(angle) // X part of the x unit vector
        val m10 = sin(angle) // Y part of the x unit vector
        val m01 = -sin(angle) // X part of the y unit vector
        val m11 = cos(angle) // Y part of the y unit vector

        // Do current*rotate matrix multiply, which only affects the top-left 2x2.
        // The bottom row is always 0,0,1 and for the right column, our rotation
        // matrix's right column is implicitly 0,0,1 (as stated above) so it'll
        // just be multiplying the current values by one.
        val newM00 = m00 * transform.m00 + m10 * transform.m01
        val newM10 = m00 * transform.m10 + m10 * transform.m11
        val newM01 = m01 * transform.m00 + m11 * transform.m01
        val newM11 = m01 * transform.m10 + m11 * transform.m11

        transform.m00 = newM00
        transform.m10 = newM10
        transform.m01 = newM01
        transform.m11 = newM11

        // Undo the origin point translation we did at the start
        translate(-pointX, -pointY)
    }

    fun scale(xScaling: Float, yScaling: Float) {
        // Multiplying by a scaled identity matrix (where m00 and m11
        // come from the x and y scaling factors) results in scaling the
        // first two columns.
        transform.m00 *= xScaling
        transform.m10 *= xScaling
        transform.m01 *= yScaling
        transform.m11 *= yScaling
    }

    fun clear(colour: Color) {
        GL11.glClearColor(colour.r, colour.g, colour.b, colour.a)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
    }

    fun checkNoPushedTransforms() {
        require(transformStack.size == 0)
    }

    fun loadIdentityMatrix() {
        transform = Matrix3f()
    }

    /**
     * Use this instance's transform matrix for image rendering.
     */
    fun markCurrentImageTransformSource() {
        CURRENT = this
    }

    private fun drawArc(
        x1: Float, y1: Float, width: Float, height: Float,
        segments: Int, start: Float, end: Float
    ) {
        Texture.unbind()
        colour.bind()

        GL11.glBegin(GL11.GL_LINE_STRIP)
        addArcVertices(x1, y1, width, height, segments, start, end)
        GL11.glEnd()
    }

    private fun fillArc(
        x1: Float, y1: Float, width: Float, height: Float,
        segments: Int, start: Float, end: Float
    ) {
        Texture.unbind()
        colour.bind()

        GL11.glBegin(GL11.GL_TRIANGLE_FAN)
        addArcVertices(x1, y1, width, height, segments, start, end)
        GL11.glEnd()
    }

    private fun addArcVertices(
        x1: Float, y1: Float, width: Float, height: Float,
        segments: Int, start: Float, end: Float
    ) {
        // This was copied from Slick's graphics class, factored out
        // from drawing filled and non-filled arcs.

        require(end >= start)

        Texture.unbind()
        colour.bind()

        val cx = x1 + width / 2.0f
        val cy = y1 + height / 2.0f
        val step = 360 / segments
        glVertexTransformed(cx, cy)
        var a = start.toInt()
        while (a < (end + step).toInt()) {
            var ang = a.toFloat()
            if (ang > end) {
                ang = end
            }
            val x = (cx + cos(Math.toRadians(ang.toDouble())) * width / 2.0f).toFloat()
            val y = (cy + sin(Math.toRadians(ang.toDouble())) * height / 2.0f).toFloat()
            glVertexTransformed(x, y)
            a += step
        }
    }

    companion object {
        private var CURRENT: Graphics? = null

        fun getTextureTransformMatrix(): Matrix3f {
            return CURRENT!!.transform
        }

        /**
         * Call glVertex3f, while multiplying the coordinates by the transform matrix.
         */
        fun glVertexTransformed(baseX: Float, baseY: Float) {
            val m = getTextureTransformMatrix()

            // 3x3 matrix multiply, with (baseX,baseY,1)
            val x = baseX * m.m00 + baseY * m.m01 + m.m02
            val y = baseX * m.m10 + baseY * m.m11 + m.m12
            // Don't need to calculate a W value

            GL11.glVertex3f(x, y, 0f)
        }
    }
}
