package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.Translator
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
        val INFO: SystemInfo = MindControlInfo
    }
}

private object MindControlInfo : SystemInfo("mind") {
    override val canBeManned: Boolean get() = false

    override fun create(blueprint: SystemBlueprint) = MindControl(blueprint)

    override fun getLevelName(level: Int, translator: Translator): String {
        return when (level) {
            0 -> translator["mind_1"]
            1 -> translator["mind_2"]
            2 -> translator["mind_3"]
            else -> "INVALID LEVEL ${level + 1}"
        }
    }
}
