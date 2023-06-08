package xyz.znix.xftl.game

import org.newdawn.slick.Graphics
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

abstract class Window {
    var position: IPoint = ConstPoint.ZERO
        set(value) {
            // Don't run positionUpdated if nothing changed
            if (value == field)
                return

            field = value
            positionUpdated()
        }

    /**
     * This is added to the window's position, after it's position
     * to make it centred was calculated.
     */
    open val windowCentreOffset: IPoint get() = ConstPoint.ZERO

    abstract val size: IPoint

    val buttons = ArrayList<Button>()

    abstract fun draw(g: Graphics)

    open fun mouseClick(button: Int, x: Int, y: Int) {
        // Mouse clicking may change the buttons array (eg in the store
        // window when switching tabs), so copy it.
        for (btn in ArrayList(buttons)) {
            btn.mouseDown(button, x, y)
        }
    }

    open fun mouseReleased(button: Int, x: Int, y: Int) {
    }

    open fun escapePressed() {
        // Subclasses may close the window
    }

    protected open fun positionUpdated() {
        for (button in buttons) {
            button.windowOffset = position
        }
    }

    open fun updateUI(x: Int, y: Int) {
        for (button in buttons) {
            button.update(x, y)
        }
    }

    /**
     * Called whenever the ship is changed in some way - for example,
     * weapons are added or moved around.
     */
    open fun shipModified() {}
}
