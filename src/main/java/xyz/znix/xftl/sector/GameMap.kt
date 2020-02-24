package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.mapChildrenText
import xyz.znix.xftl.requireAttributeValue

/**
 * Represents the overall map of the game. This loads and parses the sector data (which defines all the
 * different types of sectors) and generates a random set of them to be used in the sector map.
 */
class GameMap(df: Datafile, private val eventManager: EventManager) {
    private val sectorClasses = HashMap<String, List<SectorType>>()
    private val sectorTypes = HashMap<String, SectorType>()

    init {
        val xml = df.parseXML(df["data/sector_data.xml"])

        val namedSectorTypes = HashMap<String, List<String>>()

        for (elem in xml.rootElement.children) {
            when (elem.name) {
                "sectorType" -> parseSectorType(elem, namedSectorTypes)
                "sectorDescription" -> parseSectorDescription(elem)
                else -> error("Unknown node type ${elem.name}")
            }
        }

        for ((name, sectors) in namedSectorTypes) {
            sectorClasses[name] = sectors.map {
                sectorTypes[it] ?: error("Missing sector $it specificed in category $name")
            }
        }
    }

    private fun parseSectorType(elem: Element, namedSectorTypes: HashMap<String, List<String>>) {
        // TODO handle the Advanced Edition overrides
        val name = elem.requireAttributeValue("name")
        check(!namedSectorTypes.containsKey(name))
        namedSectorTypes[name] = elem.mapChildrenText("sector")
    }

    private fun parseSectorDescription(elem: Element) {
        val name = elem.requireAttributeValue("name")

        // Specialcase a couple of unused sectors which don't have names set, and should never appear otherwise anyway
        // Note that abandoned sector doesn't refer to the lanius-filled in-game sector - that's LANIUS_SECTOR
        if (name == "ABANDONED_SECTOR" || name == "DEEP_SPACE_SECTOR") return

        check(!sectorTypes.containsKey(name))

        val sector = SectorType(eventManager, elem)
        check(name == sector.name)
        sectorTypes[name] = sector
    }

    private fun generateSector(): Sector {
        val category = listOf("CIVILIAN", "NEBULA", "OVERRIDE_HOSTILE").random()
        val type = sectorClasses[category]?.random() ?: error("Missing sector category $category")

        val eventPool = ArrayList<Event>()
        for (ev in type.events) {
            val count = ev.count.random()
            for (i in 1..count) {
                eventPool += ev.event.resolve()
            }
        }

        return Sector(type, eventPool, eventManager["NEUTRAL"])
    }

    // Temporary, this is just a single linear line of sectors
    // TODO shape them how they are in standard FTL
    val sectors = Array(7) { generateSector() }
}
