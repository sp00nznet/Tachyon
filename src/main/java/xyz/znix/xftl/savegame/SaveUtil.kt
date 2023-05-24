package xyz.znix.xftl.savegame

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.RoomPoint

object SaveUtil {
    // Saving
    fun addPoint(parent: Element, newName: String, point: IPoint) {
        val elem = Element(newName)
        elem.setAttribute("x", point.x.toString())
        elem.setAttribute("y", point.y.toString())
        parent.addContent(elem)
    }

    fun addRoomPoint(parent: Element, newName: String, point: RoomPoint) {
        val elem = Element(newName)
        // Use different element names to make this incompatible with addPoint,
        // to prevent them from being used in place of each other.
        elem.setAttribute("rx", point.x.toString())
        elem.setAttribute("ry", point.y.toString())
        elem.setAttribute("rid", point.room.id.toString())
        parent.addContent(elem)
    }

    fun addTag(parent: Element, newName: String, value: String) {
        // Using an attribute rather than an element contents means that
        // whitespace won't be changed by formatting the XML, and the
        // element name won't be repeated in the closing tag.
        val elem = Element(newName)
        elem.setAttribute("v", value)
        parent.addContent(elem)
    }

    fun addTagInt(parent: Element, newName: String, value: Int?) {
        val elem = Element(newName)
        elem.setAttribute("i", value?.toString() ?: "null")
        parent.addContent(elem)
    }

    fun addTagFloat(parent: Element, newName: String, value: Float?) {
        val elem = Element(newName)
        elem.setAttribute("f", value?.toString() ?: "null")
        parent.addContent(elem)
    }

    fun addTagBool(parent: Element, newName: String, value: Boolean) {
        val elem = Element(newName)
        elem.setAttribute("b", value.toString())
        parent.addContent(elem)
    }

    fun addRef(parent: Element, newName: String, refs: ObjectRefs, value: Any?) {
        // IDR stands for 'ID Reference'
        val elem = Element(newName)
        elem.setAttribute("idr", refs[value])
        parent.addContent(elem)
    }

    /**
     * Add the attribute that denotes an element represents an object
     * that can be referenced by its unique ID.
     */
    fun addObjectId(elem: Element, refs: ObjectRefs, obj: Any) {
        val id = refs[obj]
        elem.setAttribute("oid", id)
    }

    // Reading

    fun getPoint(parent: Element, name: String): ConstPoint {
        val elem = parent.getChild(name) ?: error("Missing point '$name'")
        val x = elem.getAttributeValue("x").toInt()
        val y = elem.getAttributeValue("y").toInt()
        return ConstPoint(x, y)
    }

    fun getRoomPoint(parent: Element, name: String, ship: Ship): RoomPoint {
        val elem = parent.getChild(name) ?: error("Missing room point '$name'")
        val x = elem.getAttributeValue("rx").toInt()
        val y = elem.getAttributeValue("ry").toInt()
        val roomId = elem.getAttributeValue("rid").toInt()
        val room = ship.rooms[roomId]
        return RoomPoint(room, x, y)
    }

    fun getTag(parent: Element, name: String): String {
        val elem = parent.getChild(name) ?: error("Missing tag '$name'")
        return elem.getAttributeValue("v")
    }

    fun getTagIntOrNull(parent: Element, name: String): Int? {
        val elem = parent.getChild(name) ?: error("Missing int tag '$name'")
        val value = elem.getAttributeValue("i")
        if (value == "null")
            return null
        return value.toInt()
    }

    fun getTagInt(parent: Element, name: String): Int {
        return getTagIntOrNull(parent, name) ?: error("Null int tag '$name'")
    }

    fun getTagFloatOrNull(parent: Element, name: String): Float? {
        val elem = parent.getChild(name) ?: error("Missing float tag '$name'")
        val value = elem.getAttributeValue("f")
        if (value == "null")
            return null
        return value.toFloat()
    }

    fun getTagFloat(parent: Element, name: String): Float {
        return getTagFloatOrNull(parent, name) ?: error("Null float tag '$name'")
    }

    fun getTagBool(parent: Element, name: String): Boolean {
        val elem = parent.getChild(name) ?: error("Missing boolean tag '$name'")
        val value = elem.getAttributeValue("b")!!
        return value.toBoolean()
    }

    fun <T> getRef(parent: Element, name: String, refs: RefLoader, type: Class<T>, callback: (T?) -> Unit) {
        // IDR stands for 'ID Reference'
        val elem = parent.getChild(name) ?: error("Missing reference tag '$name'")
        val ref = elem.getAttributeValue("idr")!!
        return refs.asyncResolve(type, ref, callback)
    }

    fun <T> getRefImmediate(parent: Element, name: String, refs: RefLoader, type: Class<T>): T? {
        // IDR stands for 'ID Reference'
        val elem = parent.getChild(name) ?: error("Missing reference tag '$name'")
        val ref = elem.getAttributeValue("idr")!!
        return refs.resolve(type, ref)
    }

    /**
     * This is the counterpart of [addObjectId].
     */
    fun registerObjectId(elem: Element, refs: RefLoader, obj: Any) {
        val id = elem.getAttributeValue("oid") ?: error("Missing object ID!")
        refs.register(obj, id)
    }
}
