package xyz.znix.xftl.ai

import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Weapons
import xyz.znix.xftl.weapons.LaserBlueprint
import xyz.znix.xftl.weapons.MissileBlueprint

class ShipAI(val ship: Ship, val player: Ship) {
    fun update(dt: Float) {
        val weapons = ship.weapons ?: return

        for (hp in ship.hardpoints) {
            val weapon = hp.weapon ?: continue

            if (!weapon.isCharged)
                continue

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

    private fun pickTarget(): Room {
        return player.rooms[(Math.random() * player.rooms.size).toInt()]
    }
}
