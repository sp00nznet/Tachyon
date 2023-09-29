package xyz.znix.xftl.devutil

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.f
import xyz.znix.xftl.game.ButtonImageSet
import xyz.znix.xftl.game.Buttons
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint

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
    protected open fun getCompletionString(item: T): String = getItemName(item)

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
            itemUpdateFinished(sortedEntries)
            return
        }

        sortEntries(sortedEntries, currentToken)

        itemUpdateFinished(sortedEntries)
    }

    protected open fun sortEntries(entries: ArrayList<T>, currentToken: String) {
        // Display all the entries - sorting only orders them
        val searcher = DebugConsole.FuzzySearcher(currentToken)
        sortedEntries.sortByDescending { searcher.rank(getItemName(it)) }
    }

    protected open fun itemUpdateFinished(sortedEntries: List<T>) = Unit

    protected open fun itemSelected(item: T) {
        console.input = console.input.substring(0, positionInInput) + getCompletionString(item) + " "
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

class BlueprintCompleter(console: DebugConsole, val target: BlueprintTypeProcessor) :
    BasicCompletionEngine<Blueprint>(console) {

    private val isWeapons = AbstractWeaponBlueprint::class.java.isAssignableFrom(target.type)
    private val isDrones = DroneBlueprint::class.java.isAssignableFrom(target.type)

    private var lastLeftClick = false
    private var buttons: List<CompletionButton> = emptyList()

    override val items = console.game.blueprintManager.blueprints.values
        .filterIsInstance(Blueprint::class.java)
        .filter { target.type.isAssignableFrom(it.javaClass) }

    override fun getItemName(item: Blueprint): String {
        return item.translateTitle(console.game)
    }

    override fun getCompletionString(item: Blueprint): String {
        return item.name
    }

    override fun itemUpdateFinished(sortedEntries: List<Blueprint>) {
        if (isWeapons) {
            val images = ButtonImageSet.select2(console.game, "img/storeUI/store_buy_weapons")
            buttons = sortedEntries.map { CompletionButton(it, images) }
        }
        if (isDrones) {
            val images = ButtonImageSet.select2(console.game, "img/storeUI/store_buy_drones")
            buttons = sortedEntries.map { CompletionButton(it, images) }
        }
    }

    override fun render(gc: GameContainer, g: Graphics, height: Float) {
        if (isWeapons || isDrones) {
            renderButtons(gc, g, height)
        } else {
            super.render(gc, g, height)
        }
    }

    fun renderButtons(gc: GameContainer, g: Graphics, height: Float) {
        val mouseX = gc.input.mouseX
        val mouseY = gc.input.mouseY

        val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
        val clicking = leftDown && !lastLeftClick
        lastLeftClick = leftDown

        val baseX = 10
        var x = baseX
        var y = height.toInt() + 10

        for ((i, button) in buttons.withIndex()) {
            button.windowOffset = ConstPoint(x, y)
            button.update(mouseX, mouseY)
            button.draw(g)

            if (clicking) {
                button.mouseDown(Input.MOUSE_LEFT_BUTTON, mouseX, mouseY)
            }


            // Move along to the next position
            val img = button.image.normal
            x += img.width + 5

            // Wrap around
            if (x + img.width > gc.width) {
                x = baseX
                y += img.height + 5
            }

            if (y > gc.height) {
                break
            }
        }
    }

    // Use BlueprintButton's rendering for weapons/drones
    private inner class CompletionButton(override val blueprint: Blueprint, images: ButtonImageSet) :
        Buttons.BlueprintButton(ConstPoint.ZERO, console.game, images) {

        override fun click(button: Int) {
            itemSelected(blueprint)
        }
    }
}
