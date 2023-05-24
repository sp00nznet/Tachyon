package xyz.znix.xftl.systems

import org.jdom2.Element

class BackupBattery(blueprint: SystemBlueprint, elem: Element) : SubSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.BATTERY

    // TODO implement

    companion object {
        const val NAME = "battery"
    }
}
