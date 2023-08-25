package xyz.znix.xftl.ui

import org.jdom2.Element
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Graphics
import kotlin.math.max

/**
 * A container that lays out all it's children in a line, packed together.
 */
class BoxContainer(provider: UIProvider, val layoutDirection: Direction) : Widget(provider) {
    private val mutableSize = Point(10, 10)
    override val size: IPoint get() = mutableSize

    // Store the sizes we want the children to be, after applying stretching
    private val childSizes = ArrayList<Int>()

    init {
        require(layoutDirection == Direction.RIGHT || layoutDirection == Direction.DOWN)
    }

    override fun draw(g: Graphics) {
        postDraw(g)
    }

    override fun updateSizes() {
        super.updateSizes()

        mutableSize.set(0, 0)
        childSizes.clear()
        for (child in children) {
            val cSize = child.size
            if (layoutDirection == Direction.RIGHT) {
                mutableSize.x += cSize.x
                childSizes.add(cSize.x)
                mutableSize.y = max(mutableSize.y, cSize.y)
            } else {
                mutableSize.x = max(mutableSize.x, cSize.x)
                mutableSize.y += cSize.y
                childSizes.add(cSize.y)
            }
        }

        // Try and stretch the children in the direction we're not laying them out in
        for (child in children) {
            if (layoutDirection == Direction.RIGHT) {
                child.attemptStretch(0, size.y)
            } else {
                child.attemptStretch(size.x, 0)
            }
        }
    }

    override fun updateLayout() {
        var offset = 0
        for ((index, child) in children.withIndex()) {
            if (layoutDirection == Direction.RIGHT) {
                child.position.set(position.x + offset, position.y)
            } else {
                child.position.set(position.x, position.y + offset)
            }

            offset += childSizes[index]
        }

        // Update the layout of our children once they're positioned, so their
        // children can inherit the position we just set.
        super.updateLayout()
    }

    override fun attemptStretch(availableWidth: Int, availableHeight: Int) {
        val extraX = availableWidth - size.x
        if (extraX > 0 && layoutDirection.isHorizontal) {
            val totalStretch = children.sumByDouble { it.xStretch.toDouble() }.toFloat()

            for ((i, child) in children.withIndex()) {
                if (child.xStretch == 0f)
                    continue

                val fraction = child.xStretch / totalStretch
                val thisExtra = (extraX * fraction).toInt()

                val newWidth = childSizes[i] + thisExtra
                childSizes[i] = newWidth

                child.attemptStretch(newWidth, 0)
            }

            mutableSize.x = availableWidth
        }

        // Copy-paste of the above, but for Y
        val extraY = availableHeight - size.y
        if (extraY > 0 && layoutDirection.isHorizontal) {
            val totalStretch = children.sumByDouble { it.yStretch.toDouble() }.toFloat()

            for ((i, child) in children.withIndex()) {
                if (child.yStretch == 0f)
                    continue

                val fraction = child.yStretch / totalStretch
                val thisExtra = (extraY * fraction).toInt()

                val newHeight = childSizes[i] + thisExtra
                childSizes[i] = newHeight

                child.attemptStretch(0, newHeight)
            }

            mutableSize.y = availableHeight
        }
    }

    companion object {
        fun fromXML(provider: UIProvider, elem: Element, vertical: Boolean): BoxContainer {
            val dir = when (vertical) {
                true -> Direction.DOWN
                false -> Direction.RIGHT
            }
            val box = BoxContainer(provider, dir)
            box.loadXML(elem)
            return box
        }
    }
}
