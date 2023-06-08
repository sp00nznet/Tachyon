package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import kotlin.math.min

class Medbay(blueprint: SystemBlueprint) : MainSystem(blueprint) {
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

    // Nothing to serialise
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        val INFO: SystemInfo = MedbayInfo
    }
}

private object MedbayInfo : SystemInfo("medbay") {
    override val canBeManned: Boolean get() = false
    override val isComputerObstruction: Boolean get() = true

    override fun create(blueprint: SystemBlueprint) = Medbay(blueprint)
}
