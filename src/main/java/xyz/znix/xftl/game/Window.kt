package xyz.znix.xftl.game

import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.drawSection
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint

abstract class Window(position: IPoint) {
    val position = position.const
    abstract val size: IPoint
    abstract val outlineImage: Image

    val buttons = ArrayList<Button>()

    abstract fun draw(g: Graphics)

    open fun mouseClick(button: Int, x: Int, y: Int) {
        // Mouse clicking may change the buttons array (eg in the store
        // window when switching tabs), so copy it.
        for (btn in ArrayList(buttons)) {
            btn.mouseDown(button, x, y)
        }
    }

    open fun escapePressed() {
        // Subclasses may close the window
    }

    protected fun drawCorner(edge: Direction) {
        val x = position.x + edge.x.coerceAtLeast(0) * (size.x - 33)
        val y = position.y + edge.y.coerceAtLeast(0) * (size.y - 36)
        val tx = edge.x.coerceAtLeast(0) * outlineImage.width / 2
        val ty = edge.y.coerceAtLeast(0) * outlineImage.height / 2

        outlineImage.drawSection(x, y, 33, 36, tx, ty)
    }

    protected fun drawSide(edge: Direction, start: Int? = null, stop: Int? = null) {
        val xb = position.x + edge.x.coerceAtLeast(0) * (size.x - 33)
        val yb = position.y + edge.y.coerceAtLeast(0) * (size.y - 36)

        val texPos = when (edge) {
            Direction.UP -> ConstPoint(33, 0)
            Direction.LEFT -> ConstPoint(0, 36)
            Direction.DOWN, Direction.RIGHT -> ConstPoint(33, 36)
            else -> error("Invalid side edge $edge")
        }

        val texSize = when (edge) {
            Direction.UP, Direction.DOWN -> ConstPoint(1, 36)
            Direction.LEFT, Direction.RIGHT -> ConstPoint(33, 1)
            else -> error("Invalid side edge $edge")
        }

        val x: Int
        val y: Int

        val drawSize: ConstPoint
        when (edge) {
            Direction.UP, Direction.DOWN -> {
                x = xb + (start ?: 33)
                y = yb
                drawSize = ConstPoint((stop ?: size.x - 33) - (start ?: 33), 36)
            }

            Direction.LEFT, Direction.RIGHT -> {
                x = xb
                y = yb + (start ?: 36)
                drawSize = ConstPoint(33, (stop ?: size.y - 36) - (start ?: 36))
            }

            else -> error("Invalid edge $edge")
        }

        outlineImage.draw(
            x.f,
            y.f,
            x.f + drawSize.x,
            y.f + drawSize.y,
            texPos.x.f,
            texPos.y.f,
            texPos.x.f + texSize.x,
            texPos.y.f + texSize.y
        )
    }

    open fun updateUI(x: Int, y: Int) {
        for (button in buttons) {
            button.update(x, y)
        }
    }
}
