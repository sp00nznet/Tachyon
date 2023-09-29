package xyz.znix.xftl.devutil

import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.InGameState

@Target(AnnotationTarget.FUNCTION)
annotation class ConsoleCommand(
    val name: String,

    /**
     * The number of arguments this command takes.
     *
     * The special value -1 means a variable number, not validated by the [DebugConsole].
     */
    val argCount: Int,
)

@Target(AnnotationTarget.FUNCTION)
annotation class CmdHelp(
    val help: String
)

/**
 * Represents a class that adds console commands.
 */
abstract class ConsoleCommandProvider(val console: DebugConsole) {
    open val ship: Ship = console.game.player
    open val game: InGameState = console.game

    /**
     * A big list of commands, generated from the [ConsoleCommand]-annotated
     * methods in this class.
     */
    val commands: List<DebugConsole.Cmd>

    init {
        commands = ArrayList()

        val methods = javaClass.declaredMethods
        for (method in methods) {
            val cmdAnnotation = method.getAnnotation(ConsoleCommand::class.java) ?: continue
            val helpString = method.getAnnotation(CmdHelp::class.java)?.help ?: "No help message provided."

            if (method.parameterCount != 1) {
                error("Invalid parameter count ${method.parameterCount} for console command $method")
            }

            // Allow private methods
            method.isAccessible = true

            val argCount = when (val count = cmdAnnotation.argCount) {
                -1 -> null
                else -> count
            }

            val caller: (List<String>) -> Unit = {
                method.invoke(this, it)
            }

            val cmd = DebugConsole.Cmd(cmdAnnotation.name, argCount, caller, helpString)

            commands.add(cmd)
        }
    }

    fun addLine(line: String) {
        console.addLine(line)
    }
}
