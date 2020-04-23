package xyz.znix.xftl.systems

import org.jdom2.Element

class Oxygen(elem: Element) : MainSystem("oxygen", elem) {
    override val sortingType: SortingType get() = SortingType.OXYGEN

    // Note: it takes ~85 seconds for the ship to refill with oxygen from 0%
    // Note we also add the drain rate to counter that out, since that was included in the test
    // Maybe this is done wrong and the room drain is only applied with oxygen off, but it's not going
    // to have much of an effect since it only applies to level 2/3 oxygen.
    val refillRate: Float get() = listOf(0f, 1f, 3f, 6f)[powerSelected] * (1f / 85f + ROOM_DRAIN_RATE)

    companion object {
        // ~1% per second according to the FTL wiki
        const val ROOM_DRAIN_RATE = 0.01f
    }
}
