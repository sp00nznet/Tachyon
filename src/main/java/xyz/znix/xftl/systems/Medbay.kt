package xyz.znix.xftl.systems

import org.jdom2.Element

class Medbay(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.MEDBAY
}
