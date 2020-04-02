package xyz.znix.xftl.sector

import org.jdom2.Element

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
