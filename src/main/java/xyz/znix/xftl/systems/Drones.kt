package xyz.znix.xftl.systems

import org.jdom2.Element

class Drones(blueprint: SystemBlueprint, xml: Element) : MainSystem(blueprint, xml) {
    override val sortingType: SortingType get() = SortingType.DRONES
}
