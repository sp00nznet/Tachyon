package xyz.znix.xftl.ai

import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.IRoomTargetingWeapon

/**
 * The overall enemy ship AI.
 *
 * This controls stuff like firing the weapons, activating systems/drones,
 * and other stuff like that.
 *
 * It specifically does not handle crew - see [FriendlyCrewAI] for that.
 */
class ShipAI(val ship: Ship, val player: Ship) {
    private val fireAtChargeLevel = HashMap<AbstractWeaponInstance, Int>()

    fun update(dt: Float) {
        // If the ship is dying, don't do anything.
        if (ship.isDead)
            return

        // Power up all the systems
        for (sys in ship.mainSystems) {
            for (i in 1..sys.energyLevels) sys.increasePower()
        }

        updateWeapons()

        // Constantly try to trigger cloaking, it'll be ignored
        // whenever it's unavailable.
        ship.cloaking?.activateCloak()

        updateHacking()

        // Check if we need to escape or surrender
        if (ship.health <= ship.surrenderHealth) {
            // Don't surrender repeatedly
            ship.surrenderHealth = 0

            // The surrender event must exist, it's XML tag did
            // for surrenderHealth to be set.
            ship.sys.shipUI.showEventDialogue(ship.spec!!.surrender!!.resolve())

            // Use an else-if so we don't try and surrender and escape in
            // the same update cycle.
        } else if (ship.health <= ship.escapeHealth) {
            // Show the event and start the timer running
            ship.escapeHealth = 0
            ship.sys.shipUI.showEventDialogue(ship.spec!!.escape!!.resolve())
            ship.escapeTimer = ship.spec.escapeTimer
        }

        val escapeTimer = ship.escapeTimer
        if (escapeTimer != null) {
            if (ship.canChargeFTL) {
                ship.escapeTimer = escapeTimer - dt
            }

            // SlickGame will check the escape timer, and remove
            // the ship if it runs out.
        }
    }

    private fun updateWeapons() {
        val weapons = ship.weapons ?: return

        // Does nothing if already fully powered
        weapons.increasePower()

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            if (!weapon.isCharged)
                continue

            if (weapon.maxTotalCharges > 1) {
                // Pick a random level to charge up to
                var fireAt = fireAtChargeLevel[weapon]
                if (fireAt == null) {
                    fireAt = (1..weapon.maxTotalCharges).random()
                    fireAtChargeLevel[weapon] = fireAt
                }

                if (weapon.totalReadyCharges < fireAt)
                    continue

                // Pick a new number of shots next time
                fireAtChargeLevel.remove(weapon)
            }

            if (ship.sys.debugFlags.noEnemyFire.set)
                continue

            when (weapon) {
                is IRoomTargetingWeapon -> {
                    weapon.fire(pickTarget())
                }
            }
        }
    }

    private fun pickTarget(): Room {
        return player.rooms[(Math.random() * player.rooms.size).toInt()]
    }

    private fun updateHacking() {
        val hacking = ship.hacking ?: return

        // Fire the probe at a random system, if
        // it's not already launched.
        if (!hacking.droneLaunched) {
            hacking.selectTarget(player.systems.random().room!!)
            return
        }

        // Run this all the time and it'll only start one
        // when it's ready.
        hacking.startHackingPulse()
    }
}
