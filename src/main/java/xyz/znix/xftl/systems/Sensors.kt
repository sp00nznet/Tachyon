package xyz.znix.xftl.systems

import org.jdom2.Element

class Sensors(blueprint: SystemBlueprint, elem: Element) : SubSystem(blueprint, elem) {
    override val sortingType = SortingType.SENSORS
}
