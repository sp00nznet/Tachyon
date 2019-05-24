package xyz.znix.xftl.shipgen

import org.jdom2.Element
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.SlickGame

class ShipGenerator(val df: Datafile) {
    val blueprints: Map<String, BlueprintList>

    init {
        val parseXML = df.parseXML(df["data/autoBlueprints.xml"])

        blueprints = HashMap()

        for (elem in parseXML.rootElement.getChildren("blueprintList")) {
            val name = elem.getAttributeValue("name")

            val items = ArrayList<String>()
            for (node in elem.getChildren("name"))
                items += node.textTrim

            blueprints[name] = BlueprintList(name, items)
        }
    }

    fun buildShip(sys: SlickGame, name: String): Ship? {
        val parseXML = df.parseXML(df["data/autoBlueprints.xml"])

        for (elem in parseXML.rootElement.getChildren("shipBlueprint")) {
            if (elem.getAttributeValue("name") != name)
                continue

            // Make a clone of this element we can load the random stuff into, if we want to reuse the main XMl later
            val mutableElem = elem.clone()

            mutableElem.getChild("weaponList")?.getAttributeValue("load")?.let { l ->
                val lst = blueprints[l] ?: throw IllegalArgumentException("Cannot load unknown list $l")

                val weapon = Element("weapon")
                weapon.setAttribute("name", lst.pickRandom())
                mutableElem.getChild("weaponList").addContent(weapon)
            }

            return Ship(df, mutableElem, sys)
        }

        return null
    }

    class BlueprintList(val name: String, val items: List<String>) {
        fun pickRandom(): String {
            val id = (2).toInt()
            return items[id]
        }
    }
}
