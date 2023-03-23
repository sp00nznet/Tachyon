package xyz.znix.xftl.sector

import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.StoreData
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
    val event: Event
) {

    /**
     * Without long-range scanners, what should this beacon be displayed as?
     *
     * This stores the state of the beacon as the player left it, so they can see if they've previously visited
     * a beacon and if so whether they jumped out of a fight.
     */
    val state: State
        get() = when {
            !visited -> State.UNVISITED
            ship != null -> State.VISITED_DANGER
            else -> State.VISITED_CLEAR
        }

    val environmentType: EnvironmentType get() = event.environment ?: EnvironmentType.NORMAL

    /**
     * The sector this beacon resides within.
     *
     * It can only be set once, via [bindSector], and will throw an exception if used before it is set.
     */
    lateinit var sector: Sector
        private set

    /**
     * The beacons connected this beacon on the star map, to which the player can jump from here.
     */
    lateinit var neighbours: List<Beacon>
        private set

    /**
     * The ship remaining here. Either the player jumped off while fighting it, or it was/became
     * neutral from an event. Currently only the former is supported, when coming back the ship
     * will be gone.
     */
    var ship: Ship? = null

    /**
     * Has this beacon been previously visited?
     */
    var visited: Boolean = false

    /**
     * Is a store available at this beacon? This isn't the same as event.isStore, as events
     * where a store becomes available as a result of player actions (eg a quest to save
     * a store from the rebels, or saving a merchant that was under attack by pirates) can
     * reveal a store.
     */
    val hasStore: Boolean get() = store != null

    var store: StoreData? = if (event.isStore) StoreData() else null

    fun bindSector(sector: Sector, neighbours: List<Beacon>) {
        synchronized(this) {
            check(!this::sector.isInitialized) { "Sector already set!" }
            this.sector = sector
            this.neighbours = neighbours
        }
    }

    enum class State {
        UNVISITED,
        VISITED_CLEAR,
        VISITED_DANGER,
    }

    enum class EnvironmentType(val backgroundName: String?, val isDangerous: Boolean) {
        NORMAL(null, false),
        ASTEROID("low_asteroid", true),
        SUN("low_sun", true),
        PULSAR("low_pulsar", true),
        NEBULA("low_nebula", false),
        ION_STORM("low_storm", false);

        // TODO how should we represent PDS/ABSes, given they can be targed at the player or enemy (or both?)
    }
}
