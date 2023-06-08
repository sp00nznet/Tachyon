package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
import xyz.znix.xftl.game.UIUtils
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import kotlin.math.roundToInt

class Oxygen(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.OXYGEN

    // Note: it takes ~85 seconds for the ship to refill with oxygen from 0%
    // Note we also add the drain rate to counter that out, since that was included in the test
    // Maybe this is done wrong and the room drain is only applied with oxygen off, but it's not going
    // to have much of an effect since it only applies to level 2/3 oxygen.
    val refillRate: Float
        get() {
            // From the wiki, drains about 6% per second.
            if (isHackActive)
                return -0.06f

            return REFILL_RATES[powerSelected] * (1f / 85f + ROOM_DRAIN_RATE)
        }

    // Nothing to serialise
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        // ~1% per second according to the FTL wiki
        const val ROOM_DRAIN_RATE = 0.01f

        /**
         * The amount of oxygen below which crew take damage and the warning stripes appear.
         */
        const val OXYGEN_CRITICAL_LEVEL = 0.05f

        val REFILL_RATES = listOf(0f, 1f, 3f, 6f)

        val INFO: SystemInfo = OxygenInfo
    }
}

private object OxygenInfo : SystemInfo("oxygen") {
    override val canBeManned: Boolean get() = false

    override fun create(blueprint: SystemBlueprint) = Oxygen(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        val fixedPoint = (Oxygen.REFILL_RATES[level + 1] * 100).roundToInt()
        val speedStr = UIUtils.formatStringFTL(fixedPoint)
        return translator["oxygen_on"].replace("\\1", speedStr)
    }
}
