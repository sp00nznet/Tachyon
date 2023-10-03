package xyz.znix.xftl.ui

import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.Window
import xyz.znix.xftl.rendering.Graphics

class WidgetContainer(val root: Widget) {
    private val sortedWidgets = ArrayList<Widget>()

    val byId: Map<String, Widget>

    init {
        fun recurse(widget: Widget) {
            sortedWidgets.add(widget)
            for (child in widget.children) {
                recurse(child)
            }
        }
        recurse(root)

        // Draw in z (depth) order
        sortedWidgets.sortBy { it.zOrder }

        byId = HashMap()
        for (widget in sortedWidgets) {
            val id = widget.id ?: continue

            if (byId.containsKey(id)) {
                throw IllegalUISpecException("Duplicate widget id: '$id'")
            }

            byId[id] = widget
        }
    }

    fun draw(g: Graphics) {
        for (widget in sortedWidgets) {
            if (!widget.isVisible)
                continue

            widget.draw(g)
        }
    }

    fun addButtonListener(id: String, listener: () -> Unit) {
        val button = byId[id] as UIKitButton
        button.clickListener = listener
    }

    fun buildButtons(game: InGameState, window: Window): List<Button> {
        return sortedWidgets.flatMap { it.createGameButtons(game, window) }
    }
}
