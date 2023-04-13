package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.f
import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.ButtonImageSet
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

class Teleporter(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.TELEPORTER

    val isSendAvailable: Boolean
        get() {
            if (isPowerLocked || powerSelected == 0)
                return false

            // There must be an enemy ship
            // TODO support enemy ships using the teleporter
            if (ship.sys.enemy == null)
                return false

            // There must be at least one crew member standing in the room
            // TODO make this work if a crew member is re-targeted while paused
            return room!!.reservedPlayerSlots.filterNotNull().any { it.room == room && it.movement == null }
        }

    val isReceiveAvailable: Boolean
        get() {
            if (isPowerLocked || powerSelected == 0)
                return false

            // There must be an enemy ship
            // TODO support enemy ships using the teleporter
            val enemy = ship.sys.enemy ?: return false

            // There must be at least one crew member in the enemy ship
            require(ship != enemy)
            return enemy.intruders.isNotEmpty()
        }

    private var commandedTeleport: TeleportAction? = null

    override fun update(dt: Float) {
        super.update(dt)

        // If there's a command ready, grab and action it
        val command = commandedTeleport ?: return
        commandedTeleport = null

        val enemyShip = command.room.ship

        if (command.send) {
            if (!isSendAvailable)
                return

            // TODO support crew that are passing through
            val ourCrew = room!!.reservedPlayerSlots.filterNotNull().filter { it.room == room }

            // Don't waste a cooldown if we're not teleporting anyone
            if (ourCrew.isEmpty())
                return

            for (crew in ourCrew) {
                crew.teleportAnimatedTo(command.room)
            }
        } else {
            if (!isReceiveAvailable)
                return

            // TODO support crew that are passing through
            val ourCrew = command.room.reservedEnemySlots.filterNotNull().filter { it.room == command.room }

            // Don't waste a cooldown if we're not teleporting anyone
            if (ourCrew.isEmpty())
                return

            for (crew in ourCrew) {
                crew.teleportAnimatedTo(room!!)
            }
        }

        // Ion-stun for 20s at 1 power, 15s at 2, and 10s at 3.
        ionTimer += 5f * (5 - powerSelected)
    }

    override fun makeExtraButtons(powerPos: IPoint): List<Button> {
        val bottom = ButtonImageSet.select2(ship.sys, "img/systemUI/button_teleport_bottom")
        val top = ButtonImageSet.select2(ship.sys, "img/systemUI/button_teleport_top")

        val buttonBase = powerPos + ConstPoint(22, -49)
        return listOf(
            // The background comes first so it doesn't draw on top of the others
            TeleporterButtonBackground(buttonBase),

            TeleporterButton(buttonBase, ConstPoint(4, 4), true, top),
            TeleporterButton(buttonBase, ConstPoint(4, 27), false, bottom)
        )
    }

    fun selectTeleportAction(send: Boolean, room: Room) {
        commandedTeleport = TeleportAction(room, send)
    }

    private inner class TeleporterButton(
        val base: IPoint,
        offset: IPoint,
        val isSend: Boolean,
        val images: ButtonImageSet
    ) :
        Button(base + offset, ConstPoint(20, 20)) {

        override fun draw(g: Graphics) {
            val img = when {
                isSend && !isSendAvailable -> images.off
                !isSend && !isReceiveAvailable -> images.off
                hovered -> images.hover

                // If we're selecting, or have selected, a teleport of this type,
                // then keep the button highlighted.
                ship.sys.shipUI.teleportMode == isSend -> images.hover
                commandedTeleport?.send == isSend -> images.hover

                else -> images.normal
            }
            img.draw(base.x.f - BASE_GLOW, base.y.f - BASE_GLOW)
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            // If there's already an order queued up, clear it out.
            commandedTeleport = null

            ship.sys.shipUI.teleportSelected(isSend)
        }
    }

    private inner class TeleporterButtonBackground(pos: IPoint) : Button(pos, ConstPoint.ZERO) {
        override fun draw(g: Graphics) {
            val img = ship.sys.getImg("img/systemUI/button_teleport_base.png")
            img.draw(pos.x.f - BASE_GLOW, pos.y.f - BASE_GLOW)
        }

        override fun click(button: Int) = Unit
    }

    private class TeleportAction(val room: Room, val send: Boolean)

    companion object {
        private const val BASE_GLOW: Int = 6
    }
}
