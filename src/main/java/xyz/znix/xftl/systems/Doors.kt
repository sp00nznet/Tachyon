package xyz.znix.xftl.systems

class Doors(blueprint: SystemBlueprint) : SubSystem(blueprint) {
    override val sortingType = SortingType.DOORS

    companion object {
        const val NAME = "doors"
    }
}
