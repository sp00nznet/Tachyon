package xyz.znix.xftl.devutil

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.game.ButtonImageSet
import xyz.znix.xftl.game.Buttons
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.sector.*
import xyz.znix.xftl.weapons.ShipWeaponBlueprint
import java.util.*

/**
 * A development console, to quickly do stuff like load events or get scrap.
 */
class DebugConsole(val game: SlickGame, val ship: Ship) {
    private val history = ArrayList<String>()
    private var historyCursor: Int = -1

    private var continued: ContinuedCommand? = null
    private var input: String = ""

    private val lines = ArrayList<String>()

    private val font = game.getFont("c&c")

    private var flashTimer: Float = 0f

    // Used for event searches - put them here so they persiste between
    // searches.
    var eventSearchIds = true
    var eventSearchMessageText = true
    var eventSearchOptions = true

    private val commands: List<Cmd> = listOf(
        Cmd("rich", 0, this::cmdRich, "Get a huge amount of scrap, fuel, drones, and missiles"),
        Cmd("weapon", 0, this::cmdWeapon, "Select a weapon, and add it to the ship's cargo area"),
        Cmd("store", 0, this::cmdStore, "Create a store at this beacon"),
        Cmd("event", 0, this::cmdEvent, "Load an event at this beacon"),
        Cmd("help", 0, this::cmdHelp, "Show the available commands")
    )

    private val prompt: String get() = continued?.prompt ?: PROMPT

    private val currentLine: String
        get() = if (historyCursor == -1) input
        else history[historyCursor]

    fun render(gc: GameContainer, g: Graphics) {
        val height = gc.height / 2f

        g.color = Color(127, 127, 127, 180)
        g.fillRect(0f, 0f, gc.width.f, height)

        var y = height - 6

        val fontHeight = 7
        val lineSpacing = fontHeight + 5 // Some letters are a bit outside the font height

        var inputLine = prompt + currentLine
        if (flashTimer.rem(FLASH_TIME) > FLASH_TIME / 2) {
            inputLine += "_"
        }
        font.drawString(20f, y, inputLine, Color.white)
        y -= lineSpacing + 4

        for (i in lines.size - 1 downTo 0) {
            val line = lines[i]
            font.drawString(20f, y, line, Color.white)

            y -= lineSpacing
            if (y < 0)
                break
        }

        continued?.render(gc, g, height)
    }

    fun update(@Suppress("UNUSED_PARAMETER") gc: GameContainer, dt: Float) {
        flashTimer += dt
    }

    fun keyPressed(key: Int, c: Char) {
        // Check if the continuation UI wants to handle the keypress
        if (continued?.keyPressed(key, c) == true)
            return

        when (key) {
            Input.KEY_ENTER -> {
                selectHistory()
                runCommand()
            }

            Input.KEY_UP -> {
                if (historyCursor == -1) {
                    historyCursor = history.size - 1
                } else {
                    historyCursor--
                    if (historyCursor < 0)
                        historyCursor = 0
                }
            }

            Input.KEY_DOWN -> {
                if (historyCursor != -1)
                    historyCursor++
                if (historyCursor >= history.size)
                    historyCursor = -1
            }

            Input.KEY_BACK -> {
                if (input != "") {
                    input = input.substring(0, input.length - 1)
                }
            }

            // On a desktop keyboard, delete is very close to enter so it's
            // easy to press it to clear the current line.
            Input.KEY_DELETE -> {
                historyCursor = -1
                input = ""
            }

            Input.KEY_GRAVE -> {
                // This key opens and closes the console, so ignore it to prevent
                // it ending up in commands.
            }

            else -> {
                // Ignore characters our font doesn't support, which
                // includes any odd ASCII characters that could be
                // somehow generated.
                if (!font.supportsCharacter(c))
                    return

                selectHistory()
                input += c
            }
        }
    }

    /**
     * If the user has scrolled back to a previous command, copy
     * that to the buffer.
     */
    private fun selectHistory() {
        if (historyCursor != -1) {
            input = history[historyCursor]
            historyCursor = -1
        }
    }

    private fun runCommand() {
        // Clear out the input
        val input = this.input
        this.input = ""

        // Don't add blank lines or the same command multiple times in a row
        if (history.lastOrNull() != input && input.isNotBlank())
            history.add(input)

        lines.add(prompt + input)

        // If we're part way through a command that takes multiple
        // lines of input, run that.
        continued?.let { cmd ->
            continued = null
            cmd.run(input.trim())
            return
        }

        // Ignore empty commands
        if (input.isBlank())
            return

        // Build the arguments list, ignoring subsequent spaces
        val args = input.split(' ').filter { it.isNotEmpty() }
        val command = args[0]

        val cmd = commands.firstOrNull { it.name == command }
        if (cmd == null) {
            lines.add("Unknown command '${command}', see the help command")
            return
        }

        val numArgs = args.size - 1 // Exclude the command itself
        if (cmd.argCount != null && cmd.argCount != numArgs) {
            lines.add("Command '${command}' takes ${cmd.argCount} arguments, but $numArgs were supplied.")
            return
        }

        cmd.func(args)
    }

    private fun cmdHelp(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        lines.add("Available commands:")
        for (cmd in commands) {
            lines.add("  ${cmd.name.padEnd(15)} ${cmd.helpText}")
        }
    }

    private fun cmdRich(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        lines.add("Resources added.")

        ship.scrap = 5000
        ship.fuelCount = 99
        ship.missilesCount = 99
        ship.dronesCount = 99
    }

    private fun cmdWeapon(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        getWeapon { weapon ->
            for ((i, bp) in ship.cargoBlueprints.withIndex()) {
                if (bp != null)
                    continue

                ship.cargoBlueprints[i] = weapon
                lines.add("Added weapon ${weapon.translateTitle(game)} to cargo slot ${i + 1}.")
                return@getWeapon
            }

            lines.add("No space in cargo hold, can't add weapon.")
        }
    }

    private fun cmdStore(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        game.currentBeacon.hasStore = true
        game.shipUI.updateButtons()

        lines.add("A store is now available at this beacon.")
    }

    private fun cmdEvent(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        getEvent { event ->
            game.shipUI.showEventDialogue(event.resolve())
        }
    }

    private fun getWeapon(callback: (ShipWeaponBlueprint) -> Unit) {
        continued = object : ContinuedCommand() {
            // A little caching for the search
            var lastInput: String? = null
            val visibleWeapons = ArrayList<Buttons.BlueprintButton>()

            override val prompt: String get() = "WEAPON> "

            var lastLeftClick = false

            override fun run(line: String) {
                val weapon = game.blueprintManager.blueprints[line]
                if (weapon == null) {
                    lines.add("No such blueprint '$line'")
                    return
                }
                if (weapon !is ShipWeaponBlueprint) {
                    lines.add("Blueprint '$line' is not a weapon - ${weapon.javaClass.name}")
                    return
                }
                callback(weapon)
            }

            override fun render(gc: GameContainer, g: Graphics, height: Float) {
                updateSearch()

                val mouseX = gc.input.mouseX
                val mouseY = gc.input.mouseY

                val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
                val clicking = leftDown && !lastLeftClick
                lastLeftClick = leftDown

                for ((i, button) in visibleWeapons.withIndex()) {
                    val x = 10 + (button.image.normal.width + 5) * i
                    val y = height.toInt() + 10

                    if (x > gc.width)
                        break

                    button.windowOffset = ConstPoint(x, y)
                    button.update(mouseX, mouseY)
                    button.draw(g)

                    if (clicking) {
                        button.mouseDown(Input.MOUSE_LEFT_BUTTON, mouseX, mouseY)
                    }
                }
            }

            private fun updateSearch() {
                val line = currentLine

                if (lastInput == line)
                    return
                lastInput = line

                val searcher = FuzzySearcher(line)

                val names = game.blueprintManager.blueprints.keys.mapNotNull {
                    val score = searcher.rank(it)

                    return@mapNotNull if (score == 0) {
                        null
                    } else {
                        Pair(it, score)
                    }
                }.sortedByDescending { it.second }.map { it.first }

                val images = ButtonImageSet.select2(game, "img/storeUI/store_buy_weapons")

                // Use BlueprintButton's rendering
                class DummyButton(override val blueprint: Blueprint) :
                    Buttons.BlueprintButton(ConstPoint.ZERO, game, images) {
                    override fun click(button: Int) {
                        historyCursor = -1
                        input = blueprint.name
                        runCommand()
                    }
                }

                val weapons: List<DummyButton> = names.mapNotNull {
                    val bp = game.blueprintManager.blueprints[it]
                    val weapon = bp as? ShipWeaponBlueprint ?: return@mapNotNull null
                    return@mapNotNull DummyButton(weapon)
                }

                visibleWeapons.clear()
                visibleWeapons.addAll(weapons)
            }
        }
    }

    private fun getEvent(callback: (IEvent) -> Unit) {
        data class EventInfo(val event: Event, val text: String)

        continued = object : ContinuedCommand() {
            // A little caching for the search
            var lastInput: String? = null
            val visibleEvents = ArrayList<EventInfo>()

            override val prompt: String get() = "EVENT> "

            var lastLeftClick = false

            override fun run(line: String) {
                val names = game.eventManager.eventNames
                if (!names.contains(line)) {
                    lines.add("No such event '$line'")
                    return
                }
                val event = game.eventManager[line]

                callback(event)
            }

            override fun render(gc: GameContainer, g: Graphics, height: Float) {
                updateSearch()

                val mouseX = gc.input.mouseX
                val mouseY = gc.input.mouseY

                val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
                val clicking = leftDown && !lastLeftClick
                lastLeftClick = leftDown

                val x = 30
                val blockHeight = 15
                val width = gc.width - x - 40

                val idWidth = 125

                // Draw the search category options
                val optionHeight = blockHeight + 2
                var optionX = x
                fun drawOption(value: Boolean, text: String) {
                    val boxWidth = 5 + font.getWidth(text) + 5
                    g.color = Color(55, if (value) 180 else 55, 55, 180)

                    val y = height + 10f
                    g.fillRect(optionX.f, y, boxWidth.f, optionHeight.f)
                    font.drawString(optionX + 5f, y + 10f, text, Color.white)

                    optionX += boxWidth
                }
                drawOption(eventSearchIds, "Search IDs (F1)")
                drawOption(eventSearchMessageText, "Search event text (F2)")
                drawOption(eventSearchOptions, "Search choice text (F3)")

                for ((i, event) in visibleEvents.withIndex()) {
                    val y = height.toInt() + 10 + optionHeight + i * blockHeight

                    if (y > gc.height)
                        break

                    val hovering = mouseX in x until x + width && mouseY in y until y + blockHeight

                    val shade = if (hovering) 140 else 100
                    g.color = Color(shade, shade, shade, 180)
                    g.fillRect(x.f, y.f, width.f, blockHeight.f)

                    font.drawStringTruncated(x + 5f, y + 10f, idWidth.f, event.event.debugId, Color.white)

                    val descriptionX = x + idWidth + 10
                    val descriptionWidth = width - descriptionX - 5f
                    font.drawStringTruncated(
                        descriptionX.f, y + 10f, descriptionWidth,
                        event.text.replace("\n", " \\n "),
                        Color.white
                    )

                    if (clicking && hovering) {
                        // Note the debug ID matches the event name for top-level events (we don't
                        // display nested events).
                        historyCursor = -1
                        input = event.event.debugId
                        runCommand()
                    }
                }
            }

            override fun keyPressed(key: Int, c: Char): Boolean {
                val handled = when (key) {
                    Input.KEY_F1 -> {
                        eventSearchIds = !eventSearchIds
                        true
                    }

                    Input.KEY_F2 -> {
                        eventSearchMessageText = !eventSearchMessageText
                        true
                    }

                    Input.KEY_F3 -> {
                        eventSearchOptions = !eventSearchOptions
                        true
                    }

                    else -> false
                }

                // Update the search entries
                if (handled)
                    lastInput = null

                return handled
            }

            private fun updateSearch() {
                val line = currentLine

                if (lastInput == line)
                    return
                lastInput = line

                val searcher = FuzzySearcher(line)

                val events = game.eventManager.eventNames.mapNotNull { name ->
                    // Filter out event lists
                    val event = game.eventManager[name] as? Event ?: return@mapNotNull null

                    var score = 0

                    // Search the name, and consider it to be more important
                    // than the body text. This makes it easier to find a specific
                    // event.
                    if (eventSearchIds)
                        searcher.rank(name) * 10

                    // And the body of the event text - just search the first
                    // available one, it'd get a bit unmanageable otherwise.
                    val textBody = event.text?.let(::getTextBody) ?: return@mapNotNull null
                    if (eventSearchMessageText)
                        score += searcher.rank(textBody)

                    // Rank all the options, too.
                    if (eventSearchOptions) {
                        for (choice in event.choices) {
                            val text = getTextBody(choice.text) ?: continue
                            score += searcher.rank(text)
                        }
                    }

                    return@mapNotNull if (score == 0) {
                        null
                    } else {
                        Pair(EventInfo(event, textBody), score)
                    }
                }.sortedByDescending { it.second }

                visibleEvents.clear()
                visibleEvents.addAll(events.map { it.first })
            }

            private fun getTextBody(text: IEventText): String? {
                if (text is EventText)
                    return text.localised

                require(text is TextList)

                for (item in text.items) {
                    getTextBody(item)?.let { return it }
                }
                return null
            }
        }
    }

    private data class Cmd(val name: String, val argCount: Int?, val func: (List<String>) -> Unit, val helpText: String)

    private class FuzzySearcher(query: String) {
        // Split up the words
        private val query = query.toLowerCase(Locale.UK)
        private val inputParts = query.split(" ", "_").filter { it.isNotBlank() }

        fun rank(target: String): Int {
            val lower = target.toLowerCase(Locale.UK)

            // Only match words once, as otherwise a word that's repeated a lot could
            // cause one entry to very frequently appear at the top of the list.
            val parts = lower
                .replace("_", " ")
                .split(" ")
                .toSet()

            var score = 0

            for (part in parts) {
                for (ip in inputParts) {
                    if (part == ip) {
                        score += 20 + ip.length * 3
                    } else if (part.contains(ip)) {
                        // Add a per-word weight
                        score += 10 + ip.length
                    }
                }
            }

            // Add a bonus if the search matches literally (though case-insensitively).
            if (target.contains(query)) {
                score += 4 * query.length
            }

            return score
        }
    }

    private abstract class ContinuedCommand {
        abstract val prompt: String
        open fun render(gc: GameContainer, g: Graphics, height: Float) {}
        open fun keyPressed(key: Int, c: Char): Boolean = false
        abstract fun run(line: String)
    }

    companion object {
        private const val PROMPT = "> "
        private const val FLASH_TIME = 0.65f
    }
}
