package xyz.znix.xftl.systems

class MindControl(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.MIND_CONTROL

    // TODO implement

    companion object {
        const val NAME = "mind"
    }
}
