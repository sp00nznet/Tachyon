package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20
import org.newdawn.slick.Color
import xyz.znix.xftl.f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Instances of this class accumulate a bunch of stuff
 * to render, and then send it to the graphics API when flushed.
 *
 * This can be used to improve rendering performance for
 * stuff like text where there's otherwise a silly number
 * of draw calls.
 */
class BulkImageRenderer(var image: Image) : AutoCloseable {
    private var vbo: Int = 0
    private var data: ByteBuffer = generateBuffer(128)
    private var numVerts = 0

    private val transformMatData = generateBuffer(4 * 9).asFloatBuffer()

    // Same signature as Image.draw
    fun pushImage(x: Float, y: Float) {
        val w = image.width.f
        val h = image.height.f
        pushVert(x + 0f, y + 0f, 0f, 0f)
        pushVert(x + w, y + 0f, w, 0f)
        pushVert(x + w, y + h, w, h)
        pushVert(x + 0f, y + h, 0f, h)
    }

    fun pushImage(
        x1: Float, y1: Float, // Corner A on-screen
        x2: Float, y2: Float, // Corner B on-screen
        u1: Float, v1: Float, // Corner A in-texture
        u2: Float, v2: Float, // Corner B in-texture
        colour: Color
    ) {
        pushImage(
            x1, y1, x2, y2,
            u1, v1, u2, v2,
            colour.r, colour.g, colour.b, colour.a
        )
    }

    fun pushImage(
        x1: Float, y1: Float, // Corner A on-screen
        x2: Float, y2: Float, // Corner B on-screen
        u1: Float, v1: Float, // Corner A in-texture
        u2: Float, v2: Float, // Corner B in-texture
        r: Float, g: Float, b: Float, a: Float // Modulate colour
    ) {
        pushVert(x1, y1, u1, v1, r, g, b, a)
        pushVert(x2, y1, u2, v1, r, g, b, a)
        pushVert(x2, y2, u2, v2, r, g, b, a)
        pushVert(x1, y2, u1, v2, r, g, b, a)
    }

    fun pushVert(x: Float, y: Float, u: Float, v: Float) {
        pushVert(x, y, u, v, 1f, 1f, 1f, 1f)
    }

    fun pushVert(
        x: Float, y: Float,
        u: Float, v: Float,
        r: Float, g: Float, b: Float, a: Float // Modulate colour
    ) {
        checkSize(4 * 4) // 4 32-bit ints

        data.putFloat(x)
        data.putFloat(y)
        data.putFloat(u)
        data.putFloat(v)

        data.putFloat(r)
        data.putFloat(g)
        data.putFloat(b)
        data.putFloat(a)

        numVerts++
    }

    private fun checkSize(required: Int) {
        if (data.remaining() >= required)
            return

        val current = data.position() + data.remaining()
        val newData = generateBuffer(max(required, current * 2))

        // Copy the old data into the new buffer
        data.flip()
        newData.put(data)
        data = newData
    }

    private fun generateBuffer(size: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(size).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    fun flush() {
        if (numVerts == 0)
            return

        if (vbo == 0) {
            vbo = glGenBuffers()
        }

        image.bind() // Binds to GL_TEXTURE_2D

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        data.flip()
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW)

        // Packed as position then UV
        GL20.glEnableVertexAttribArray(posAttrib)
        GL20.glEnableVertexAttribArray(uvAttrib)
        GL20.glEnableVertexAttribArray(colourAttrib)
        GL20.glVertexAttribPointer(posAttrib, 2, GL11.GL_FLOAT, false, 32, 0)
        GL20.glVertexAttribPointer(uvAttrib, 2, GL11.GL_FLOAT, false, 32, 8)
        GL20.glVertexAttribPointer(colourAttrib, 4, GL11.GL_FLOAT, false, 32, 16)

        GL20.glUseProgram(shader.handle)

        // Set the transform uniforms
        updatePosTransformMatrix()
        updateUvTransformMatrix()

        GL11.glDrawArrays(GL11.GL_QUADS, 0, numVerts)

        // Cleanup
        GL20.glDisableVertexAttribArray(posAttrib)
        GL20.glDisableVertexAttribArray(uvAttrib)
        GL20.glDisableVertexAttribArray(colourAttrib)
        GL20.glUseProgram(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        // Clear out the buffer for subsequent renders
        data.clear()
        numVerts = 0

        return
    }

    private fun updatePosTransformMatrix() {
        // We have to transform from pixel-space to NDC (normalised
        // device coordinates), which go from -1 to 1 in both x and y.
        // Also note that positive is up, instead of down, so we have
        // to flip that around.

        transformMatData.clear()

        val size = ShaderProgramme.SHADER_SCREEN_SIZE

        transformMatData.put(2f / size.x)
        transformMatData.put(0f)
        transformMatData.put(0f)

        transformMatData.put(0f)
        transformMatData.put(-2f / size.y)
        transformMatData.put(0f)

        // Transform column, 1 is multiplied into this in the shader.
        transformMatData.put(-1f)
        transformMatData.put(1f)
        transformMatData.put(0f)

        transformMatData.flip()
        GL20.glUniformMatrix3(posTransformLoc, false, transformMatData)
    }

    private fun updateUvTransformMatrix() {
        // UVs are 0-1 in both axes.

        transformMatData.clear()

        transformMatData.put(image.textureWidth / image.width)
        transformMatData.put(0f)
        transformMatData.put(0f)

        transformMatData.put(0f)
        transformMatData.put(image.textureHeight / image.height)
        transformMatData.put(0f)

        // Transform column, 1 is multiplied into this in the shader.
        transformMatData.put(image.textureOffsetX)
        transformMatData.put(image.textureOffsetY)
        transformMatData.put(0f)

        transformMatData.flip()
        GL20.glUniformMatrix3(uvTransformLoc, false, transformMatData)
    }

    override fun close() {
        if (vbo != 0) {
            glDeleteBuffers(vbo)
            vbo = 0
        }
    }

    companion object {
        private val shader by lazy {
            ShaderProgramme("shaders/image_rect_vert.glsl", "shaders/image_rect_frag.glsl")
        }

        private val posAttrib: Int by lazy { shader.getAttributeLocation("pos") }
        private val uvAttrib: Int by lazy { shader.getAttributeLocation("uv") }
        private val colourAttrib: Int by lazy { shader.getAttributeLocation("filterColour") }
        private val posTransformLoc: Int by lazy { shader.getUniformLocation("posTransform") }
        private val uvTransformLoc: Int by lazy { shader.getUniformLocation("uvTransform") }
    }
}
