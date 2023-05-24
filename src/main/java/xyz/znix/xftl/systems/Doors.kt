package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader

class Doors(blueprint: SystemBlueprint) : SubSystem(blueprint) {
    override val sortingType = SortingType.DOORS

    // Nothing required, as the doors are serialised individually
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        const val NAME = "doors"
    }
}
