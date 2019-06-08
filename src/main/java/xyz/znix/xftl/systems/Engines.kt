package xyz.znix.xftl.systems

import org.jdom2.Element

class Engines(elem: Element) : MainSystem("engines", elem) {
    override val sortingType: SortingType get() = SortingType.ENGINES

    // TODO add crew evasion bonus
    val evasion: Int get() = evasions[powerSelected]

    companion object {
        val evasions: Array<Int> = arrayOf(0, 5, 10, 15, 20, 25, 28, 31, 35)
    }
}
