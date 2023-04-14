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

    private val specialEvents = SpecialEvents(
        eventManager["NEUTRAL"],
        eventManager["FINISH_BEACON"],
        eventManager["FINISH_BEACON_NEBULA"]
    )

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

    private fun generateSector(sectorNum: Int): Sector {
        val category = listOf("CIVILIAN", "NEBULA", "OVERRIDE_HOSTILE").random()
        val type = sectorClasses[category]?.random() ?: error("Missing sector category $category")

        val eventPool = ArrayList<Event>()
        for (ev in type.events) {
            val count = ev.count.random()
            for (i in 1..count) {
                eventPool += ev.event.resolve()
            }
        }

        return Sector(type, sectorNum, eventPool, specialEvents)
    }

    // Temporary, this is just a single linear line of sectors
    // TODO shape them how they are in standard FTL
    val sectors = Array(7) { sectorNum -> generateSector(sectorNum) }

    class SpecialEvents(
        /**
         * The event to be used when we've run out of events, but still have more beacons
         * to be filled. After modifying the vanilla FTL event XMLs, this is hardcoded
         * to the 'NEUTRAL' event/eventList, so we should obviously behave likewise.
         *
         * Note that I haven't confirmed these should *only* be used after running out of
         * other events - it's perfectly possible the base game peppers these in on a
         * random chance.
         *
         * The filler will be resolved once per element, so if it's an [EventList] each beacon
         * will a random (though certainly not guaranteed to be unique) event.
         */
        val filler: IEvent,

        /**
         * The event to show on regular (non-nebula-covered) exit beacons. This seems
         * to be the same for all sectors.
         *
         * Whether this or [exitNebula] is used depends on whether the exit beacon
         * is situated in a nebula itself, not whether the sector type is a nebula.
         */
        val exit: IEvent,

        val exitNebula: IEvent
    )
}
