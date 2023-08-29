package xyz.znix.xftl.devutil.uiedit

import org.newdawn.slick.BasicGame
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Input
import xyz.znix.xftl.*
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Color
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.ShaderProgramme
import xyz.znix.xftl.ui.SpecDeserialiser
import xyz.znix.xftl.ui.UIProvider
import xyz.znix.xftl.ui.Widget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.pow
import kotlin.math.roundToInt
import org.newdawn.slick.Graphics as SlickGraphics

/**
 * A simple UI 'editor'.
 *
 * This doesn't actually let you edit anything, but it does show you what a UI
 * would look like in-game, and it can be reloaded by pressing F3, and supports
 * zooming and highlighting elements to get everything lined up quickly.
 */
class UIEditor(val df: Datafile, val filename: String) : BasicGame("XFTL UI Editor"), UIProvider {
    private lateinit var g: Graphics

    private val fonts = HashMap<String, SILFontLoader>()
    private val images = HashMap<String, Image>()

    private lateinit var utilFont: SILFontLoader

    private val translator = Translator(df, "en")

    private var ui: SpecDeserialiser.LoadedUI? = null
    private var loadError: String = ""

    private val mousePos = Point(0, 0)

    // The level of zoom controlled by the mouse, this is a log scale
    private var zoomLevel: Float = 0f
        set(value) {
            field = value
            updateZoomScale()
        }

    // How much things on the screen are scaled by
    private var zoomScale: Float = 0f

    private var panOffsetX: Float = 0f
    private var panOffsetY: Float = 0f

    private var highlighted: Widget? = null
    private var showOutlines: Boolean = true

    override fun init(container: GameContainer) {
        updateZoomScale()

        g = Graphics()
        g.markCurrentImageTransformSource()

        utilFont = getFont("c&c")

        reload()
    }

    override fun update(container: GameContainer, delta: Int) {
        val input = container.input

        if (input.isKeyPressed(Input.KEY_F1)) {
            reload()
        }
        if (input.isKeyPressed(Input.KEY_F2)) {
            showOutlines = !showOutlines
        }
        if (input.isKeyPressed(Input.KEY_F3)) {
            zoomLevel = 0f
            panOffsetX = 0f
            panOffsetY = 0f
        }

        mousePos.set(input.mouseX, input.mouseY)
    }

    override fun render(container: GameContainer, slickG: SlickGraphics) {
        ShaderProgramme.SHADER_SCREEN_SIZE.set(container.width, container.height)

        g.clear(Color.lightGray)

        // Reset the transform from last frame, in case there was a transform
        // call that wasn't inside a pushTransform block.
        g.loadIdentityMatrix()

        g.pushTransform()
        g.translate(PREVIEW_POS_X.f, PREVIEW_POS_Y.f)
        g.scale(zoomScale, zoomScale)
        g.translate(-panOffsetX, -panOffsetY)
        renderUI()
        g.popTransform()

        g.pushTransform()
        g.translate(10f, 10f)
        drawTree()
        g.popTransform()

        // Check there aren't any mismatched pushTransform/popTransform calls.
        g.checkNoPushedTransforms()
    }

    private fun renderUI() {
        val ui = ui ?: return

        ui.mainWidget.draw(g)

        // If a widget is highlighted, draw the bounding box for that
        // after everything else, so it shows up on top.
        val hl = highlighted ?: return

        g.colour = Color.blue
        g.drawRect(hl.position.x.f, hl.position.y.f, hl.size.x - 1f, hl.size.y - 1f)
    }

    private fun drawTree() {
        val ui = ui
        if (ui == null) {
            var y = utilFont.baselineToTop
            utilFont.drawString(0f, y.f, "Error loading UI XML:", Color.black)
            y += utilFont.lineSpacing
            utilFont.drawString(0f, y.f, loadError, Color.black)
            return
        }

        val height = utilFont.baselineToTop

        var y = 0

        utilFont.drawString(0f, y.f + height, "Scale (F3 to reset): %.2fx".format(zoomScale), Color.black)
        y += utilFont.lineSpacing

        val mouseY = mousePos.y - Graphics.getTextureTransformMatrix().m12.roundToInt()

        highlighted = null

        fun recurse(widget: Widget, depth: Int) {
            val text = widget.javaClass.simpleName
            val x = depth * 10
            val width = utilFont.getWidth(text)

            val margin = 2
            val hover = (mousePos.x - x) in 0..width && (mouseY - y) in -margin..height + margin

            if (hover) {
                g.colour = Color.blue
                highlighted = widget
                g.fillRect(x - 2, y - 2, width + 4, height + 4)
            } else {
                // g.colour = Color(200, 200, 200, 150)
            }
            utilFont.drawString(x.f, y.f + height, text, Color.black)

            y += utilFont.lineSpacing

            for (child in widget.children) {
                recurse(child, depth + 1)
            }
        }

        recurse(ui.mainWidget.root, 0)
    }

    private fun reload() {
        loadError = ""
        ui = null
        try {
            doReload()
        } catch (ex: Exception) {
            ex.printStackTrace()
            loadError = ex.localizedMessage
        }
    }

    private fun doReload() {
        val path = Path.of("src/main/resources/assets/ui/$filename.xml")

        ui = Files.newInputStream(path).use { SpecDeserialiser(this).load(it) }
    }

    override fun getFont(name: String): SILFontLoader {
        // Always return a copy of the font, so our instance doesn't get broken
        // when a consumer sets their instance's scale property or anything
        // like that.
        fonts[name]?.let { return SILFontLoader(it) }

        val font = SILFontLoader(df, df["fonts/$name.font"])
        fonts[name] = font
        return SILFontLoader(font)
    }

    override fun getImg(path: String): Image {
        images[path]?.let { return it }

        val image = df.readImage(path)
        images[path] = image
        return image
    }

    override fun translate(key: String): String? {
        return translator.translations[key]
    }

    override fun getDebugOutlineColour(widget: Widget): Color? {
        // We'll draw a blue highlight on top later for selected widgets
        return if (showOutlines) Color.yellow else null
    }

    override fun mouseWheelMoved(change: Int) {
        // Keep the pixel the mouse is over the same, to zoom in on what's being hovered.
        val mouseX = mousePos.x - PREVIEW_POS_X
        val mouseY = mousePos.y - PREVIEW_POS_Y
        val pixelX = mouseX / zoomScale + panOffsetX
        val pixelY = mouseY / zoomScale + panOffsetY

        // Updates zoomScale
        zoomLevel += change

        panOffsetX = pixelX - mouseX / zoomScale
        panOffsetY = pixelY - mouseY / zoomScale
    }

    override fun mouseDragged(oldX: Int, oldY: Int, newX: Int, newY: Int) {
        val deltaX = newX - oldX
        val deltaY = newY - oldY

        panOffsetX -= deltaX / zoomScale
        panOffsetY -= deltaY / zoomScale
    }

    private fun updateZoomScale() {
        zoomScale = (1.2f).pow(zoomLevel / 50)
    }

    companion object {
        private const val PREVIEW_POS_X = 200
        private const val PREVIEW_POS_Y = 10

        @JvmStatic
        fun main(args: Array<String>) {
            val filename = args[0]

            Utils.startSlick { UIEditor(it, filename) }
        }
    }
}
