package xyz.znix.xftl.sector

import org.jdom2.Element
import org.newdawn.slick.Image
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.game.RewardTier
import xyz.znix.xftl.game.RewardType
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.requireAttributeValue

/**
 * Represents something that can be used as an event. This is either an Event or
 * an EventList.
 *
 * When this is to be used, [resolve] is called which provides a concrete event.
 */
interface IEvent {
    fun resolve(): Event
}

class Event(val text: IEventText?, val choices: List<Choice>, elem: Element, val debugId: String,
            imageFinder: (String) -> ImageList) : IEvent {
    val isDistressBeacon: Boolean = elem.getChild("distressBeacon") != null
    val isStore: Boolean = elem.getChild("store") != null

    val itemsModifySteal: Boolean?
    val itemsModify: Map<Resource, IntRange>
    val autoRewards: Pair<RewardType, RewardTier>?
    val blueprintRewards: List<String>

    init {
        val modElem = elem.getChild("item_modify")
        if (modElem != null) {
            itemsModifySteal = modElem.getAttributeValue("steal")?.toBoolean() ?: false
            val items = HashMap<Resource, IntRange>()
            itemsModify = items

            for (child in modElem.children) {
                check(child.name == "item")
                val type = Resource.byName(child.getAttributeValue("type"))
                val min = child.getAttributeValue("min").toInt()
                val max = child.getAttributeValue("max").toInt()
                items[type] = min..max
            }
        } else {
            itemsModifySteal = null
            itemsModify = emptyMap()
        }

        val auto = elem.getChild("autoReward")
        autoRewards = if (auto != null) {
            val type = RewardType.valueOf(auto.textTrim.toUpperCase())
            val tier = RewardTier.fromName(auto.getAttributeValue("level"))
            Pair(type, tier)
        } else {
            null
        }

        blueprintRewards = ArrayList()
        for (e in listOf("weapon", "drone", "augment").flatMap(elem::getChildren)) {
            val name = e.requireAttributeValue("name")
            check(e.children.size == 0)
            blueprintRewards += if (name == "RANDOM") "xftl_rand_${e.name.toLowerCase()}" else name
        }
        blueprintRewards.trimToSize()
    }

    val loadShipName: String?
    val loadShipHostile: Boolean?

    init {
        val ship = elem.getChild("ship")
        if (ship != null) {
            check(ship.children.isEmpty()) { "Inline ship specs not yet supported" }
            loadShipName = ship.getAttributeValue("load")
            loadShipHostile = ship.getAttributeValue("hostile")?.toBoolean()
        } else {
            loadShipName = null
            loadShipHostile = null
        }
    }

    val backImg: ImageList?
    val planetImg: ImageList?

    init {
        val img = elem.getChild("img")
        backImg = img?.getAttributeValue("back")?.let(imageFinder)
        planetImg = img?.getAttributeValue("planet")?.let(imageFinder)
    }

    val environment: Beacon.EnvironmentType?

    init {
        environment = when (val env = elem.getChild("environment")?.requireAttributeValue("type")) {
            "asteroid" -> Beacon.EnvironmentType.ASTEROID
            "nebula" -> Beacon.EnvironmentType.NEBULA
            "pulsar" -> Beacon.EnvironmentType.PULSAR
            "storm" -> Beacon.EnvironmentType.ION_STORM
            "sun" -> Beacon.EnvironmentType.SUN
            "PDS" -> null // TODO implement PDS/ASBs
            null -> null
            else -> error("Unknown environment $env")
        }
    }

    override fun resolve() = this
}

class EventList(val name: String, events: List<Lazy<IEvent>>) : IEvent {
    val events by lazy { events.map { it.value } }

    override fun resolve() = events.random().resolve()
}

class Choice(val text: IEventText, lazyEvent: Lazy<IEvent>, val blue: Boolean) {
    val event by lazyEvent
}

interface IEventText {
    fun resolve(): String
}

class TextList(val name: String, val items: List<IEventText>) : IEventText {
    override fun resolve(): String = items.random().resolve()
}

class EventText(val localised: String) : IEventText {
    override fun resolve(): String = localised
}

class ImageList(val name: String, val images: List<String>) {
    fun get(game: SlickGame): Image? {
        if (this == NONE) return null
        return game.getImg(images.random())
    }

    companion object {
        // The special image list 'NONE' can be specified to remove an inhertied image
        val NONE = ImageList("NONE", emptyList())
    }
}

enum class Resource {
    FUEL,
    MISSILES,
    DRONES,
    SCRAP;

    private var image: Image? = null

    fun getIcon(game: SlickGame): Image {
        image?.let { return it }

        val img = game.getImg("img/ui_icons/icon_${name.toLowerCase()}.png")
        image = img
        return img
    }

    companion object {
        fun byName(name: String): Resource = when (name.toLowerCase()) {
            "fuel" -> FUEL
            "missiles" -> MISSILES
            "drones" -> DRONES
            "scrap" -> SCRAP

            // ... and then 'missile' is used once by ROCK_STARSHIP_MINE
            "missile" -> MISSILES

            else -> throw IllegalArgumentException("Invalid resource name $name")
        }
    }
}

class ResourceSet() : Map<Resource, Int> {
    var fuel: Int = 0
    var missiles: Int = 0
    var droneParts: Int = 0
    var scrap: Int = 0
    val items = ArrayList<Blueprint>()

    constructor(basicResources: Map<Resource, Int>) : this() {
        for ((k, v) in basicResources) {
            this[k] = v
        }
    }

    override val entries: Set<Map.Entry<Resource, Int>>
        get() = keys.map { res ->
            object : Map.Entry<Resource, Int> {
                override val key: Resource get() = res
                override val value: Int get() = this@ResourceSet[res]!!
            }
        }.toSet()

    override val keys: Set<Resource>
        get() {
            val hs = HashSet<Resource>(size)
            if (fuel != 0) hs.add(Resource.FUEL)
            if (missiles != 0) hs.add(Resource.MISSILES)
            if (droneParts != 0) hs.add(Resource.DRONES)
            if (scrap != 0) hs.add(Resource.SCRAP)
            return hs
        }

    override val size: Int
        get() {
            var count = 0
            if (fuel != 0) count++
            if (missiles != 0) count++
            if (droneParts != 0) count++
            if (scrap != 0) count++
            return count
        }

    override fun containsKey(key: Resource): Boolean = this[key] != null

    override operator fun get(key: Resource): Int? {
        val value = when (key) {
            Resource.FUEL -> fuel
            Resource.MISSILES -> missiles
            Resource.DRONES -> droneParts
            Resource.SCRAP -> scrap
        }

        return if (value == 0) null else value
    }

    operator fun set(key: Resource, value: Int) {
        when (key) {
            Resource.FUEL -> fuel = value
            Resource.MISSILES -> missiles = value
            Resource.DRONES -> droneParts = value
            Resource.SCRAP -> scrap = value
        }
    }

    fun remove(key: Resource) = set(key, 0)

    override fun isEmpty(): Boolean = size == 0

    // Neither of these make sense for this class
    override val values: Collection<Int> get() = error("Not supported, not very useful")
    override fun containsValue(value: Int): Boolean = error("Unimplemented")

    operator fun plusAssign(other: ResourceSet) {
        this.fuel += other.fuel
        this.scrap += other.scrap
        this.droneParts += other.droneParts
        this.missiles += other.missiles
    }

    companion object {
        fun of(type: Resource, count: Int) = ResourceSet().apply { this[type] = count }
    }
}
