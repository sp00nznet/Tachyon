package xyz.znix.xftl.ui

import org.jdom2.Element
import org.newdawn.slick.Color
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.f
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.requireAttributeValue
import xyz.znix.xftl.requireAttributeValueInt
import kotlin.math.max
import kotlin.math.roundToInt

class Label(provider: UIProvider, text: String, font: SILFontLoader) : Widget(provider) {
    var text: String = text
        set(value) {
            field = value
            updateSize()
        }
    var font: SILFontLoader = font
        set(value) {
            field = value
            updateSize()
        }

    var colour: Color = Color.black

    override val size = Point(0, 0)
    private var textWidth: Int = 1

    init {
        updateSize()
    }

    override fun draw(g: Graphics) {
        val y = position.y + font.baselineToTop * font.scale
        val x = position.x + (size.x - textWidth) / 2
        font.drawString(x.f, y, text, colour)

        postDraw(g)
    }

    private fun updateSize() {
        textWidth = font.getWidth(text)

        val height = ((font.baselineToTop + font.trueBaselineOffset) * font.scale).roundToInt()

        // We can only increase the size, as otherwise we'd shrink down even
        // if we're stretched out in XML by attaching to both sides of
        // our parent widget.
        attemptStretch(textWidth, height)
    }

    override fun attemptStretch(availableWidth: Int, availableHeight: Int) {
        size.x = max(size.x, availableWidth)
        size.y = max(size.y, availableHeight)
    }

    companion object {
        private const val NO_LOC_PREFIX = "noloc:"

        fun fromXML(provider: UIProvider, elem: Element): Label {
            val rawText = elem.requireAttributeValue("text")
            val text = when {
                rawText.startsWith(NO_LOC_PREFIX) -> rawText.removePrefix(NO_LOC_PREFIX)
                else -> provider.translate(rawText)
                    ?: throw IllegalUISpecException("Invalid label translation key '$rawText'")
            }

            val fontName = elem.requireAttributeValue("font")
            val fontScale = elem.requireAttributeValueInt("scale")

            val font = provider.getFont(fontName)
            font.scale = fontScale.f

            val label = Label(provider, text, font)
            label.loadXML(elem)

            label.colour = SpecDeserialiser.parseColour(elem, "colour")

            return label
        }
    }
}
