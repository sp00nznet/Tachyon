package xyz.znix.xftl.bxml

import org.jdom2.*
import org.jdom2.internal.ArrayCopy
import sun.misc.Unsafe
import java.io.DataInputStream
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.InaccessibleObjectException
import kotlin.math.max

class BXMLReader private constructor(input: DataInputStream) {
    private val input = BXMLBufferedInputStream(input)
    private val strings = ArrayList<String>()

    private fun readElement(): Element {
        val elem = UnsafeXML.createElement(readString())

        // Write the attributes
        val attrCount = input.readVarInt()
        for (i in 0 until attrCount) {
            val name = readString()
            val value = readString()

            // Create the Attribute object ourselves, since the
            // Element.setAttribute(string, string) function first checks
            // if there's already an attribute with the same name, which
            // is slow (11% of the readElement time, when this was written).
            UnsafeXML.addAttribute(elem, UnsafeXML.createAttribute(name, value))
        }

        // Read the contents, notably including children.
        while (true) {
            when (val typeId = input.readByte()) {
                BXMLUtils.ID_EOF -> break

                BXMLUtils.ID_TEXT -> {
                    // This doesn't reconstruct a CDATA object, instead it'll
                    // probably use escaping, but we don't really care.
                    // It'll be uglier, but if a human is reading something
                    // that's gone to BXML and back then something has gone
                    // horribly wrong.
                    elem.addContent(readString())
                }

                BXMLUtils.ID_ELEM -> {
                    // Recursively read the child elements
                    elem.addContent(readElement())
                }

                else -> error("Invalid BXML type ID: $typeId")
            }
        }

        return elem
    }

    private fun readString(): String {
        val id = input.readVarInt()

        // 0 indicates a literal, anything else is a previous string
        if (id != 0) {
            // IDs are 1-indexed, to disambiguate the first string and literals.
            return strings[id - 1]
        }

        val length = input.readVarInt()
        val bytes = input.readNBytes(length)
        val string = String(bytes, Charsets.UTF_8)
        strings.add(string)
        return string
    }

    companion object {
        @JvmStatic
        fun read(inputStream: InputStream): Document {
            val rootElem = DataInputStream(inputStream).use {
                val reader = BXMLReader(it)
                reader.readElement()
            }
            return Document(rootElem)
        }
    }
}

/**
 * Attribute objects are surprisingly expensive to create, as they always
 * validate their name/value/type/namespace.
 *
 * We know they're valid since otherwise we couldn't have serialised them,
 * so defeat those checks if we can.
 *
 * This uses Unsafe to create a blank object instance, so fallback to creating
 * them normally if that's not available.
 *
 * The same applies for creating elements (name validation), adding attributes
 * (checking for duplicates), and adding content (checking for parent loops).
 */
private object UnsafeXML {
    // Making this static allows constant folding
    @JvmStatic
    private val unsafe: Unsafe? = try {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        field.get(null) as Unsafe
    } catch (ex: InaccessibleObjectException) {
        println("Cannot access sun.misc.Unsafe, BXML deserialisation will be slightly slower.")
        null
    }

    private val offsetAttrSpecified: Long = lookupOffset(Attribute::class.java, "specified")
    private val offsetAttrName: Long = lookupOffset(Attribute::class.java, "name")
    private val offsetAttrValue: Long = lookupOffset(Attribute::class.java, "value")
    private val offsetAttrType: Long = lookupOffset(Attribute::class.java, "type")
    private val offsetAttrNamespace: Long = lookupOffset(Attribute::class.java, "namespace")
    private val offsetAttrParent: Long = lookupOffset(Attribute::class.java, "parent")

    private val elementCtor: MethodHandle = MethodHandles.lookup()
        .unreflectConstructor(Element::class.java.getDeclaredConstructor().apply { isAccessible = true })
    private val offsetElemName: Long = lookupOffset(Element::class.java, "name")
    private val offsetElemNamespace: Long = lookupOffset(Element::class.java, "namespace")
    private val offsetElemAttributes: Long = lookupOffset(Element::class.java, "attributes")

    private val attributeList = Class.forName("org.jdom2.AttributeList")
    private val attributeListCtor: MethodHandle = MethodHandles.lookup()
        .unreflectConstructor(attributeList.getDeclaredConstructor(Element::class.java).apply { isAccessible = true })
    private val offsetAttributeListAttributes: Long = lookupOffset(attributeList, "attributeData")
    private val offsetAttributeListSize: Long = lookupOffset(attributeList, "size")

    private const val ATTRIBUTE_LIST_INITIAL_ARRAY_SIZE = 4

    private fun lookupOffset(type: Class<*>, name: String): Long {
        return unsafe?.objectFieldOffset(type.getDeclaredField(name)) ?: 0
    }

    fun createAttribute(name: String, value: String): Attribute {
        if (unsafe == null) {
            return Attribute(name, value)
        }

        // Create an instance without running the constructor.
        // You're not at all supposed to be able to do this, and if JDOM
        // changes its internals it's our fault if this breaks (though that's
        // not terribly likely).
        val attr = unsafe.allocateInstance(Attribute::class.java) as Attribute

        unsafe.putBoolean(attr, offsetAttrSpecified, true)
        unsafe.putObject(attr, offsetAttrName, name)
        unsafe.putObject(attr, offsetAttrValue, value)
        unsafe.putObject(attr, offsetAttrType, AttributeType.UNDECLARED)
        unsafe.putObject(attr, offsetAttrNamespace, Namespace.NO_NAMESPACE)

        return attr
    }

    fun createElement(name: String): Element {
        if (unsafe == null) {
            return Element(name)
        }

        // Run the basic constructor, it doesn't do anything slow.
        val elem = elementCtor.invoke() as Element

        unsafe.putObject(elem, offsetElemName, name)
        unsafe.putObject(elem, offsetElemNamespace, Namespace.NO_NAMESPACE)

        return elem
    }

    fun addAttribute(elem: Element, attr: Attribute) {
        if (unsafe == null) {
            elem.setAttribute(attr)
            return
        }

        unsafe.putObject(attr, offsetAttrParent, elem)

        // Get or create the element's attribute list
        var attributes = unsafe.getObject(elem, offsetElemAttributes) as java.util.AbstractList<*>?
        if (attributes == null) {
            attributes = attributeListCtor.invoke(elem) as java.util.AbstractList<*>
            unsafe.putObject(elem, offsetElemAttributes, attributes)
        }

        // Grow the backing array
        val oldSize = unsafe.getInt(attributes, offsetAttributeListSize)
        unsafe.putInt(attributes, offsetAttributeListSize, oldSize + 1)

        @Suppress("UNCHECKED_CAST")
        var attributeData = unsafe.getObject(attributes, offsetAttributeListAttributes) as Array<Attribute?>?

        // Copied from AttributeList
        val minCapacity = oldSize + 1
        if (attributeData == null) {
            attributeData = arrayOfNulls(max(minCapacity, ATTRIBUTE_LIST_INITIAL_ARRAY_SIZE))
            unsafe.putObject(attributes, offsetAttributeListAttributes, attributeData)
        } else if (minCapacity >= attributeData.size) {
            attributeData = ArrayCopy.copyOf(attributeData, minCapacity + ATTRIBUTE_LIST_INITIAL_ARRAY_SIZE)!!
            unsafe.putObject(attributes, offsetAttributeListAttributes, attributeData)
        }

        // Put the attribute into the array
        attributeData[oldSize] = attr
    }
}
