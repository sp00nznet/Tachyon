package xyz.znix.xftl.devutil

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.game.SlickGame

/**
 * A development console, to quickly do stuff like load events or get scrap.
 */
class DebugConsole(val game: SlickGame, val ship: Ship) {
    private val history = ArrayList<String>()
    private var historyCursor: Int = -1

    private var input: String = ""

    private val lines = ArrayList<String>()

    private val font = game.getFont("c&c")

    private var flashTimer: Float = 0f

    private val commands: List<Cmd> = listOf(
        Cmd("help", 0, this::cmdHelp, "Show the available commands"),
        Cmd("rich", 0, this::cmdRich, "Get a huge amount of scrap, fuel, drones, and missiles")
    )

    fun render(gc: GameContainer, g: Graphics) {
        val height = gc.height / 2f

        g.color = Color(127, 127, 127, 180)
        g.fillRect(0f, 0f, gc.width.f, height)

        var y = height - 6

        val fontHeight = 7
        val lineSpacing = fontHeight + 5 // Some letters are a bit outside the font height

        var inputLine = input
        if (historyCursor != -1) {
            inputLine = history[historyCursor]
        }
        inputLine = PROMPT + inputLine
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
    }

    fun update(gc: GameContainer, dt: Float) {
        flashTimer += dt
    }

    fun keyPressed(key: Int, c: Char) {
        when (key) {
            Input.KEY_ENTER -> {
                selectHistory()
                runCommand()
                input = ""
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

        lines.add(PROMPT + input)

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

    private fun cmdRich(@Suppress("UNUSED_PARAMETER") strings: List<String>) {
        lines.add("Resources added.")

        ship.scrap = 5000
        ship.fuelCount = 99
        ship.missilesCount = 99
        ship.dronesCount = 99
    }

    private data class Cmd(val name: String, val argCount: Int?, val func: (List<String>) -> Unit, val helpText: String)

    companion object {
        private const val PROMPT = "> "
        private const val FLASH_TIME = 0.65f
    }
}
