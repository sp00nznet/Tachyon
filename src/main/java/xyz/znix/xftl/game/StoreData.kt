package xyz.znix.xftl.game

import xyz.znix.xftl.systems.SystemBlueprint
import java.util.*

/**
 * Carries persistent information about a store, like its available items and prices.
 */
class StoreData(game: SlickGame) {
    val availableResources = ResourceSet()

    val sections: List<Section>

    /**
     * If this store has a systems section, this contains the systems, or null if they're sold out.
     */
    val systems: MutableList<SystemBlueprint?>

    init {
        // TODO initialise with proper values
        availableResources[Resource.FUEL] = 5
        availableResources[Resource.MISSILES] = 5
        availableResources[Resource.DRONES] = 5

        sections = listOf(Section.SYSTEMS, Section.CREW)

        val systemNames = listOf("teleporter", "cloaking", "drones")
        systems = ArrayList(systemNames.map { game.blueprintManager[it] as SystemBlueprint })
    }

    enum class Section {
        AUGMENTS,
        CREW,
        DRONES,
        SYSTEMS,
        WEAPONS;

        val localisationKey get() = "store_title_" + name.toLowerCase(Locale.UK)
    }
}
