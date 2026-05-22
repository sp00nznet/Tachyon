package xyz.znix.xftl.sys

import org.lwjgl.Version
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.KHRDebug
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.newdawn.slick.InputListener
import org.newdawn.slick.KeyListener
import org.newdawn.slick.MouseListener
import org.newdawn.slick.opengl.ImageDataFactory
import xyz.znix.xftl.devmenu.DevMenu
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.net.Multiplayer
import xyz.znix.xftl.rendering.Cursor
import xyz.znix.xftl.rendering.Graphics
import java.io.BufferedInputStream


class LWJGLGameContainer(private val game: Game) : GameContainer {
    private lateinit var lwjglInput: LWJGLInput
    override val input: Input get() = lwjglInput

    /**
     * The game's logical resolution. This stays fixed; the OpenGL viewport
     * stretches it to fill the (possibly differently-sized) window, so the
     * window can be resized without the game's UI layout changing.
     */
    override var width: Int = 400
        private set
    override var height: Int = 400
        private set

    /** The actual size of the OS window, in pixels. */
    var windowWidth: Int = 400
        private set
    var windowHeight: Int = 400
        private set

    override var inputOverlay: InputOverlay? = null

    var fullscreen: Boolean = false
        private set
    var vSyncEnabled: Boolean = true
        private set

    // The window's position and size before going fullscreen, so it can be restored.
    private var windowedX: Int = 0
    private var windowedY: Int = 0
    private var windowedWidth: Int = 1280
    private var windowedHeight: Int = 720

    private lateinit var g: Graphics

    /**
     * The GLFW window handle.
     */
    private var window: Long = 0

    override fun exit() {
        glfwSetWindowShouldClose(window, true)
    }

    override fun setCursor(cursor: Cursor?) {
        if (cursor != null) {
            cursor.setActive(window)
        } else {
            glfwSetCursor(window, NULL)
        }
    }

    fun start() {
        println("Using LWJGL version ${Version.getVersion()}")

        // Must happen before any window is created, so the taskbar uses our
        // icon instead of the java.exe one.
        PlatformSpecific.INSTANCE.setApplicationId()

        init()

        try {
            loop()
        } finally {
            // Call shutdown if there was an exception, to avoid a VM crash
            // from not freeing native resources.
            game.shutdown()
        }

        // Free the window callbacks and destroy the window
        Callbacks.glfwFreeCallbacks(window)
        glfwDestroyWindow(window)

        // Terminate GLFW and free the error callback
        glfwTerminate()
        glfwSetErrorCallback(null)!!.free()
    }

    private fun init() {
        // Set up an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        check(glfwInit()) { "Unable to initialize GLFW" }

        // Configure GLFW
        glfwDefaultWindowHints() // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE) // the window will be resizable

        // Set the OpenGL stuff
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

        // TODO disable this for end users
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)

        // Create the window
        window = glfwCreateWindow(windowWidth, windowHeight, game.title, NULL, NULL)
        if (window == NULL)
            throw RuntimeException("Failed to create the GLFW window")

        stackPush().use { stack ->
            val pWidth = stack.mallocInt(1) // int*
            val pHeight = stack.mallocInt(1) // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight)

            // Get the resolution of the primary monitor
            val vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!

            // Center the window
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth[0]) / 2,
                (vidmode.height() - pHeight[0]) / 2
            )
        }

        // Set the window's icon
        setupWindowIcon()

        // Make the OpenGL context current
        glfwMakeContextCurrent(window)
        // Enable v-sync
        glfwSwapInterval(1)

        // Make the window visible
        glfwShowWindow(window)

        // Re-apply the icon now the window (and its taskbar button) exists,
        // so the taskbar reliably picks it up.
        setupWindowIcon()

        lwjglInput = LWJGLInput(window, this)
        g = Graphics()
        g.markCurrentImageTransformSource()

        if (game is KeyListener)
            lwjglInput.keyListeners.add(game)
        if (game is MouseListener)
            lwjglInput.mouseListeners.add(game)

        // Set up OpenGL
        initOpenGL()
    }

    private fun setupWindowIcon() {
        // Provide the icon at several sizes; the OS picks the best fit for the
        // title bar, taskbar and alt-tab switcher.
        val sizes = listOf(16, 32, 48, 64, 128, 256)

        // The decoded images must stay referenced until glfwSetWindowIcon runs,
        // since their pixel buffers are read at that point.
        val images = sizes.map { size ->
            val path = "assets/img/tachyon_icon_$size.png"
            val imageData = ImageDataFactory.getImageDataFor(path)
            javaClass.classLoader.getResourceAsStream(path).use { stream ->
                imageData.loadImage(BufferedInputStream(stream), false, null)
            }
            imageData
        }

        stackPush().use { stack ->
            val imageArray = GLFWImage.Buffer(stack.malloc(GLFWImage.SIZEOF * images.size))
            for ((i, imageData) in images.withIndex()) {
                imageArray[i].set(imageData.width, imageData.height, imageData.imageBufferData)
            }
            glfwSetWindowIcon(window, imageArray)
        }
    }

    private fun initOpenGL() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities()

        // From Slick's ImmediateModeOGLRenderer
        glDisable(GL_DEPTH_TEST)

        glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        glClearDepth(1.0)

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        glViewport(0, 0, windowWidth, windowHeight)

        // Set up debug output
        glEnable(KHRDebug.GL_DEBUG_OUTPUT)

        // Don't fill the console with errors if there's an issue each frame
        val maxErrorCount = 50
        var errorCount = 0

        // OSX lacks debug capabilities, we'll get an exception if we try to set them
        if (!GL.getCapabilities().GL_KHR_debug) {
            return
        }

        // Disable the flood of notification messages
        KHRDebug.glDebugMessageControl(
            GL_DONT_CARE,
            GL_DONT_CARE,
            KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION,
            null as IntArray?,
            false
        )

        KHRDebug.glDebugMessageCallback({ source, type, id, severity, length, message, _ ->
            errorCount++
            if (errorCount == maxErrorCount) {
                println("Got too many OpenGL debug messages, stopping output")
            }
            if (errorCount >= maxErrorCount) {
                return@glDebugMessageCallback
            }

            val messageString = MemoryUtil.memUTF8(message, length)

            // Filtering for end users?
            println("Got GL debug message (id=$id src=$source type=$type sev=$severity): $messageString")
        }, 0)
    }

    private fun loop() {
        game.init(this)

        var lastNanos = System.nanoTime()

        // Run the rendering loop until the user has attempted to close
        // the window, or exit is called.
        while (!glfwWindowShouldClose(window)) {
            // Read any changes to the input system
            lwjglInput.update()

            // Find the delta-time, in seconds
            val thisUpdateTime = System.nanoTime()
            val deltaNS = thisUpdateTime - lastNanos
            val deltaSec = deltaNS / 1_000_000_000f
            lastNanos = thisUpdateTime

            val isFocused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE

            // Update the game state
            // If there's an exception, catch it instead of crashing the whole game.
            // If we're in an invalid state this won't help, but on-click/on-keypress
            // stuff should generally be OK.
            try {
                game.update(this, deltaSec)
            } catch (ex: Exception) {
                println("Exception during game update")
                ex.printStackTrace()
            }

            // Normally we skip rendering while unfocused to save power. During
            // a co-op session we keep rendering anyway, so the other player's
            // window stays live on a shared screen (or when testing locally).
            if (!isFocused && !Multiplayer.isConnected) {
                // Internally run at ~20 updates/sec when unfocused to save CPU.
                glfwWaitEventsTimeout(1.0 / 20)
                continue
            }

            // Render out the game
            game.render(this, g)

            // Swap the colour buffers, presenting the newly-drawn one
            // to the screen.
            glfwSwapBuffers(window)

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents()
        }
    }

    fun setDisplayMode(width: Int, height: Int, fullScreen: Boolean) {
        // The logical resolution is fixed for the lifetime of the game.
        this.width = width
        this.height = height

        // The window is the game area plus the menu bar stacked on top, so the
        // menu doesn't cover any of the game.
        val initialHeight = height + DevMenu.BAR_HEIGHT
        this.windowWidth = width
        this.windowHeight = initialHeight
        this.windowedWidth = width
        this.windowedHeight = initialHeight
    }

    /**
     * The height, in window pixels, of the game area (the window below the bar).
     */
    private val gameViewportHeight: Int
        get() = windowHeight * height / (height + DevMenu.BAR_HEIGHT)

    override fun setGameViewport() {
        val h = gameViewportHeight
        // The game occupies the lower part of the window; the bar sits above it.
        glViewport(0, 0, windowWidth, h)
    }

    override fun setMenuViewport() {
        glViewport(0, 0, windowWidth, windowHeight)
    }

    /**
     * Resize the OS window. The game keeps rendering at its logical resolution;
     * the OpenGL viewport stretches that to fill the new window size.
     */
    fun setWindowSize(newWidth: Int, newHeight: Int) {
        if (fullscreen) {
            // Drop out of fullscreen first, then apply the requested size.
            setFullscreen(false)
        }

        var w = newWidth
        var h = newHeight

        // Clamp to the monitor work area (the screen minus the taskbar),
        // keeping the aspect ratio, so a window larger than the screen never
        // ends up unusable with its title bar off-screen.
        val monitor = glfwGetPrimaryMonitor()
        stackPush().use { stack ->
            val ax = stack.mallocInt(1)
            val ay = stack.mallocInt(1)
            val aw = stack.mallocInt(1)
            val ah = stack.mallocInt(1)
            glfwGetMonitorWorkarea(monitor, ax, ay, aw, ah)
            if (aw[0] > 0 && ah[0] > 0 && (w > aw[0] || h > ah[0])) {
                val scale = minOf(aw[0].toFloat() / w, ah[0].toFloat() / h)
                w = (w * scale).toInt()
                h = (h * scale).toInt()
            }
        }

        windowWidth = w
        windowHeight = h
        windowedWidth = w
        windowedHeight = h

        glfwSetWindowSize(window, w, h)
        glViewport(0, 0, w, h)

        // Re-centre the window, keeping the title bar on-screen.
        val vidmode = glfwGetVideoMode(monitor)
        if (vidmode != null) {
            glfwSetWindowPos(
                window,
                maxOf(0, (vidmode.width() - w) / 2),
                maxOf(0, (vidmode.height() - h) / 2)
            )
        }
    }

    /**
     * Resize the window to the largest size that keeps the game's aspect
     * ratio and fits the monitor work area - the biggest it can be windowed.
     */
    fun fitWindowToScreen() {
        stackPush().use { stack ->
            val ax = stack.mallocInt(1)
            val ay = stack.mallocInt(1)
            val aw = stack.mallocInt(1)
            val ah = stack.mallocInt(1)
            glfwGetMonitorWorkarea(glfwGetPrimaryMonitor(), ax, ay, aw, ah)

            val canvasHeight = height + DevMenu.BAR_HEIGHT
            val scale = minOf(aw[0].toFloat() / width, ah[0].toFloat() / canvasHeight)
            setWindowSize((width * scale).toInt(), (canvasHeight * scale).toInt())
        }
    }

    /**
     * Switch between borderless-fullscreen and windowed mode.
     */
    fun setFullscreen(enabled: Boolean) {
        if (enabled == fullscreen)
            return

        if (enabled) {
            // Remember the current windowed placement so it can be restored.
            stackPush().use { stack ->
                val px = stack.mallocInt(1)
                val py = stack.mallocInt(1)
                glfwGetWindowPos(window, px, py)
                windowedX = px[0]
                windowedY = py[0]
            }

            val monitor = glfwGetPrimaryMonitor()
            val vidmode = glfwGetVideoMode(monitor) ?: return

            glfwSetWindowMonitor(
                window, monitor,
                0, 0, vidmode.width(), vidmode.height(), vidmode.refreshRate()
            )

            windowWidth = vidmode.width()
            windowHeight = vidmode.height()
        } else {
            glfwSetWindowMonitor(
                window, NULL,
                windowedX, windowedY, windowedWidth, windowedHeight, 0
            )

            windowWidth = windowedWidth
            windowHeight = windowedHeight
        }

        fullscreen = enabled
        glViewport(0, 0, windowWidth, windowHeight)

        // glfwSetWindowMonitor doesn't preserve the v-sync setting on all drivers.
        glfwSwapInterval(if (vSyncEnabled) 1 else 0)
    }

    fun setVSyncEnabled(enabled: Boolean) {
        vSyncEnabled = enabled
        glfwSwapInterval(if (enabled) 1 else 0)
    }
}

private class LWJGLInput(val window: Long, private val container: LWJGLGameContainer) : Input {
    override var mouseX: Int = 0
        private set
    override var mouseY: Int = 0
        private set

    private val mouseClickPos = Point(0, 0)

    private var scrollLeftover: Double = 0.0

    // The cursor position in the dev menu's coordinate space, which spans the
    // whole window. The game's mouseX/mouseY are this, shifted up by the bar.
    private var menuMouseX: Int = 0
    private var menuMouseY: Int = 0

    // Tracks which mouse buttons were pressed while the input overlay had
    // captured the cursor, so the matching releases are also routed to it.
    private val overlayHoldingButton = BooleanArray(8)

    // Note that GLFW_KEY_LAST is the maximum possible ID, not one past it.
    private val pendingKeyPresses = BooleanArray(GLFW_KEY_LAST + 1)

    val keyListeners = ArrayList<KeyListener>()
    val mouseListeners = ArrayList<MouseListener>()

    init {
        // Set up a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(
            window
        ) { _: Long, key: Int, scancode: Int, action: Int, mods: Int ->
            keyCallback(key, scancode, action, mods)
        }
        glfwSetCharCallback(window) { _, codepoint -> charCallback(codepoint.toChar()) }
        glfwSetMouseButtonCallback(window) { _, button, action, mods -> mouseButtonCallback(button, action, mods) }
        glfwSetScrollCallback(window) { _, xOffset, yOffset -> mouseWheelCallback(xOffset, yOffset) }
    }

    fun update() {
        // Block updates while unfocused, so the player doesn't hear
        // the mouse-over sounds for buttons through another window.
        // This happens on Linux/X11 without this check.
        if (glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_FALSE)
            return

        val lastX = mouseX
        val lastY = mouseY

        stackPush().use { stack ->
            val x = stack.mallocDouble(1)
            val y = stack.mallocDouble(1)
            glfwGetCursorPos(window, x, y)

            // The cursor is in window pixels. Scale it into the dev menu's
            // coordinate space (the whole window), then shift it up by the
            // menu bar to get the game's coordinate space.
            val canvasHeight = container.height + DevMenu.BAR_HEIGHT
            menuMouseX = (x[0] * container.width / container.windowWidth).toInt()
            menuMouseY = (y[0] * canvasHeight / container.windowHeight).toInt()
            mouseX = menuMouseX
            mouseY = menuMouseY - DevMenu.BAR_HEIGHT
        }

        if (lastX == mouseX && lastY == mouseY)
            return

        // Don't deliver hover/drag events to the game state while the overlay
        // (the dev menu) is covering the cursor.
        val overlay = container.inputOverlay
        if (overlay != null && (overlayHoldingButton.any { it } || overlay.isCapturingMouse(menuMouseX, menuMouseY)))
            return

        val dragging = isMouseButtonDown(Input.MOUSE_LEFT_BUTTON) || isMouseButtonDown(Input.MOUSE_RIGHT_BUTTON)

        iterateAllowingMutation(mouseListeners) { listener ->
            when (dragging) {
                true -> listener.mouseDragged(lastX, lastY, mouseX, mouseY)
                false -> listener.mouseMoved(lastX, lastY, mouseX, mouseY)
            }
        }
    }

    private fun keyCallback(rawKey: Int, scancode: Int, action: Int, mods: Int) {
        // Convert between the different codes used by the "|" UK/ISO key on different platforms
        val key = remapKey(rawKey)

        // Ignore unsupported keys
        if (key == GLFW_KEY_UNKNOWN) {
            return
        }

        if (action == GLFW_PRESS) {
            pendingKeyPresses[key] = true
        }

        // Keys don't map 1-1 with characters (most characters on most European
        // keyboards do, but you can't rely on that!) so send 0 for the character
        // now, and then send the character by itself as a second press.
        iterateAllowingMutation(keyListeners) { listener ->
            when (action) {
                GLFW_PRESS -> listener.keyPressed(key, 0.toChar())
                GLFW_RELEASE -> listener.keyReleased(key, 0.toChar())
            }
        }
    }

    private fun charCallback(codepoint: Char) {
        iterateAllowingMutation(keyListeners) { listener ->
            listener.keyPressed(-1, codepoint)
        }
    }

    private fun mouseButtonCallback(button: Int, action: Int, mods: Int) {
        // Give the input overlay (the dev menu) first chance at the event.
        val overlay = container.inputOverlay
        if (overlay != null && button in overlayHoldingButton.indices) {
            if (action == GLFW_PRESS) {
                if (overlay.overlayMousePressed(button, menuMouseX, menuMouseY)) {
                    overlayHoldingButton[button] = true
                    return
                }
            } else if (action == GLFW_RELEASE && overlayHoldingButton[button]) {
                overlayHoldingButton[button] = false
                overlay.overlayMouseReleased(button, menuMouseX, menuMouseY)
                return
            }
        }

        iterateAllowingMutation(mouseListeners) { listener ->
            when (action) {
                GLFW_PRESS -> listener.mousePressed(button, mouseX, mouseY)
                GLFW_RELEASE -> listener.mouseReleased(button, mouseX, mouseY)
            }
        }

        if (action == GLFW_PRESS) {
            mouseClickPos.set(mouseX, mouseY)
        }

        // Check if this is a 'click' event, with little to no dragging.
        // We don't support double-clicking, but that's not used anywhere.
        if (action == GLFW_RELEASE) {
            val dist = mouseClickPos.distToSq(mouseX, mouseY)
            if (dist <= CLICK_DISTANCE * CLICK_DISTANCE) {
                iterateAllowingMutation(mouseListeners) { listener ->
                    listener.mouseClicked(button, mouseX, mouseY, 1)
                }
            }
        }
    }

    private fun mouseWheelCallback(xOffset: Double, yOffset: Double) {
        val input = yOffset + scrollLeftover
        val change = (input * SCROLL_SCALE).toInt()

        // Store any integer round-off away, to be added onto the next scroll.
        scrollLeftover = input - change / SCROLL_SCALE

        if (container.inputOverlay?.overlayMouseWheel(change) == true)
            return

        iterateAllowingMutation(mouseListeners) { listener ->
            listener.mouseWheelMoved(change)
        }
    }

    /**
     * Iterate through a list, while accepting that it might be mutated while doing so.
     *
     * If this happens an input might be delivered twice or missed, but in
     * the case this is dealing with - clicking a button or pressing a key
     * that causes the game's state to switch - it's not a problem.
     */
    private inline fun <T> iterateAllowingMutation(list: List<T>, callback: (T) -> Unit) {
        var i = 0
        while (i < list.size) {
            callback(list[i])
            i++
        }
    }

    override fun isMouseButtonDown(button: Int): Boolean {
        // Hide button presses from the game while the overlay holds the cursor,
        // so clicks on the dev menu don't also fall through to the game state.
        val overlay = container.inputOverlay
        if (overlay != null && button in overlayHoldingButton.indices &&
            (overlayHoldingButton[button] || overlay.isCapturingMouse(menuMouseX, menuMouseY))
        ) {
            return false
        }

        return glfwGetMouseButton(window, button) == GLFW_PRESS
    }

    override fun isKeyPressed(key: Int): Boolean {
        val oldValue = pendingKeyPresses[key]
        pendingKeyPresses[key] = false
        return oldValue
    }

    override fun isKeyDown(key: Int): Boolean {
        return glfwGetKey(window, remapKey(key)) == GLFW_PRESS
    }

    override fun addListener(listener: InputListener) {
        keyListeners.add(listener)
        mouseListeners.add(listener)
    }

    override fun removeAllListeners() {
        keyListeners.clear()
        mouseListeners.clear()
    }

    override fun clearInputPressedRecord() {
        for (i in pendingKeyPresses.indices) {
            pendingKeyPresses[i] = false
        }
    }

    private fun remapKey(original: Int): Int {
        // See the documentation for KEY_BAR.
        if (original == GLFW_KEY_WORLD_2) {
            return Input.KEY_BAR
        }

        return original
    }

    companion object {
        // The maximum movement in pixels that doesn't count as dragging
        private const val CLICK_DISTANCE = 4

        // How much one movement of the scroll wheel equates to in Slick's units
        private const val SCROLL_SCALE = 120
    }
}
