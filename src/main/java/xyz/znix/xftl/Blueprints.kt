package xyz.znix.xftl

import org.jdom2.Element
import xyz.znix.xftl.weapons.*

class BlueprintManager(df: Datafile) {
    val blueprints: Map<String, IBlueprint>

    init {
        blueprints = HashMap()

        loadFile(df, "blueprints.xml")
        loadFile(df, "autoBlueprints.xml")
        loadFile(df, "dlcBlueprints.xml")
        // loadFile(df, "dlcBlueprintsOverride.xml") // AE stuff, TODO figure this out later
        loadFile(df, "dlcPirateBlueprints.xml")

        // Remove any blueprints from lists that don't actually exist
        for (bp in blueprints.values) {
            if (bp !is BlueprintList) continue
            bp.cleanup()
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
                else -> buildBlueprint(elem, file)
            } ?: continue

            val bpName = elem.requireAttributeValue("name")
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

    private fun buildBlueprint(elem: Element, file: FTLFile): IBlueprint {
        return MiscBlueprint(elem, file)
    }

    private fun buildWeaponBlueprint(elem: Element): IBlueprint? {
        val type = elem.getChildTextTrim("type")

        // Anything without a cooldown is a drone weapon, skip it
        if (elem.getChild("cooldown") == null)
            return null

        return when (type) {
            "LASER" -> LaserBlueprint(elem)
            "MISSILES" -> MissileBlueprint(elem)
            "BEAM" -> BeamBlueprint(elem)
            "BOMB" -> BombBlueprint(elem)
            else -> null
        }
    }
}

interface IBlueprint {
    fun resolve(): Blueprint
    fun list(): List<Blueprint>
}

class BlueprintList(private val blueprints: ArrayList<String>, private val manager: BlueprintManager) : IBlueprint {
    override fun resolve(): Blueprint = manager[blueprints.random()].resolve()
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

    override fun resolve(): Blueprint = this
    override fun list(): List<Blueprint> = listOf(this)
}

class MiscBlueprint(elem: Element, val file: FTLFile) : Blueprint(elem) {
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
