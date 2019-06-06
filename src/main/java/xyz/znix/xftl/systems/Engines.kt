package xyz.znix.xftl.systems

import org.jdom2.Element

class Engines(elem: Element) : MainSystem("engines", elem) {
    override val sortingType: SortingType get() = SortingType.ENGINES
}