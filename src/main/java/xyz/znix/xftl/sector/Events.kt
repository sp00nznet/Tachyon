package xyz.znix.xftl.sector

import org.jdom2.Element
import org.newdawn.slick.Image
import xyz.znix.xftl.game.*
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

class Event(
    val text: IEventText?, val choices: List<Choice>, elem: Element, val debugId: String,
    imageFinder: (String) -> ImageList
) : IEvent {
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

    /**
     * Evaluate all the random values in this event's rewards, and pack them into a ResourceSet
     */
    fun resolveResources(game: SlickGame): ResourceSet {
        // The plain resources (fuel, missiles, etc)
        val resourcesGained = ResourceSet(itemsModify.mapValues { it.value.random() })

        // Add all the blueprints
        resourcesGained.items += blueprintRewards.map { name ->
            when (name) {
                "xftl_rand_weapon" -> game.lootPool.getWeapon()
                "xftl_rand_drone" -> TODO()
                "xftl_rand_augment" -> TODO()
                else -> game.blueprintManager[name].resolve()
            }
        }

        // Add the standard type/tier rewards - these are the standard results and most commonly used
        // eg destroying a ship usually gives STANDARD/MEDIUM rewards.
        if (autoRewards != null) {
            val sector = game.currentBeacon.sector.sectorNumber + 1
            val rewards = LootDropGenerator.generateRewards(autoRewards.second, autoRewards.first, sector)
            resourcesGained += rewards
        }

        return resourcesGained
    }
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
