package xyz.znix.xftl.sector

import org.jdom2.Element
import xyz.znix.xftl.mapChildrenText
import xyz.znix.xftl.requireAttributeValue
import xyz.znix.xftl.requireAttributeValueInt

class SectorType(private val eventManager: EventManager, elem: Element) {
    val name: String = elem.requireAttributeValue("name")
    val soundtracks: List<String> = elem.getChild("trackList").mapChildrenText("track")
    val startEventName: String? = elem.getChildTextTrim("startEvent")
    val startEvent: IEvent? get() = startEventName?.let { eventManager[it] }

    val events: List<EventInfo> = elem.getChildren("event").map {
        val name = it.requireAttributeValue("name")
        val count = it.requireAttributeValueInt("min")..it.requireAttributeValueInt("max")
        EventInfo(name, count, eventManager[name])
    }

    init {
        // TODO store these
        val nameList = elem.getChild("nameList")
        check(nameList.children.size == 1)
    }

    // TODO rarity list - usually contains crew, seems it can contain weapons (though that's rarely used)

    class EventInfo(val name: String, val count: IntRange, val event: IEvent)
}
