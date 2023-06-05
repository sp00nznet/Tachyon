package xyz.znix.xftl

import org.jdom2.Element
import xyz.znix.xftl.augments.AugEngiMedbots
import xyz.znix.xftl.augments.AugPreigniter
import xyz.znix.xftl.augments.AugZoltanShield
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.*
import kotlin.random.Random

class BlueprintManager(df: Datafile, private val enableAE: Boolean) {
    val blueprints: Map<String, IBlueprint>

    init {
        blueprints = HashMap()

        loadFile(df, "blueprints.xml")
        loadFile(df, "autoBlueprints.xml")
        loadFile(df, "bosses.xml")
        if (enableAE) {
            loadFile(df, "dlcBlueprints.xml")
            loadFile(df, "dlcBlueprintsOverwrite.xml")
            loadFile(df, "dlcPirateBlueprints.xml")
        }

        // If AE is enabled, rename all the OVERRIDE blueprints to
        // remove that prefix - thus they'll be used instead of the
        // original ones.
        if (enableAE) {
            // Use toList to duplicate the entries list, since we'll
            // be mutating the main blueprints map.
            for ((name, bp) in blueprints.entries.toList()) {
                if (!name.startsWith(AE_PREFIX))
                    continue
                blueprints[name.removePrefix(AE_PREFIX)] = bp
            }
        }

        // Remove the now-redundant override entries - this is probably
        // unnecessary, but it'll ensure we don't end up accidentally
        // using something from AE in non-AE mode.
        val toRemove = blueprints.keys.filter { it.startsWith(AE_PREFIX) }
        for (name in toRemove) {
            blueprints.remove(name)
        }

        // Remove any blueprints from lists that don't actually exist
        for (bp in blueprints.values) {
            if (bp !is BlueprintList) continue
            bp.cleanup()
        }
    }

    // Required to bind sounds to the weapons
    fun finishLoading(content: InGameState.GameContent) {
        for (bp in blueprints.values) {
            if (bp !is Blueprint)
                continue

            bp.finishSetup(content)
        }
    }

    operator fun get(name: String): IBlueprint = blueprints[name] ?: error("Unknown blueprint $name")

    private fun loadFile(df: Datafile, name: String) {
        val file = df["data/$name"]
        val mutableBlueprints = blueprints as HashMap<String, IBlueprint>
        val parseXML = df.parseXML(file)
        for (elem in parseXML.rootElement.children) {
            val bp = when (elem.name) {
                "blueprintList" -> buildList(elem)
                "weaponBlueprint" -> buildWeaponBlueprint(elem)
                "droneBlueprint" -> buildDroneBlueprint(elem)
                "systemBlueprint" -> buildSystemBlueprint(elem)
                "crewBlueprint" -> buildCrewBlueprint(elem)
                "augBlueprint" -> buildAugmentBlueprint(elem)
                "shipBlueprint" -> ShipBlueprint(elem, file)

                // Intentionally ignore itemBlueprint - this contains fuel, drones, and missiles.
                // The name 'drones' conflicts with the drones system, and it's the only such name
                // conflict. Since there's very little of value in those blueprints, we can just
                // ignore them.
                "itemBlueprint" -> null

                // Ignore unknown blueprints
                else -> null
            } ?: continue

            val bpName = elem.requireAttributeValue("name")
            if (mutableBlueprints.containsKey(bpName)) {
                println("Warning: duplicate blueprint name '$bpName'")
            }
            mutableBlueprints[bpName] = bp
        }
    }

    private fun buildList(elem: Element): IBlueprint {
        val items = ArrayList<String>()

        for (node in elem.children) {
            check(node.name == "name")
            items += node.textTrim
        }

        return BlueprintList(items, this)
    }

    private fun buildWeaponBlueprint(elem: Element): IBlueprint {
        val type = elem.getChildTextTrim("type")

        return when (type) {
            "LASER" -> LaserBlueprint(elem)
            "MISSILES" -> MissileBlueprint(elem)
            "BEAM" -> BeamBlueprint(elem)
            "BOMB" -> BombBlueprint(elem)
            "BURST" -> FlakBlueprint(elem)
            else -> UknWeaponBlueprint(elem)
        }
    }

    private fun buildDroneBlueprint(elem: Element): IBlueprint {
        return DroneBlueprint(elem)
    }

    private fun buildAugmentBlueprint(elem: Element): IBlueprint {
        return when (val name = elem.getAttributeValue("name")) {
            AugEngiMedbots.NAME -> AugEngiMedbots(elem)
            AugPreigniter.NAME -> AugPreigniter(elem)
            AugZoltanShield.NAME -> AugZoltanShield(elem)

            // Augments that use the default AugmentBlueprint and are implemented
            // as part of another system should be explicitly handled here.
            AugmentBlueprint.LONG_RANGE_SCANNERS -> AugmentBlueprint(elem)
            AugmentBlueprint.RECONSTRUCTIVE_TELEPORT -> AugmentBlueprint(elem)
            AugmentBlueprint.OXYGEN_MASKS -> AugmentBlueprint(elem)
            AugmentBlueprint.BACKUP_DNA -> AugmentBlueprint(elem)

            else -> {
                println("WARNING: Adding unknown augment '$name'")
                AugmentBlueprint(elem)
            }
        }
    }

    private fun buildSystemBlueprint(elem: Element): IBlueprint {
        return SystemBlueprint(elem)
    }

    private fun buildCrewBlueprint(elem: Element): IBlueprint {
        return CrewBlueprint(elem)
    }

    companion object {
        const val AE_PREFIX = "OVERRIDE_"
    }
}

interface IBlueprint {
    fun resolve(random: Random = Random): Blueprint
    fun list(): List<Blueprint>
}

class BlueprintList(private val blueprints: ArrayList<String>, private val manager: BlueprintManager) : IBlueprint {
    override fun resolve(random: Random): Blueprint = manager[blueprints.random(random)].resolve(random)
    override fun list(): List<Blueprint> = blueprints.flatMap { manager[it].list() }

    fun cleanup() {
        val toRemove = blueprints.filter { !manager.blueprints.containsKey(it) }

        for (item in toRemove) {
            println("Warning: Removing non-existant blueprint $item from blueprint list!")
            blueprints.remove(item)
        }
    }
}

open class Blueprint(elem: Element) : IBlueprint {
    val name = elem.requireAttributeValue("name")
    val rarity: Int? = elem.getChild("rarity")?.textTrim?.toInt()

    val title = elem.getGameTextChild("title")
    val short = elem.getGameTextChild("short")
    val desc = elem.getGameTextChild("desc")

    /**
     * Get this item's purchase price (the price you can buy it at, not the
     * price you sell it at) in scrap. Returns null if the item doesn't
     * have a price.
     */
    open val cost: Int? = null

    fun translateTitle(game: InGameState): String = title?.let { game.translator[it] } ?: "MISSING TITLE: $name"
    fun translateShort(game: InGameState): String = short?.let { game.translator[it] } ?: "MISSING SHORT: $name"

    override fun resolve(random: Random): Blueprint = this
    override fun list(): List<Blueprint> = listOf(this)

    /**
     * Finish setting up this blueprint, loading any other
     * required resources (eg, sounds).
     */
    open fun finishSetup(content: InGameState.GameContent) {
    }
}

class ShipBlueprint(elem: Element, val file: FTLFile) : Blueprint(elem) {
    val layout: String = elem.getAttributeValue("layout")
    val img: String = elem.getAttributeValue("img")

    fun loadElem(df: Datafile): Element {
        val rootXml = df.parseXML(file)

        for (item in rootXml.rootElement.children) {
            if (item.requireAttributeValue("name") == name) {
                return item
            }
        }

        error("Could not find blueprint!")
    }
}
