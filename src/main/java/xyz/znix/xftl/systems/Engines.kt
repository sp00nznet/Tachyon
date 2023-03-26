package xyz.znix.xftl.systems

import org.jdom2.Element

class Engines(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.ENGINES

    // TODO add crew evasion and charge bonus
    val evasion: Int get() = evasions[powerSelected]
    val chargeRate: Float get() = chargeRates[powerSelected]

    companion object {
        // https://ftl.fandom.com/wiki/Engines
        private val evasions: Array<Int> = arrayOf(0, 5, 10, 15, 20, 25, 28, 31, 35)
        private val chargeRates = arrayOf(0f, 1f, 1.26f, 1.58f, 1.84f, 2.11f, 2.4f, 2.68f, 2.96f)
    }
}
