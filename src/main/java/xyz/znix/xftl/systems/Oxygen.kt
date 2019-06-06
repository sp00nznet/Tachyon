package xyz.znix.xftl.systems

import org.jdom2.Element

class Oxygen(elem: Element) : MainSystem("oxygen", elem) {
    override val sortingType: SortingType get() = SortingType.OXYGEN
}