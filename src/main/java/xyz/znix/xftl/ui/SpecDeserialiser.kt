package xyz.znix.xftl.ui

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import org.newdawn.slick.Color
import xyz.znix.xftl.Constants
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.requireAttributeValue
import java.io.InputStream

/**
 * Loads an XML-based widget tree, and deserialises it into a working UI.
 */
class SpecDeserialiser(private val provider: UIProvider) {
    fun load(name: String): LoadedUI {
        val path = "assets/ui/$name.xml"
        return javaClass.classLoader.getResourceAsStream(path).use { load(it) }
    }

    fun load(reader: InputStream): LoadedUI {
        @Suppress("VulnerableCodeUsages") // we set expandEntities
        val builder = SAXBuilder()
        builder.expandEntities = false
        return load(builder.build(reader))
    }

    fun load(doc: Document): LoadedUI {
        val root = doc.rootElement
        if (root.name != "ui")
            throw IllegalUISpecException("Invalid root element name")

        val widgets = loadWidgets(root)
        if (widgets.isEmpty())
            throw IllegalUISpecException("No root widget!")

        for (widget in widgets) {
            widget.init(null)
            widget.updateSizes()
            widget.expandToParent(ConstPoint.ZERO)
            widget.updateLayout()
        }

        return LoadedUI(WidgetContainer(widgets.first()), emptyMap())
    }

    private fun loadWidgets(parent: Element): List<Widget> {
        return parent.getChildren("widget").map { loadWidget(it) }
    }

    private fun loadWidget(elem: Element): Widget {
        val widget = when (val type = elem.requireAttributeValue("type")) {
            "label" -> Label.fromXML(provider, elem)
            "vbox" -> BoxContainer.fromXML(provider, elem, true)
            "hbox" -> BoxContainer.fromXML(provider, elem, false)
            "image" -> ImageView.fromXML(provider, elem)
            "button" -> UIKitButton.fromXML(provider, elem)
            else -> throw IllegalUISpecException("Unknown widget type '$type'")
        }

        widget.children += loadWidgets(elem)

        return widget
    }

    class LoadedUI(val mainWidget: WidgetContainer, val extraRoots: Map<String, WidgetContainer>)

    companion object {
        fun parseColour(elem: Element, attrName: String): Color {
            return parseColour(elem.requireAttributeValue(attrName))
        }

        fun parseColour(elem: Element, attrName: String, defaultColour: Color): Color {
            val value = elem.getAttributeValue(attrName) ?: return defaultColour
            return parseColour(value)
        }

        fun parseColour(value: String): Color {
            // TODO parse CSS hex codes

            return when (value) {
                "text-dark" -> Constants.JUMP_DISABLED_TEXT
                "glow-white" -> Constants.SECTOR_CUTOUT_TEXT
                else -> throw IllegalUISpecException("Unknown colour name '$value'")
            }
        }
    }
}

class IllegalUISpecException(message: String) : Exception(message)
