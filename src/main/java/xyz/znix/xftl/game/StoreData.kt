package xyz.znix.xftl.game

import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.rollChance
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import java.util.*
import kotlin.math.min
import kotlin.random.Random

/**
 * Carries persistent information about a store, like its available items and prices.
 */
class StoreData(game: InGameState) {
    val availableResources = ResourceSet()

    val sections: List<Section>

    /**
     * If this store has a systems section, this contains the systems, or null if they're sold out.
     */
    val systems: MutableList<SystemBlueprint?>

    /**
     * If this store has a weapons section, this contains the weapons, or null if they're sold out.
     */
    val weapons: MutableList<AbstractWeaponBlueprint?>

    /**
     * If this store has a drones section, this contains the drones, or null if they're sold out.
     */
    val drones: MutableList<DroneBlueprint?>

    /**
     * If this store has an augments section, this contains the augments, or null if they're sold out.
     */
    val augments: MutableList<AugmentBlueprint?>

    /**
     * If this store has a crew section, this is the crew, or null if they're sold out.
     */
    val crew: MutableList<LivingCrewInfo?>

    init {
        // See doc/stores for generation details
        availableResources[Resource.FUEL] = (3..7).random()
        availableResources[Resource.MISSILES] = (2..6).random()
        availableResources[Resource.DRONES] = (2..4).random()

        // Generate the section types
        val numSections = (2..4).random()
        sections = ArrayList()

        val ship = game.player
        val numSystems = ship.systems.size

        val possibleSections = ArrayList<Section>()
        possibleSections += Section.AUGMENTS
        possibleSections += Section.CREW
        possibleSections += Section.DRONES
        possibleSections += Section.WEAPONS

        // On non-AE, systems don't spawn once you've got them all.
        if (game.content.enableAdvancedEdition || numSystems < 11) {
            possibleSections += Section.SYSTEMS
        }

        // If you have <11 systems (including subsystems), there's
        // a 50% chance the first section will be a systems section.
        if (numSystems < 11 && Random.rollChance(50)) {
            possibleSections.remove(Section.SYSTEMS)
            sections += Section.SYSTEMS
        }

        // Randomly spawn the rest of the sections
        for (i in 0 until numSections) {
            sections += possibleSections.removeAt(possibleSections.indices.random())
        }

        systems = generateSystems(game, ship)

        weapons = getForSection(game, Section.WEAPONS, AbstractWeaponBlueprint::class.java)
        drones = getForSection(game, Section.DRONES, DroneBlueprint::class.java)
        augments = getForSection(game, Section.AUGMENTS, AugmentBlueprint::class.java)

        if (sections.contains(Section.CREW)) {
            crew = ArrayList(game.lootPool.getManyRandom(CrewBlueprint::class.java, 3)
                .map { LivingCrewInfo.generateRandom(it, game) })
        } else {
            crew = ArrayList()
        }
    }

    private fun <T> getForSection(game: InGameState, section: Section, type: Class<T>): ArrayList<T?> {
        // Return an empty list to reduce the savefile size
        if (!sections.contains(section)) {
            return ArrayList()
        }

        return ArrayList(game.lootPool.getManyRandom(type, 3))
    }

    private fun generateSystems(game: InGameState, ship: Ship): MutableList<SystemBlueprint?> {
        val systems = ArrayList<SystemBlueprint?>()

        // Return an empty list to reduce the savefile size
        if (!sections.contains(Section.SYSTEMS)) {
            return systems
        }

        val possibleSystems = ArrayList(game.blueprintManager.blueprints.values.filterIsInstance<SystemBlueprint>())
        val dronesSystem = possibleSystems.first { it.type == Drones.NAME }
        val shieldsSystem = possibleSystems.first { it.type == Shields.NAME }
        val medbaySystem = possibleSystems.first { it.type == Medbay.NAME }
        val clonebaySystem = possibleSystems.firstOrNull { it.type == Clonebay.NAME } // Null on non-AE

        val playerHasDrones = ship.systems.any { it is Drones }
        val playerHasShields = ship.systems.any { it is Shields }
        val playerHasMedical = ship.systems.any { it is Medbay || it is Clonebay }

        val forcedSystems = ArrayList<SystemBlueprint>()

        if (sections.contains(Section.DRONES) && !playerHasDrones) {
            forcedSystems += dronesSystem
            possibleSystems.remove(dronesSystem)
        }

        if (!playerHasShields) {
            forcedSystems += shieldsSystem
            possibleSystems.remove(shieldsSystem)
        }

        if (!playerHasMedical) {
            val medicalSystem = when {
                game.content.enableAdvancedEdition -> listOf(medbaySystem, clonebaySystem!!).random()
                else -> medbaySystem
            }

            forcedSystems += medicalSystem

            // A forced medical system hides both the clonebay and medbay.
            possibleSystems.remove(medbaySystem)
            clonebaySystem?.let { possibleSystems.remove(it) }
        }

        // Remove all the systems the player already has
        possibleSystems.removeAll(ship.systems.map { it.blueprint }.toSet())

        // Remove any system that can't be installed on the player's ship.
        // This is here for modded ships, which might not be able to accommodate all the systems.
        val shipSlots = ship.systemSlots.associateBy { it.system }
        possibleSystems.removeIf { !shipSlots.containsKey(it) }

        // If the ship is full and can't take any more main systems, remove those
        // which don't replace an existing system.
        if (ship.mainSystems.size >= 8) {
            possibleSystems.removeIf { shipSlots.getValue(it).room.system == null }
        }

        // To match FTL with a little bit of odd behaviour, if the list of non-forced
        // systems is less than three systems long, that sets the number of systems.
        // Thus if you don't have a medical system, you can end up with a blank system
        // slot even though there's a system that's not displayed.
        // See doc/stores for more information.
        val numSystems = min(3, possibleSystems.size)

        while (systems.size < numSystems) {
            systems += possibleSystems.removeAt(possibleSystems.indices.random())
        }

        return systems
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
