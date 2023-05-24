package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import kotlin.math.min

class Medbay(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.MEDBAY

    override fun update(dt: Float) {
        super.update(dt)

        var healthPerSec = when (powerSelected) {
            1 -> 6.4f
            2 -> 9.6f
            3 -> 19.2f
            else -> 0f
        }

        // Hacking hurts the friendly crew, and rather quickly.
        if (isHackActive) {
            healthPerSec = -13f
        }

        val healing = healthPerSec * dt

        for (crew in room!!.crew) {
            // Don't heal drones
            if (crew !is LivingCrew)
                continue

            // Only heal friendly crew
            if (crew.mode == AbstractCrew.SlotType.INTRUDER)
                continue

            // Don't revive dying crewmembers
            if (crew.currentAction == AbstractCrew.Action.DYING)
                continue

            crew.health = min(crew.health + healing, crew.maxHealth)
        }
    }

    companion object {
        const val NAME = "medbay"
    }
}
