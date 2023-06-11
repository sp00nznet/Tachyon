package xyz.znix.xftl.hangar

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.Constants.ROOM_SIZE
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.math.Rectangle
import xyz.znix.xftl.systems.SystemBlueprint
import kotlin.math.*

class ShipEditor(val state: SelectShipState, val ship: EditableShip) {
    // Our general-purpose font.
    val font = state.font

    // These are set by the state
    var editorWidth: Int = 100
    var editorHeight: Int = 100

    // This is just here because it's useful.
    val allSystems: List<SystemBlueprint> = state.blueprints.blueprints.values
        .filterIsInstance(SystemBlueprint::class.java)

    private var selected: UIObject? = null
        set(value) {
            field = value

            // When the user cycles through selected objects, that can
            // chance which one is considered as hovered.
            updateHover()
        }

    private val systemPalette = SystemPaletteObject(this)

    private var hovered: UIObject? = null
    private val objects = ArrayList<UIObject>()
    private val newObjects = ArrayList<UIObject>()

    private var dragging: DragObject? = null
        set(value) {
            val old = field
            field = value

            if (old != value && old != null) {
                old.onDropped(mousePixelPos.x, mousePixelPos.y)
            }
        }

    private val dragOffset = Point(0, 0)

    private var creatingRoom: Boolean = false
    private var creatingRoomStart: ConstPoint? = null

    private val mousePixelPos = Point(0, 0)
    private val mouseGridPos = Point(0, 0)

    private var menu: EditorMenu? = null

    /**
     * Called to draw stuff on top of the edited ship.
     */
    fun draw(g: Graphics) {
        updateObjects()

        for (obj in objects) {
            obj.draw(g)
        }

        val overlaps = findOverlappingRooms()
        for (overlap in overlaps) {
            g.color = Color(255, 0, 0, 60)
            g.fillRect(
                overlap.pos.x * ROOM_SIZE.f,
                overlap.pos.y * ROOM_SIZE.f,
                overlap.size.x * ROOM_SIZE.f,
                overlap.size.y * ROOM_SIZE.f
            )
        }

        // Draw the green blob for dragging out a new room
        if (creatingRoom) {
            dragging = null
            selected = null

            val bounds = getNewRoomBounds()
            if (bounds == null) {
                g.color = Color(0, 255, 0, 150)
                g.fillRect(
                    mouseGridPos.x * ROOM_SIZE.f,
                    mouseGridPos.y * ROOM_SIZE.f,
                    ROOM_SIZE.f,
                    ROOM_SIZE.f
                )
            } else {
                g.color = Color(0, 255, 0, 150)
                g.fillRect(
                    bounds.pos.x * ROOM_SIZE.f,
                    bounds.pos.y * ROOM_SIZE.f,
                    bounds.size.x * ROOM_SIZE.f,
                    bounds.size.y * ROOM_SIZE.f
                )
            }
        } else {
            creatingRoomStart = null
        }

        menu?.draw(g)
    }

    /**
     * Update the list of UI objects on the screen. This avoids replacing
     * objects with new instances of the same thing, so we can use the
     * selected variable to refer to an object in a stable way.
     */
    private fun updateObjects() {
        newObjects.clear()

        val remainingRooms = HashSet(ship.rooms)

        newObjects += systemPalette
        newObjects.addAll(systemPalette.systems)

        for (obj in objects) {
            // Rooms are copied over if possible
            if (obj is RoomObject) {
                if (remainingRooms.remove(obj.room)) {
                    newObjects += obj
                    obj.systemObject?.let { newObjects += it }
                }
            }
        }

        // Add any new rooms
        for (room in remainingRooms) {
            newObjects += RoomObject(this, room)
        }

        // Apply our changes
        objects.clear()
        objects.addAll(newObjects)

        selected?.let {
            if (!objects.contains(it)) {
                // De-select the object if it's gone
                selected = null
            } else {
                // Otherwise add it's sub-objects, for rooms this is the drag handle.
                objects += it.subObjects
            }
        }
    }

    /**
     * Like updateObjects, but also causes all the objects to update their children.
     */
    fun fullUpdateObjects() {
        for (obj in objects) {
            obj.updateSubObjects()
        }
    }

    private fun findOverlappingRooms(): List<Rectangle> {
        val overlaps = ArrayList<Rectangle>()

        // Search the ship for overlapping rooms. To do this efficiently,
        // step through all the rooms and only compare it to the next few
        // until it finds one that's too far away in X to overlap.
        val orderedRooms = ship.rooms.sortedBy { it.x }

        for ((indexA, roomA) in orderedRooms.withIndex()) {
            for (indexB in indexA + 1 until orderedRooms.size) {
                val roomB = orderedRooms[indexB]
                // We know that roomA.x <= roomB.x as the rooms list is sorted.

                if (roomA.x + roomA.w <= roomB.x) {
                    // We've gone past the limit of what our room can overlap
                    break
                }

                if (roomA.y in roomB.y..roomB.y + roomB.h || roomB.y in roomA.y..roomA.y + roomA.h) {
                    // These rooms overlap!
                    // Find the smallest overlapping portion, which means the maximum
                    // of the room top-lefts for the overlap top-left, and the minmum
                    // of the room bottom-rights for the overlap bottom-right.
                    val regionX1 = roomB.x
                    val regionX2 = min(roomA.x + roomA.w, roomB.x + roomB.w)

                    val regionY1 = max(roomA.y, roomB.y)
                    val regionY2 = min(roomA.y + roomA.h, roomB.y + roomB.h)

                    overlaps += Rectangle(
                        ConstPoint(regionX1, regionY1),
                        ConstPoint(regionX2 - regionX1, regionY2 - regionY1)
                    )
                }
            }
        }

        return overlaps
    }

    private fun getNewRoomBounds(): Rectangle? {
        val startPos = creatingRoomStart ?: return null

        // If the user picked the bottom-right point first, then these
        // values are negative. That's useful since we can clamp them
        // to keep the start position fixed, even if the cursor moves
        // far enough away to clamp the room size.
        var x = startPos.x
        var y = startPos.y
        var width = mouseGridPos.x - x
        var height = mouseGridPos.y - y

        // Increase the width and height by one point, so it includes both
        // the origin point and the cursor.
        width += width.sign
        height += height.sign

        // If the start position is on the right/bottom, we need to bump
        // the box along by one. Note we don't change the width/height,
        // since the above add-one operation already does that.
        if (width < 0)
            x++
        if (height < 0)
            y++

        width = width.coerceIn(-MAX_ROOM_LENGTH..MAX_ROOM_LENGTH)
        height = height.coerceIn(-MAX_ROOM_LENGTH..MAX_ROOM_LENGTH)

        // Expand zero-size rooms
        if (width == 0)
            width = 1
        if (height == 0)
            height = 1

        // Move the x,y point to the top-left
        x = min(x, x + width)
        y = min(y, y + height)
        width = abs(width)
        height = abs(height)

        return Rectangle(
            ConstPoint(x, y),
            ConstPoint(width, height)
        )
    }

    fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        updateMousePos(x, y)

        menu?.let {
            it.mouseClicked(button, x, y, clickCount)
            return
        }

        if (button == Input.MOUSE_LEFT_BUTTON) {
            if (creatingRoom) {
                return
            }

            // We're clearly not dragging anything any more, since we just
            // released the button. But we need to do this before changing
            // the selected object, so the dragging-is-hovered logic doesn't apply.
            dragging = null

            val objectsAtCursor = objects.filter { it.canSelectFrom(x, y) }.sortedByDescending { it.selectPriority }

            if (objectsAtCursor.isEmpty()) {
                // Nothing under the cursor
                selected = null
            } else if (selected != null) {
                // If there's multiple stacked objects, clicking cycles through them.
                val index = objectsAtCursor.indexOf(selected!!)
                val nextIndex = (index + 1) % objectsAtCursor.size
                selected = objectsAtCursor[nextIndex]
            } else {
                // Start at the one with the highest priority.
                selected = objectsAtCursor.first()
            }
        }

        if (button == Input.MOUSE_RIGHT_BUTTON) {
            val selected = selected // For smart-casting
            if (selected != null && selected.canSelectFrom(x, y)) {
                selected.onRightClick(x, y)
            }
        }
    }

    fun mousePressed(button: Int, x: Int, y: Int) {
        if (menu != null)
            return

        if (button == Input.MOUSE_LEFT_BUTTON) {
            if (creatingRoom) {
                if (creatingRoomStart == null) {
                    creatingRoomStart = ConstPoint(mouseGridPos)
                }
                return
            }

            val selected = selected // For smart-casting
            if (selected is DragObject && selected.canStartDragging(x, y)) {
                // Always prefer to drag the selected object
                dragging = selected
            } else {
                dragging = objects
                    .filterIsInstance<DragObject>()
                    .filter { it.canStartDragging(x, y) }
                    .maxBy { it.selectPriority }
            }

            val dragStart = dragging
            if (dragStart != null) {
                dragOffset.set(x - dragStart.dragX, y - dragStart.dragY)
            }
        }

        if (button == Input.MOUSE_RIGHT_BUTTON) {
            // Right-clicking cancels a new room
            if (creatingRoom) {
                creatingRoom = false
                return
            }
        }
    }

    fun mouseReleased(button: Int, x: Int, y: Int) {
        if (menu != null)
            return

        if (button == Input.MOUSE_LEFT_BUTTON) {
            if (creatingRoom) {
                val bounds = getNewRoomBounds()
                if (bounds != null) {
                    // Place the new room
                    creatingRoom = false

                    val newRoom = EditableRoom(
                        bounds.pos.x, bounds.pos.y,
                        bounds.size.x, bounds.size.y
                    )
                    ship.rooms += newRoom
                    updateObjects()
                    selected = objects.firstOrNull { it is RoomObject && it.room == newRoom }
                }
                return
            }

            dragging = null
        }
    }

    fun mouseDragged(oldX: Int, oldY: Int, newX: Int, newY: Int) {
        updateMousePos(newX, newY)

        if (menu != null)
            return

        val dragged = dragging
        if (dragged != null) {
            dragged.setPixelPos(newX, newY)

            // Calculate the new grid position, for grid-snapped objects.
            // We need to use roundToInt to make it snap when you drag the thing
            // half a cell, rather than going all the way.
            val gridX = ((newX - dragOffset.x) / ROOM_SIZE.f).roundToInt()
            val gridY = ((newY - dragOffset.y) / ROOM_SIZE.f).roundToInt()
            dragged.setGridPos(gridX, gridY)
        }
    }

    fun mouseMoved(x: Int, y: Int) {
        menu?.let {
            it.mouseMoved(x, y)
            return
        }

        updateMousePos(x, y)
    }

    private fun updateMousePos(x: Int, y: Int) {
        mousePixelPos.set(x, y)

        // We must round down to avoid breaking at the x=0 and y=0 lines
        mouseGridPos.set(
            floor(x / ROOM_SIZE.f).toInt(),
            floor(y / ROOM_SIZE.f).toInt()
        )

        updateHover()
    }

    private fun updateHover() {
        if (dragging != null) {
            hovered = dragging
        } else if (selected?.canHover(mousePixelPos.x, mousePixelPos.y) == true) {
            // If there's multiple overlapping objects, prefer the selected one.
            hovered = selected
        } else {
            hovered = objects.filter { it.canHover(mousePixelPos.x, mousePixelPos.y) }.maxBy { it.selectPriority }
        }
    }

    fun keyReleased(key: Int, c: Char) {
        if (menu != null) {
            menu!!.keyReleased(key, c)
            if (key == Input.KEY_ESCAPE) {
                menu = null
            }
            return
        }

        if (key == Input.KEY_DELETE) {
            selected?.onDeletePressed()
        }

        // 'N' for 'new room'
        if (key == Input.KEY_N) {
            creatingRoom = true
        }
    }

    fun openPopupMenu(entries: List<PopupMenu.Entry>) {
        menu = PopupMenu(this, mousePixelPos.const, entries)
    }

    fun closeMenu() {
        menu = null
    }

    fun isHovered(obj: UIObject): Boolean {
        return hovered == obj
    }

    fun isSelected(obj: UIObject): Boolean {
        return selected == obj
    }

    fun isDragging(obj: UIObject): Boolean {
        return dragging == obj
    }

    companion object {
        // This is just a limit on how large you can drag a room out to be,
        // to avoid getting too silly.
        const val MAX_ROOM_LENGTH: Int = 6

        val VALID_ROOM_SIZE = 1..MAX_ROOM_LENGTH
    }
}
