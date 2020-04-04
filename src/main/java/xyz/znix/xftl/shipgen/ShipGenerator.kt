package xyz.znix.xftl.shipgen

import org.jdom2.Element
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.SlickGame

class ShipGenerator(val df: Datafile, val bp: BlueprintManager) {
    fun buildShip(sys: SlickGame, name: String): Ship {
        val elem = bp[name].resolve().loadElem(df)

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
