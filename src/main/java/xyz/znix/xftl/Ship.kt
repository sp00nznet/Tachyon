package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Animation
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.HumanCrew
import xyz.znix.xftl.game.ShipGib
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.PathFinder
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.*
import xyz.znix.xftl.shipgen.EnemyShipSpec
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.*
import java.awt.Rectangle
import java.util.stream.Collectors
import kotlin.math.min

class Ship(base: Datafile, shipNode: Element, val sys: SlickGame, val spec: EnemyShipSpec?) {
    val name: String = shipNode.getAttributeValue("name")
    val rooms: List<Room>
    val doors: MutableList<Door> = ArrayList()

    val offset: ConstPoint
    val floorOffset: ConstPoint
    val hullOffset: ConstPoint

    val imageName: String = shipNode.getAttributeValue("img")

    val isPlayerShip: Boolean = name.startsWith("PLAYER_SHIP_")

    val floorImage: Image? = base.getOrNull("img/ship/${imageName}_floor.png")?.let { i -> base.readImage(i) }
    val hullImage: Image = base.readImage("img/${if (isPlayerShip) "ship" else "ships_glow"}/${imageName}_base.png")
    val gibs: List<ShipGib>

    val shieldImage: Image = base.readImage(
        if (isPlayerShip) "img/ship/${
            shipNode.getChildTextTrim("shieldImage")
                ?: imageName
        }_shields1.png" else "img/ship/enemy_shields.png"
    )

    val shieldOffset: ConstPoint

    val selectedShieldHalfSize: ConstPoint

    val shieldHalfSize: ConstPoint
        get() = if (isPlayerShip)
            ConstPoint(shieldImage.width / 2, shieldImage.height / 2)
        else
            selectedShieldHalfSize

    val weaponSlots: Int? = shipNode.getChildTextTrim("weaponSlots")?.toInt()
    val droneSlots: Int? = shipNode.getChildTextTrim("droneSlots")?.toInt()

    val isAutoScout = shipNode.getChild("crewCount")?.getAttributeValue("amount")?.trim() == "0"
    val crew: MutableList<AbstractCrew> = ArrayList()

    val cargoBlueprints = ArrayList<Blueprint?>(listOf(null, null, null, null))

    val pathFinder: PathFinder

    val hardpoints: List<Hardpoint>

    val inboundProjectiles: MutableList<AbstractProjectile> = ArrayList()
    val inboundBombs: MutableList<BombBlueprint.FiredBomb> = ArrayList()
    val animations: MutableList<FloatingAnimation> = ArrayList()

    // The number of fuel, missiles and drones this ship has. The missiles and drones
    // are set during ship loading. Player ships always seem to start with 16 fuel.
    var fuelCount: Int = 16
    var missilesCount: Int = 0
    var dronesCount: Int = 0

    // How much scrap the player has
    var scrap: Int = 10 // TODO 30 on easy

    // How far through charging the FTL drive, 1=fully charged
    var ftlChargeProgress: Float = 0f
    val isFtlCharged get() = ftlChargeProgress >= 1f
    val isFtlReady get() = isFtlCharged && engines!!.powerSelected > 0

    // The raw amount of reactor power purchased by the player
    var purchasedReactorPower: Int = shipNode.getChild("maxPower").getAttributeValue("amount").toInt()

    // The amount of reactor power available for use by the player, taking ion storms, events and so on into account
    val reactorPower: Int get() = purchasedReactorPower

    // The amount of unused reactor power
    val powerAvailable: Int get() = reactorPower - powerConsumed

    val maxHealth = shipNode.getChild("health").getAttributeValue("amount").toInt()
    var health = maxHealth
        set(value) {
            field = value.coerceAtLeast(0).coerceAtMost(maxHealth)
        }

    /**
     * Returns true if this ship has ran out of health
     */
    val isDead: Boolean get() = health == 0

    /**
     * Returns true if this ship is dead, and either it doesn't have any gibs or their animation has finished.
     */
    val isGone: Boolean get() = isDead && gibs.firstOrNull()?.isFinished != false

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

    val drones: Drones?
        get() {
            for (room in rooms)
                return room.system as? Drones ?: continue
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

    val oxygen: Oxygen?
        get() {
            for (room in rooms)
                return room.system as? Oxygen ?: continue
            return null
        }

    // The ship's evasion, in percent
    val evasion: Int get() = (piloting!!.evasionMultiplier * (piloting!!.evasion + engines!!.evasion)).toInt()

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

                else -> error("Unknown line '$line'")
            }
        }

        for (room in rooms) {
            val roomDoors =
                doors.stream().filter { d -> d.left == room || d.right == room }.collect(Collectors.toList())
            room.initialise(roomDoors)
        }

        offset = ConstPoint(found_offset_x, found_offset_y)

        checkNotNull(found_ellipse) { "Shield ellipse not specified!" }

        shieldOffset = ConstPoint(found_ellipse.x, found_ellipse.y)
        selectedShieldHalfSize = ConstPoint(found_ellipse.width, found_ellipse.height)

        for (node in shipNode.getChild("systemList").children) {
            if (node.name == "clonebay") {
                // TODO support
                // for now, just don't overwrite the medbay
                continue
            }

            val blueprint = sys.blueprintManager[node.name] as SystemBlueprint

            val system: AbstractSystem = when (node.name) {
                "doors" -> Doors(blueprint, node)
                "engines" -> Engines(blueprint, node)
                "medbay" -> Medbay(blueprint, node)
                "oxygen" -> Oxygen(blueprint, node)
                "pilot" -> Piloting(blueprint, node)
                "sensors" -> Sensors(blueprint, node)
                "shields" -> Shields(blueprint, node)
                "weapons" -> Weapons(blueprint, node)
                "drones" -> Drones(blueprint, node)
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

            // Load defaults
            // TODO what is this for? Kestrel seems to work fine, and I wrote this ages ago and forgot
            when (system) {
                is Weapons -> {
                    compPoint = ConstPoint(1, 0)
                    compDir = Direction.UP
                }

                is Engines -> {
                    compPoint = ConstPoint(0, 1)
                    compDir = Direction.DOWN
                }

                is Shields -> {
                    compPoint = ConstPoint(0, 0)
                    compDir = Direction.LEFT
                }
            }

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
                        else -> error("Invalid point value '${idx[0].textTrim}'")
                    }

                check(dir.size <= 1)
                check(idx.size <= 1)
            }

            val room = rooms[node.getAttributeValue("room").toInt()]

            // Pick a room with the invalid computer formula if it's a mannable system
            // and the computer is not set.
            compPoint = compPoint ?: when (system) {
                is Piloting, is Engines, is Shields, is Weapons, is Doors, is Sensors -> ConstPoint(999, 999)
                else -> null
            }

            // If the computer position is invalid (outside the room), just find a point
            // that makes sense (doesn't overlap a door).
            if (compPoint != null && !room.containsRelative(compPoint)) {
                // Take a range of the valid X values
                val validPlaces = (0 until room.width).asSequence().flatMap { x ->
                    // Flatmap each of them to the valid positions in that column
                    (0 until room.height).asSequence().map { y -> ConstPoint(x, y) }
                }.flatMap {
                    // Expand each position into two valid edges
                    // Note that in a 1x2/2x1 room this doesn't cover all edges - close enough though
                    val horizontal = if (it.x == 0) Direction.LEFT else Direction.RIGHT
                    val vertical = if (it.y == 0) Direction.UP else Direction.DOWN
                    sequenceOf(Pair(it, horizontal), Pair(it, vertical))
                }.filter { pos ->
                    // Filter out anything that intersects with a door
                    room.doors.none { it.roomPos(room) posEq pos.first && it.dirFor(room) == pos.second }
                }.sortedBy { pos ->
                    // Prefer things that aren't on the same tile as a door
                    if (room.doors.none { it.roomPos(room) posEq pos.first }) 0 else 1
                }.filterNotNull()

                val place = validPlaces.first()
                compPoint = place.first
                compDir = place.second
            }

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

        // The stage-2 and stage-3 boss layouts don't have an offsets tag, but everything else does
        val offsets = visualsXML.rootElement.getChildren("offsets")
        check(offsets.size <= 1)
        val floors = offsets.firstOrNull()?.getChildren("floor") ?: emptyList()
        check(floors.size <= 1)
        floorOffset = if (floors.size == 1) {
            ConstPoint(floors[0].getAttributeValue("x").toInt(), floors[0].getAttributeValue("y").toInt())
        } else {
            ConstPoint.ZERO
        }

        val imgTag = visualsXML.rootElement.getChild("img")
        hullOffset = ConstPoint(
            imgTag.getAttributeValue("x").toInt() + ROOM_SIZE * offset.x,
            imgTag.getAttributeValue("y").toInt() + ROOM_SIZE * offset.y
        )

        // Load the hardpoints
        hardpoints = ArrayList()

        for (node in visualsXML.rootElement.getChild("weaponMounts").children) {
            // In rebel_long.xml the hardpoint direction is missing for some testing stuff.
            // Artillery (used in the boss and federation cruiser) has slide set to 'no'
            val dirName = node.getAttributeValue("slide")?.toUpperCase() ?: continue
            val dir = if (dirName == "NO") null else dirName.let(Direction::valueOf)
            val hardpoint = Hardpoint(
                node.getAttributeValue("x").toInt(),
                node.getAttributeValue("y").toInt(),
                node.getAttributeValue("rotate")!!.toBoolean(),
                node.getAttributeValue("mirror")!!.toBoolean(),
                node.getAttributeValue("gib").toInt(),
                dir
            )
            hardpoints += hardpoint
        }

        // Initialise all the systems
        for (room in rooms) {
            room.system?.initialise(this)
        }

        gibs = ArrayList()
        for (node in visualsXML.rootElement.getChild("explosion").children) {
            gibs += ShipGib(sys, this, node)
        }

        for ((nextHardpoint, node) in shipNode.getChild("weaponList").children.withIndex()) {
            val name = node.getAttributeValue("name")
            val weapon = sys.blueprintManager[name] as ShipWeaponBlueprint

            hardpoints[nextHardpoint].weapon = weapon.buildInstance(this)
        }

        // Load the starting number of missiles
        missilesCount = shipNode.getChild("weaponList")?.getAttributeValue("missiles")?.toInt() ?: 0

        shipNode.getChild("droneList")?.let { droneList ->
            for ((idx, node) in droneList.children.withIndex()) {
                val name = node.getAttributeValue("name")
                val drone = sys.blueprintManager[name] as DroneBlueprint

                drones!!.blueprints[idx] = drone
            }

            // Load the starting number of drones
            dronesCount = droneList.getAttributeValue("drones")?.toInt() ?: 0
        }

        // Set up the pathfinder after the layout is loaded
        pathFinder = PathFinder(this)
    }

    fun render(g: Graphics, interiorVisible: Boolean, selected: Room?) {
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

                g.drawImage(
                    shieldImage,
                    shieldPos.x.f, shieldPos.y.f,
                    shieldPos.x.f + shieldHalfSize.x * 2, shieldPos.y.f + shieldHalfSize.y * 2,
                    0f, 0f,
                    shieldImage.width.f, shieldImage.height.f
                )
            }
        }

        for (room in rooms)
            room.system?.drawBackground(g)

        // If the ship is exploding, draw no further
        if (isDead) {
            if (isGone) {
                //health++ // testing
                //gibs.forEach { it.reset() }
                return
            }
            for (gib in gibs.asReversed()) {
                gib.draw(g, ConstPoint.ZERO)
            }
        } else {
            g.drawImage(hullImage, 0f, 0f)

            if (interiorVisible)
                drawInterior(g, selected)
        }

        // Draw the projectiles
        for (proj in inboundProjectiles) {
            val pos = proj.position
            val angle = (proj.projectileAngle * 180 / Math.PI).toFloat()
            proj.render(g, pos.x.f, pos.y.f, angle)
        }

        for (bomb in inboundBombs) {
            bomb.render()
        }

        // Draw the floating animations (eg, from projectile explosions)
        for (a in animations)
            a.render()

        animations.removeIf { a -> a.isFinished }
    }

    /**
     * Render the crosshairs, beam marker, etc that appear when a weapon
     * is targeted to a specific room. This is in Ship rather than the
     * hostile ship UI, as the player can teleport bombs to their own ship
     * and for debug purposes we may want to show where the AI is targeting
     * their shots.
     */
    fun renderTargeting(targets: Weapons.TargetList) {
        for (target in targets) {
            val room = target.room

            val weaponNumber = min(target.weaponNumber + 1, 4)
            val img = sys.getImg("img/misc/crosshairs_placed${weaponNumber}.png")

            val pos = Point(room.offsetX, room.offsetY)
            pos.x += room.width * ROOM_SIZE / 2
            pos.y += room.height * ROOM_SIZE / 2

            pos.x -= img.width / 2
            pos.y -= img.height / 2

            img.draw(pos)
        }
    }

    private fun drawInterior(g: Graphics, selected: Room?) {
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

                g.color = DOOR_COLOUR_1
                g.fillRect(x.f + 1, y.f + 1, 4f, 21f - 2f)

                g.color = Color.black
                g.drawLine(x.f + 1, y.f + 10, x.f + 5, y.f + 10)
            } else {
                val x = door.offsetX + 8
                val y = door.offsetY - 3

                g.color = Color.black
                g.fillRect(x.f, y.f, 21f, 6f)

                g.color = DOOR_COLOUR_1
                g.fillRect(x.f + 1, y.f + 1, 21f - 2f, 4f)

                g.color = Color.black
                g.drawLine(x.f + 10, y.f + 1, x.f + 10, y.f + 5)
            }
        }

        // Draw the crew
        for (crew in crew) {
            crew.draw()
        }

        // Draw the system foregrounds
        for (room in rooms)
            room.system?.drawForeground(g)
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
        updateExterior(dt)

        if (isDead) {
            for (gib in gibs)
                gib.update(dt)
            return
        }

        for (room in rooms)
            room.update(dt)

        for (crew in crew)
            crew.update(dt)

        ftlChargeProgress += (engines?.chargeRate ?: 0f) * dt / 68f
    }

    private fun updateExterior(dt: Float) {
        // Walk backwards, since missiles remove themselves when they hit
        // FIXME update weapons targeted at this ship after it's been destroyed,
        //  otherwise they just sit there in space which obviously looks weird.
        for (i in inboundProjectiles.size - 1 downTo 0) {
            inboundProjectiles[i].update(dt)
        }
        for (i in inboundBombs.size - 1 downTo 0) {
            inboundBombs[i].update(dt)
        }

        // Remove any projectiles that are now off-screen
        inboundProjectiles.removeIf { it.isDead() }

        // Update the animations
        for (a in animations)
            a.update(dt)
    }

    fun damage(target: Room, type: AbstractWeaponBlueprint, vfx: Boolean = true) {
        damage(target, type.damage, type.sysDamage)

        if (!vfx) return

        val centreX = target.offsetX + target.width * ROOM_SIZE / 2
        val centreY = target.offsetY + target.height * ROOM_SIZE / 2
        playDamageEffect(type, ConstPoint(centreX, centreY))
    }

    fun playDamageEffect(type: AbstractWeaponBlueprint, position: IPoint) {
        val animation = sys.animations[type.explosion ?: error("Default explosion not set")]
        animations += FloatingAnimation.centered(animation.start(), position)
    }

    fun damage(target: Room, damage: Int, systemDamage: Int) {
        health -= damage
        target.system?.dealDamage(systemDamage)
    }

    fun resetAfterJump() {
        // Recharge the shields
        shields?.let { it.activeShields = it.selectedShieldBars }

        // And reset the FTL drive
        ftlChargeProgress = 0f

        // Remove all incoming projectiles
        inboundProjectiles.clear()

        // Reset the weapon charge times
        for (hp in hardpoints) {
            hp.weapon?.timeCharged = 0f
        }
    }

    fun addCrewMember(race: String): AbstractCrew {
        var freeSpace: RoomPoint? = null

        for (room in rooms) {
            for (i in 0 until (room.width * room.height)) {
                if (room.reservedPlayerSlots[i] != null) continue

                freeSpace = RoomPoint(room, room.slotToPoint(i))
                break
            }
        }

        if (freeSpace == null) {
            error("No free cells on ship, cannot spawn crew '$race'")
        }

        val crewMember = HumanCrew(sys.animations, freeSpace.room, AbstractCrew.SlotType.CREW)
        crewMember.position.set(freeSpace)
        crew.add(crewMember)
        freeSpace.room.reservedPlayerSlots[freeSpace.room.pointToSlot(freeSpace)] = crewMember

        return crewMember
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

    data class Hardpoint(
        val x: Int,
        val y: Int,
        val rotate: Boolean,
        val mirror: Boolean,
        val gib: Int,
        val slide: Direction?
    ) {
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
