package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Animation
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.drones.AbstractDrone
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
    val cloakImage: Image? = base.getOrNull("img/ship/${imageName}_cloak.png")?.let { i -> base.readImage(i) }
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

    val shieldOrigin: ConstPoint

    val weaponSlots: Int? = shipNode.getChildTextTrim("weaponSlots")?.toInt()
    val droneSlots: Int? = shipNode.getChildTextTrim("droneSlots")?.toInt()

    val isAutoScout = shipNode.getChild("crewCount")?.getAttributeValue("amount")?.trim() == "0"

    // All the crew currently standing on this ship. This includes drone pawns.
    val crew: MutableList<AbstractCrew> = ArrayList()

    // The subset of the crew that are or aren't intruders. This also includes drone pawns.
    val intruders: List<AbstractCrew> = ArrayList()
    val friendlyCrew: List<AbstractCrew> = ArrayList()

    // These are the friendly drones that exist around the ship,
    // even though they're been removed from the drones system. They're
    // kept here so that if you take a blueprint out of the drones system
    // then put it back in, you won't lose the drone.
    val orphanedDrones = ArrayList<AbstractDrone>()

    val cargoBlueprints = ArrayList<Blueprint?>(listOf(null, null, null, null))
    val augments = ArrayList<AugmentBlueprint>()

    val pathFinder: PathFinder

    val hardpoints: List<Hardpoint>

    val inboundProjectiles: MutableList<IProjectile> = ArrayList()
    val inboundBombs: MutableList<BombBlueprint.FiredBomb> = ArrayList()
    val inboundBeams: MutableList<BeamBlueprint.BeamInstance> = ArrayList()
    val animations: MutableList<FloatingAnimation> = ArrayList()

    // This really is a bit horrible - if this is the enemy ship, we store the beam the
    // player is currently aiming at us to highlight the affected rooms.
    private var inboundBeamAim: SelectedTarget.BeamAim? = null

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

    // The amount of health at which this ship will surrender and attempt to escape
    var escapeHealth: Int = 0
    var surrenderHealth: Int = 0

    /**
     * If this ship is attempting to escape, this is the time remaining until it jumps.
     */
    var escapeTimer: Float? = null

    /**
     * Returns true if this ship has ran out of health
     */
    val isDead: Boolean get() = health == 0

    /**
     * Returns true if this ship is dead, and either it doesn't have any gibs or their animation has finished.
     */
    val isGone: Boolean get() = isDead && gibs.firstOrNull()?.isFinished != false

    val isCloakActive: Boolean get() = cloaking?.active ?: false

    // The amount of power currently used by the ship's systems
    val powerConsumed: Int
        get() {
            var used = 0
            for (room in rooms) {
                used += (room.system as? MainSystem ?: continue).powerSelected
            }
            return used
        }

    var weapons: Weapons? = null
        private set

    var drones: Drones? = null
        private set

    var engines: Engines? = null
        private set

    var shields: Shields? = null
        private set

    var medbay: Medbay? = null
        private set

    var cloaking: Cloaking? = null
        private set

    var teleporter: Teleporter? = null
        private set

    var hacking: Hacking? = null
        private set

    var mindControl: MindControl? = null
        private set

    var piloting: Piloting? = null
        private set

    var oxygen: Oxygen? = null
        private set

    // The ship's evasion, in percent
    val evasion: Int
        get() {
            var evasion: Float = piloting!!.evasion + engines!!.evasion.f
            evasion *= piloting!!.evasionMultiplier

            if (isCloakActive)
                evasion += 60

            return evasion.toInt()
        }

    /**
     * From 0-1.
     */
    var averageOxygen: Float = 1f
        private set

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

        val origin = Point(hullImage.width, hullImage.height)
        origin.divide(2)
        if (isPlayerShip)
            origin += shieldOffset
        shieldOrigin = origin.const

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
                "cloaking" -> Cloaking(blueprint, node)
                "weapons" -> Weapons(blueprint, node)
                "drones" -> Drones(blueprint, node)
                "teleporter" -> Teleporter(blueprint, node)

                // AE-only
                "mind" -> MindControl(blueprint, node)
                "hacking" -> Hacking(blueprint, node)
                "battery" -> BackupBattery(blueprint, node)

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

            val configuration = Room.SystemInstallConfiguration(system, compPoint, compDir)

            // If the system isn't installed by default, set it aside so
            // the user can purchase it in a store.
            // Note that if not specified, the system is included by default. This
            // is commonly found with enemy ships.
            if (node.getAttributeValue("start")?.toBoolean() != false) {
                room.setSystem(configuration)
            } else {
                room.purchasableSystem = configuration
            }
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

        // Load all the drone blueprints
        shipNode.getChild("droneList")?.let { droneList ->
            for ((idx, node) in droneList.children.withIndex()) {
                val name = node.getAttributeValue("name")
                val drone = sys.blueprintManager[name] as DroneBlueprint

                drones!!.drones[idx] = Drones.DroneInfo(drone, null)
            }

            // Load the starting number of drones
            dronesCount = droneList.getAttributeValue("drones")?.toInt() ?: 0
        }

        // Load any augments
        for (augNode in shipNode.getChildren("aug")) {
            val name = augNode.getAttributeValue("name")
            val augment = sys.blueprintManager[name] as AugmentBlueprint
            augments.add(augment)
        }
        require(augments.size <= MAX_AUGMENTS) { "Ship $name has too many augments - ${augments.size}!" }

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
            // Animate between cloaked and uncloaked
            // Note this is guesstimated, not measured - change it if
            // you think it can be made more accurate.
            val cloakFade = cloaking?.cloakFade ?: 0f

            val hullOpacity = 1f - cloakFade * 0.2f
            g.drawImage(hullImage, 0f, 0f, Color(1f, 1f, 1f, hullOpacity))

            if (cloakFade != 0f) {
                requireNotNull(cloakImage) { "Ship '$name' cloaked, but it doesn't have a cloak image!" }

                // The cloak image isn't always the same size as the background image :(
                // Thus we have to centre it or they won't line up.
                val offset = Point(hullImage.imageSize)
                offset -= cloakImage.imageSize
                offset.divideFloor(2)

                g.drawImage(cloakImage, offset.x.f, offset.y.f, Color(1f, 1f, 1f, cloakFade))
            }

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

        for (beam in inboundBeams) {
            beam.renderInbound()
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
    fun renderTargeting(g: Graphics, targets: Weapons.TargetList) {
        for (target in targets) {
            if (target is SelectedTarget.BeamAim)
                renderTargetingBeam(g, target)
            if (target !is SelectedTarget.RoomAim)
                continue

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

        inboundBeamAim = targets.beamAiming
        targets.beamAiming?.let { renderTargetingBeam(g, it) }
    }

    fun renderTargetingBeam(g: Graphics, beam: SelectedTarget.BeamAim) {
        // This is false for beams the player is currently aiming, where their
        // mouse is too close to the start of the beam.
        if (!beam.visible)
            return

        // FTL has a nice 'cap' image that squares off the ends of a beam. However
        // this seemed to cause flickering at the transition between the cap and centre
        // image when rotated, so don't bother with it. Notably the game itself
        // doesn't seem to use this asset.
        // TODO use the cap without a flicker, it really does look nicer.
        val img = sys.getImg("img/misc/beam_center.png")

        g.pushTransform()
        g.translate(beam.startShipPoint.x.f, beam.startShipPoint.y.f)
        g.rotate(0f, 0f, Math.toDegrees(beam.angle.toDouble()).toFloat())

        // Draw the stretched-out middle image
        val length = (beam.weapon.type as BeamBlueprint).length
        img.draw(
            0f, -img.height / 2f, length.f, img.height.f / 2f,
            // Stretch out the centre of the beam - if we don't do this, then
            // the ends appear faded (windows and nvidia, if that's important).
            0.5f, 0f, 0.5f, img.height.f
        )

        g.popTransform()
    }

    private fun drawInterior(g: Graphics, selected: Room?) {
        if (floorImage != null)
            g.drawImage(floorImage, floorOffset.x.f, floorOffset.y.f)

        // Draw the rooms
        for (room in rooms) {
            var roomSelected = selected == room

            // If the user is currently aiming a beam, highlight
            // the rooms it would hit.
            if (inboundBeamAim?.hitRooms?.contains(room) == true) {
                roomSelected = true
            }

            room.render(g, roomSelected)
        }

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
            crew.draw(g)
        }

        // Draw the health bars on top of all the other crew, so two
        // fighting crewmembers can't block one of their health bars.
        for (crew in crew) {
            crew.drawForeground(g)
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

        averageOxygen = rooms.map { it.oxygen }.average().toFloat()

        // Duplicate the crew and drone lists, in case some are removed
        // by dying or (in the case of drones) the enemy ship blowing up.
        for (crew in crew.toTypedArray())
            crew.update(dt)

        // Update the list of intruders and friendly (non-intruder) crewmembers
        require(intruders is ArrayList)
        require(friendlyCrew is ArrayList)
        intruders.clear()
        friendlyCrew.clear()
        for (crew in crew) {
            when (crew.mode) {
                AbstractCrew.SlotType.CREW -> friendlyCrew.add(crew)
                AbstractCrew.SlotType.INTRUDER -> intruders.add(crew)
            }
        }

        ftlChargeProgress += (engines?.chargeRate ?: 0f) * dt / 68f

        for (augment in augments) {
            augment.update(this, dt)
        }
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
        damage(target, type.damage, type.sysDamage, type.ionDamage)

        if (!vfx) return

        val centreX = target.offsetX + target.width * ROOM_SIZE / 2
        val centreY = target.offsetY + target.height * ROOM_SIZE / 2
        playDamageEffect(type, ConstPoint(centreX, centreY))
    }

    fun playDamageEffect(type: AbstractWeaponBlueprint, position: IPoint) {
        val animation = sys.animations[type.explosion ?: error("Default explosion not set")]
        animations += FloatingAnimation.centered(animation.start(), position)
    }

    fun damage(target: Room, damage: Int, systemDamage: Int, ionDamage: Int) {
        health -= damage
        target.system?.dealDamage(systemDamage, ionDamage)
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

    fun addCrewMember(race: String, initial: Boolean, isIntruder: Boolean = false): LivingCrew {
        var freeSpace: RoomPoint? = null
        val mode = if (isIntruder) AbstractCrew.SlotType.INTRUDER else AbstractCrew.SlotType.CREW

        // If this crewmember is being created with the ship, put them
        // in a system that makes sense.
        if (initial && !isIntruder) {
            val systems = listOf(piloting, engines, weapons, shields)
            for (system in systems) {
                if (system == null)
                    continue

                val room = system.room!!

                // If someone's already at the computer, don't put another person there.
                val firstSlot = room.computerPoint ?: ConstPoint.ZERO
                if (!room.isSlotFree(firstSlot, mode))
                    continue

                // Stand by the computer, if possible
                freeSpace = RoomPoint(room, firstSlot)
                break
            }
        }

        // Otherwise just put them wherever they'll fit.
        if (freeSpace == null) {
            freeSpace = findSpaceForCrew(null, mode)
        }

        val raceBlueprint = sys.blueprintManager.blueprints[race] ?: error("Missing crew blueprint for race '$race'")
        require(raceBlueprint is CrewBlueprint)

        val crewMember = raceBlueprint.spawn(freeSpace.room, mode)
        crew.add(crewMember)
        crewMember.jumpTo(freeSpace.room, freeSpace)

        return crewMember
    }

    /**
     * Find a slot somewhere in the ship (accessible from [startingRoom],
     * if this ship has some disconnected rooms) that's empty.
     *
     * This works with the pathfinding slots - the points that a crewmember
     * can be sent to by right-clicking. Thus a room that has no crew in
     * it would be considered full if crew are walking towards every spot.
     *
     * Similarly, a room full of crew would be considered empty if they're
     * all walking elsewhere.
     */
    fun findSpaceForCrew(startingRoom: Room?, type: AbstractCrew.SlotType): RoomPoint {
        val checked = HashSet<Room>()

        // Checks a single room for free space
        fun checkRoom(room: Room): RoomPoint? {
            if (checked.contains(room))
                return null
            checked.add(room)

            val freeSpot = room.firstFreeSlot(type)
            if (freeSpot != null) {
                return RoomPoint(room, freeSpot)
            }

            for (door in room.doors) {
                val neighbour = door.other(room) ?: continue
                checkRoom(neighbour)?.let { return it }
            }

            return null
        }

        // If no room has been specified, start at the piloting room.
        // This is the behaviour used for newly-spawned crew.
        // This might be called very early on before the systems
        // are added, so don't require that piloting exists.
        val firstRoom = startingRoom ?: piloting?.room ?: rooms.first()

        // Check every room via recursion. This will prefer the first
        // room if it's empty, and will only return a room if every room
        // in a path from it to piloting is connected.
        checkRoom(firstRoom)?.let { return it }

        // No free space!
        error("Couldn't find any free space in ship $name, starting at room $firstRoom")
    }

    fun updateAvailableSystems() {
        val systems = rooms.mapNotNull { it.system }

        weapons = systems.mapNotNull { it as? Weapons }.firstOrNull()
        drones = systems.mapNotNull { it as? Drones }.firstOrNull()
        engines = systems.mapNotNull { it as? Engines }.firstOrNull()
        shields = systems.mapNotNull { it as? Shields }.firstOrNull()
        medbay = systems.mapNotNull { it as? Medbay }.firstOrNull()
        cloaking = systems.mapNotNull { it as? Cloaking }.firstOrNull()
        teleporter = systems.mapNotNull { it as? Teleporter }.firstOrNull()
        hacking = systems.mapNotNull { it as? Hacking }.firstOrNull()
        mindControl = systems.mapNotNull { it as? MindControl }.firstOrNull()
        piloting = systems.mapNotNull { it as? Piloting }.firstOrNull()
        oxygen = systems.mapNotNull { it as? Oxygen }.firstOrNull()

        // The UI will need to change to reflect this
        // Note that shipUI may be called this this function is called
        // very early on, right after ship initialisation.
        sys.shipUI?.shipModified()
    }

    fun cargoUpdated() {
        // Update all the buttons that have to change to reflect
        // the cargo being modified.
        sys.shipUI.shipModified()
    }

    /**
     * Place an item in this ship's inventory.
     *
     * If [forced] is true and the ship's inventory is full, the item is placed
     * into the left-behind-when-you-jump window. Otherwise, nothing happens.
     *
     * @return True if the item fits in the ship, false otherwise.
     */
    fun addBlueprint(item: Blueprint, forced: Boolean): Boolean {
        // Augments can only go in their special area
        if (item is AugmentBlueprint) {
            if (augments.size < MAX_AUGMENTS) {
                augments.add(item)
                return true
            }
            // TODO handle forced=true.
            return false
        }

        if (item is ShipWeaponBlueprint) {
            for (slot in 0 until weaponSlots!!) {
                if (hardpoints[slot].weapon != null)
                    continue

                hardpoints[slot].weapon = item.buildInstance(this)
                cargoUpdated()
                return true
            }
        }

        val drones = drones
        if (item is DroneBlueprint && drones != null) {
            for (slot in 0 until droneSlots!!) {
                if (drones.drones[slot] != null)
                    continue

                drones.drones[slot] = Drones.DroneInfo(item, null)
                cargoUpdated()
                return true
            }
        }

        for ((slot, current) in cargoBlueprints.withIndex()) {
            if (current != null)
                continue

            cargoBlueprints[slot] = item
            cargoUpdated()
            return true
        }

        // TODO handle forced=true.

        return false
    }

    /**
     * Check if the ship has an augment by name.
     *
     * This throws an exception if the named blueprint doesn't exist.
     */
    fun hasAugment(name: String): Boolean {
        val blueprint = sys.blueprintManager[name] as AugmentBlueprint
        return augments.contains(blueprint)
    }

    /**
     * Update [Room.reservedPlayerSlots] and [Room.reservedEnemySlots]
     * for every room on the ship.
     *
     * This should mainly be called by [AbstractCrew], whenever a change
     * to their pathfinding target is made. It can also be called whenever
     * the crew on the ship are changed, such as from deaths or new crew.
     */
    fun updateCrewReservedSlots() {
        val conflicts = ArrayList<AbstractCrew>()

        for (room in rooms) {
            room.updateCrewReservedSlots(conflicts)
        }

        // If two crewmembers are in, or set to be in, the same location then
        // one of them will be in the conflicts list. Make them find a new
        // free slot.
        for (conflict in conflicts) {
            val freeSpot = findSpaceForCrew(conflict.room, conflict.mode)
            require(conflict.setTargetRoom(freeSpot.room)) { "Failed to set target of empty room!" }
        }
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

        const val MAX_AUGMENTS: Int = 3
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
