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

    /**
     * Order the player crew at [crewIndices] to walk to the room with id
     * [roomId]. Crew are referenced by their index in the ship's crew list,
     * which the host and client share.
     */
    data class MoveCrew(val crewIndices: List<Int>, val roomId: Int) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val room = ship.rooms.firstOrNull { it.id == roomId } ?: return
            for (index in crewIndices) {
                val crew = ship.crew.getOrNull(index) ?: continue
                // Only move controllable crew that are aboard the player ship.
                if (crew.playerControllable && crew.room.ship == ship) {
                    crew.setTargetRoom(room)
                }
            }
        }

        override fun encode(): ByteArray {
            val buf = ByteBuffer.allocate(12 + crewIndices.size * 4)
            buf.putInt(TYPE_MOVE_CREW)
            buf.putInt(roomId)
            buf.putInt(crewIndices.size)
            for (index in crewIndices)
                buf.putInt(index)
            return buf.array()
        }
    }

    companion object {
        private const val TYPE_TOGGLE_DOOR = 0
        private const val TYPE_MOVE_CREW = 1

        // A sane upper bound on how many crew one command can move.
        private const val MAX_CREW = 1000

        /** Rebuild a command from [encode]d bytes, or null if it isn't valid. */
        @JvmStatic
        fun decode(data: ByteArray): Command? {
            try {
                if (data.size < 4) return null
                val buf = ByteBuffer.wrap(data)
                return when (val type = buf.int) {
                    TYPE_TOGGLE_DOOR -> ToggleDoor(buf.int)

                    TYPE_MOVE_CREW -> {
                        val roomId = buf.int
                        val count = buf.int
                        if (count < 0 || count > MAX_CREW) return null
                        MoveCrew(MutableList(count) { buf.int }, roomId)
                    }

                    else -> {
                        System.err.println("Co-op: ignoring unknown command type $type")
                        null
                    }
                }
            } catch (ex: Exception) {
                System.err.println("Co-op: failed to decode a command: ${ex.message}")
                return null
            }
        }
    }
}
