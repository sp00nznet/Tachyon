package xyz.znix.xftl

import org.jdom2.Element
import org.lwjgl.BufferUtils
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.opengl.renderer.Renderer
import org.newdawn.slick.opengl.renderer.SGL
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.drones.AbstractDrone
import xyz.znix.xftl.drones.AbstractExternalDrone
import xyz.znix.xftl.drones.AbstractIndoorsDrone
import xyz.znix.xftl.game.FTLSound
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ResourceSet
import xyz.znix.xftl.game.ShipGib
import xyz.znix.xftl.layout.Door
import xyz.znix.xftl.layout.PathFinder
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.*
import xyz.znix.xftl.savegame.ISerialReferencable
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.shipgen.EnemyShipSpec
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.*
import java.awt.Rectangle
import java.util.stream.Collectors
import kotlin.math.min

class Ship(base: Datafile, shipNode: Element, val sys: InGameState, val spec: EnemyShipSpec?) : ISerialReferencable {
    val name: String = shipNode.getAttributeValue("name")
    val rooms: List<Room>
    val doors: MutableList<Door> = ArrayList()

    // TODO do this properly, to avoid potentially breaking mods
    val isFlagship: Boolean = name.startsWith("BOSS_")

    /**
     * The localisation key of the ship, defining the in-hangar title
     * for player ships (eg 'The Kestrel').
     */
    val shipTitleKey: String? = shipNode.getChild("name")?.getAttributeValue("id")

    val offset: ConstPoint
    val floorOffset: ConstPoint
    val cloakOffset: ConstPoint
    val hullOffset: ConstPoint

    val imageName: String = shipNode.getAttributeValue("img")

    val isPlayerShip: Boolean = name.startsWith("PLAYER_SHIP_")

    val weaponFireDirection: Direction = when (isPlayerShip) {
        true -> Direction.RIGHT // Weapons fly right
        false -> Direction.UP // Weapons fly upwards
    }

    // Played when any two projectiles collide.
    private val projectileCollisionSound: FTLSound = sys.sounds.getSample("hitHull1")

    val floorImage: Image? = sys.getImgIfExists("img/ship/${imageName}_floor.png")
    val hullImage: Image = sys.getImg("img/${if (isPlayerShip) "ship" else "ships_glow"}/${imageName}_base.png")
    val cloakImage: Image?
    val gibs: List<ShipGib>

    val shieldImage: Image = sys.getImg(
        if (isPlayerShip) "img/ship/${
            shipNode.getChildTextTrim("shieldImage")
                ?: imageName
        }_shields1.png" else "img/ship/enemy_shields.png"
    )

    val shieldOffset: ConstPoint

    val selectedShieldHalfSize: ConstPoint
    val shieldHalfSize: ConstPoint

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

    // This contains all the projectiles, both incoming and outgoing.
    val projectiles: MutableList<IProjectile> = ArrayList()

    val inboundBeams: MutableList<BeamBlueprint.BeamInstance> = ArrayList()
    val animations: MutableList<FloatingAnimation> = ArrayList()

    /**
     * A list of all the drones (friendly or otherwise) that are deployed
     * around this ship.
     */
    val externalDrones = ArrayList<AbstractExternalDrone>()

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

    val maxReactorPower: Int get() = 25

    // The raw amount of reactor power purchased by the player
    var purchasedReactorPower: Int = 5

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
     * The maximum number of super-shield levels this ship can have.
     *
     * This is always 5, except for the flagship since it has a super-super-shield.
     */
    var maxSuperShield: Int = 5

    /**
     * The number of points of super-shield this ship currently has.
     */
    var superShield: Int = 0
        set(value) {
            field = value.coerceIn(0..maxSuperShield)
        }

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

    /**
     * Set by SlickGame, this is true if the opponent's cloak is active.
     *
     * This prevents the weapons from charging.
     */
    var opponentCloakActive: Boolean = false

    /**
     * This is the list of all the possible systems that can be installed on this ship.
     *
     * It's simply the sum of all the [Room.systemSlots] lists.
     */
    val systemSlots: List<SystemInstallConfiguration>

    var systems: List<AbstractSystem> = emptyList()
        private set

    /**
     * The list of all the [MainSystem]s in the ship, sorted into the order
     * they appear left-to-right in the power UI.
     */
    var mainSystems: List<MainSystem> = emptyList()
        private set

    /**
     * The list of all the [SubSystem]s in the ship, sorted into the order
     * they appear in the subsystem tray.
     */
    var subSystems: List<SubSystem> = emptyList()
        private set

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

    var clonebay: Clonebay? = null
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

    // Can't just call this 'doors' since that's the list of all
    // the doors in the ship.
    var doorsSystem: Doors? = null
        private set

    var oxygen: Oxygen? = null
        private set

    // The flagship has multiple artillery systems.
    var artillery: List<Artillery> = emptyList()
        private set

    // The ship's evasion, in percent
    val evasion: Int
        get() {
            var evasion: Float = piloting!!.evasion + engines!!.evasion.f
            evasion *= piloting!!.evasionMultiplier
            evasion *= engines!!.evasionMultiplier

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
        // Load the cloak image - if one is set by name use that, otherwise
        // guess based on the ship name (this is required on the Kestrel, for example).
        val customCloakName = shipNode.getChildTextTrim("cloakImage")
        val customCloakImage = customCloakName?.let { sys.getImg("img/ship/${it}_cloak.png") }
        val autoCloakImage = sys.getImgIfExists("img/ship/${imageName}_cloak.png")
        cloakImage = customCloakImage ?: autoCloakImage

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

        // The player ship uses the exact size of the shield image,
        // while enemies scale it to fit a custom size.
        shieldHalfSize = when {
            isPlayerShip -> ConstPoint(shieldImage.width / 2, shieldImage.height / 2)
            else -> selectedShieldHalfSize
        }

        for ((index, node) in shipNode.getChild("systemList").children.withIndex()) {
            val room = rooms[node.getAttributeValue("room").toInt()]

            // Load the information about the available system into the room.
            // This will be used when loading the system from a save, spawning
            // a new ship, or buying a system at a store.
            room.systemSlots += SystemInstallConfiguration(node, sys, room, index)
        }

        systemSlots = rooms.flatMap { it.systemSlots }

        val visualsXML = base.parseXML(base["data/${shipNode.getAttributeValue("layout")}.xml"]).rootElement

        // The stage-2 and stage-3 boss layouts don't have an offsets tag, but everything else does
        val offsets = visualsXML.getChild("offsets")
        floorOffset = offsets?.getChild("floor")?.let { Utils.parsePosElem(it) } ?: ConstPoint.ZERO
        cloakOffset = offsets?.getChild("cloak")?.let { Utils.parsePosElem(it) } ?: ConstPoint.ZERO

        val imgTag = visualsXML.getChild("img")
        hullOffset = Utils.parsePosElem(imgTag) + ConstPoint(ROOM_SIZE * offset.x, ROOM_SIZE * offset.y)

        // We can only calculate the shield origin after we've got the hull offset
        val origin = Point(hullImage.width, hullImage.height)
        origin.divide(2)
        origin += hullOffset
        if (isPlayerShip)
            origin += shieldOffset
        shieldOrigin = origin.const

        // Load the hardpoints
        hardpoints = ArrayList()

        for (node in visualsXML.getChild("weaponMounts").children) {
            // In rebel_long.xml the hardpoint direction is missing for some testing stuff.
            // Artillery (used in the boss and federation cruiser) has slide set to 'no'
            val dirName = node.getAttributeValue("slide")?.toUpperCase() ?: continue
            val pos = Utils.parsePosElem(node) + hullOffset
            val dir = if (dirName == "NO") null else dirName.let(Direction::valueOf)
            val hardpoint = Hardpoint(
                pos,
                node.getAttributeValue("rotate")!!.toBoolean(),
                node.getAttributeValue("mirror")!!.toBoolean(),
                node.getAttributeValue("gib").toInt(),
                dir
            )
            hardpoints += hardpoint
        }

        gibs = ArrayList()
        for (node in visualsXML.getChild("explosion").children) {
            gibs += ShipGib(sys, this, node)
        }

        // Set up the pathfinder after the layout is loaded
        pathFinder = PathFinder(this)
    }

    /**
     * Loads the default version of the ship's stuff (system, blueprints, etc) - this
     * is all stuff that the player can upgrade and needs to be saved.
     */
    fun loadDefaultContents(shipNode: Element) {
        // Load the starting reactor power
        purchasedReactorPower = shipNode.getChild("maxPower").getAttributeValue("amount").toInt()

        // Load the starting systems
        for (config in systemSlots) {
            if (!config.availableByDefault)
                continue

            config.room.setSystem(config)
        }

        // Load all the weapon blueprints
        val weaponsList = shipNode.getChild("weaponList")
        if (weaponsList != null) {
            for ((nextHardpoint, node) in weaponsList.children.withIndex()) {
                val name = node.getAttributeValue("name")
                val weapon = sys.blueprintManager[name] as AbstractWeaponBlueprint

                hardpoints[nextHardpoint].weapon = weapon.buildInstance(this)
            }

            // Load the starting number of missiles
            missilesCount = weaponsList.getAttributeValue("missiles")?.toInt() ?: 0
        }

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
            augment.onShipSpawn(this)
            augments.add(augment)
        }
        require(augments.size <= MAX_AUGMENTS) { "Ship $name has too many augments - ${augments.size}!" }

        // If we don't have an oxygen system, the rooms start with no oxygen by default
        if (oxygen == null) {
            for (room in rooms) {
                room.oxygen = 0f
            }
        }
    }

    fun render(g: Graphics, interiorVisible: Boolean, selected: Room?) {
        // Draw the shield
        renderShields()

        // Draw departing shots. We'll draw the rest of them later.
        for (proj in projectiles) {
            if (proj.drawUnderShip) {
                proj.render(g, this)
            }
        }

        for (system in systems)
            system.drawBackground(g)

        // If the ship is exploding, draw no further
        if (isDead) {
            if (isGone) {
                //health++ // testing
                //gibs.forEach { it.reset() }
                return
            }
            for (gib in gibs.asReversed()) {
                gib.draw(g, hullOffset)
            }
        } else {
            // Animate between cloaked and uncloaked
            // Note this is guesstimated, not measured - change it if
            // you think it can be made more accurate.
            val cloakFade = cloaking?.cloakFade ?: 0f

            val hullOpacity = 1f - cloakFade * 0.2f
            g.drawImage(hullImage, hullOffset.x.f, hullOffset.y.f, Color(1f, 1f, 1f, hullOpacity))

            if (cloakFade != 0f) {
                requireNotNull(cloakImage) { "Ship '$name' cloaked, but it doesn't have a cloak image!" }

                g.drawImage(
                    cloakImage,
                    hullOffset.x.f + cloakOffset.x.f, hullOffset.y.f + cloakOffset.y.f,
                    Color(1f, 1f, 1f, cloakFade)
                )
            }

            if (interiorVisible)
                drawInterior(g, selected)
        }

        // Draw the debug hardpoint visuals
        if (sys.debugFlags.showHardpoints.set) {
            // Get our translation
            val buffer = BufferUtils.createFloatBuffer(16)
            Renderer.get().glGetFloat(SGL.GL_MODELVIEW_MATRIX, buffer)
            val translateX = buffer[12]
            val translateY = buffer[13]

            for ((index, hp) in hardpoints.withIndex()) {
                val pos = hp.position
                g.color = Color.red
                g.drawLine(pos.x - 5f, pos.y - 5f, pos.x + 5f, pos.y + 5f)
                g.drawLine(pos.x + 5f, pos.y - 5f, pos.x - 5f, pos.y + 5f)

                sys.getFont("JustinFont8").drawString(
                    translateX + pos.x.f + 5f,
                    translateY + pos.y + 8f,
                    index.toString(), Color.red
                )
            }
        }

        // Draw the projectiles, except for those we already
        // drew underneath the ship earlier.
        for (proj in projectiles) {
            if (!proj.drawUnderShip) {
                proj.render(g, this)
            }
        }

        for (beam in inboundBeams) {
            beam.renderInbound()
        }

        // Draw any drones flying around the ship
        for (drone in externalDrones) {
            drone.renderExternal(g)
        }

        // Draw the floating animations (eg, from projectile explosions)
        for (a in animations)
            a.render(g)

        animations.removeIf { a -> a.isFinished }
    }

    private fun renderShields() {
        val level = shields?.activeShields ?: 0

        // Draw the standard (non-Zoltan) shield
        val shieldMax = if (isPlayerShip) 3.5f else 4f // This is odd, but correct
        val levelFraction = level / shieldMax
        val alpha = SHIELD_OPACITY_BASE + SHIELD_OPACITY_SCALING * levelFraction

        if (level == 0) {
            // Do nothing, shields disabled
        } else {
            renderSingleShield(alpha, Color.white)
        }

        // Draw the super shields
        if (superShield != 0) {
            var superShieldAlpha = superShield.f / maxSuperShield

            if (level == 0) {
                superShieldAlpha = SHIELD_OPACITY_BASE + SHIELD_OPACITY_SCALING * superShieldAlpha
            }

            renderSingleShield(superShieldAlpha, SYS_ENERGY_ACTIVE)

            // TODO fix the ugly darkened line around the edge of enemy super-shields
            // I suspect this is caused by the scaling - Slick has had problems
            // rendering drones while rotating with white aliasing artefacts
            // along their edges, so maybe it's the same thing?
            // Or it could possible be some kind of SRGB-related issue.
        }
    }

    private fun renderSingleShield(alpha: Float, filter: Color) {
        val basePosX = shieldOrigin.x - shieldHalfSize.x
        val basePosY = shieldOrigin.y - shieldHalfSize.y

        shieldImage.alpha = alpha

        // Draw the image scaled to fit the
        shieldImage.draw(
            basePosX.f, basePosY.f,
            basePosX.f + shieldHalfSize.x * 2, basePosY.f + shieldHalfSize.y * 2,
            0f, 0f,
            shieldImage.width.f, shieldImage.height.f,
            filter
        )
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

            val pos = Point(room.pixelCentre)

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
        if (floorImage != null) {
            g.drawImage(floorImage, floorOffset.x.f + hullOffset.x.f, floorOffset.y.f + hullOffset.y.f)
        }

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
            door.render(g)
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
        for (system in systems)
            system.drawForeground(g)
    }

    fun screenPosToShipPos(point: Point) {
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

        // Update the door open/close animations
        for (door in doors) {
            door.update(dt)
        }

        averageOxygen = rooms.map { it.oxygen }.average().toFloat()

        updateCrew(dt)

        ftlChargeProgress += (engines?.chargeRate ?: 0f) * dt / 68f
        if (ftlChargeProgress > 1f)
            ftlChargeProgress = 1f

        for (augment in augments) {
            augment.update(this, dt)
        }
    }

    private fun updateCrew(dt: Float) {
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
    }

    private fun updateExterior(dt: Float) {
        // Walk backwards, since projectiles may remove themselves
        for (i in projectiles.size - 1 downTo 0) {
            projectiles[i].update(dt, this)
        }

        // Check for collisions between any two projectiles. To avoid
        // checking a pair twice (but in a swapped order), only check
        // a projectile against those that come after it in the list.
        // By iterating down, we can again ignore the problem of changing
        // indices when projectiles are removed.
        for (lowerI in projectiles.size - 1 downTo 0) {
            val first = projectiles[lowerI]
            if (!first.collisionsEnabled)
                continue

            for (upperI in projectiles.size - 1 downTo lowerI + 1) {
                val second = projectiles[upperI]

                if (!second.collisionsEnabled)
                    continue

                // Make sure the projectiles are targeting different ships - otherwise
                // you could shoot down your own projectiles, and in particular flak
                // projectiles would collide with each other.
                if (first !is AbstractProjectile || second !is AbstractProjectile) {
                    continue
                }

                // A projectile that's not fired at a ship is assumed to be friendly,
                // like a defence drone laser.
                val isFirstFriendly = first.targetShip != this
                val isSecondFriendly = second.targetShip != this
                if (isFirstFriendly == isSecondFriendly) {
                    continue
                }

                // Check how close the projectiles are - their collision shape is represented
                // by a couple of circles, so this is how we do the check.
                val distSq = first.position.distToSq(second.position)
                val maxDist = first.hitboxRadius + second.hitboxRadius

                // No overlap?
                if (distSq > maxDist * maxDist)
                    continue

                first.hitOtherProjectile(this)
                second.hitOtherProjectile(this)

                // Play a single hit sound. It seems that FTL doesn't
                // play this missile explode sound when a drone shoots
                // down a missile, so always using one is probably
                // close to correct.
                // It also means we don't play two sounds for the same
                // thing, which would be the case if hitOtherProjectile
                // played a sound effect.
                projectileCollisionSound.play()

                projectiles.removeAt(upperI)
                projectiles.removeAt(lowerI)

                // Don't process any more projectiles in relation to the
                // one from the outer loop, as we removed it.
                break
            }
        }

        // Check for collisions between projectiles and drones
        for (droneI in externalDrones.size - 1 downTo 0) {
            val drone = externalDrones[droneI]

            for (projectileI in projectiles.size - 1 downTo 0) {
                val proj = projectiles[projectileI]

                if (!proj.collisionsEnabled)
                    continue

                val distSq = drone.flightController.position.distToSq(proj.position)
                val maxDist = drone.hitboxRadius + proj.hitboxRadius

                // No overlap?
                if (distSq > maxDist * maxDist)
                    continue

                if (!drone.canCollideWith(proj))
                    continue

                proj.hitOtherProjectile(this)
                projectiles.removeAt(projectileI)

                // The drone is expected to play a sound.
                drone.hitProjectile(proj)

                break
            }
        }


        // Update the animations
        for (a in animations) {
            a.update(dt)
        }
    }

    fun damage(target: Room, type: AbstractWeaponBlueprint, vfx: Boolean = true) {
        damage(target, type.damage, type.sysDamage, type.ionDamage)

        if (!vfx) return

        playDamageEffect(type, target.pixelCentre)

        type.hitShipSounds?.get()?.play()
    }

    fun playDamageEffect(type: AbstractWeaponBlueprint, position: IPoint) {
        val animation = sys.animations[type.explosion ?: error("Default explosion not set")]
        animations += FloatingAnimation.centred(animation, position)
    }

    fun damage(target: Room, damage: Int, systemDamage: Int, ionDamage: Int) {
        if (sys.debugFlags.noDmg.set)
            return

        health -= damage
        target.system?.dealDamage(systemDamage, ionDamage)
    }

    fun resetAfterJump() {
        // Recharge the shields
        shields?.let { it.activeShields = it.selectedShieldBars }

        // And reset the FTL drive
        ftlChargeProgress = 0f

        // Jumping clears the super shield (if the augment is
        // present, it'll add it back in afterwards).
        superShield = 0

        // Remove all incoming and outgoing projectiles
        projectiles.clear()

        // Get rid of any drones orbiting us
        externalDrones.clear()

        // Reset the weapon charge times
        for (hp in hardpoints) {
            hp.weapon?.timeCharged = 0f
        }

        // Clear any previously-set scripted power limits, and re-apply
        // any that were previously set at this beacon.
        updateScriptedPowerLimits()

        for (augment in augments) {
            augment.onJump(this)
        }

        for (system in systems) {
            system.onJump()
        }

        // TODO when we implement door attacking, clear out the damage
        //  during a jump to reduce the size of the save file. This doesn't
        //  exactly match vanilla, but it's exceedingly unlikely to matter
        //  as long as there aren't any boarders.
    }

    /**
     * This updates the power limits imposed on systems by scripted events.
     *
     * This must only be called on the player ship - on the enemy ship, systems
     * should be limited directly. This is because the player can jump between
     * beacons, and the effects need to be cleared and re-applied as appropriate.
     */
    fun updateScriptedPowerLimits() {
        for (system in systems) {
            val limit = sys.currentBeacon.powerLimitEffects[system.codename]
            system.scriptedPowerLimit = limit
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
                val firstSlot = system.configuration.computerPoint ?: ConstPoint.ZERO
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
        crewMember.jumpTo(freeSpace)

        // If this is an intruder, make sure they don't count as being
        // 'owned' by the player. This means they can't use the player's
        // augments, for example.
        if (isIntruder) {
            crewMember.ownerShip = null
        }

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
        systems = rooms.mapNotNull { it.system }
        mainSystems = systems.mapNotNull { it as? MainSystem }.sortedBy { it.sortingType }
        subSystems = systems.mapNotNull { it as? SubSystem }.sortedBy { it.sortingType }

        weapons = systems.mapNotNull { it as? Weapons }.firstOrNull()
        drones = systems.mapNotNull { it as? Drones }.firstOrNull()
        engines = systems.mapNotNull { it as? Engines }.firstOrNull()
        shields = systems.mapNotNull { it as? Shields }.firstOrNull()
        medbay = systems.mapNotNull { it as? Medbay }.firstOrNull()
        clonebay = systems.mapNotNull { it as? Clonebay }.firstOrNull()
        cloaking = systems.mapNotNull { it as? Cloaking }.firstOrNull()
        teleporter = systems.mapNotNull { it as? Teleporter }.firstOrNull()
        hacking = systems.mapNotNull { it as? Hacking }.firstOrNull()
        mindControl = systems.mapNotNull { it as? MindControl }.firstOrNull()
        piloting = systems.mapNotNull { it as? Piloting }.firstOrNull()
        doorsSystem = systems.mapNotNull { it as? Doors }.firstOrNull()
        oxygen = systems.mapNotNull { it as? Oxygen }.firstOrNull()

        // If we have multiple artillery systems, sort them by the order
        // they appear in the XML. This probably isn't necessary, but if
        // we're using this order to display them in the UI or anything
        // like that it's nice to have this order.
        artillery = systems.mapNotNull { it as? Artillery }.sortedBy { it.configuration.systemIndex }

        // The UI will need to change to reflect this
        // Note that this function is called very early on, before shipUI
        // is initialised, right after ship initialisation.
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
                // TODO break down non-stackable duplicated augments
                //  to scrap, using the AUGMENT_FULL event.
                augments.add(item)
                cargoUpdated()
                return true
            }
            // TODO handle forced=true.
            return false
        }

        if (item is AbstractWeaponBlueprint) {
            val weaponSlotCount = weaponSlots ?: hardpoints.size

            for (slot in 0 until weaponSlotCount) {
                if (hardpoints[slot].weapon != null)
                    continue

                hardpoints[slot].weapon = item.buildInstance(this)
                cargoUpdated()
                return true
            }
        }

        val drones = drones
        if (item is DroneBlueprint && drones != null) {
            for (slot in 0 until drones.drones.size) {
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

    /**
     * Serialise this ship and everything related to it (projectiles,
     * crew, etc) into an XML element.
     */
    fun saveToXML(elem: Element, globalRefs: ObjectRefs) {
        // Create IDs for all the objects that might reference each other
        // Note that events can reference crewmembers, so register them
        // into the global refs.
        val refs = ObjectRefs(globalRefs)
        for (crew in this.crew) {
            globalRefs.register(crew, crew.codename)
        }

        // Build the XML
        SaveUtil.addObjectId(elem, refs, this)

        // These two are read by InGameState, as they're needed for constructor arguments.
        SaveUtil.addAttr(elem, "shipId", name)
        SaveUtil.addAttr(elem, "specId", spec?.name ?: "null")

        val resources = ResourceSet()
        resources.fuel = fuelCount
        resources.missiles = missilesCount
        resources.droneParts = dronesCount
        resources.scrap = scrap

        val resourcesElem = Element("resources")
        resources.saveToXML(resourcesElem, refs)
        elem.addContent(resourcesElem)

        SaveUtil.addAttrFloat(elem, "ftlProgress", ftlChargeProgress)
        SaveUtil.addAttrInt(elem, "reactorPower", purchasedReactorPower)
        SaveUtil.addAttrInt(elem, "health", health)

        SaveUtil.addTagInt(elem, "escapeHealth", escapeHealth, 0)
        SaveUtil.addTagInt(elem, "surrenderHealth", surrenderHealth, 0)
        SaveUtil.addTagFloat(elem, "escapeTimer", escapeTimer, null)
        SaveUtil.addTagInt(elem, "maxSuperShield", maxSuperShield, 5)
        SaveUtil.addTagInt(elem, "superShield", superShield, 0)

        val crewListElem = Element("crew")
        for (crew in this.crew) {
            // We save drones separately
            if (crew is AbstractIndoorsDrone.Pawn)
                continue

            val crewElem = Element("crewMember")
            crew.saveToXML(crewElem, refs)
            crewListElem.addContent(crewElem)
        }
        elem.addContent(crewListElem)

        val systemListElem = Element("systems")
        for (system in systems) {
            val systemElem = Element("system")
            system.saveToXML(systemElem, refs)

            // Save the room this system is in, as we'll need it to match up
            // artillery weapons to the correct room on the flagship.
            SaveUtil.addAttrInt(systemElem, "room", system.room!!.id)

            systemListElem.addContent(systemElem)
        }
        elem.addContent(systemListElem)

        // Serialise the weapons
        val weaponsElem = Element("weapons")
        for ((index, hardpoint) in hardpoints.withIndex()) {
            val weapon = hardpoint.weapon ?: continue

            val weaponElem = Element("weapon")
            SaveUtil.addAttrInt(weaponElem, "hardpoint", index)
            weapon.saveToXML(weaponElem, refs)
            weaponsElem.addContent(weaponElem)
        }
        elem.addContent(weaponsElem)

        // Serialise the drones. If a drone is deployed to another ship, it's
        // serialised on that ship instead of this one.
        val dronesElem = Element("drones")
        for (drone in externalDrones) {
            val droneElem = Element("drone")
            drone.saveToXML(droneElem, refs)
            dronesElem.addContent(droneElem)
        }
        for (pawn in crew.mapNotNull { it as? AbstractIndoorsDrone.Pawn }) {
            val droneElem = Element("drone")
            pawn.drone.saveToXML(droneElem, refs)
            dronesElem.addContent(droneElem)
        }
        for (flyingDrone in projectiles.mapNotNull { it as? FlyingDroneProjectile }) {
            val droneElem = Element("drone")
            flyingDrone.drone.saveToXML(droneElem, refs)
            dronesElem.addContent(droneElem)
        }
        elem.addContent(dronesElem)

        // Serialise the doors. Most of the time there's nothing interesting
        // about them other than whether they're open or closed, but occasionally
        // they have more information (like the damage they've taken from
        // being attacked).
        val doorsElem = Element("doors")
        val doorStateString = StringBuilder()
        for ((index, door) in doors.withIndex()) {
            val thisDoorElem = door.saveToXML()
            thisDoorElem?.setAttribute("doorIndex", index.toString())
            thisDoorElem?.let { doorsElem.addContent(it) }

            doorStateString.append(if (door.open) 'Y' else 'n')
        }
        val doorStateElem = Element("isOpen")
        doorStateElem.addContent(doorStateString.toString())
        doorsElem.addContent(doorStateElem)
        elem.addContent(doorsElem)

        // Serialise the room oxygen levels. Since there's a lot of rooms,
        // don't serialise them all individually.
        val oxygenLevelString = StringBuilder()
        for (room in rooms) {
            oxygenLevelString.append(room.oxygen)
            oxygenLevelString.append(' ')
        }
        val oxygenLevelElem = Element("oxygenLevels")
        oxygenLevelElem.addContent(oxygenLevelString.toString().trim())
        elem.addContent(oxygenLevelElem)

        // Serialise any floating animations, like the animations from a weapon
        // hitting a room or the shields.
        val animationsElem = Element("animations")
        for (animation in animations) {
            val animElem = Element("animation")
            animation.saveToXML(animElem)
            animationsElem.addContent(animElem)
        }
        elem.addContent(animationsElem)

        // Serialise in-flight projectile
        val projectilesElem = Element("projectiles")
        for (projectile in projectiles) {
            // A flying drone is saved as a drone rather than a projectile,
            // though really it's kinda both.
            if (projectile is FlyingDroneProjectile)
                continue

            val projectileElem = Element("projectile")
            SaveUtil.addAttr(projectileElem, "loadType", projectile.serialisationType)
            projectile.saveToXML(projectileElem, refs)
            projectilesElem.addContent(projectileElem)
        }
        elem.addContent(projectilesElem)
    }

    fun loadFromXml(rootElem: Element, refs: RefLoader) {
        SaveUtil.registerObjectId(rootElem, refs, this)

        val resources = ResourceSet(rootElem.getChild("resources"), refs, sys.content)
        fuelCount = resources.fuel
        missilesCount = resources.missiles
        dronesCount = resources.droneParts
        scrap = resources.scrap

        ftlChargeProgress = SaveUtil.getAttrFloat(rootElem, "ftlProgress")
        purchasedReactorPower = SaveUtil.getAttrInt(rootElem, "reactorPower")
        health = SaveUtil.getAttrInt(rootElem, "health")

        escapeHealth = SaveUtil.getOptionalTagInt(rootElem, "escapeHealth") ?: 0
        surrenderHealth = SaveUtil.getOptionalTagInt(rootElem, "surrenderHealth") ?: 0
        escapeTimer = SaveUtil.getOptionalTagFloat(rootElem, "escapeTimer")
        maxSuperShield = SaveUtil.getOptionalTagInt(rootElem, "maxSuperShield") ?: 5

        // This must be set after maxSuperShield to avoid it being wrongly clamped
        superShield = SaveUtil.getOptionalTagInt(rootElem, "superShield") ?: 0

        // Load the systems
        for (elem in rootElem.getChild("systems").getChildren("system")) {
            // Spawn in the system
            val name: String = elem.getAttributeValue("name")
            val blueprint = sys.blueprintManager[name] as SystemBlueprint

            // Find the slot this system is in.
            // This isn't as simple as it might sound, since for artillery
            // we can have multiple systems installed. Thus we need to use
            // the serialised room ID to make sure we load this into the
            // correct instance of the system.
            val roomId = SaveUtil.getAttrInt(elem, "room")
            val room = rooms[roomId]

            val slot = room.systemSlots.first { it.system == blueprint }
            room.setSystem(slot)

            // Re-load the system's properties
            room.system!!.loadFromXML(elem, refs)
        }

        // Load the crew
        for (crewElem in rootElem.getChild("crew").getChildren("crewMember")) {
            // TODO support drone pawns
            val crewMember = addCrewMember(crewElem.getAttributeValue("type"), false)
            crewMember.loadFromXML(crewElem, refs)
        }

        // Give the crew a couple of zero-time updates, to let them
        // update stuff like their icons.
        // Note in particular that if there's a queued teleport action,
        // the crew have to realise they're standing in a cell otherwise
        // it'll think there's no-one available to teleport.
        for (i in 0 until 3) {
            updateCrew(0f)
        }

        // Deserialise the doors.
        val doorsElem = rootElem.getChild("doors")
        for (doorElem in doorsElem.getChildren("door")) {
            val index = SaveUtil.getAttrInt(doorElem, "doorIndex")
            doors[index].loadFromXML(doorElem)
        }
        val doorStateString = doorsElem.getChildTextTrim("isOpen")
        for ((index, door) in doors.withIndex()) {
            val open = doorStateString[index] == 'Y'
            door.loadSavedOpen(open)
        }

        // Deserialise the oxygen levels, which are just numbers for each room.
        val oxygenLevels = rootElem.getChildTextTrim("oxygenLevels").split(' ', '\t')
        for ((index, levelStr) in oxygenLevels.withIndex()) {
            val room = rooms[index]
            room.oxygen = levelStr.toFloat()
        }

        // Deserialise the weapons
        for (weaponElem in rootElem.getChild("weapons").getChildren("weapon")) {
            val hardpointIndex = SaveUtil.getAttrInt(weaponElem, "hardpoint")
            val type = SaveUtil.getAttr(weaponElem, "type")
            val blueprint = sys.blueprintManager[type] as AbstractWeaponBlueprint

            val weapon = blueprint.buildInstance(this)
            weapon.loadFromXML(weaponElem, refs)
            hardpoints[hardpointIndex].weapon = weapon
        }

        // Deserialise the drones, both friendly and not.
        for (droneElem in rootElem.getChild("drones").getChildren("drone")) {
            val type = SaveUtil.getAttr(droneElem, "type")
            val blueprint = sys.blueprintManager[type] as DroneBlueprint

            val drone = blueprint.makeInstance()
            drone.loadFromXML(droneElem, refs, this)

            // The drone will add itself to the externalDrones or crew list.
        }

        // Deserialise explosion (and similar) animations
        for (animElem in rootElem.getChild("animations").getChildren("animation")) {
            animations += FloatingAnimation.loadFromXML(animElem, sys)
        }

        // Deserialise the in-flight projectiles
        for (projectileElem in rootElem.getChild("projectiles").getChildren("projectile")) {
            val serialisationType = SaveUtil.getAttr(projectileElem, "loadType")
            IProjectile.loadFromXML(sys, projectileElem, refs, serialisationType) { projectiles += it }
        }

        updateCrewReservedSlots()
        updateAvailableSystems()
    }

    companion object {
        const val MAX_AUGMENTS: Int = 3
    }

    data class Hardpoint(
        val position: IPoint,
        val rotate: Boolean,
        val mirror: Boolean,
        val gib: Int,
        val slide: Direction?
    ) {
        var weapon: AbstractWeaponInstance? = null
    }

    class FloatingAnimation(val spec: AnimationSpec, val pos: ConstPoint, val scaling: Float = 1f) {
        private var timer: Float = 0f
        val isFinished get() = timer >= spec.totalTime

        fun render(g: Graphics) {
            val frameId = (timer / spec.time).toInt().coerceIn(0 until spec.length)
            val frame = spec.spriteAt(frameId)

            if (scaling == 1f) {
                frame.draw(pos.x.f, pos.y.f)
                return
            }

            // This is used for flak
            g.pushTransform()
            g.translate(pos.x.f, pos.y.f)
            g.scale(scaling, scaling)
            frame.draw()
            g.popTransform()
        }

        fun update(dt: Float) {
            timer += dt
        }

        fun saveToXML(elem: Element) {
            SaveUtil.addAttr(elem, "name", spec.name)
            SaveUtil.addAttrInt(elem, "x", pos.x)
            SaveUtil.addAttrInt(elem, "y", pos.y)
            SaveUtil.addAttrFloat(elem, "timer", timer)
            SaveUtil.addAttrFloat(elem, "scaling", scaling)
        }

        companion object {
            fun centred(animation: AnimationSpec, centre: IPoint): FloatingAnimation {
                val firstFrame = animation.spriteAt(0)
                val offsetPos = ConstPoint(centre.x - firstFrame.width / 2, centre.y - firstFrame.height / 2)
                return FloatingAnimation(animation, offsetPos)
            }

            fun loadFromXML(elem: Element, game: InGameState): FloatingAnimation {
                val name = SaveUtil.getAttr(elem, "name")
                val spec = game.animations[name]

                val x = SaveUtil.getAttrInt(elem, "x")
                val y = SaveUtil.getAttrInt(elem, "y")
                val scaling = SaveUtil.getAttrFloat(elem, "scaling")

                val anim = FloatingAnimation(spec, ConstPoint(x, y), scaling)
                anim.timer = SaveUtil.getAttrFloat(elem, "timer")

                return anim
            }
        }
    }
}
