package xyz.znix.xftl.systems

import org.jdom2.Element

class MindControl(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.MIND_CONTROL

    // TODO implement

    companion object {
        const val NAME = "mind"
    }
}
