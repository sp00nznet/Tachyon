package xyz.znix.xftl

import org.jdom2.Element
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.PathFinder
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.RoomPoint
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import java.awt.Rectangle
import java.util.stream.Collectors

class Ship(base: Datafile, shipNode: Element, val sys: SlickGame) {
    constructor(base: Datafile, name: String, sys: SlickGame) : this(base, findDefaultShipElement(base, name)
            ?: throw IllegalArgumentException("Cannot find ship $name in blueprints"), sys)

    val name: String = shipNode.getAttributeValue("name")
    val rooms: List<Room>
    val doors: MutableList<Door> = ArrayList()

    val offset: ConstPoint
    val floorOffset: ConstPoint
    val hullOffset: ConstPoint

    val imageName: String = shipNode.getAttributeValue("img")

    val crew: MutableList<AbstractCrew> = ArrayList()

    val pathFinder: PathFinder

    val hardpoints: List<Hardpoint>

    val inboundProjectiles: MutableList<AbstractProjectile> = ArrayList()

    init {
        val layout = base.readString(base["data/${shipNode.getAttributeValue("layout")}.txt"])

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

        for (node in shipNode.getChild("systemList").children) {
            if (node.name == "clonebay") {
                // TODO support
                // for now, just don't overwrite the medbay
                continue
            }

            val system: AbstractSystem = when (node.name) {
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
                    System.out.println("Warning: unimplemented system ${node.name}")
                    null
                }
            } ?: continue

            // TODO remove when all systems are here

            val slotElems = node.getChildren("slot")
            check(slotElems.size < 2)

            var compDir: Direction? = null
            var compPoint: ConstPoint? = null

            if (slotElems.size == 1) {
                val elem: Element = slotElems[0]

                val dir = elem.getChildren("direction")
                if (dir.size == 1)
                    compDir = Direction.valueOf(dir[0].textTrim.toUpperCase())

                val idx = elem.getChildren("number")

                if (idx.size == 1)
                    compPoint = when (idx[0].textTrim) {
                        "0" -> ConstPoint(0, 0)
                        "1" -> ConstPoint(1, 0)
                        "2" -> ConstPoint(0, 1)
                        "3" -> ConstPoint(1, 1)
                        else -> throw IllegalStateException("Invalid point value '${idx[0].textTrim}'")
                    }

                check(dir.size <= 1)
                check(idx.size <= 1)
            }

            rooms[node.getAttributeValue("room").toInt()].setSystem(system, compPoint, compDir)
        }

        val visualsXML = base.parseXML(base["data/${shipNode.getAttributeValue("layout")}.xml"])
        val offsets = visualsXML.rootElement.getChildren("offsets")
        check(offsets.size == 1)
        val floors = offsets[0].getChildren("floor")
        check(floors.size == 1)
        val floorElem = floors[0]

        floorOffset = ConstPoint(floorElem.getAttributeValue("x").toInt(), floorElem.getAttributeValue("y").toInt())

        val imgTag = visualsXML.rootElement.getChild("img")
        hullOffset = ConstPoint(
                imgTag.getAttributeValue("x").toInt() + ROOM_SIZE * offset.x,
                imgTag.getAttributeValue("y").toInt() + ROOM_SIZE * offset.y)

        // Load the hardpoints
        val mutableHardpoints = ArrayList<Hardpoint>()
        hardpoints = mutableHardpoints

        for (node in visualsXML.rootElement.getChild("weaponMounts").children) {
            val hardpoint = Hardpoint(
                    node.getAttributeValue("x").toInt(),
                    node.getAttributeValue("y").toInt(),
                    node.getAttributeValue("rotate")!!.toBoolean(),
                    node.getAttributeValue("mirror")!!.toBoolean(),
                    node.getAttributeValue("gib").toInt(),
                    Direction.valueOf(node.getAttributeValue("slide").toUpperCase())
            )
            mutableHardpoints += hardpoint
        }

        for ((nextHardpoint, node) in shipNode.getChild("weaponList").children.withIndex()) {
            val name = node.getAttributeValue("name")
            val weapon = sys.weapons.blueprints.getValue(name)
            hardpoints[nextHardpoint].weapon = weapon
        }

        // Set up the pathfinder after the layout is loaded
        pathFinder = PathFinder(this)
    }

    private fun roomByIdOrNull(id: Int): Room? = if (id == -1) null else rooms[id]

    fun shipToRoomPos(pos: IPoint): RoomPoint? {
        if (pos is RoomPoint)
            return pos

        for (r in rooms) {
            if (r.containsAbsolute(pos))
                return RoomPoint(r, pos - r.position)
        }

        return null
    }

    companion object {
        private fun findDefaultShipElement(df: Datafile, name: String): Element? {
            val blueprints = df.parseXML(df["data/blueprints.xml"])

            for (node in blueprints.rootElement.getChildren("shipBlueprint")) {
                if (node.getAttributeValue("name") != name)
                    continue

                return node
            }

            return null
        }
    }

    data class Hardpoint(val x: Int, val y: Int, val rotate: Boolean, val mirror: Boolean, val gib: Int, val slide: Direction) {
        var weapon: AbstractWeaponBlueprint? = null
    }
}