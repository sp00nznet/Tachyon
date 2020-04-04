package xyz.znix.xftl.shipgen

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.game.SlickGame

class ShipGenerator(val df: Datafile, val bp: BlueprintManager) {
    fun buildShip(sys: SlickGame, spec: EnemyShipSpec): Ship {
        val elem = spec.autoBlueprint.resolve().loadElem(df)

        val weaponList = elem.getChild("weaponList")
        weaponList?.getAttributeValue("load")?.let { listName ->
            val blueprint = bp[listName].resolve()

            val weapon = Element("weapon")
            weapon.setAttribute("name", blueprint.name)
            weaponList.addContent(weapon)
        }

        return Ship(df, elem, sys)
    }
}

class EnemyShipSpec(elem: Element, bp: BlueprintManager) {
    val name = elem.requireAttributeValue("name")
    val autoBlueprint = bp[elem.requireAttributeValue("auto_blueprint")]
}
