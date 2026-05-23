package xyz.znix.xftl.net

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.Drones
import xyz.znix.xftl.systems.SelectedTarget
import xyz.znix.xftl.systems.SubSystem
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

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

    /**
     * Step the reactor power of the player ship's main system at
     * [systemIndex] up (if [increase]) or down by one bar.
     */
    data class SetSystemPower(val systemIndex: Int, val increase: Boolean) : Command() {
        override fun apply(game: InGameState) {
            val system = game.player.mainSystems.getOrNull(systemIndex) ?: return
            if (increase) {
                system.increasePower()
            } else {
                system.decreasePower()
            }
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(12)
                .putInt(TYPE_SET_SYSTEM_POWER)
                .putInt(systemIndex)
                .putInt(if (increase) 1 else 0)
                .array()
    }

    /** Arm or disarm the weapon at hardpoint [hardpointIndex]. */
    data class SetWeaponArmed(val hardpointIndex: Int, val armed: Boolean) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val weapon = ship.hardpoints.getOrNull(hardpointIndex)?.weapon ?: return
            ship.weapons?.setWeaponPower(weapon, armed)
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(12)
                .putInt(TYPE_SET_WEAPON_ARMED)
                .putInt(hardpointIndex)
                .putInt(if (armed) 1 else 0)
                .array()
    }

    /**
     * Aim the room-targeting weapon at hardpoint [hardpointIndex] at the room
     * with id [roomId], on the enemy ship if [targetEnemy] is set, otherwise
     * on the player's own ship.
     */
    data class TargetWeapon(
        val hardpointIndex: Int,
        val targetEnemy: Boolean,
        val roomId: Int,
    ) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val weapons = ship.weapons ?: return
            val weapon = ship.hardpoints.getOrNull(hardpointIndex)?.weapon ?: return
            if (weapon !is IRoomTargetingWeapon) return
            val targetShip = if (targetEnemy) game.enemy else ship
            val room = targetShip?.rooms?.firstOrNull { it.id == roomId } ?: return
            weapons.selectedTargets.targetRoom(hardpointIndex, room)
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(16)
                .putInt(TYPE_TARGET_WEAPON)
                .putInt(hardpointIndex)
                .putInt(if (targetEnemy) 1 else 0)
                .putInt(roomId)
                .array()
    }

    /**
     * Aim the beam weapon at hardpoint [hardpointIndex] as a swipe starting
     * at ([startX], [startY]) in the target ship's space, at [angle] radians.
     */
    data class TargetBeam(
        val hardpointIndex: Int,
        val targetEnemy: Boolean,
        val startX: Int,
        val startY: Int,
        val angle: Float,
    ) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val weapons = ship.weapons ?: return
            val weapon = ship.hardpoints.getOrNull(hardpointIndex)?.weapon
            if (weapon !is BeamBlueprint.BeamInstance) return
            val targetShip = (if (targetEnemy) game.enemy else ship) ?: return
            val beam = SelectedTarget.BeamAim(
                weapon, hardpointIndex, targetShip, ConstPoint(startX, startY)
            )
            beam.angle = angle
            beam.updateHitRooms()
            weapons.selectedTargets.targetBeam(hardpointIndex, beam)
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(24)
                .putInt(TYPE_TARGET_BEAM)
                .putInt(hardpointIndex)
                .putInt(if (targetEnemy) 1 else 0)
                .putInt(startX)
                .putInt(startY)
                .putFloat(angle)
                .array()
    }

    /** Toggle the game-paused flag - either player can pause or unpause. */
    object TogglePause : Command() {
        override fun apply(game: InGameState) {
            game.togglePaused()
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(4).putInt(TYPE_TOGGLE_PAUSE).array()
    }

    /**
     * Jump to the next sector at grid position ([column], [row]) on the
     * galaxy map. The host generates the new sector and warps in.
     */
    data class JumpToSector(val column: Int, val row: Int) : Command() {
        override fun apply(game: InGameState) {
            val sectorCol = game.gameMap.sectors.getOrNull(column) ?: return
            val info = sectorCol.getOrNull(row) ?: return
            // Only one of the reachable next sectors is valid.
            if (!game.currentBeacon.sector.info.nextSectors.contains(info)) return
            val newSector = game.gameMap.generateSector(info, game)
            game.currentBeacon = newSector.startBeacon
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(12).putInt(TYPE_JUMP_TO_SECTOR)
                .putInt(column).putInt(row).array()
    }

    /**
     * Spend scrap to upgrade the player ship's main system or subsystem at
     * [systemIndex] (in [Ship.mainSystems] or the sorted subsystem list).
     */
    data class UpgradeSystem(val systemIndex: Int, val isSubsystem: Boolean) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val system = if (isSubsystem) {
                ship.rooms.mapNotNull { it.system as? SubSystem }
                    .sortedBy { it.sortingType }.getOrNull(systemIndex)
            } else {
                ship.mainSystems.getOrNull(systemIndex)
            } ?: return

            if (system.energyLevels >= system.blueprint.maxPower) return
            val price = system.blueprint.upgradeCost[system.energyLevels - 1]
            if (ship.scrap < price) return

            ship.scrap -= price
            system.energyLevels++
            // Remember the price so either player can undo this upgrade.
            game.pushSystemUpgrade(systemIndex, isSubsystem, price)
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(12).putInt(TYPE_UPGRADE_SYSTEM)
                .putInt(systemIndex).putInt(if (isSubsystem) 1 else 0).array()
    }

    /** Spend scrap to buy one more bar of reactor power. */
    object UpgradeReactor : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            if (ship.purchasedReactorPower >= ship.maxReactorPower) return
            val price = if (ship.purchasedReactorPower < 5) 30
                        else (ship.purchasedReactorPower / 5) * 5 + 15
            if (ship.scrap < price) return
            ship.scrap -= price
            ship.purchasedReactorPower++
            game.pushReactorUpgrade(price)
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(4).putInt(TYPE_UPGRADE_REACTOR).array()
    }

    /**
     * Undo the most recent upgrade for the player ship's [systemIndex]
     * (main or sub), refunding the scrap.
     */
    data class UndoSystemUpgrade(val systemIndex: Int, val isSubsystem: Boolean) : Command() {
        override fun apply(game: InGameState) {
            val refund = game.popSystemUpgrade(systemIndex, isSubsystem)
            if (refund == 0) return
            val ship = game.player
            val system = if (isSubsystem) {
                ship.rooms.mapNotNull { it.system as? SubSystem }
                    .sortedBy { it.sortingType }.getOrNull(systemIndex)
            } else {
                ship.mainSystems.getOrNull(systemIndex)
            }
            if (system == null || system.energyLevels <= 1) {
                // Nothing to revert - restore the price so we don't lose track.
                game.pushSystemUpgrade(systemIndex, isSubsystem, refund)
                return
            }
            system.energyLevels--
            ship.scrap += refund
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(12)
                .putInt(TYPE_UNDO_SYSTEM_UPGRADE)
                .putInt(systemIndex)
                .putInt(if (isSubsystem) 1 else 0)
                .array()
    }

    /** Undo the most recent reactor upgrade, refunding the scrap. */
    object UndoReactorUpgrade : Command() {
        override fun apply(game: InGameState) {
            val refund = game.popReactorUpgrade()
            if (refund == 0) return
            val ship = game.player
            if (ship.purchasedReactorPower <= 0) {
                game.pushReactorUpgrade(refund)
                return
            }
            ship.purchasedReactorPower--
            ship.scrap += refund
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(4).putInt(TYPE_UNDO_REACTOR_UPGRADE).array()
    }

    /**
     * Swap or drag-drop a piece of equipment between two slots on the player
     * ship. [srcKind]/[dstKind] are EQUIP_KIND_* constants. Dropping onto the
     * sell slot ([EQUIP_KIND_SELL]) destroys the source for half its scrap
     * cost.
     */
    data class SwapEquipment(
        val srcKind: Int, val srcIndex: Int,
        val dstKind: Int, val dstIndex: Int,
    ) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val src = equipmentSlot(ship, srcKind, srcIndex) ?: return
            val dst = equipmentSlot(ship, dstKind, dstIndex) ?: return

            val srcVal = src.get() ?: return
            if (!dst.accepts(srcVal)) return
            val dstVal = dst.get()
            if (dstVal != null && !src.accepts(dstVal)) return

            dst.set(srcVal)
            src.set(dstVal)
            ship.cargoUpdated()
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(20).putInt(TYPE_SWAP_EQUIPMENT)
                .putInt(srcKind).putInt(srcIndex)
                .putInt(dstKind).putInt(dstIndex)
                .array()
    }

    /** Dismiss the crew member at [crewIndex] from the player ship. */
    data class DismissCrew(val crewIndex: Int) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val crew = ship.crew.getOrNull(crewIndex) as? LivingCrew ?: return
            ship.clonebay?.queue?.remove(crew)
            crew.removeFromShip()
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(8).putInt(TYPE_DISMISS_CREW).putInt(crewIndex).array()
    }

    /** Rename the crew member at [crewIndex] to [name]. */
    data class RenameCrew(val crewIndex: Int, val name: String) : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            val crew = ship.crew.getOrNull(crewIndex) as? LivingCrew ?: return
            // The game's text-input handler caps the length too; clamp here in
            // case a client sends something longer.
            crew.info.name = name.take(MAX_CREW_NAME)
        }

        override fun encode(): ByteArray {
            val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
            val buf = ByteBuffer.allocate(12 + nameBytes.size)
            buf.putInt(TYPE_RENAME_CREW)
            buf.putInt(crewIndex)
            buf.putInt(nameBytes.size)
            buf.put(nameBytes)
            return buf.array()
        }
    }

    /** Undo every pending upgrade in the current session. */
    object UndoAllUpgrades : Command() {
        override fun apply(game: InGameState) {
            val ship = game.player
            // Main systems.
            for ((idx, prices) in game.mainSystemUpgradeHistory) {
                val system = ship.mainSystems.getOrNull(idx) ?: continue
                while (prices.isNotEmpty() && system.energyLevels > 1) {
                    system.energyLevels--
                    ship.scrap += prices.removeAt(prices.size - 1)
                }
            }
            // Subsystems.
            val subs = ship.rooms.mapNotNull { it.system as? SubSystem }.sortedBy { it.sortingType }
            for ((idx, prices) in game.subSystemUpgradeHistory) {
                val system = subs.getOrNull(idx) ?: continue
                while (prices.isNotEmpty() && system.energyLevels > 1) {
                    system.energyLevels--
                    ship.scrap += prices.removeAt(prices.size - 1)
                }
            }
            // Reactor.
            val reactor = game.reactorUpgradeHistory
            while (reactor.isNotEmpty() && ship.purchasedReactorPower > 0) {
                ship.purchasedReactorPower--
                ship.scrap += reactor.removeAt(reactor.size - 1)
            }
            game.clearUpgradeHistory()
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(4).putInt(TYPE_UNDO_ALL_UPGRADES).array()
    }

    /** Jump the ship to the beacon at [beaconIndex] within the current sector. */
    data class JumpToBeacon(val beaconIndex: Int) : Command() {
        override fun apply(game: InGameState) {
            val beacons = game.currentBeacon.sector.beacons
            val beacon = beacons.getOrNull(beaconIndex) ?: return
            // Only a neighbouring beacon is reachable, unless any-jump is on.
            if (!game.currentBeacon.neighbours.contains(beacon) && !game.debugFlags.anyJump.set)
                return

            // Jumping needs fuel.
            if (game.player.fuelCount <= 0)
                return

            // Close the jump map first, so the new beacon's event dialogue
            // (opened by setCurrentBeacon) is not immediately closed again.
            game.shipUI?.closeJumpWindow()
            game.player.fuelCount--
            game.advanceFleet()
            game.currentBeacon = beacon
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(8).putInt(TYPE_JUMP_TO_BEACON).putInt(beaconIndex).array()
    }

    /** Choose option [optionIndex] in the currently-open event dialogue. */
    data class SelectDialogueOption(val optionIndex: Int) : Command() {
        override fun apply(game: InGameState) {
            game.shipUI?.applyDialogueOption(optionIndex)
        }

        override fun encode(): ByteArray =
            ByteBuffer.allocate(8).putInt(TYPE_SELECT_DIALOGUE).putInt(optionIndex).array()
    }

    companion object {
        private const val TYPE_TOGGLE_DOOR = 0
        private const val TYPE_MOVE_CREW = 1
        private const val TYPE_SET_SYSTEM_POWER = 2
        private const val TYPE_SET_WEAPON_ARMED = 3
        private const val TYPE_TARGET_WEAPON = 4
        private const val TYPE_TARGET_BEAM = 5
        private const val TYPE_SELECT_DIALOGUE = 6
        private const val TYPE_JUMP_TO_BEACON = 7
        private const val TYPE_TOGGLE_PAUSE = 8
        private const val TYPE_JUMP_TO_SECTOR = 9
        private const val TYPE_UPGRADE_SYSTEM = 10
        private const val TYPE_UPGRADE_REACTOR = 11
        private const val TYPE_UNDO_SYSTEM_UPGRADE = 12
        private const val TYPE_UNDO_REACTOR_UPGRADE = 13
        private const val TYPE_UNDO_ALL_UPGRADES = 14
        private const val TYPE_DISMISS_CREW = 15
        private const val TYPE_RENAME_CREW = 16
        private const val TYPE_SWAP_EQUIPMENT = 17

        // Equipment slot kinds, used by SwapEquipment.
        const val EQUIP_KIND_WEAPON = 0
        const val EQUIP_KIND_DRONE = 1
        const val EQUIP_KIND_AUGMENT = 2
        const val EQUIP_KIND_CARGO = 3
        const val EQUIP_KIND_SELL = 4

        private const val MAX_CREW_NAME = 15
        private const val MAX_NAME_BYTES = 64

        /**
         * Build a Get/Set accessor for the equipment slot identified by
         * [kind] and [idx] on [ship]. Mirrors the dropBlueprint logic in
         * ShipEquipmentPanel so SwapEquipment can apply on the host without
         * needing the UI.
         */
        private fun equipmentSlot(ship: Ship, kind: Int, idx: Int): EquipmentSlot? = when (kind) {
            EQUIP_KIND_WEAPON -> {
                val hp = ship.hardpoints.getOrNull(idx)
                if (hp == null) null else object : EquipmentSlot {
                    override fun get(): Blueprint? = hp.weapon?.type
                    override fun set(value: Blueprint?) {
                        hp.weapon = (value as? AbstractWeaponBlueprint)?.buildInstance(ship)
                    }
                    override fun accepts(value: Blueprint): Boolean = value is AbstractWeaponBlueprint
                }
            }
            EQUIP_KIND_DRONE -> {
                val drones = ship.drones
                if (drones == null || idx !in drones.drones.indices) null
                else object : EquipmentSlot {
                    override fun get(): Blueprint? = drones.drones[idx]?.type
                    override fun set(value: Blueprint?) {
                        drones.drones[idx]?.instance?.removeInstance()
                        drones.drones[idx] = (value as? DroneBlueprint)?.let { Drones.DroneInfo(it, null) }
                    }
                    override fun accepts(value: Blueprint): Boolean = value is DroneBlueprint
                }
            }
            EQUIP_KIND_AUGMENT -> object : EquipmentSlot {
                override fun get(): Blueprint? = ship.augments.getOrNull(idx)
                override fun set(value: Blueprint?) {
                    val current = ship.augments.getOrNull(idx)
                    if (current != null) ship.augments.remove(current)
                    if (value is AugmentBlueprint) {
                        val insertAt = min(ship.augments.size, idx)
                        ship.augments.add(insertAt, value)
                    }
                }
                override fun accepts(value: Blueprint): Boolean = value is AugmentBlueprint
            }
            EQUIP_KIND_CARGO -> {
                if (idx !in ship.cargoBlueprints.indices) null
                else object : EquipmentSlot {
                    override fun get(): Blueprint? = ship.cargoBlueprints[idx]
                    override fun set(value: Blueprint?) { ship.cargoBlueprints[idx] = value }
                    override fun accepts(value: Blueprint): Boolean = true
                }
            }
            EQUIP_KIND_SELL -> object : EquipmentSlot {
                override fun get(): Blueprint? = null
                override fun set(value: Blueprint?) {
                    // Refund half the cost; the source slot is cleared by the
                    // swap that put us here.
                    ship.scrap += (value?.cost ?: 0) / 2
                }
                override fun accepts(value: Blueprint): Boolean = true
            }
            else -> null
        }

        private interface EquipmentSlot {
            fun get(): Blueprint?
            fun set(value: Blueprint?)
            fun accepts(value: Blueprint): Boolean
        }

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

                    TYPE_SET_SYSTEM_POWER -> SetSystemPower(buf.int, buf.int != 0)

                    TYPE_SET_WEAPON_ARMED -> SetWeaponArmed(buf.int, buf.int != 0)

                    TYPE_TARGET_WEAPON -> TargetWeapon(buf.int, buf.int != 0, buf.int)

                    TYPE_TARGET_BEAM ->
                        TargetBeam(buf.int, buf.int != 0, buf.int, buf.int, buf.float)

                    TYPE_SELECT_DIALOGUE -> SelectDialogueOption(buf.int)

                    TYPE_JUMP_TO_BEACON -> JumpToBeacon(buf.int)

                    TYPE_TOGGLE_PAUSE -> TogglePause

                    TYPE_JUMP_TO_SECTOR -> JumpToSector(buf.int, buf.int)

                    TYPE_UPGRADE_SYSTEM -> UpgradeSystem(buf.int, buf.int != 0)

                    TYPE_UPGRADE_REACTOR -> UpgradeReactor

                    TYPE_UNDO_SYSTEM_UPGRADE -> UndoSystemUpgrade(buf.int, buf.int != 0)

                    TYPE_UNDO_REACTOR_UPGRADE -> UndoReactorUpgrade

                    TYPE_UNDO_ALL_UPGRADES -> UndoAllUpgrades

                    TYPE_DISMISS_CREW -> DismissCrew(buf.int)

                    TYPE_RENAME_CREW -> {
                        val idx = buf.int
                        val len = buf.int
                        if (len < 0 || len > MAX_NAME_BYTES) return null
                        val nameBytes = ByteArray(len)
                        buf.get(nameBytes)
                        RenameCrew(idx, String(nameBytes, StandardCharsets.UTF_8))
                    }

                    TYPE_SWAP_EQUIPMENT ->
                        SwapEquipment(buf.int, buf.int, buf.int, buf.int)

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
