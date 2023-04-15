package xyz.znix.xftl.crew

import org.jdom2.Element
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.layout.Room

class CrewBlueprint(elem: Element) : Blueprint(elem) {
    override val cost: Int = elem.getChildTextTrim("cost").toInt()

    /**
     * A list of text ID keys corresponding to this race's special abilities.
     *
     * This is used for UI purposes only.
     */
    val powerStringIds: List<String> =
        elem.getChild("powerList")?.getChildren("power")?.map { it.getAttributeValue("id") }
            ?: emptyList()

    fun spawn(room: Room, mode: AbstractCrew.SlotType): LivingCrew {
        val animations = room.ship.sys.animations

        return when (name) {
            "human" -> HumanCrew(this, animations, room, mode)

            // Until we've implemented all the others, don't give a hard error here.
            else -> {
                println("Warning: Cannot spawn crew of unimplemented race '$name', replacing with human")
                val human = room.ship.sys.blueprintManager["human"] as CrewBlueprint
                return human.spawn(room, mode)
            }
        }
    }

    companion object {
        val PLAYABLE_RACE_NAMES = listOf(
            "human",
            "mantis",
            "engi",
            "energy",
            "slug",
            "rock",
            "crystal",
            "anaerobic"
        )
    }
}
