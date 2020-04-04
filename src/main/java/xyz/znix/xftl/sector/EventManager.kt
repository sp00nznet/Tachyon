package xyz.znix.xftl.sector

import org.jdom2.Document
import org.jdom2.Element
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Translator
import xyz.znix.xftl.requireAttributeValue
import xyz.znix.xftl.shipgen.EnemyShipSpec

class EventManager(df: Datafile, private val translator: Translator, private val bp: BlueprintManager) {
    private val events = HashMap<String, IEvent>()
    private val textLists = HashMap<String, TextList>()
    private val ships = HashMap<String, EnemyShipSpec>()

    init {
        for (event in FILE_NAMES) {
            loadEvents(df.parseXML(df["data/$event.xml"]), true)
        }
        for (event in FILE_NAMES) {
            loadEvents(df.parseXML(df["data/$event.xml"]), false)
        }
    }

    operator fun get(name: String): IEvent = events[name] ?: error("Missing event $name")

    fun getShip(name: String): EnemyShipSpec = ships[name] ?: error("Missing enemy ship spec '$name'")

    private fun loadEvents(doc: Document, resourcePass: Boolean) {
        val root = doc.rootElement
        check(root.name == "FTL")

        for (elem in root.children) {
            // Everything is loaded in two passes: once for the textlists/images (later is not yet implemented), and
            // a second time to load the events (which reference the resources).
            if (resourcePass) {
                when (elem.name) {
                    "textList" -> loadTextList(elem)
                }
            } else {
                when (elem.name) {
                    "textList" -> Unit // Handled in the resource pass
                    "eventList" -> loadEventList(elem)
                    "ship" -> loadShip(elem)
                    "eventCounts" -> loadEventCounts(elem)
                    "event" -> {
                        val name = elem.requireAttributeValue("name")
                        if (eventCheck(name, false))
                            events[name] = loadEvent(elem, name).value
                    }
                    else -> error("Unknown eventfile item ${elem.name}")
                }
            }
        }
    }

    private fun loadEventList(elem: Element) {
        check(elem.name == "eventList")
        val name = elem.requireAttributeValue("name")
        val events = elem.children.withIndex().map { loadEvent(it.value, "$name.${it.index}") }
        if (!eventCheck(name, true)) return
        this.events[name] = EventList(name, events)
    }

    private fun eventCheck(name: String, eventList: Boolean): Boolean {
        if (this.events.containsKey(name))
            println("Found duplicate event $name")

        // TODO make sure I've set these up right - I'm not completely sure about this, we could be losing events
        when (name) {
            "NEBULA_PIRATE" -> return !eventList // The individual event for this is definitely a testing event
            "BOARDERS_PIRATE" -> return eventList
            "NEBULA_REBEL" -> return !eventList
        }

        check(!this.events.containsKey(name))

        return true
    }

    private fun loadTextList(elem: Element) {
        check(elem.name == "textList")
        val name = elem.requireAttributeValue("name")
        val texts = elem.children.map { loadText(it) }
        textLists[name] = TextList(name, texts)
    }

    private fun loadEventCounts(elem: Element) {
        check(elem.name == "eventCounts")

        // It appears this element is unused. It does not appear in the game binary. It looks
        // like it used to hold the the likelihood of events per sector (1..7), but that's now
        // stored in the sector type definitions (so a sector 4 nebula will have different contents
        // to an engi-controlled sector 4)
    }

    private fun loadShip(elem: Element) {
        check(elem.name == "ship")

        // These two are weird and don't have autoBlueprint attributes
        when (elem.getAttributeValue("name")) {
            "TUTORIAL_PIRATE", "IMPOSSIBLE_PIRATE" -> return
        }

        val ship = EnemyShipSpec(elem, bp)
        ships[ship.name] = ship
    }

    private fun loadEvent(elem: Element, debugId: String): Lazy<IEvent> {
        check(elem.name == "event")

        elem.getAttributeValue("load")?.let {
            return lazy { events[it]!! }
        }

        val text = elem.getChild("text")?.let(::loadText)
        val choices = elem.getChildren("choice").map(::loadChoice)
        return lazyOf(Event(text, choices, elem, debugId))
    }

    private fun loadChoice(elem: Element): Choice {
        check(elem.name == "choice")
        val text = elem.getChild("text").let(::loadText)
        val event = loadEvent(elem.getChild("event"), "choice.ukn")
        val req = elem.getAttributeValue("req")
        val blue = elem.getAttributeValue("blue")?.toBoolean() ?: req != null
        return Choice(text, event, blue)
    }

    private fun loadText(elem: Element): IEventText {
        check(elem.name == "text")
        elem.getAttributeValue("load")?.let {
            return textLists[it] ?: error("Missing textList $it")
        }
        elem.getAttributeValue("id")?.let {
            return EventText(translator[it])
        }
        val text = elem.textTrim
        check(text.isNotEmpty())
        return EventText(text)
    }

    companion object {
        private val FILE_NAMES = listOf(
                "events",
                "events_boss",
                "events_crystal",
                "events_engi",
                "events_fuel",
                // "events_imageList",
                "events_mantis",
                "events_nebula",
                "events_pirate",
                "events_rebel",
                "events_rock",
                "events_ships",
                "events_slug",
                "events_zoltan",
                "nameEvents",
                "newEvents",

                "dlcEvents_anaerobic",
                // "dlcEventsOverwrite", // TODO set up overwrite support
                "dlcEvents"
        )
    }
}
