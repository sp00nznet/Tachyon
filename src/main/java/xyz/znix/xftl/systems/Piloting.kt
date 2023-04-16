package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.math.ConstPoint

class Piloting(blueprint: SystemBlueprint, elem: Element) : SubSystem(blueprint, elem) {
    // TODO add crew evasion
    val evasion: Int get() = 0

    private val computerPoint by lazy { room?.computerPoint ?: ConstPoint.ZERO }

    override val sortingType = SortingType.PILOTING

    val evasionMultiplier: Float
        get() {
            // If piloting is broken, you get no evasion
            if (undamagedEnergy == 0) return 0f

            // It seems there's a fake crewmember in every room?
            // https://www.reddit.com/r/ftlgame/comments/2e30zc/question_re_autoscouts/
            // TODO make some way to query crew skill that takes that into account for everything else
            if (ship.isAutoScout) return 1.05f

            val room = this.room!!

            // Find the pilot at the computer point, or the top-left if the computer point is not defined
            val pilot = room.crew.firstOrNull {
                it.mode == AbstractCrew.SlotType.CREW && it.position == computerPoint && it.canManSystem
            }

            // If a pilot is present (not just walking there), we get 100% of our original piloting
            if (pilot != null && pilot.movement == null)
                return 1f

            // At this point, the system is not broken and we don't have a pilot. Use the evasion
            // multipliers for the autopilot.
            // We're using the AE numbers for this, taken from: https://ftl.fandom.com/wiki/Piloting
            return when (undamagedEnergy) {
                1 -> 0f
                2 -> 0.5f
                3 -> 0.8f
                else -> error("Unsupported power level for piloting: $undamagedEnergy")
            }
        }
}
