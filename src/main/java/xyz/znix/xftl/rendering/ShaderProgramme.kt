package xyz.znix.xftl.rendering

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import xyz.znix.xftl.math.Point

class ShaderProgramme(vertPath: String, fragPath: String) : AutoCloseable {
    var handle: Int = -1
        private set

    init {
        checkError()

        var success = false

        var vert = -1
        var frag = -1
        try {
            vert = createShader(vertPath, GL20.GL_VERTEX_SHADER)
            frag = createShader(fragPath, GL20.GL_FRAGMENT_SHADER)

            handle = GL20.glCreateProgram()
            GL20.glAttachShader(handle, vert)
            GL20.glAttachShader(handle, frag)
            GL20.glLinkProgram(handle)
            checkError()

            val logLength = GL20.glGetProgrami(handle, GL20.GL_INFO_LOG_LENGTH)
            // IDK if it needs to be null-terminated, so +1 just in case
            val log = GL20.glGetProgramInfoLog(handle, logLength + 1)

            if (log.isNotEmpty()) {
                println("Message when linking shader '$vertPath'/'$fragPath':")
                println(log.trim())
            }

            val status = GL20.glGetProgrami(handle, GL20.GL_LINK_STATUS)
            if (status != 1) {
                error("Failed to link shader '$vertPath'/'$fragPath'!")
            }

            checkError()

            success = true
        } finally {
            if (vert != -1)
                GL20.glDeleteShader(vert)
            if (frag != -1)
                GL20.glDeleteShader(frag)

            if (!success)
                close()
        }

        checkError()
    }

    fun getAttributeLocation(name: String): Int {
        val location = GL20.glGetAttribLocation(handle, name)
        checkError()
        require(location != -1) { "Missing attribute location '$name'" }
        return location
    }

    fun getUniformLocation(name: String): Int {
        val location = GL20.glGetUniformLocation(handle, name)
        checkError()
        require(location != -1) { "Missing uniform location '$name'" }
        return location
    }

    private fun createShader(path: String, type: Int): Int {
        val url = javaClass.classLoader.getResource(path)
        requireNotNull(url) { "Could not find shader file '$path'" }
        val bytes = url.openStream().use { it.readAllBytes() }
        val content = bytes.toString(Charsets.UTF_8)

        val shader = GL20.glCreateShader(type)
        GL20.glShaderSource(shader, content)
        GL20.glCompileShader(shader)
        checkError()

        val logLength = GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH)
        // IDK if it needs to be null-terminated, so +1 just in case
        val log = GL20.glGetShaderInfoLog(shader, logLength + 1)

        if (log.isNotEmpty()) {
            println("Message when compiling shader $path:")
            println(log.trim())
        }

        val status = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS)
        if (status != 1) {
            error("Failed to compile shader '$path'!")
        }

        checkError()

        return shader
    }

    override fun close() {
        if (handle != -1) {
            GL20.glDeleteProgram(handle)
            handle = -1
        }
    }

    companion object {
        private fun checkError() {
            val err = GL11.glGetError()
            if (err == GL11.GL_NO_ERROR)
                return

            error("OpenGL error: $err")
        }

        /**
         * This must be set by SlickGame, and is used to transform
         * the coordinates into pixel-space correctly.
         */
        @JvmStatic
        val SHADER_SCREEN_SIZE = Point(1, 1)
    }
}
