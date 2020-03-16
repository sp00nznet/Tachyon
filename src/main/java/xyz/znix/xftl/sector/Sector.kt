package xyz.znix.xftl.sector

import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import java.util.*
import kotlin.collections.ArrayList

/**
 * Represents an in-game sector. Handles the placement of the beacons within it.
 */
class Sector(val type: SectorType,
             /**
              * The list of events that should be used in this sector. Each beacon will be assigned an event from
              * this list, but no two beacons will share an event (unless that event appears twice in the list).
              *
              * This assignment is random, and the order of the items in the list does not matter. If there are
              * more events in the list than beacons in the sector, a random set of the events will go unused.
              *
              * If the list of events is shorter than the number of beacons we generate, those beacons will all
              * have the filler event.
              */
             events: List<Event>,

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
             filler: IEvent) {

    val beacons = ArrayList<Beacon>()

    init {
        val eventPool = ArrayDeque(events.shuffled())

        val rand = Random()

        // Generate a random 6x6 grid. Each beacon to be placed will be offset from one position on the grid.
        val grid = ArrayList<IPoint>()
        for (x in 0..5) {
            for (y in 0..5) {
                val pnt = Point(x, y)
                pnt.x *= STARMAP_SIZE.x / 5
                pnt.y *= STARMAP_SIZE.y / 5
                grid += pnt
            }
        }

        for (i in 1..24) {
            val pos = findPositionFor(rand, grid)

            val event = eventPool.pollFirst() ?: filler.resolve()
            beacons += Beacon(pos, event)
        }

        for (b in beacons) {
            val neighbours = findNeighboursFor(b.pos).map { it.first }.filter { it != b }
            b.bindSector(this, neighbours)
        }
    }

    /**
     * Find a suitable position for the next beacon.
     *
     * This takes a list of grid positions, picks one, and adds a random offset. It tests if that position
     * is valid, and if not it tries over and over again until a suitable position is found.
     *
     * FIXME the layouts from this are completely useless for a game map, due to large disconnected areas.
     */
    private fun findPositionFor(rand: Random, grid: MutableList<IPoint>): ConstPoint {
        fun signed(limit: Int) = rand.nextInt(limit * 2) - limit

        // For now hardcode the first beacon position
        if (beacons.isEmpty()) {
            return ConstPoint(50, 50)
        }

        // The distance between the beacons and the edges of the maps
        val margin = 25

        val limitSq = MAX_BEACON_REACH * MAX_BEACON_REACH
        val minSq = MIN_BEACON_REACH * MIN_BEACON_REACH

        while (true) {
            val gridPos = grid.random()
            val pos = gridPos + ConstPoint(signed(150), signed(150))

            // Check if the point is off the screen
            if (pos.x < margin || pos.y < margin)
                continue

            if (pos.x > STARMAP_SIZE.x - margin || pos.y > STARMAP_SIZE.y - margin)
                continue

            // Find all the 'neighbours' - these are any beacons within MAX_BEACON_REACH
            val neighbours = findNeighboursFor(pos)

            // We then have a minimum distance, within which it's impossible to have multiple beacons - this stops
            // them from clipping into each other.
            if (neighbours.any { it.second < minSq })
                continue

            // And apply a 1 in neighbourCount² change to successfully place this beacon - one neighbour means
            // we have a 50% chance of placing the node, two means a 25% chance, three is ~12% and so on. This
            // greatly decreases the chance of a heavy cluster of nodes.
            if (neighbours.isNotEmpty() && rand.nextInt(neighbours.size * neighbours.size) != 0)
                continue

            // Remove the candidate position from the list (to keep everything spaced out)
            check(grid.remove(gridPos))
            return pos
        }
    }

    private fun findNeighboursFor(pos: IPoint): List<Pair<Beacon, Int>> {
        val maxDist = MAX_BEACON_REACH * MAX_BEACON_REACH
        return beacons.map { Pair(it, pos.distToSq(it.pos)) }.filter { it.second < maxDist }.toList()
    }

    companion object {
        const val MAX_BEACON_REACH = 350
        const val MIN_BEACON_REACH = 35
        val STARMAP_SIZE = ConstPoint(752, 548) // TODO does this need resizing?
    }
}
