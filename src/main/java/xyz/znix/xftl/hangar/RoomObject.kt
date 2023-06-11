package xyz.znix.xftl.hangar

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants
import xyz.znix.xftl.f
import xyz.znix.xftl.systems.SystemBlueprint
import kotlin.math.roundToInt

class RoomObject(val editor: ShipEditor, val room: EditableRoom) : UIObject, DragObject {
    override val dragX: Int get() = room.x * Constants.ROOM_SIZE
    override val dragY: Int get() = room.y * Constants.ROOM_SIZE

    override val selectPriority: Int get() = 0

    override val subObjects: List<UIObject> = listOf(
        RoomCornerDragObject(editor, room, false, false),
        RoomCornerDragObject(editor, room, false, true),
        RoomCornerDragObject(editor, room, true, false),
        RoomCornerDragObject(editor, room, true, true)
    )

    var systemObject: SystemObject? = null
    private var lastSystem: SystemBlueprint? = null

    init {
        updateSubObjects()
    }

    override fun setGridPos(x: Int, y: Int) {
        room.x = x
        room.y = y
    }

    override fun canSelectFrom(mouseX: Int, mouseY: Int): Boolean {
        return room.containsPixel(mouseX, mouseY)
    }

    override fun canStartDragging(mouseX: Int, mouseY: Int): Boolean {
        // Only allow dragging when we're selected, to avoid accidentally
        // moving rooms around while dragging systems etc.
        if (!editor.isSelected(this))
            return false

        return super.canStartDragging(mouseX, mouseY)
    }

    override fun draw(g: Graphics) {
        if (lastSystem != room.system) {
            updateSubObjects()
        }

        // If we're dragged around, we need to update the system icon
        updateSystemPosition()

        if (!editor.isSelected(this))
            return

        g.color = Color(0, 255, 0, 100)
        g.fillRect(room.pixelX.f, room.pixelY.f, room.pixelWidth.f, room.pixelHeight.f)
    }

    override fun onDeletePressed() {
        editor.ship.rooms.remove(room)
    }

    override fun onRightClick(x: Int, y: Int) {
        val systemEntries = ArrayList<PopupMenu.Entry>()

        systemEntries += PopupMenu.Entry("No system") {
            room.system = null
        }

        for (system in editor.allSystems) {
            systemEntries += PopupMenu.Entry(system.name) {
                // Clear this system from all other rooms
                for (otherRoom in editor.ship.rooms) {
                    if (otherRoom.system == system)
                        otherRoom.system = null
                }

                room.system = system
            }
        }

        editor.openPopupMenu(listOf(
            PopupMenu.Entry("System", systemEntries, null),
            PopupMenu.Entry("Something else") {}
        ))
    }

    override fun updateSubObjects() {
        systemObject = room.system?.let { SystemObject(editor, room, 0, 0, it) }
        lastSystem = room.system
        updateSystemPosition()
    }

    private fun updateSystemPosition() {
        val system = systemObject ?: return
        system.centreX = ((room.x + room.w / 2f) * Constants.ROOM_SIZE).roundToInt()
        system.centreY = ((room.y + room.h / 2f) * Constants.ROOM_SIZE).roundToInt()
    }
}
