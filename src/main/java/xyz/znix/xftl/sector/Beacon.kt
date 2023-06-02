package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.StoreData
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.savegame.ISerialReferencable
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import kotlin.random.Random

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
    var event: Event,

    /**
     * True if this is the exit beacon to progress to the next sector.
     */
    val isExit: Boolean,

    /**
     * True if this is the location of the rebel base, in the last stand.
     */
    val isBase: Boolean
) : ISerialReferencable {

    /**
     * Without long-range scanners, what should this beacon be displayed as?
     *
     * This stores the state of the beacon as the player left it, so they can see if they've previously visited
     * a beacon and if so whether they jumped out of a fight.
     */
    val state: State
        get() = when {
            isOvertaken -> State.OVERTAKEN
            !visited -> State.UNVISITED
            ship != null -> State.VISITED_DANGER
            else -> State.VISITED_CLEAR
        }

    val environmentType: EnvironmentType
        get() {
            val type = event.environment ?: EnvironmentType.NORMAL
            return when {
                !isOvertaken -> type

                // If this beacon has been overtaken, clear any special events,
                // though preserving the nebula-ness of this beacon.
                type == EnvironmentType.NEBULA || type == EnvironmentType.ION_STORM -> EnvironmentType.NEBULA
                else -> EnvironmentType.NORMAL
            }
        }

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
    var hasStore: Boolean = event.isStore

    private var internalStore: StoreData? = null

    /**
     * True if the event at this beacon was created by an event.
     *
     * This is used to display the quest label in the map screen,
     * if this beacon is unvisited.
     */
    var hasQuest: Boolean = false

    /**
     * For all the status effects applied at this beacon, this states what
     * the final power limit is for each system by name.
     *
     * Enemy ship effects are left in place and never cleared, while the player
     * can jump between beacons and these need to be preserved.
     */
    val powerLimitEffects = HashMap<String, Int>()

    /**
     * This is a random value between 0-1 that's used to offset
     * the timings of the 'this beacon will be overtaken' flash
     * animation on the beacon map, to ensure all the beacons
     * aren't flashing in sync.
     */
    val overtakeFlashAnimationOffset: Float = Random.nextFloat()

    var isOvertaken: Boolean = false
        set(value) {
            field = value
            if (value) {
                hasStore = false
                hasQuest = false
            }
        }

    private var backgroundImageIndex: Int = Random.nextInt(999)
    private var planetImageIndex: Int = Random.nextInt(999)

    fun getStore(game: InGameState): StoreData? {
        if (!hasStore)
            return null
        if (internalStore != null)
            return internalStore

        internalStore = StoreData(game)
        return internalStore
    }

    fun bindSector(sector: Sector, neighbours: List<Beacon>) {
        synchronized(this) {
            check(!this::sector.isInitialized) { "Sector already set!" }
            this.sector = sector
            this.neighbours = neighbours
        }
    }

    /**
     * Get the image to use for the planet and background (in that order in the pair).
     */
    fun getEnvironmentImages(game: InGameState): Pair<EnvironmentImage?, EnvironmentImage> {
        // We have three goals here:
        // 1. If the environment or event changes, the background should too.
        // 3. We can't be random here, we need to deserialise to the same values each time.
        // 3. Make the planet/background information easy to serialise.
        // Thus pick the image list for the planet and background deterministically,
        // and serialise the index into that list. If the list changes and the index
        // becomes invalid, we can just pick a new one.
        // This does have the limitation that if the event changes to one with a new
        // image list that's larger than the previous one we won't be able to access
        // all it's images. To get around it, we actually serialise a large random number
        // which we then use as an index into the image list, modulo the list's size.

        var backgroundList: ImageList = game.eventManager.getImageList("BACKGROUND")
        var planetList: ImageList = game.eventManager.getImageList("PLANET")

        val bgName = environmentType.backgroundName
        if (bgName != null) {
            val backgroundImg = EnvironmentImage("img/stars/$bgName.png")
            return Pair(null, backgroundImg)
        }

        event.backImg?.let { backgroundList = it }
        event.planetImg?.let { planetList = it }

        val backImg = backgroundList.getRandom(backgroundImageIndex)
        val planetImg = planetList.getRandom(planetImageIndex)

        requireNotNull(backImg) { "Cannot set NONE as a background image with event ${event.deserialisationId}" }

        // TODO show the rebel fleet in the background if we're at an overtaken beacon
        // TODO show the flagship rebel/fed mixed fight backgrounds

        return Pair(planetImg, backImg)
    }

    fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addObjectId(elem, refs, this)

        SaveUtil.addAttrInt(elem, "x", pos.x)
        SaveUtil.addAttrInt(elem, "y", pos.y)

        SaveUtil.addAttrBool(elem, "visited", visited)
        SaveUtil.addTagBoolIfTrue(elem, "isExit", isExit)
        SaveUtil.addTagBoolIfTrue(elem, "isBase", isBase)
        SaveUtil.addTagBoolIfTrue(elem, "hasStore", hasStore)
        SaveUtil.addTagBoolIfTrue(elem, "hasQuest", hasQuest)

        // Save the event by name - events are immutable and are
        // loaded from the game's XML, so we only need to uniquely
        // identify them, not store all their data.
        SaveUtil.addAttr(elem, "eventId", event.deserialisationId)

        // See getEnvironmentImages for more information on these.
        SaveUtil.addAttrInt(elem, "planetImg", planetImageIndex)
        SaveUtil.addAttrInt(elem, "backImg", backgroundImageIndex)

        // TODO save the store data

        // Save the power limits
        for ((system, limit) in powerLimitEffects) {
            val powerLimit = Element("powerLimit")
            powerLimit.setAttribute("system", system)
            powerLimit.setAttribute("limit", limit.toString())
            elem.addContent(powerLimit)
        }

        // If there's a ship at this beacon, save it.
        // This is how the ship the player is fighting is loaded.
        if (ship != null) {
            val shipElem = Element("enemyShip")
            ship!!.saveToXML(shipElem, refs)
            elem.addContent(shipElem)
        }

        // Note we don't save the neighbours - the sector does that using a more
        // compact encoding than what we can easily use.
    }

    companion object Deserialiser {
        fun loadFromXML(elem: Element, refs: RefLoader, game: InGameState): Beacon {
            // To create our beacon, we need to load a few things first.
            // Everything else goes in mutable variables, so we can set them later.
            val eventId = SaveUtil.getAttr(elem, "eventId")
            val event = game.eventManager.getByDeserialisationId(eventId)
            val isExit = SaveUtil.getOptionalTagBool(elem, "isExit") ?: false
            val isBase = SaveUtil.getOptionalTagBool(elem, "isBase") ?: false

            val pos = ConstPoint(
                SaveUtil.getAttrInt(elem, "x"),
                SaveUtil.getAttrInt(elem, "y")
            )

            // This gets us all the information we need to create and start
            // populating our beacon.
            val beacon = Beacon(pos, event, isExit, isBase)
            SaveUtil.registerObjectId(elem, refs, beacon)

            beacon.visited = SaveUtil.getAttrBool(elem, "visited")
            beacon.hasStore = SaveUtil.getOptionalTagBool(elem, "hasStore") ?: false
            beacon.hasQuest = SaveUtil.getOptionalTagBool(elem, "hasQuest") ?: false

            beacon.planetImageIndex = SaveUtil.getAttrInt(elem, "planetImg")
            beacon.backgroundImageIndex = SaveUtil.getAttrInt(elem, "backImg")

            // Deserialise the enemy ship, if present
            val shipElem = elem.getChild("enemyShip")
            beacon.ship = shipElem?.let { game.deserialiseSingleShip(it, refs) }

            for (powerLimit in elem.getChildren("powerLimit")) {
                val system = powerLimit.getAttributeValue("system")
                val limit = powerLimit.getAttributeValue("limit").toInt()
                beacon.powerLimitEffects[system] = limit
            }

            return beacon
        }
    }

    enum class State {
        UNVISITED,
        VISITED_CLEAR,
        VISITED_DANGER,

        /**
         * Overtaken by the rebel fleet.
         */
        OVERTAKEN,
    }

    enum class EnvironmentType(val backgroundName: String?, val isDangerous: Boolean) {
        NORMAL(null, false),
        ASTEROID("low_asteroid", true),
        SUN("low_sun", true),
        PULSAR("low_pulsar", true),
        NEBULA("low_nebula", false),
        ION_STORM("low_storm", false);

        // TODO how should we represent PDS/ABSes, given they can be targed at the player or enemy (or both?)

        val isNebula: Boolean get() = this == NEBULA || this == ION_STORM
    }
}
