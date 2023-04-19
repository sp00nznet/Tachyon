package xyz.znix.xftl.sector

import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import java.util.*

/**
 * Represents an in-game sector. Handles the placement of the beacons within it.
 */
class Sector(
    val info: GameMap.SectorInfo,

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

    specialEvents: GameMap.SpecialEvents
) {
    /**
     * The zero-indexed position of this sector in the sector map. Zero is the starting
     * sector, 7 is the last stand.
     */
    val sectorNumber: Int get() = info.columnNumber

    val type: SectorType get() = info.type

    val beacons = ArrayList<Beacon>()

    // The start and finish beacon. The finish beacon will only
    // be null in the final sector.
    val startBeacon: Beacon
    val finishBeacon: Beacon?

    // The position of the centre of the danger zone circle.
    // See doc/sector-map (heading 'Rebel fleet advance') for more
    // information about this.
    val dangerZoneCentre: Point

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

        var tmpStartBeacon: Beacon? = null
        var tmpFinishBeacon: Beacon? = null

        val beaconCount = GRID_SIZE.x * GRID_SIZE.y - rand.nextInt(3)
        for (i in 1..beaconCount) {
            val gridPos = grid.random()
            grid.remove(gridPos)
            val pos = Point(gridPos.x * CELL_SIZE.x, gridPos.y * CELL_SIZE.y)
            pos += OFFSET
            pos.x += rand.nextInt(CELL_SIZE.x)
            pos.y += rand.nextInt(CELL_SIZE.y)

            var event: Event? = null
            var isFinish = false
            var isStart = false

            // If we're at the right-most side of the map, add an exit beacon.
            // Since we're using the grid cells in a random order, we can
            // just add this in the first time we see such a beacon.
            // TODO don't place this in the final sector.
            if (gridPos.x == GRID_SIZE.x - 1 && tmpFinishBeacon == null) {
                event = specialEvents.exit.resolve()
                isFinish = true
            }

            // Same goes for the start beacon
            if (gridPos.x == 0 && tmpStartBeacon == null) {
                event = type.startEvent.resolve()
                isStart = true

                // If this is the very first beacon in the game, it's replaced
                // with the START_GAME event.
                if (sectorNumber == 0) {
                    event = specialEvents.startGame.resolve()
                }
            }

            // Otherwise, just use a standard beacon from our event pool - or
            // if we ran out of those, use a filler event.
            if (event == null) {
                event = eventPool.pollFirst() ?: specialEvents.filler.resolve()
            }

            val beacon = Beacon(pos.const, event, isFinish)
            beacons += beacon
            cells[beacon] = gridPos

            if (isFinish)
                tmpFinishBeacon = beacon
            if (isStart)
                tmpStartBeacon = beacon
        }

        for (b in beacons) {
            val neighbours = beacons.filter { cells[it]!!.distToSq(cells[b]!!) <= 2 && it != b }
            b.bindSector(this, neighbours)
        }

        startBeacon = tmpStartBeacon ?: error("Failed to place a starting beacon!")
        finishBeacon = tmpFinishBeacon

        dangerZoneCentre = Point(-959, rand.nextInt(50, 300))
    }

    fun getFleetAdvanceFor(beacon: Beacon): Int {
        val isNebula = beacon.environmentType.isNebula

        return when {
            // Non-nebula beacons use normal advance
            !isNebula -> DANGER_ZONE_ADVANCE

            // Nebula beacons in normal sectors use half the normal advance
            info.sectorClass != GameMap.SectorClass.NEBULA -> DANGER_ZONE_ADVANCE / 2

            // In nebula beacons, they use 80% of the normal advance.
            else -> (DANGER_ZONE_ADVANCE * 0.8f).toInt()
        }
    }

    companion object {
        val OFFSET = ConstPoint(40, 35)
        var GRID_SIZE = ConstPoint(6, 4)
        val CELL_SIZE = ConstPoint(110, 100)

        const val DANGER_ZONE_RADIUS = 767
        const val DANGER_ZONE_RADIUS_SQUARED = DANGER_ZONE_RADIUS * DANGER_ZONE_RADIUS

        // The amount the x position of the danger zone increases by
        // per jump in a normal sector.
        const val DANGER_ZONE_ADVANCE = 64
    }
}
