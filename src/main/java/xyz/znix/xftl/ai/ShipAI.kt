package xyz.znix.xftl.ai

import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Weapons
import xyz.znix.xftl.weapons.LaserBlueprint
import xyz.znix.xftl.weapons.MissileBlueprint

class ShipAI(val ship: Ship, val player: Ship) {
    fun update(dt: Float) {
        val weapons = findWeapons() ?: return

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            if (!weapon.isCharged)
                return

            when (weapon) {
                is LaserBlueprint.LaserInstance -> {
                    weapon.fire(weapons, pickTarget())
                }
                is MissileBlueprint.MissileInstance -> {
                    weapon.fire(weapons, pickTarget())
                }
            }
        }
    }

    private fun findWeapons(): Weapons? {
        for (room in ship.rooms)
            return room.system as? Weapons ?: continue

        return null
    }

    private fun pickTarget(): Room {
        return player.rooms[(Math.random() * player.rooms.size).toInt()]
    }
}
