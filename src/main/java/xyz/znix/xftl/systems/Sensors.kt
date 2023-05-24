package xyz.znix.xftl.systems

class Sensors(blueprint: SystemBlueprint) : SubSystem(blueprint) {
    override val sortingType = SortingType.SENSORS

    companion object {
        const val NAME = "sensors"
    }
}
