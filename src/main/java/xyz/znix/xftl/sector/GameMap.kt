package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.mapChildrenText
import xyz.znix.xftl.requireAttributeValue
import kotlin.math.abs
import kotlin.random.Random

/**
 * Represents the overall map of the game. This loads and parses the sector data (which defines all the
 * different types of sectors) and generates a random set of them to be used in the sector map.
 */
class GameMap(df: Datafile, private val eventManager: EventManager) {
    private val sectorClasses = HashMap<SectorClass, List<SectorType>>()
    private val sectorTypes = HashMap<String, SectorType>()

    private val specialEvents = SpecialEvents(
        eventManager["START_GAME"],
        eventManager["NEUTRAL"],
        eventManager["FINISH_BEACON"],
        eventManager["FINISH_BEACON_NEBULA"]
    )

    val sectors: List<List<SectorInfo>>

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

        outer@ for ((name, sectors) in namedSectorTypes) {
            val sectorClass = when (name) {
                "CIVILIAN" -> SectorClass.CIVILIAN
                "HOSTILE" -> SectorClass.HOSTILE
                "NEBULA" -> SectorClass.NEBULA

                // TODO use OVERRIDE_HOSTILE instead in AE

                // Skip unknown sector classes
                else -> continue@outer
            }

            sectorClasses[sectorClass] = sectors.map {
                sectorTypes[it] ?: error("Missing sector $it specificed in category $name")
            }
        }

        // Initialise here so we can add stuff to it in this constructor
        sectors = ArrayList()

        // Add the starting sector
        val startingType = sectorTypes["CIVILIAN_SECTOR"] ?: error("Cannot find CIVILIAN_SECTOR for first sector")
        val startingSector = SectorInfo(0, 0, startingType, SectorClass.CIVILIAN)
        sectors.add(listOf(startingSector))

        // Keep track of what sector types we've used, to avoid duplicating
        // sectors like homewords that are unique.
        val usedSectorTypes = HashSet<SectorType>()

        // Add the six intermediate columns
        for (columnNum in 1..6) {
            val lastColumn = sectors.last()

            // Choose how many sectors there will be in this column, choosing
            // a number different to the previous one.
            var numInColumn: Int
            do {
                numInColumn = (2..4).random()
            } while (numInColumn == lastColumn.size)

            // Except the 2nd column is special, as it joins the starting
            // beacon and thus must only have two beacons to avoid giving
            // the player more than two options.
            if (lastColumn.size == 1) {
                numInColumn = 2
            }

            // Build all the sectors in this column
            val column = ArrayList<SectorInfo>()
            for (i in 0 until numInColumn) {
                val sectorClass = SectorClass.random(Random.Default)

                val allTypes = sectorClasses[sectorClass] ?: error("No sectors for sector class $sectorClass")

                // Filter down the sector types to only those permitted at this point
                val availableTypes = allTypes.filter {
                    // Gated to later sectors
                    if (columnNum < it.minSector)
                        return@filter false

                    // If this is a unique sector, make sure it's not already been used
                    if (it.unique) {
                        if (usedSectorTypes.contains(it))
                            return@filter false
                    }

                    return@filter true
                }

                val type = availableTypes.random()
                usedSectorTypes.add(type)

                column.add(SectorInfo(columnNum, i, type, sectorClass))
            }

            // Add the connections between the previous sectors and
            // the new ones. This depends on the number of sectors
            // in this and the previous column, see sector-map for
            // more information.

            if (lastColumn.size == 2 && numInColumn == 4) {
                lastColumn[0].nextSectors += column[0]
                lastColumn[0].nextSectors += column[1]
                lastColumn[1].nextSectors += column[2]
                lastColumn[1].nextSectors += column[3]
            } else if (lastColumn.size == 4 && numInColumn == 2) {
                lastColumn[0].nextSectors += column[0]
                lastColumn[1].nextSectors += column[0]
                lastColumn[2].nextSectors += column[1]
                lastColumn[3].nextSectors += column[1]
            } else {
                // This should only happen when we change size by one.
                require(abs(lastColumn.size - numInColumn) == 1)

                // Link each sector (except the edge ones in the column
                // with the most sectors) to two others.

                // Link the nth+a sector in the previous column to the nth+b
                // one in the current column.
                fun linkColumnsOffset(prevOffset: Int, currOffset: Int) {
                    var i = 0
                    while (prevOffset + i < lastColumn.size && currOffset + i < column.size) {
                        lastColumn[prevOffset + i].nextSectors += column[currOffset + i]
                        i += 1
                    }
                }

                if (lastColumn.size > numInColumn) {
                    // Less nodes in this column, we need the first and second
                    // sectors in the first column linking to the first sector
                    // in the second column, and so on.
                    linkColumnsOffset(0, 0)
                    linkColumnsOffset(1, 0)
                } else {
                    // There's more sectors in the new column, do the opposite
                    linkColumnsOffset(0, 0)
                    linkColumnsOffset(0, 1)
                }
            }

            sectors.add(column)
        }

        // Add sector 8
        val finalType = sectorTypes["FINAL"] ?: error("Cannot find FINAL sector for the boss fight")
        val finalSector = SectorInfo(7, 0, finalType, SectorClass.HOSTILE)
        sectors.add(listOf(finalSector))

        // ... and link up all the sectors from the previous column, so it's accessible
        for (sector in sectors[sectors.size - 2]) {
            sector.nextSectors += finalSector
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

    fun generateSector(sectorInfo: SectorInfo): Sector {
        val eventPool = ArrayList<Event>()
        for (ev in sectorInfo.type.events) {
            val count = ev.count.random()
            for (i in 1..count) {
                eventPool += ev.event.resolve()
            }
        }

        return Sector(sectorInfo, eventPool, specialEvents)
    }

    class SpecialEvents(
        /**
         * The very first event that appears in the first beacon of the game.
         */
        val startGame: IEvent,

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

    /**
     * This stores the information about an available sector as it appears
     * on the sector map.
     */
    inner class SectorInfo(
        /**
         * The 0-7 index of this column throughout the game - this sets
         * the sector's X position.
         */
        val columnNumber: Int,

        /**
         * The index of this sector within it's column - this sets it's Y position.
         *
         * Row doesn't seem like the appropriate word since they're not
         * in a grid, but it's pretty much that.
         */
        val columnIndex: Int,

        /**
         * The exact type of sector.
         */
        var type: SectorType,

        val sectorClass: SectorClass
    ) {
        /**
         * The sectors the player can jump to from this one.
         */
        val nextSectors = ArrayList<SectorInfo>()

        // Note we don't store the actual Sector object here - that's
        // only needed when the player is inside it, so it can be stored
        // separately and discarded when not in use to save memory.
    }

    /**
     * The broad types of sector, which determines the colour of the sector on the map.
     */
    enum class SectorClass {
        CIVILIAN,
        HOSTILE,
        NEBULA;

        companion object {
            fun random(rand: Random): SectorClass {
                // 20% chance of a nebula
                if (rand.nextInt(5) == 0) {
                    return NEBULA
                }

                // Otherwise, 60% civilian and 40% hostile
                return if (rand.nextInt(10) < 4) HOSTILE else CIVILIAN
            }
        }
    }
}
