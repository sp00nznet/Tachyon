package xyz.znix.xftl.systems

class BackupBattery(blueprint: SystemBlueprint) : SubSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.BATTERY

    // TODO implement

    companion object {
        const val NAME = "battery"
    }
}
