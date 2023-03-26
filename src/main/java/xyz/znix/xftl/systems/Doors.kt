package xyz.znix.xftl.systems

import org.jdom2.Element

class Doors(blueprint: SystemBlueprint, elem: Element) : SubSystem(blueprint, elem) {
    override val sortingType = SortingType.DOORS
}
