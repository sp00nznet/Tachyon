package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

abstract class BulkRenderer : AutoCloseable {
    private var vao: Int = 0
    private var vbo: Int = 0

    protected var data: ByteBuffer = generateBuffer(128)
    protected var indices: ByteBuffer = generateBuffer(128)
    protected var numVerts = 0

    private val transformMatData = generateBuffer(4 * 9).asFloatBuffer()

    abstract fun flush()

    override fun close() {
        if (vao != 0) {
            GL30.glDeleteBuffers(vao)
            vao = 0
        }
        if (vbo != 0) {
            GL30.glDeleteBuffers(vbo)
            vbo = 0
        }
    }

    protected fun getOrCreateVBO(): Int {
        if (vbo == 0) {
            vbo = GL30.glGenBuffers()
        }

        return vbo
    }

    protected fun getOrCreateVAO(): Int {
        if (vao == 0) {
            vao = GL30.glGenVertexArrays()
        }

        return vao
    }

    protected fun updateTransformMatrix(uniformLocation: Int) {
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
        GL30.glUniformMatrix3fv(uniformLocation, false, transformMatData)
    }

    /**
     * Ensures there's at least [required] bytes of space remaining in [data].
     */
    protected fun checkSize(required: Int) {
        data = checkSizeOf(data, required)
    }

    /**
     * Ensures there's at least [required] bytes of space remaining in [indices].
     */
    protected fun checkSizeIndices(required: Int) {
        indices = checkSizeOf(indices, required)
    }

    private fun checkSizeOf(buf: ByteBuffer, required: Int): ByteBuffer {
        if (buf.remaining() >= required)
            return buf

        val current = buf.position() + buf.remaining()
        val newData = generateBuffer(max(required, current * 2))

        // Copy the old data into the new buffer
        buf.flip()
        newData.put(buf)
        return newData
    }

    protected fun generateBuffer(size: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(size).apply {
            order(ByteOrder.nativeOrder())
        }
    }
}
