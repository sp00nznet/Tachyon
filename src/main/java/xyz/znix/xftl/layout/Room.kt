package xyz.znix.xftl.layout

import org.newdawn.slick.Animation
import org.newdawn.slick.Graphics
import xyz.znix.xftl.AbstractSystem
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.Ship
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.f
import xyz.znix.xftl.lerp
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.systems.Oxygen
import java.util.*
import kotlin.math.PI
import kotlin.math.max
import kotlin.random.Random

data class Room(val ship: Ship, val id: Int, val x: Int, val y: Int, val width: Int, val height: Int) {

    init {
        require(x >= 0) { "Negative x room coordinates are not allowed for ship ${ship.name}" }
        require(y >= 0) { "Negative y room coordinates are not allowed for ship ${ship.name}" }
    }

    var system: AbstractSystem? = null
        private set
    var computerDirection: Direction? = null
        private set
    var computerPoint: IPoint? = null
        private set

    /**
     * The system that can be bought and installed into this room, but
     * hasn't been yet.
     */
    var purchasableSystem: SystemInstallConfiguration? = null

    private var _doors: List<Door>? = null
    val doors: List<Door>
        get() {
            return _doors ?: error("Doors are not yet initialised")
        }

    /**
     * A list of all the crew (friendly or intruders, including drones)
     * currently inside this room.
     */
    private val _crew = ArrayList<AbstractCrew>()
    val crew: List<AbstractCrew> get() = _crew

    /**
     * Oxygen level from 1-0.
     */
    var oxygen: Float = 1f

    /**
     * Is the oxygen level so low the crew will suffocate?
     */
    val isOxygenCritical: Boolean get() = oxygen < Oxygen.OXYGEN_CRITICAL_LEVEL

    // Offset of this room from the ship's 0,0 screen position
    val offsetX get() = ROOM_SIZE * (x + ship.offset.x)
    val offsetY get() = ROOM_SIZE * (y + ship.offset.y)

    val position = ConstPoint(x, y)

    val pixelWidth = width * ROOM_SIZE
    val pixelHeight = height * ROOM_SIZE

    private val reservedPlayerSlots: Array<AbstractCrew?> = Array(width * height) { null }
    private val reservedEnemySlots: Array<AbstractCrew?> = Array(width * height) { null }

    val obstructions = HashSet<ConstPoint>()

    private var computerHackAnimation: Animation? = null
    private var bigSparksHackAnimation: Animation? = null
    private var bigSparksRotation: Float = 0f
    private var bigSparksMaskX: Int = 0

    fun initialise(doors: List<Door>) {
        check(_doors == null) { "Cannot reinitialise room" }

        _doors = doors
    }

    fun update(dt: Float) {
        system?.update(dt)

        // On the observation from the FTL wiki that ships loose ~1% oxygen per second
        val refillRate = (ship.oxygen?.refillRate ?: 0f) - Oxygen.ROOM_DRAIN_RATE
        oxygen = (oxygen + refillRate * dt).coerceAtLeast(0f).coerceAtMost(1f)

        // Note that transferring oxygen through open doors is handled
        // in the Door update function.

        // Update the crew standing in this room
        _crew.clear()
        for (crew in ship.crew) {
            if (crew.room == this)
                _crew.add(crew)
        }

        computerHackAnimation?.update((dt * 1000).toLong())
        bigSparksHackAnimation?.update((dt * 1000).toLong())
    }

    fun render(g: Graphics, selected: Boolean) {
        val x = offsetX
        val y = offsetY

        g.color = FLOOR_COLOUR_NO_OXYGEN.lerp(FLOOR_COLOUR, oxygen)
        g.fillRect(
            x.f,
            y.f,
            pixelWidth.f,
            pixelHeight.f
        )

        if (isOxygenCritical) {
            val img = ship.sys.getImg("img/effects/low_o2_stripes_${width}x${height}.png")
            img.alpha = 0.5f
            img.draw(x.f, y.f)
        }

        g.color = FLOOR_GRID_COLOUR
        for (i in 1 until width) {
            g.drawLine(
                (x + i * ROOM_SIZE - 1).f,
                y.f,
                (x + i * ROOM_SIZE - 1).f,
                (y + pixelHeight - 1).f
            )
        }

        for (i in 1 until height) {
            g.drawLine(
                x.f,
                (y + ROOM_SIZE * i - 1).f,
                (x + pixelWidth - 1).f,
                (y + ROOM_SIZE * i - 1).f
            )
        }

        renderSystemStuff(g)

        // Draw the pathing-to boxes, if required
        reservedPlayerSlots.forEachIndexed draw@{ i, crew ->
            if (crew == null)
                return@draw

            val slot = slotToPoint(i)

            // If the crewmember is in their assigned position, don't draw the box
            if (crew.standingPosition?.posEq(slot) == true && crew.room == this)
                return@draw

            val point = Point(slot)
            point *= ROOM_SIZE
            point.x += offsetX
            point.y += offsetY

            ship.sys.getImg("img/people/green_destination.png").draw(point.x.f, point.y.f)
        }

        // Draw the highlight on the room if it's being selected for weapon targeting.
        if (selected) {
            for (i in 2..5) {
                g.color = when {
                    i < 5 -> ROOM_BORDER_COLOUR_SELECTED
                    else -> ROOM_BORDER_COLOUR_SELECTED_INNER
                }

                g.drawRect(
                    (x + i).f, (y + i).f,
                    (pixelWidth - 1 - i * 2).f,
                    (pixelHeight - 1 - i * 2).f
                )
            }
        }

        // Draw the walls, leaving gaps for the doors.
        // Note that when drawing right-hand or bottom
        // walls, we need to subtract two from the x/y
        // in order to keep the lines internal to the room.
        g.color = ROOM_BORDER_COLOUR
        g.lineWidth = 2f
        for (cellX in 0 until width) {
            drawWall(g, x, y, cellX, 0, Direction.UP)
            drawWall(g, x, y - 2, cellX, height - 1, Direction.DOWN)
        }
        for (cellY in 0 until height) {
            drawWall(g, x, y, 0, cellY, Direction.LEFT)
            drawWall(g, x - 2, y, width - 1, cellY, Direction.RIGHT)
        }
        g.lineWidth = 1f
    }

    private fun renderSystemStuff(g: Graphics) {
        val system = system ?: return

        val x = offsetX
        val y = offsetY

        if (system.img != null) {
            // Render the interior decals
            val bg = ship.sys.getImg(system.img)
            g.drawImage(bg, x.f, y.f)
        } else if (computerPoint != null) {
            // AI ships rarely (never?) use proper room textures. For systems like
            // engines and piloting that can be manned, draw a standard computer image
            // on instead.
            val comp = ship.sys.getImg("img/ship/interior/computer1.png")
            val imgX = x.f + computerPoint!!.x * ROOM_SIZE
            val imgY = y.f + computerPoint!!.y * ROOM_SIZE
            g.pushTransform()
            g.rotate(imgX + ROOM_SIZE / 2, imgY + ROOM_SIZE / 2, computerDirection!!.angle.f)
            g.drawImage(comp, imgX, imgY)
            g.popTransform()
        }

        // Draw the system icon
        system.drawRoom(g)

        // Render the hacking sparks - both those on the console (if one is present),
        // and the big ones on the floor while the hacking is active.
        if (computerPoint != null) {
            // If we have a not-disabled hacking system, show the
            // hacking effect on the computer. This runs constantly,
            // unless the hacking system is powered down.
            if (system.hackedBy?.isPoweredUp == true) {
                if (computerHackAnimation == null)
                    computerHackAnimation = ship.sys.animations["hacked_console"].start()

                // This can't be merged with drawing the computer above, as
                // some decals have computers drawn into them.
                val imgX = x.f + computerPoint!!.x * ROOM_SIZE
                val imgY = y.f + computerPoint!!.y * ROOM_SIZE
                g.pushTransform()
                g.rotate(imgX + ROOM_SIZE / 2, imgY + ROOM_SIZE / 2, computerDirection!!.angle.f)
                computerHackAnimation!!.draw(imgX, imgY)
                g.popTransform()
            } else {
                computerHackAnimation = null
            }
        }

        if (system.isHackActive) {
            if (bigSparksHackAnimation == null || bigSparksHackAnimation?.isStopped == true) {
                bigSparksHackAnimation = ship.sys.animations["stun_spark_big"].start().also {
                    it.setLooping(false)
                }

                // See doc/hacking for details about this
                if (width == 1 || height == 1) {
                    bigSparksMaskX = Random.nextInt(ROOM_SIZE)
                    bigSparksRotation = 0f
                } else {
                    bigSparksMaskX = -1
                    bigSparksRotation = PI.toFloat() / 2f * Random.nextInt(4)
                }
            }

            g.pushTransform()
            g.translate(x.f, y.f)
            if (height == 1) {
                // Horizontal room, thus we need to rotate the vertical slicee
                // of the image into place.
                g.rotate(ROOM_SIZE / 2f, ROOM_SIZE / 2f, -90f)
            }
            g.rotate(pixelWidth / 2f, pixelHeight / 2f, Math.toDegrees(bigSparksRotation.toDouble()).toFloat())

            if (bigSparksMaskX == -1) {
                bigSparksHackAnimation!!.draw(0f, 0f)
            } else {
                bigSparksHackAnimation!!.currentFrame.draw(
                    0f, 0f, 35f, 70f,
                    bigSparksMaskX.f, 0f, bigSparksMaskX + 35f, 70f
                )
            }

            g.popTransform()
        } else {
            bigSparksHackAnimation = null
        }
    }

    private fun drawWall(g: Graphics, baseX: Int, baseY: Int, x: Int, y: Int, side: Direction) {
        // When we're drawing horizontal surfaces (the top and bottom
        // of a room) we get a vertical (up/down respectively) direction.
        // Thus we need to flip the isVertical flag.
        val vertical = !side.isVertical

        val hasDoor = doors.any {
            if (it.dirFor(this) != side)
                return@any false

            if (it.left == this && it.leftPos!!.posEq(x, y))
                return@any true

            if (it.right == this && it.rightPos!!.posEq(x, y))
                return@any true

            return@any false
        }

        // The .5 offset is required since we're drawing a two-pixel-thick line
        val inWorldX = baseX + (x + max(0, side.x)) * ROOM_SIZE + 0.5f
        val inWorldY = baseY + (y + max(0, side.y)) * ROOM_SIZE + 0.5f

        // The end position of the line, relative to the start
        var lineX = 0f
        var lineY = 0f

        // Use -2 to avoid overshooting, given we already added
        // a 0.5 offset.
        if (vertical) {
            lineY += ROOM_SIZE - 2
        } else {
            lineX += ROOM_SIZE - 2
        }

        if (hasDoor) {
            val doorEndFraction = 0.2f

            val doorStartX = inWorldX + lineX * doorEndFraction
            val doorStartY = inWorldY + lineY * doorEndFraction

            val doorEndX = inWorldX + lineX * (1 - doorEndFraction)
            val doorEndY = inWorldY + lineY * (1 - doorEndFraction)

            g.drawLine(
                inWorldX, inWorldY,
                doorStartX, doorStartY
            )

            g.drawLine(
                doorEndX, doorEndY,
                inWorldX + lineX, inWorldY + lineY
            )
        } else {
            g.drawLine(
                inWorldX, inWorldY,
                inWorldX + lineX, inWorldY + lineY
            )
        }
    }

    fun setSystem(config: SystemInstallConfiguration) {
        system = config.system
        computerDirection = config.computerDirection
        computerPoint = config.computerPoint

        system?.room = this
        system?.initialise(ship)

        if (system == null)
            check(computerPoint == null)

        if (computerPoint == null)
            check(computerDirection == null)

        ship.updateAvailableSystems()
    }

    // Check if a point (relative to this room's origin) is inside this room
    fun containsRelative(target: IPoint): Boolean {
        if (target.x < 0 || target.y < 0)
            return false

        if (target.x >= width || target.y >= height)
            return false

        return true
    }

    // Check if a point (relative to the ship's origin) is inside this room
    fun containsAbsolute(target: IPoint): Boolean {
        if (target.x < x || target.y < y)
            return false

        if (target.x >= x + width || target.y >= y + height)
            return false

        return true
    }

    /**
     * Check if a point (relative to the ship's origin) is inside this room.
     *
     * Unlike [containsAbsolute], this works in pixels rather than cells.
     */
    fun containsShipSpace(target: IPoint): Boolean {
        if (target.x !in offsetX until offsetX + pixelWidth)
            return false

        return target.y in offsetY until offsetY + pixelHeight
    }


    fun slotToPoint(slot: Int): IPoint {
        if (slot >= width * height)
            throw ArrayIndexOutOfBoundsException("Invalid slot $slot for $width*$height room - range is 0 to ${width * height}")

        return ConstPoint(slot % width, slot / width)
    }

    fun pointToSlot(point: IPoint): Int {
        check(containsRelative(point))

        return point.x + point.y * width
    }

    fun isSlotFree(point: IPoint, type: AbstractCrew.SlotType): Boolean {
        // Skip obstructed cells - eg, healer in the medbay
        if (obstructions.contains(point))
            return false

        val slots = slotsFor(type)
        return slots[pointToSlot(point)] == null
    }

    /**
     * Update all of this rooms crew and intruder slots.
     *
     * If two crew are set to the same location, one of them (the one
     * not put in the slot) will be added to the [conflicts] list.
     */
    fun updateCrewReservedSlots(conflicts: ArrayList<AbstractCrew>) {
        // Clear out all the pathfinding slots, and fill them back in.
        // This is much better than manually updating the slots, since
        // they can't get out-of-sync with the current crew.
        Arrays.fill(reservedPlayerSlots, null)
        Arrays.fill(reservedEnemySlots, null)

        // Update all the crew and intruder slots. Note we can't
        // use ship.friendlyCrew or ship.intruders, as they might
        // not have updated yet (eg four crew teleporting to
        // a two-peron room; we need to update the slots between
        // each crew reserving a slot).
        for (crew in ship.crew) {
            addCrewReservedSlot(conflicts, crew.mode, crew)
        }
    }

    private fun addCrewReservedSlot(
        conflicts: ArrayList<AbstractCrew>,
        type: AbstractCrew.SlotType,
        crew: AbstractCrew
    ) {
        val slots = slotsFor(type)

        // Crew standing in one of the slots
        val crewPos = crew.standingPosition
        if (crewPos != null && crewPos.room == this) {
            val slot = pointToSlot(crewPos)

            if (slots[slot] == null) {
                slots[slot] = crew
            } else {
                conflicts.add(crew)
            }

            // This shouldn't be required, as standingPosition should
            // be null if pathingTarget isn't, but leave it here just in case.
            return
        }

        // Crew walking towards one of the slots
        val target = crew.pathingTarget
        if (target != null && target.room == this) {
            val slot = pointToSlot(target)

            if (slots[slot] == null) {
                slots[slot] = crew
            } else {
                conflicts.add(crew)
            }
        }
    }

    /**
     * Find the first free slot in this room that another crewmember
     * isn't already standing on or pathing to.
     *
     * This defines the order in which crew are placed into a room,
     * and using this ensures crew are always sent to the computer
     * position first.
     */
    fun firstFreeSlot(type: AbstractCrew.SlotType): IPoint? {
        computerPoint?.let { computer ->
            if (isSlotFree(computer, type) && type == AbstractCrew.SlotType.CREW) {
                return computer
            }
        }

        val slots = slotsFor(type)

        for (i in slots.indices) {
            val point = slotToPoint(i)
            if (isSlotFree(point, type))
                return point
        }

        return null
    }

    private fun slotsFor(type: AbstractCrew.SlotType): Array<AbstractCrew?> = when (type) {
        AbstractCrew.SlotType.CREW -> reservedPlayerSlots
        AbstractCrew.SlotType.INTRUDER -> reservedEnemySlots
    }

    /**
     * Information about how a system is installed into this room - this is both
     * the system and the location of its computer.
     */
    data class SystemInstallConfiguration(
        val system: AbstractSystem,
        val computerPoint: IPoint?,
        val computerDirection: Direction?
    )
}
