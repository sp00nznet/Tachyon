package xyz.znix.xftl.sector

import org.jdom2.Element
import org.newdawn.slick.Image
import xyz.znix.xftl.game.SlickGame

/**
 * Represents something that can be used as an event. This is either an Event or
 * an EventList.
 *
 * When this is to be used, [resolve] is called which provides a concrete event.
 */
interface IEvent {
    fun resolve(): Event
}

class Event(val text: IEventText?, val choices: List<Choice>, elem: Element, val debugId: String) : IEvent {
    val isDistressBeacon: Boolean = elem.getChild("distressBeacon") != null
    val isStore: Boolean = elem.getChild("store") != null

    val itemsModifySteal: Boolean?
    val itemsModify: Map<Resource, IntRange>

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

typealias ResourceSet = Map<Resource, Int>
