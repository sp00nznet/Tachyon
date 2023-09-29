package xyz.znix.xftl.devutil

import xyz.znix.xftl.f
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.Input

/**
 * An auto-completion engine for the debug console.
 */
abstract class AutoCompleter(val console: DebugConsole) : DebugConsole.Overlay {
    abstract fun update(currentToken: String, positionInInput: Int)
    abstract fun applyAutoCompletion()
}

abstract class BasicCompletionEngine<T>(console: DebugConsole) : AutoCompleter(console) {
    abstract val items: List<T>

    // A little caching for the search
    private var lastInput: String? = null
    private val sortedEntries = ArrayList<T>()
    private var lastLeftClick = false
    private var positionInInput: Int = 0

    protected abstract fun getItemName(item: T): String

    override val autoCompleteSuggestion: String? get() = sortedEntries.firstOrNull()?.let { getItemName(it) }

    override fun render(gc: GameContainer, g: Graphics, height: Float) {
        val mouseX = gc.input.mouseX
        val mouseY = gc.input.mouseY

        val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
        val clicking = leftDown && !lastLeftClick
        lastLeftClick = leftDown

        val x = 30
        val blockHeight = 15
        val width = gc.width - x - 40

        for ((i, item) in sortedEntries.withIndex()) {
            val y = height.toInt() + 10 + i * blockHeight

            if (y > gc.height)
                break

            val hovering = mouseX in x until x + width && mouseY in y until y + blockHeight

            val shade = if (hovering) 140 else 100
            g.colour = Colour(shade, shade, shade, 180)
            g.fillRect(x.f, y.f, width.f, blockHeight.f)

            console.font.drawString(x + 5f, y + 10f, getItemName(item), Colour.white)

            if (clicking && hovering) {
                itemSelected(item)
            }
        }
    }

    override fun update(currentToken: String, positionInInput: Int) {
        this.positionInInput = positionInInput

        // Only update when something changes
        if (currentToken == lastInput)
            return
        lastInput = currentToken

        sortedEntries.clear()
        sortedEntries.addAll(items)

        if (currentToken.isBlank()) {
            // If there's no search term, leave the items in their original order.
            return
        }

        sortEntries(sortedEntries, currentToken)
    }

    protected open fun sortEntries(entries: ArrayList<T>, currentToken: String) {
        // Display all the entries - sorting only orders them
        val searcher = DebugConsole.FuzzySearcher(currentToken)
        sortedEntries.sortByDescending { searcher.rank(getItemName(it)) }
    }

    protected open fun itemSelected(item: T) {
        console.input = console.input.substring(0, positionInInput) + getItemName(item) + " "
    }

    override fun applyAutoCompletion() {
        val first = sortedEntries.firstOrNull() ?: return
        itemSelected(first)
    }
}

class CommandCompleter(console: DebugConsole) : BasicCompletionEngine<DebugConsole.Cmd>(console) {
    override val items: List<DebugConsole.Cmd> get() = console.commands

    override fun getItemName(item: DebugConsole.Cmd): String {
        return item.name
    }

    override fun sortEntries(entries: ArrayList<DebugConsole.Cmd>, currentToken: String) {
        // Use fuzzy-search as a backup
        super.sortEntries(entries, currentToken)

        // Pull all the prefix matches to the start
        val matches = entries.filter { it.name.startsWith(currentToken) }.toList()

        // Order the matching commands by name, so they're stable
        matches.sortedBy { it.name }

        // Move them to the front
        entries.removeAll(matches.toSet())
        entries.addAll(0, matches)
    }
}
