package xyz.znix.xftl.crew

import org.jdom2.Element
import org.newdawn.slick.Color
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

    /**
     * A list of all the colour filters that can be used as a filter
     * for rendering the layer1 (or in the case of humans, layer2) images.
     */
    val colourFilters: List<List<Color>> = elem.getChild("colorList")
        ?.getChildren("layer")?.map { parseColourLayer(it) }
        ?: emptyList()

    /**
     * The images representing each of the layers tinted by [colourFilters].
     */
    val layerImageNames: List<String>

    /**
     * The number of colour variations this crewmember has.
     *
     * This is distinct from [colourFilters].size, as humans have male/female variants.
     */
    // TODO human male/female variants
    val numberOfColours: Int = colourFilters.map { it.size }.max() ?: 0

    init {
        // Load all the layer images for this race
        layerImageNames = ArrayList()
        for (i in 1..colourFilters.size) {
            layerImageNames += "img/people/${name}_layer$i.png"
        }
    }

    fun spawn(room: Room, mode: AbstractCrew.SlotType): LivingCrew {
        val animations = room.ship.sys.animations

        return when (name) {
            "human" -> CrewHuman(this, animations, room, mode)
            "engi" -> CrewEngi(this, animations, room, mode)
            "mantis" -> CrewMantis(this, animations, room, mode)
            "rock" -> CrewRock(this, animations, room, mode)
            "energy" -> CrewZoltan(this, animations, room, mode)
            "slug" -> CrewSlug(this, animations, room, mode)
            "crystal" -> CrewCrystal(this, animations, room, mode)
            "anaerobic" -> CrewAnaerobic(this, animations, room, mode)

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

        private fun parseColourLayer(layerElem: Element): List<Color> {
            val colourElements = layerElem.getChildren("color")

            return colourElements.map {
                // Alpha is 0-1, the others are 0-255
                Color(
                    it.getAttributeValue("r").toInt() / 255f,
                    it.getAttributeValue("g").toInt() / 255f,
                    it.getAttributeValue("b").toInt() / 255f,
                    it.getAttributeValue("a").toFloat()
                )
            }
        }
    }
}
