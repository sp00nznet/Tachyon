package xyz.znix.xftl.shipgen

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.HumanCrew
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

        val ship = Ship(df, elem, sys)

        val crewCount = elem.getChild("crewCount")
        crewCount?.let {
            // TODO crew types
            @Suppress("UNUSED_VARIABLE")
            val type = crewCount.getAttributeValue("class") ?: "human"

            // TODO crew count randomisation, accounting for the sector
            val amount = crewCount.requireAttributeValueInt("amount")

            for (i in 1..amount) {
                val enemyCrew = HumanCrew(sys.animations, ship.rooms.random(), AbstractCrew.SlotType.CREW)
                ship.crew.add(enemyCrew)
            }
        }

        return ship
    }
}

class EnemyShipSpec(elem: Element, bp: BlueprintManager) {
    val name = elem.requireAttributeValue("name")

    // Two ships (TUTORIAL_PIRATE and IMPOSSIBLE_PIRATE) have 'blueprint' attributes instead. While it's
    // unlikely we'll care about them for a long time (if ever), it's nice to load all the ships without
    // having to carry around a list of exceptions.
    val autoBlueprint = bp[elem.getAttributeValue("blueprint") ?: elem.requireAttributeValue("auto_blueprint")]
}
