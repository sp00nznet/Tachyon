package xyz.znix.xftl.systems

import org.jdom2.Element

class Hacking(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.HACKING

    // TODO implement
}
