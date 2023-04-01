package xyz.znix.xftl.sector

import org.jdom2.Element
import org.newdawn.slick.Image
import xyz.znix.xftl.game.*
import xyz.znix.xftl.requireAttributeValue

// Many of the comments in this file came from ftlwiki.com (now unfortunately
// offline, but accessable via the wayback machine):
// https://ftlwiki.com/wiki/Events_file_structure

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

    val itemsModifySteal: Boolean
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
            itemsModifySteal = false
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

class Choice(val text: IEventText, lazyEvent: Lazy<IEvent>, elem: Element) {
    /**
     * Event to be triggered when this choice is taken. This element is required. One of:
     * A complete event, as detailed above (which can again contain choices).
     * Empty with just the load attribute given: loads the event with the given id.
     * Completely empty (<element />), if you want nothing to happen at all.
     */
    val event by lazyEvent

    /**
     * true/false, false if omitted. When set to false or omitted, it causes rewards (fuel, drone parts, scrap, etc)
     * in the choice's event (specified by <item_modify> or <autoReward>) to appear next to the choice's text.
     * See req for behavior of hidden when used alongside req.
     */
    val hidden: Boolean = elem.getAttributeValue("hidden")?.toBoolean() ?: false

    /**
     * The name of any race, weapon, drone, augmentation or system/subsystem.
     *
     * When hidden is set to true, the choice will be visible, able to be selected, and will be a blue choice
     *   (unless blue is set to false), if and only if the player has whatever is specified by the req, otherwise
     *   it will not be shown (hidden).
     * When hidden is omitted or set to false and the player does not have the listed req, the player will be able
     *   to see the choice but will be unable to select it (it will be grayed out).
     */
    val req: String? = elem.getAttributeValue("req")

    /**
     * true/false, true if omitted. Determines whether the choice will appear as (literally) a blue choice.
     * Only has meaning when used alongside req, as only req can make the choice blue in the first place.
     */
    val blue: Boolean = elem.getAttributeValue("blue")?.toBoolean() ?: (req != null)
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
