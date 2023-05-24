package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader

class Sensors(blueprint: SystemBlueprint) : SubSystem(blueprint) {
    override val sortingType = SortingType.SENSORS

    // Nothing to serialise
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        const val NAME = "sensors"
    }
}
