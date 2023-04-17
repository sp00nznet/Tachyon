package xyz.znix.xftl.game

import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.ShipWeaponBlueprint
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

    /**
     * If this store has a weapons section, this contains the weapons, or null if they're sold out.
     */
    val weapons: MutableList<ShipWeaponBlueprint?>

    /**
     * If this store has an augments section, this contains the augments, or null if they're sold out.
     */
    val augments: MutableList<AugmentBlueprint?>

    init {
        // TODO initialise with proper values
        availableResources[Resource.FUEL] = 5
        availableResources[Resource.MISSILES] = 5
        availableResources[Resource.DRONES] = 5

        sections = listOf(Section.SYSTEMS, Section.CREW, Section.AUGMENTS, Section.WEAPONS)

        val systemNames = listOf("teleporter", "cloaking", "drones")
        systems = ArrayList(systemNames.map { game.blueprintManager[it] as SystemBlueprint })

        val weaponNames = listOf("BEAM_1", "BEAM_2", "BOMB_BREACH_2")
        weapons = ArrayList(weaponNames.map { game.blueprintManager[it] as ShipWeaponBlueprint })

        val augmentNames = listOf("ADV_SCANNERS", "NANO_MEDBAY", "ROCK_ARMOR")
        augments = ArrayList(augmentNames.map { game.blueprintManager[it] as AugmentBlueprint })
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
