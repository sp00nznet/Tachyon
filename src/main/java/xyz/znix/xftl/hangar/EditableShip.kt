package xyz.znix.xftl.hangar

import xyz.znix.xftl.Constants
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.ISystemConfiguration
import xyz.znix.xftl.f
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.systems.Weapons
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint

/**
 * A ship that can be rendered in the hangar.
 *
 * This is kept separate from [xyz.znix.xftl.Ship], since what's desirable
 * in-game (room locations etc being immutable) isn't suitable for ship that
 * can be edited.
 *
 * Note this doesn't reference any blueprints or images, so it's safe to use
 * after changing mods or otherwise re-creating the blueprint manager.
 */
class EditableShip(
    val baseBlueprint: String // ShipBlueprint name
) {
    val rooms = ArrayList<EditableRoom>()
    val doors = ArrayList<EditableDoor>()

    val weapons = ArrayList<String>() // AbstractWeaponBlueprint names
    val drones = ArrayList<String>() // DroneBlueprint names

    var weaponSlots: Int = 4
    var droneSlots: Int = 2

    fun draw(g: Graphics, state: SelectShipState, renderAll: Boolean) {
        val blueprint = state.blueprints[baseBlueprint] as ShipBlueprint

        val hullImage = state.getImg(blueprint.hullImage)
        val floorImage = blueprint.floorImage?.let { state.getImg(it) }
        val hullOffset = blueprint.hullOffset
        val floorOffset = blueprint.floorOffset

        // Draw the weapons behind the hull images, so it covers up the edge of the texture.
        drawWeapons(g, state, blueprint)

        hullImage.draw(hullOffset.x, hullOffset.y)
        floorImage?.draw(floorOffset.x + hullOffset.x, floorOffset.y + hullOffset.y)

        // Draw the rooms
        for (room in rooms) {
            drawRoom(g, state, room, renderAll)
        }

        // Draw the doors
        val doorImage = state.getImg("img/effects/door_sheet.png")
        if (renderAll) {
            for (door in doors) {
                door.draw(g, doorImage)
            }
        }
    }

    private fun drawWeapons(g: Graphics, state: SelectShipState, shipBlueprint: ShipBlueprint) {
        for ((index, weapon) in weapons.withIndex()) {
            val weaponBp = state.blueprints[weapon] as AbstractWeaponBlueprint
            val hp = shipBlueprint.hardpoints[index]

            val anim = state.animations.weaponAnimations.getValue(weaponBp.launcher)
            val spriteSheet = state.getImg(anim.sheet.sheetPath)
            val launcher = anim.spriteAt(spriteSheet, anim.chargedFrame)

            g.pushTransform()
            g.translate(-shipBlueprint.roomOffset.x.f * ROOM_SIZE, -shipBlueprint.roomOffset.y.f * ROOM_SIZE)
            Weapons.translateHardpoint(g, hp)
            g.translate(-anim.mountPoint.x.f, -anim.mountPoint.y.f)
            launcher.draw(0f, 0f)
            g.popTransform()
        }
    }

    private fun drawRoom(g: Graphics, state: SelectShipState, room: EditableRoom, drawSystems: Boolean) {
        val x = room.pixelX
        val y = room.pixelY

        g.color = Constants.ROOM_BORDER_COLOUR
        g.fillRect(
            x.f,
            y.f,
            room.pixelWidth.f,
            room.pixelHeight.f
        )

        val wallThickness = 2
        g.color = Constants.FLOOR_COLOUR
        g.fillRect(
            x + wallThickness.f,
            y + wallThickness.f,
            room.pixelWidth - 2f * wallThickness,
            room.pixelHeight - 2f * wallThickness
        )

        // Draw the floor grid
        g.color = Constants.FLOOR_GRID_COLOUR
        for (i in 1 until room.w) {
            val lineX = x + i * ROOM_SIZE - 1
            g.drawLine(
                lineX.f,
                y + wallThickness.f,
                lineX.f,
                y + room.pixelHeight - wallThickness - 1f
            )
        }

        for (i in 1 until room.h) {
            val lineY = y + ROOM_SIZE * i - 1
            g.drawLine(
                x + wallThickness.f,
                lineY.f,
                (x + room.pixelWidth - 1) - wallThickness - 1f,
                lineY.f
            )
        }

        // Draw the system icon.
        // Note that when using the editor, it draws the system icons instead.
        val system = room.system
        if (system != null && drawSystems) {
            val icon = state.getImg(system.getBP(state).roomIconPath)
            icon.draw(
                x + (room.pixelWidth - icon.width) / 2,
                y + (room.pixelHeight - icon.height) / 2,
                Constants.SYSTEM_NORMAL
            )
        }
    }

    companion object {
        fun fromBlueprint(blueprint: ShipBlueprint): EditableShip {
            val ship = EditableShip(blueprint.name)

            val rooms = blueprint.rooms.map { EditableRoom(it.pos.x, it.pos.y, it.size.x, it.size.y) }
            ship.rooms.addAll(rooms)

            val doors = blueprint.doors.map { EditableDoor(it.pos.x, it.pos.y, it.isVertical) }
            ship.doors.addAll(doors)

            for (system in blueprint.systems) {
                val room = ship.rooms[system.room.id]
                val type = system.systemName
                room.system = EditableSystem(type)

                // If this is an artillery system, set its weapon.
                if (system.weapon != null) {
                    val weapon = system.weapon
                    room.system!!.artilleryWeapon = weapon
                }
            }

            ship.weapons += blueprint.initialWeapons
            ship.drones += blueprint.initialDrones

            blueprint.weaponSlots?.let { ship.weaponSlots = it }
            blueprint.droneSlots?.let { ship.droneSlots = it }

            return ship
        }
    }
}

class EditableRoom(
    // Position
    var x: Int = 0,
    var y: Int = 0,

    // Width/height
    var w: Int = 2,
    var h: Int = 2
) {
    var system: EditableSystem? = null

    val pixelX: Int get() = x * ROOM_SIZE
    val pixelY: Int get() = y * ROOM_SIZE

    val pixelWidth: Int get() = w * ROOM_SIZE
    val pixelHeight: Int get() = h * ROOM_SIZE

    val pixelRight get() = pixelX + pixelWidth
    val pixelBottom get() = pixelY + pixelHeight

    fun containsPixel(px: Int, py: Int): Boolean {
        return px in pixelX..pixelRight && py in pixelY..pixelBottom
    }
}

class EditableDoor(
    var x: Int,
    var y: Int,
    var isVertical: Boolean
) {
    /**
     * The offset from the grid position to the centre of the door image, in the X axis.
     */
    val centreOffsetX: Int get() = if (isVertical) 0 else ROOM_SIZE / 2

    /**
     * The offset from the grid position to the centre of the door image, in the Y axis.
     */
    val centreOffsetY: Int get() = if (isVertical) ROOM_SIZE / 2 else 0

    fun draw(g: Graphics, doorsImage: Image) {
        draw(
            g, doorsImage,
            x * ROOM_SIZE + centreOffsetX,
            y * ROOM_SIZE + centreOffsetY,
            isVertical, null
        )
    }

    companion object {
        fun draw(g: Graphics, doorsImage: Image, centreX: Int, centreY: Int, isVertical: Boolean, highlight: Image?) {
            // Broken and level 1 doors use the same sprite, but broken doors
            // have a colour filter applied to them.
            val sheetY = ROOM_SIZE * 0.coerceAtLeast(0)

            // This is used to animate the door opening and closing, selecting the
            // correct frame for its motion.
            val sheetX = 0

            g.pushTransform()

            // Make the centre of the door sprite the origin
            g.translate(centreX.f, centreY.f)

            if (!isVertical) {
                g.rotate(0f, 0f, 90f)
            }

            highlight?.draw(-ROOM_SIZE / 2, -ROOM_SIZE / 2)

            doorsImage.drawSection(
                -ROOM_SIZE / 2, -ROOM_SIZE / 2, ROOM_SIZE, ROOM_SIZE,
                sheetX, sheetY
            )

            g.popTransform()
        }

        fun findNeighbourRoom(ship: EditableShip, door: EditableDoor, exclude: EditableRoom?): EditableRoom? {
            return findNeighbourRoom(ship, door.x, door.y, door.isVertical, exclude)
        }

        fun findNeighbourRoom(
            ship: EditableShip,
            cellX: Int, cellY: Int,
            vertical: Boolean,
            exclude: EditableRoom?
        ): EditableRoom? {
            for (room in ship.rooms) {
                if (room == exclude)
                    continue

                // Filter out rooms that we aren't in.
                if (cellX !in room.x..room.x + room.w)
                    continue
                if (cellY !in room.y..room.y + room.h)
                    continue

                // Check we're on a suitable edge.
                if (vertical) {
                    if (cellY == room.y + room.h)
                        continue
                    if (cellX == room.x || cellX == room.x + room.w)
                        return room
                } else {
                    if (cellX == room.x + room.w)
                        continue
                    if (cellY == room.y || cellY == room.y + room.h)
                        return room
                }
            }

            return null
        }
    }
}

data class EditableSystem(
    val type: String, // SystemBlueprint name
    var artilleryWeapon: String? = null // AbstractWeaponBlueprint name
) {
    fun getBP(state: SelectShipState): SystemBlueprint = state.blueprints[type] as SystemBlueprint
}

/**
 * An instance of [EditableSystem] that's been frozen in a ship,
 * with a fixed system index, and anything else that's only known
 * when the ship layout will no longer change.
 */
class FinalisedEditableSystem(
    val editableSystem: EditableSystem,
    override val systemIndex: Int,
    game: InGameState
) : ISystemConfiguration {
    private val system = game.blueprintManager[editableSystem.type] as SystemBlueprint

    override val systemName: String get() = system.type

    override val aiMaxPower: Int? get() = null
    override val weapon: String? get() = editableSystem.artilleryWeapon

    // TODO implement the computer
    override val slotNumber: Int? get() = null
    override val slotDirection: Direction? get() = null

    override val startingPower: Int get() = system.startPower // TODO make this adjustable
    override val availableByDefault: Boolean get() = true // TODO don't spawn all systems by default
    override val interiorImage: String? get() = null // TODO implement interior images
}
