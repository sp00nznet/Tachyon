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

    private val commands: List<Cmd> = listOf(
        Cmd("rich", 0, this::cmdRich, "Get a huge amount of scrap, fuel, drones, and missiles"),
        Cmd("weapon", 0, this::cmdWeapon, "Select a weapon, and add it to the ship's cargo area"),
        Cmd("store", 0, this::cmdStore, "Create a store at this beacon"),
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

    fun update(gc: GameContainer, dt: Float) {
        flashTimer += dt
    }

    fun keyPressed(key: Int, c: Char) {
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
        // Don't add blank lines or the same command multiple times in a row
        if (history.lastOrNull() != input && input.isNotBlank())
            history.add(input)

        lines.add(prompt + input)

        // If we're part way through a command that takes multiple
        // lines of input, run that.
        continued?.let { cmd ->
            continued = null
            cmd.run(input.trim())
            input = ""
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

        input = ""
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

                    if (y > gc.width)
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

                // Implement a simple fuzzy search, splitting up words.
                val inputParts = line.split(" ", "_")
                    .map { it.toLowerCase(Locale.UK) }
                    .filter { it.isNotBlank() }

                val names = game.blueprintManager.blueprints.keys.mapNotNull {
                    val parts = it.toLowerCase(Locale.UK).split("_")

                    var score = 0

                    for (part in parts) {
                        for (ip in inputParts) {
                            if (part.contains(ip)) {
                                // Add a per-word weight
                                score += 10 + ip.length
                            }
                        }
                    }

                    return@mapNotNull if (score == 0) {
                        null
                    } else {
                        Pair(it, score)
                    }
                }.sortedBy { it.second }.map { it.first }

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

    private data class Cmd(val name: String, val argCount: Int?, val func: (List<String>) -> Unit, val helpText: String)

    private abstract class ContinuedCommand {
        abstract val prompt: String
        open fun render(gc: GameContainer, g: Graphics, height: Float) {}
        abstract fun run(line: String)
    }

    companion object {
        private const val PROMPT = "> "
        private const val FLASH_TIME = 0.65f
    }
}
