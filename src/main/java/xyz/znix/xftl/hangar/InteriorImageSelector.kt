package xyz.znix.xftl.hangar

import org.newdawn.slick.Color
import org.newdawn.slick.Input
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image

class InteriorImageSelector(val editor: ShipEditor, val room: EditableRoom) : EditorMenu {
    private val imagePaths: List<String>
    private val images: List<Image>

    val size = ConstPoint(800, 600)
    val pos = editor.getCentralScreenPosition(size)

    private var hovering: Int? = null

    private val mousePos = Point(0, 0)

    private val scale = 2
    private val roomPixelWidth = room.w * ROOM_SIZE
    private val roomPixelHeight = room.h * ROOM_SIZE

    init {
        val systemType = room.system!!.getBP(editor.state)

        // Show all the images, not just the ones we think are suitable based on
        // which pixels are transparent.
        // Do filter out ones that are the wrong size, though.
        imagePaths = editor.state.roomImageMeta.roomImages
            .filter { it.matchesSystem(systemType) }
            .filter { it.size.x == room.w && it.size.y == room.h }
            .map { it.path }

        images = imagePaths.map { editor.state.getImg(it) }
    }

    override fun draw(g: Graphics) {
        val title = "SELECT INTERIOR IMAGE"
        editor.state.windowRenderer.renderWithTitleTab(
            g,
            editor.titleTab, editor.titleFont,
            pos.x, pos.y,
            size.x, size.y,
            title
        )

        hovering = null

        val baseX = pos.x + 30
        var x = baseX
        var y = pos.y + 50

        drawOption(g, 0, x, y, null)
        for ((index, image) in images.withIndex()) {
            drawOption(g, index + 1, x, y, image)

            x += roomPixelWidth * scale + 20

            // Warp-around if necessary.
            if (x + roomPixelWidth * scale >= pos.x + size.x - 30) {
                x = baseX
                y += roomPixelHeight * scale + 20
            }
        }
    }

    private fun drawOption(g: Graphics, index: Int, x: Int, y: Int, image: Image?) {
        val isHovering = mousePos.x - x in 0..roomPixelWidth * scale && mousePos.y - y in 0..roomPixelHeight * scale
        if (isHovering) {
            hovering = index
        }

        g.pushTransform()
        g.translate(x.f, y.f)
        g.scale(2f, 2f)

        // Draw the image with its floor
        EditableRoom.drawFloor(g, 0, 0, room.w, room.h)
        image?.drawNearest(0f, 0f)

        if (isHovering) {
            g.colour = Color(0.5f, 1f, 1f, 0.25f)
            g.fillRect(0f, 0f, roomPixelWidth.f, roomPixelHeight.f)
        }

        g.popTransform()
    }

    override fun mouseMoved(x: Int, y: Int) {
        mousePos.set(x, y)
    }

    override fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        if (button != Input.MOUSE_LEFT_BUTTON)
            return

        val hoverIndex = hovering ?: return

        // 0 is for no image
        room.system?.interiorImage = imagePaths.getOrNull(hoverIndex - 1)
        editor.closeMenu(this)
    }
}
