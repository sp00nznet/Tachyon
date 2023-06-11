package xyz.znix.xftl.hangar

import xyz.znix.xftl.Constants
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.draw
import xyz.znix.xftl.f
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.systems.SystemBlueprint

/**
 * A ship that can be rendered in the hangar.
 *
 * This is kept separate from [xyz.znix.xftl.Ship], since what's desirable
 * in-game (room locations etc being immutable) isn't suitable for ship that
 * can be edited.
 */
class EditableShip(val state: SelectShipState) {
    var hullImage: Image? = null
    var floorImage: Image? = null

    var hullOffset: IPoint = ConstPoint.ZERO
    var floorOffset: IPoint = ConstPoint.ZERO

    val rooms = ArrayList<EditableRoom>()

    fun draw(g: Graphics, drawSystems: Boolean) {
        hullImage?.draw(hullOffset.x, hullOffset.y)
        floorImage?.draw(floorOffset.x + hullOffset.x, floorOffset.y + hullOffset.y)

        // Draw the rooms
        for (room in rooms) {
            drawRoom(g, room, drawSystems)
        }

        // Draw the doors
    }

    private fun drawRoom(g: Graphics, room: EditableRoom, drawSystems: Boolean) {
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
            val icon = state.getImg(system.roomIconPath)
            icon.draw(
                x + (room.pixelWidth - icon.width) / 2,
                y + (room.pixelHeight - icon.height) / 2,
                Constants.SYSTEM_NORMAL
            )
        }
    }

    companion object {
        fun fromBlueprint(state: SelectShipState, blueprint: ShipBlueprint): EditableShip {
            val ship = EditableShip(state)

            ship.hullImage = state.getImg(blueprint.hullImage)
            ship.floorImage = blueprint.floorImage?.let { state.getImg(it) }
            ship.hullOffset = blueprint.hullOffset
            ship.floorOffset = blueprint.floorOffset

            val rooms = blueprint.rooms.map { EditableRoom(it.pos.x, it.pos.y, it.size.x, it.size.y) }
            ship.rooms.addAll(rooms)

            for (system in blueprint.systems) {
                val room = ship.rooms[system.room.id]
                room.system = state.blueprints[system.systemName] as SystemBlueprint
            }

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
    var system: SystemBlueprint? = null

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
