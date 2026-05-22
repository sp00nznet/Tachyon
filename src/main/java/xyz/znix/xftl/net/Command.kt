package xyz.znix.xftl.net

import xyz.znix.xftl.game.InGameState
import java.nio.ByteBuffer

/**
 * A single player action on the shared co-op ship.
 *
 * Player input is funnelled into [Command] objects rather than mutating the
 * game directly. On the host - and in single-player - a command is applied
 * straight away; on a co-op client it is encoded and sent to the host, which
 * applies it to the authoritative simulation. The result then reaches the
 * client through the next streamed snapshot.
 *
 * Funnelling input this way means the host and client share one code path for
 * acting on the ship, so every action behaves identically wherever it started.
 */
sealed class Command {
    /** Apply this command to the authoritative game state (host side). */
    abstract fun apply(game: InGameState)

    /** Serialise this command, including its type tag, for the network. */
    abstract fun encode(): ByteArray

    /** Toggle the open/closed state of the player ship's door at [doorIndex]. */
    data class ToggleDoor(val doorIndex: Int) : Command() {
        override fun apply(game: InGameState) {
            val doors = game.player.doors
            if (doorIndex in doors.indices) {
                val door = doors[doorIndex]
                door.open = !door.open
            }
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(8).putInt(TYPE_TOGGLE_DOOR).putInt(doorIndex).array()
    }

    companion object {
        private const val TYPE_TOGGLE_DOOR = 0

        /** Rebuild a command from [encode]d bytes, or null if it isn't recognised. */
        @JvmStatic
        fun decode(data: ByteArray): Command? {
            if (data.size < 4) return null
            val buf = ByteBuffer.wrap(data)
            return when (val type = buf.int) {
                TYPE_TOGGLE_DOOR -> ToggleDoor(buf.int)
                else -> {
                    System.err.println("Co-op: ignoring unknown command type $type")
                    null
                }
            }
        }
    }
}
