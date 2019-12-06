package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Animation
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.PathFinder
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.*
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.AbstractProjectile
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.ShipWeaponBlueprint
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

    val isPlayerShip: Boolean = name.startsWith("PLAYER_SHIP_")

    val floorImage: Image? = base.getOrNull("img/ship/${imageName}_floor.png")?.let { i -> base.readImage(i) }
    val hullImage: Image = base.readImage(base["img/${if (isPlayerShip) "ship" else "ships_glow"}/${imageName}_base.png"])

    val shieldImage: Image = base.readImage(if (isPlayerShip) base["img/ship/${shipNode.getChildTextTrim("shieldImage")
            ?: imageName}_shields1.png"] else base["img/ship/enemy_shields.png"])

    val shieldOffset: ConstPoint

    val selectedShieldHalfSize: ConstPoint

    val shieldHalfSize: ConstPoint
        get() = if (isPlayerShip)
            ConstPoint(shieldImage.width / 2, shieldImage.height / 2)
        else
            selectedShieldHalfSize

    val weaponSlots: Int? = shipNode.getChildTextTrim("weaponSlots")?.toInt()

    val crew: MutableList<AbstractCrew> = ArrayList()

    val pathFinder: PathFinder

    val hardpoints: List<Hardpoint>

    val inboundProjectiles: MutableList<AbstractProjectile> = ArrayList()
    val animations: MutableList<FloatingAnimation> = ArrayList()

    // The raw amount of reactor power purchased by the player
    var purchasedReactorPower: Int = shipNode.getChild("maxPower").getAttributeValue("amount").toInt()

    // The amount of reactor power available for use by the player, taking ion storms, events and so on into account
    val reactorPower: Int get() = purchasedReactorPower

    // The amount of unused reactor power
    val powerAvailable: Int get() = reactorPower - powerConsumed

    // The amount of power currently used by the ship's systems
    val powerConsumed: Int
        get() {
            var used = 0
            for (room in rooms) {
                used += (room.system as? MainSystem ?: continue).powerSelected
            }
            return used
        }

    val weapons: Weapons?
        get() {
            for (room in rooms)
                return room.system as? Weapons ?: continue
            return null
        }

    val engines: Engines?
        get() {
            for (room in rooms)
                return room.system as? Engines ?: continue
            return null
        }

    val shields: Shields?
        get() {
            for (room in rooms)
                return room.system as? Shields ?: continue
            return null
        }

    val piloting: Piloting?
        get() {
            for (room in rooms)
                return room.system as? Piloting ?: continue
            return null
        }

    // The ship's evasion, in percent
    val evasion: Int get() = piloting!!.evasion + engines!!.evasion

    init {
        val layout = base.readString(base["data/${shipNode.getAttributeValue("layout")}.txt"])

        val l = layout.replace("\r\n", "\n").split('\n')
        var i = 0

        rooms = ArrayList()

        var found_offset_x = 0
        var found_offset_y = 0
        var found_vertical = 0
        var found_horizontal = 0

        // TODO use our own class, not AWT's!
        var found_ellipse: Rectangle? = null

        while (i < l.size) {
            val line = l[i++]
            when (line) {
                "X_OFFSET" -> found_offset_x = l[i++].toInt()
                "Y_OFFSET" -> found_offset_y = l[i++].toInt()
                "HORIZONTAL" -> found_horizontal = l[i++].toInt()
                "VERTICAL" -> found_vertical = l[i++].toInt()
                "ELLIPSE" -> {
                    found_ellipse = Rectangle()
                    found_ellipse.width = l[i++].toInt()
                    found_ellipse.height = l[i++].toInt()
                    found_ellipse.x = l[i++].toInt()
                    found_ellipse.y = l[i++].toInt()
                }
                "ROOM" -> {
                    val id = l[i++].toInt()
                    check(id == rooms.size)
                    rooms += Room(this, id, l[i++].toInt(), l[i++].toInt(), l[i++].toInt(), l[i++].toInt())
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

        if (found_ellipse == null)
            throw IllegalStateException("Shield ellipse not specified!")

        shieldOffset = ConstPoint(found_ellipse.x, found_ellipse.y)
        selectedShieldHalfSize = ConstPoint(found_ellipse.width, found_ellipse.height)

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

            system.energyLevels = node.getAttributeValue("power").toInt()

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

            val room = rooms[node.getAttributeValue("room").toInt()]

            // The medbay at least (and maybe other systems, TODO check) use the
            // computer to represent a cell that is obstructed.
            val computerIsObstruction = when (system) {
                is Medbay -> true
                else -> false
            }

            if (computerIsObstruction && compPoint != null) {
                room.obstructions.add(compPoint)
                compPoint = null
            }

            room.setSystem(system, compPoint, compDir)
        }

        val visualsXML = base.parseXML(base["data/${shipNode.getAttributeValue("layout")}.xml"])
        val offsets = visualsXML.rootElement.getChildren("offsets")
        check(offsets.size == 1)
        val floors = offsets[0].getChildren("floor")
        check(floors.size <= 1)
        floorOffset = if (floors.size == 1) {
            ConstPoint(floors[0].getAttributeValue("x").toInt(), floors[0].getAttributeValue("y").toInt())
        } else {
            ConstPoint.ZERO
        }

        val imgTag = visualsXML.rootElement.getChild("img")
        hullOffset = ConstPoint(
                imgTag.getAttributeValue("x").toInt() + ROOM_SIZE * offset.x,
                imgTag.getAttributeValue("y").toInt() + ROOM_SIZE * offset.y)

        // Load the hardpoints
        hardpoints = ArrayList()

        for (node in visualsXML.rootElement.getChild("weaponMounts").children) {
            val hardpoint = Hardpoint(
                    node.getAttributeValue("x").toInt(),
                    node.getAttributeValue("y").toInt(),
                    node.getAttributeValue("rotate")!!.toBoolean(),
                    node.getAttributeValue("mirror")!!.toBoolean(),
                    node.getAttributeValue("gib").toInt(),
                    Direction.valueOf(node.getAttributeValue("slide").toUpperCase())
            )
            hardpoints += hardpoint
        }

        for ((nextHardpoint, node) in shipNode.getChild("weaponList").children.withIndex()) {
            val name = node.getAttributeValue("name")
            val weapon = sys.weapons.blueprints.getValue(name) as ShipWeaponBlueprint

            hardpoints[nextHardpoint].weapon = weapon.buildInstance(this)
        }

        // Set up the pathfinder after the layout is loaded
        pathFinder = PathFinder(this)
    }

    fun render(g: Graphics, selected: Room?) {
        val level = shields?.activeShields ?: 0
        shieldImage.alpha = SHIELD_OPACITY_BASE + SHIELD_OPACITY_LEVEL * level

        // Draw the shield
        when {
            level == 0 -> {
                // Do nothing, shields disabled
            }
            isPlayerShip -> {
                // This is centerpointOfHull - centerpointOfShield + shieldPos
                val shieldPos = Point(hullImage.width, hullImage.height)
                shieldPos.sub(shieldImage.width, shieldImage.height)
                shieldPos.divide(2)
                shieldPos += shieldOffset
                g.drawImage(shieldImage, shieldPos.x.f, shieldPos.y.f)
            }
            else -> {
                // This is centerpointOfHull - centerpointOfShield + shieldPos
                val shieldPos = Point(hullImage.width, hullImage.height)
                shieldPos.divide(2)
                shieldPos.sub(shieldHalfSize.x, shieldHalfSize.y)

                // FIXME the pirate interceptor has a shield point of 0,-130 which clearly makes
                // no sense. Are we just supposed to ignore this?
                // shieldPos += shieldOffset

                g.drawImage(shieldImage,
                        shieldPos.x.f, shieldPos.y.f,
                        shieldPos.x.f + shieldHalfSize.x * 2, shieldPos.y.f + shieldHalfSize.y * 2,
                        0f, 0f,
                        shieldImage.width.f, shieldImage.height.f
                )
            }
        }

        for (room in rooms)
            room.system?.drawBackground(g)

        g.drawImage(hullImage, 0f, 0f)

        if (floorImage != null)
            g.drawImage(floorImage, floorOffset.x.f, floorOffset.y.f)

        // Draw the rooms
        for (room in rooms)
            room.render(g, selected == room)

        // Draw the doors
        for (door in doors) {
            g.color = Color.blue

            if (door.isVertical) {
                val x = door.offsetX - 3
                val y = door.offsetY + 8

                g.color = Color.black
                g.fillRect(x.f, y.f, 6f, 21f)

                g.color = Constants.DOOR_COLOUR_1
                g.fillRect(x.f + 1, y.f + 1, 4f, 21f - 2f)

                g.color = Color.black
                g.drawLine(x.f + 1, y.f + 10, x.f + 5, y.f + 10)
            } else {
                val x = door.offsetX + 8
                val y = door.offsetY - 3

                g.color = Color.black
                g.fillRect(x.f, y.f, 21f, 6f)

                g.color = Constants.DOOR_COLOUR_1
                g.fillRect(x.f + 1, y.f + 1, 21f - 2f, 4f)

                g.color = Color.black
                g.drawLine(x.f + 10, y.f + 1, x.f + 10, y.f + 5)
            }
        }

        // Draw the crew
        for (crew in crew) {
            crew.icon.draw(crew.screenX.f, crew.screenY.f)
        }

        // Draw the system foregrounds
        for (room in rooms)
            room.system?.drawForeground(g)

        // Draw the projectiles
        for (proj in inboundProjectiles) {
            val pos = proj.position
            val angle = (proj.projectileAngle * 180 / Math.PI).toFloat()
            proj.render(g, pos.x.f, pos.y.f, angle)
        }

        // Draw the floating animations (eg, from projectile explosions)
        for (a in animations)
            a.render()

        animations.removeIf { a -> a.isFinished }
    }

    fun screenPosToShipPos(point: Point) {
        point.add(hullOffset.x, hullOffset.y)
        point.x = Math.floorDiv(point.x, ROOM_SIZE)
        point.y = Math.floorDiv(point.y, ROOM_SIZE)
        point.sub(offset.x, offset.y)
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

    fun update(dt: Float) {
        for (room in rooms)
            room.update(dt)

        for (crew in crew)
            crew.update(dt)

        val ib = inboundProjectiles

        // Walk backwards, since missiles remove themselves when they hit
        for (i in ib.size - 1 downTo 0) {
            ib[i].update(dt)
        }

        // Update the animations
        for (a in animations)
            a.update(dt)
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
        var weapon: AbstractWeaponInstance? = null
    }

    class FloatingAnimation(val animation: Animation, val pos: ConstPoint) {
        val isFinished get() = animation.isStopped

        init {
            animation.setLooping(false)
            animation.setAutoUpdate(false)
        }

        fun render() {
            animation.draw(pos.x.f, pos.y.f)
        }

        fun update(dt: Float) {
            animation.update((dt * 1000).toLong())
        }

        companion object {
            fun centered(animation: Animation, center: IPoint): FloatingAnimation {
                val offsetPos = ConstPoint(center.x - animation.width / 2, center.y - animation.height / 2)
                return FloatingAnimation(animation, offsetPos)
            }
        }
    }
}