package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader

class MindControl(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.MIND_CONTROL

    // TODO implement

    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        // TODO implement
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        // TODO implement
    }

    companion object {
        const val NAME = "mind"
    }
}
