package xyz.znix.xftl.sector

import xyz.znix.xftl.math.ConstPoint

/**
 * Represents a single beacon on the star map.
 */
class Beacon(
        /**
         * The beacon's position on the sector map
         */
        val pos: ConstPoint,

        /**
         * The event at this beacon. Even empty beacons have events, which set stuff like
         * the background and flavour text.
         */
        val event: Event) {

    /**
     * Without long-range scanners, what should this beacon be displayed as?
     *
     * This stores the state of the beacon as the player left it, so they can see if they've previously visited
     * a beacon and if so whether they jumped out of a fight.
     */
    var state = State.UNKNOWN

    var environmentType = EnvironmentType.NORMAL

    enum class State {
        UNKNOWN,
        VISITED_CLEAR,
        VISITED_DANGER,
    }

    enum class EnvironmentType {
        NORMAL,
        ASTEROID,
        SUN,
        PULSAR,
        NEBULA,
        ION_STORM,

        // TODO how should we represent PDS/ABSes, given they can be targed at the player or enemy (or both?)
    }
}
