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
        for (x in 0 until GRID_SIZE.x) {
            for (y in 0 until GRID_SIZE.y) {
                grid += Point(x, y)
            }
        }

        // See https://twitter.com/subsetgames/status/1234309658854084608
        // In vanilla FTL, the map is split into a 6x4 grid, each cell may or
        // may not contain a beacon.

        val cells = HashMap<Beacon, IPoint>()

        val beaconCount = GRID_SIZE.x * GRID_SIZE.y - rand.nextInt(3)
        for (i in 1..beaconCount) {
            val gridPos = grid.random()
            grid.remove(gridPos)
            val pos = Point(gridPos.x * CELL_SIZE.x, gridPos.y * CELL_SIZE.y)
            pos += OFFSET
            pos.x += rand.nextInt(CELL_SIZE.x)
            pos.y += rand.nextInt(CELL_SIZE.y)

            val event = eventPool.pollFirst() ?: filler.resolve()
            val beacon = Beacon(pos.const, event)
            beacons += beacon
            cells[beacon] = gridPos
        }

        for (b in beacons) {
            val neighbours = beacons.filter { cells[it]!!.distToSq(cells[b]!!) <= 2 }
            b.bindSector(this, neighbours)
        }
    }

    companion object {
        val OFFSET = ConstPoint(40, 35)
        var GRID_SIZE = ConstPoint(6, 4)
        val CELL_SIZE = ConstPoint(110, 100)
    }
}
