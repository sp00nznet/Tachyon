package xyz.znix.xftl

import org.w3c.dom.Element
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.systems.*
import java.awt.Rectangle
import java.util.stream.Collectors

class Ship(base: Datafile, val name: String) {
    val rooms: List<Room>
    val doors: MutableList<Door> = ArrayList()

    val offset: ConstPoint
    val floorOffset: ConstPoint
    val hullOffset: ConstPoint

    val imageName: String

    val crew: MutableList<AbstractCrew> = ArrayList()

    init {
        val blueprints = base.parseXML(base["data/blueprints.xml"])
        val shipBlueprints = blueprints.getElementsByTagName("shipBlueprint")
        var shipNode: Element? = null

        for (i in 0..shipBlueprints.length) {
            val node = shipBlueprints.item(i) as? Element ?: continue

            if (node.getAttribute("name") != name)
                continue

            shipNode = node
        }

        if (shipNode == null)
            throw IllegalArgumentException("Cannot find ship $name in blueprints")

        imageName = shipNode.getAttribute("img")

        val layout = base.readString(base["data/${shipNode.getAttribute("layout")}.txt"])

        val l = layout.replace("\r\n", "\n").split('\n')
        var i = 0

        val mutableRooms: MutableList<Room> = ArrayList()
        rooms = mutableRooms

        var found_offset_x = 0
        var found_offset_y = 0
        var found_vertical = 0
        var found_horizontal = 0

        // TODO use our own class, not AWT's!
        var found_ellipse: Rectangle?

        while (i < l.size) {
            val line = l[i++]
            when (line) {
                "X_OFFSET" -> found_offset_x = l[i++].toInt()
                "Y_OFFSET" -> found_offset_y = l[i++].toInt()
                "HORIZONTAL" -> found_horizontal = l[i++].toInt()
                "VERTICAL" -> found_vertical = l[i++].toInt()
                "ELLIPSE" -> found_ellipse = Rectangle(l[i++].toInt(), l[i++].toInt(), l[i++].toInt(), l[i++].toInt())
                "ROOM" -> {
                    val id = l[i++].toInt()
                    check(id == rooms.size)
                    mutableRooms += Room(this, id, l[i++].toInt(), l[i++].toInt(), l[i++].toInt(), l[i++].toInt())
                    check(rooms[id].id == id)
                }
                "DOOR" -> {
                    val x = l[i++].toInt()
                    val y = l[i++].toInt()
                    val left = roomByIdOrNull(l[i++].toInt())
                    val right = roomByIdOrNull(l[i++].toInt())
                    val vertical = l[i++].toInt() == 1
                    doors += Door(ConstPoint(x, y), left, right, vertical)
                }
                "" -> {
                }
                else -> throw IllegalStateException("Unknown line '$line'")
            }
        }

        for (room in rooms) {
            val roomDoors = doors.stream().filter { d -> d.left == room || d.right == room }.collect(Collectors.toList())
            room.initialise(roomDoors)
        }

        offset = ConstPoint(found_offset_x, found_offset_y)

        val systemList = (shipNode.getElementsByTagName("systemList").item(0) as Element).childNodes

        for (i in 0..systemList.length) {
            val node = systemList.item(i) as? Element ?: continue

            if (node.tagName == "clonebay") {
                // TODO support
                // for now, just don't overwrite the medbay
                continue
            }

            val system: AbstractSystem = when (node.tagName) {
                "doors" -> Doors(node)
                "engines" -> Engines(node)
                "medbay" -> Medbay(node)
                "oxygen" -> Oxygen(node)
                "pilot" -> Piloting(node)
                "sensors" -> Sensors(node)
                "shields" -> Shields(node)
                "weapons" -> Weapons(node)
                else -> {
                    // TODO throw exception when all systems are implemented
                    System.out.println("Warning: unimplemented system ${node.tagName}")
                    null
                }
            } ?: continue

            // TODO remove when all systems are here

            val slotElems = node.getElementsByTagName("slot")
            check(slotElems.length < 2)

            var compDir: Direction? = null
            var compPoint: ConstPoint? = null

            if (slotElems.length == 1) {
                val elem: Element = slotElems.item(0) as Element

                val dir = elem.getElementsByTagName("direction")
                if (dir.length == 1)
                    compDir = Direction.valueOf(dir.item(0).textContent.toUpperCase())

                val idx = elem.getElementsByTagName("number")

                if (idx.length == 1)
                    compPoint = when (idx.item(0).textContent) {
                        "0" -> ConstPoint(0, 0)
                        "1" -> ConstPoint(1, 0)
                        "2" -> ConstPoint(0, 1)
                        "3" -> ConstPoint(1, 1)
                        else -> throw IllegalStateException("Invalid point value '${idx.item(0).textContent}'")
                    }

                check(dir.length <= 1)
                check(idx.length <= 1)
            }

            rooms[node.getAttribute("room").toInt()].setSystem(system, compPoint, compDir)
        }

        val visualsXML = base.parseXML(base["data/${shipNode.getAttribute("layout")}.xml"])
        val offsets = visualsXML.getElementsByTagName("offsets")
        check(offsets.length == 1)
        val floors = (offsets.item(0) as Element).getElementsByTagName("floor")
        check(floors.length == 1)
        val floorElem = floors.item(0) as Element

        floorOffset = ConstPoint(floorElem.getAttribute("x").toInt(), floorElem.getAttribute("y").toInt())

        val imgTag = visualsXML.getElementsByTagName("img").item(0) as Element
        hullOffset = ConstPoint(
                imgTag.getAttribute("x").toInt() + ROOM_SIZE * offset.x,
                imgTag.getAttribute("y").toInt() + ROOM_SIZE * offset.y)
    }

    private fun roomByIdOrNull(id: Int): Room? = if (id == -1) null else rooms[id]
}