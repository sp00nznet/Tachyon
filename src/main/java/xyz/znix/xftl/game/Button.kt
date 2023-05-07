package xyz.znix.xftl.game

import org.newdawn.slick.*
import xyz.znix.xftl.*
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.ShipWeaponBlueprint

abstract class Button(protected val game: SlickGame, pos: IPoint, size: IPoint) {
    val basePos = pos.const
    var windowOffset: IPoint = ConstPoint.ZERO
        set(value) {
            field = value
            positionUpdated()
        }

    var pos = basePos
        private set
    val size = size.const

    var hovered: Boolean = false
        private set

    /**
     * True if the 'hoverBeep' sound should play when this button is moused over.
     */
    open val makesHoverNoise: Boolean get() = true

    /**
     * True if this button is disabled and can't be hovered.
     *
     * Note this doesn't prevent [click] from being called.
     */
    open val disabled: Boolean get() = false

    abstract fun draw(g: Graphics)

    /**
     * Alert the button that the mouse has been clicked at a specified location.
     *
     * @return true if this button absorbed the click, false otherwise
     */
    open fun mouseDown(button: Int, x: Int, y: Int): Boolean {
        if (!contains(x, y)) return false
        click(button)
        return true
    }

    protected abstract fun click(button: Int)

    fun contains(point: IPoint) = contains(point.x, point.y)

    open fun contains(x: Int, y: Int): Boolean {
        return pos.x <= x && x < pos.x + size.x && pos.y <= y && y < pos.y + size.y
    }

    fun update(x: Int, y: Int) {
        if (disabled) {
            hovered = false
            return
        }

        val lastHover = hovered
        hovered = contains(x, y)

        if (hovered && !lastHover && makesHoverNoise) {
            game.sounds.getSample("hoverBeep").play()
        }
    }

    protected open fun positionUpdated() {
        pos = basePos + windowOffset
    }
}

class SimpleButton(
    game: SlickGame, pos: IPoint, size: IPoint, imgOffset: IPoint,
    val normal: Image, val hover: Image?,
    private val callback: (Int) -> Unit
) : Button(game, pos, size) {
    val imgOffset = imgOffset.const
    private var imagePos = pos - imgOffset

    override fun draw(g: Graphics) {
        var image = normal
        if (hovered && hover != null)
            image = hover
        image.draw(imagePos)
    }

    override fun positionUpdated() {
        super.positionUpdated()
        imagePos = pos - imgOffset
    }

    override fun click(button: Int) = callback(button)

    companion object {
        // Create a new button by specifying the clickable region within it,
        // rather than using pos and imgOffset to achive it.
        fun byRegion(
            game: SlickGame, pos: IPoint, buttonOffset: IPoint, buttonSize: IPoint,
            normal: Image, hover: Image?,
            callback: (Int) -> Unit
        ): SimpleButton {
            return SimpleButton(game, pos + buttonOffset, buttonSize, buttonOffset, normal, hover, callback)
        }
    }
}

data class ButtonImageSet(val normal: Image, val off: Image, val hover: Image, val offHover: Image? = null) {
    companion object {
        fun select2(game: SlickGame, prefix: String): ButtonImageSet {
            val normal = game.getImg("${prefix}_on.png")
            val off = game.getImg("${prefix}_off.png")
            val hover = game.getImg("${prefix}_select2.png")
            return ButtonImageSet(normal, off, hover)
        }

        fun selected(game: SlickGame, prefix: String, withOffHover: Boolean = false): ButtonImageSet {
            val normal = game.getImg("${prefix}_on.png")
            val off = game.getImg("${prefix}_off.png")
            val hover = game.getImg("${prefix}_selected.png")
            var offHover: Image? = null
            if (withOffHover)
                offHover = game.getImg("${prefix}_off_selected.png")
            return ButtonImageSet(normal, off, hover, offHover)
        }
    }
}

object Buttons {
    fun drawRounded(g: Graphics, x: Int, y: Int, width: Int, height: Int, radius: Int) {
        // Note: this loop's range is inclusive, and will run for both 0 and radius.
        for (i in 0..radius) {
            // Draw a bunch of overlapping rectangles, making a diagonal corner.
            g.fillRect(x.f + i, y.f + radius - i, width.f - i * 2, height.f - (radius - i) * 2)
        }
    }

    class JumpButton(pos: IPoint, val ship: Ship, game: SlickGame, private val callback: () -> Unit) :
        Button(game, pos, ConstPoint(74, 29)) {

        private val font = game.getFont("HL2", 2f)

        override val disabled: Boolean get() = !ship.isFtlReady || ship.engines!!.powerSelected == 0

        override fun draw(g: Graphics) {
            val ftlX = pos.x + 6
            val ftlY = pos.y + 4

            game.getImg("img/buttons/FTL/FTL_base.png").draw(pos.x - 7, pos.y - 7)

            val engineOn = ship.engines!!.powerSelected > 0
            if (ship.isFtlCharged) {
                g.color = if (engineOn) Constants.JUMP_READY else Constants.JUMP_DISABLED
                drawRounded(g, pos.x + 5, pos.y + 6, size.x, size.y, 3)

                val textColour = when {
                    !engineOn -> Constants.JUMP_DISABLED_TEXT
                    hovered -> Constants.JUMP_READY_TEXT_HOVER
                    else -> Constants.JUMP_READY_TEXT
                }
                font.drawStringLegacy(ftlX + 8f, ftlY + 18f, "JUMP", textColour)
            } else {
                val suffix = if (engineOn) "" else "_off"
                val width = (ship.ftlChargeProgress * 74).toInt().coerceAtMost(74)
                game.getImg("img/buttons/FTL/FTL_loadingbars$suffix.png").drawSection(ftlX - 1, ftlY + 2, width, 29)
            }
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON) return
            if (!ship.isFtlReady) return

            callback()
        }

        // Apply an offset to make the hoverable area the big yellow centre region - by default the hoverable
        // section starts at the button's 0,0, and the main region is offset. Thus translate the mouse coordinates
        // back so 0,0 becomes the origin of the yellow area.
        override fun contains(x: Int, y: Int) = super.contains(x - 5, y - 7)
    }

    open class BasicButton(
        game: SlickGame, pos: IPoint, size: IPoint, val label: String,
        private val radius: Int, private val font: Font, private val yOffset: Int,
        private val cb: () -> Unit
    ) : Button(game, pos, size) {

        override fun draw(g: Graphics) {
            g.color = when {
                disabled -> Constants.JUMP_DISABLED
                hovered -> Constants.UI_BUTTON_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }
            drawRounded(g, pos.x, pos.y, size.x, size.y, radius)

            val x = (size.x - font.getWidth(label)) / 2f
            font.drawString(pos.x + x, pos.y + yOffset.f, label, Constants.JUMP_DISABLED_TEXT)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                cb()
        }
    }

    class ShipButton(pos: IPoint, game: SlickGame, private val cb: () -> Unit) : Button(game, pos, ConstPoint(60, 41)) {
        private val imgPos = pos - ConstPoint(7, 7)

        private val imgOff = game.getImg("img/statusUI/top_ship_off.png")
        private val imgOn = game.getImg("img/statusUI/top_ship_on.png")
        private val imgHighlight = game.getImg("img/statusUI/top_ship_select2.png")

        override val disabled: Boolean get() = game.isInDanger

        override fun draw(g: Graphics) {
            val img = when {
                disabled -> imgOff
                hovered -> imgHighlight
                else -> imgOn
            }

            img.draw(imgPos)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                cb()
        }
    }

    class StoreButton(pos: IPoint, game: SlickGame, private val callback: () -> Unit) :
        Button(game, pos, ConstPoint(88, 41)) {

        private val imgPos = pos - ConstPoint(7, 7)

        private val imgBase = game.getImg("img/statusUI/top_store_base.png")

        private val font = game.getFont("HL2", 2f)

        override fun draw(g: Graphics) {
            imgBase.draw(imgPos)

            // Change the colour if the button is hovered. Since there's only
            // one image, draw a rounded box over it - this is how vanilla
            // FTL does it.
            if (hovered) {
                g.color = Constants.UI_BUTTON_HOVER
                drawRounded(g, imgPos.x + 12, imgPos.y + 12, 78, 31, 3)
            }

            font.drawString(pos.x + 11f, pos.y + 26f, "STORE", Constants.JUMP_DISABLED_TEXT)
        }

        override fun click(button: Int) {
            if (button == Input.MOUSE_LEFT_BUTTON)
                callback()
        }
    }

    abstract class BlueprintButton(
        pos: IPoint, size: IPoint, game: SlickGame,
        private val defaultImage: ButtonImageSet?
    ) :
        Button(game, pos, size) {

        constructor(pos: IPoint, game: SlickGame, image: ButtonImageSet) :
                this(pos, image.normal.imageSize, game, image)

        open val image: ButtonImageSet
            get() = defaultImage ?: error("BlueprintButton: no background image, image not overridden!")

        private val systemNameFont = game.getFont("c&c", 2f)
        private val weaponNameFont = game.getFont("JustinFont8")

        abstract val blueprint: Blueprint?

        /**
         * Set to true if the button is disabled for some reason other than
         * not having an associated blueprint - for example, you can't buy
         * more weapons when your weapons and cargo bay are full.
         */
        open val customDisabled: Boolean get() = false

        val empty: Boolean get() = blueprint == null

        override val disabled: Boolean get() = empty || customDisabled

        protected val textColour: Color
            get() = when {
                disabled -> Constants.SECTOR_CUTOUT_TEXT
                hovered -> Constants.STORE_BUY_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }

        override fun draw(g: Graphics) {
            val image = when {
                disabled -> image.off
                hovered -> image.hover
                else -> image.normal
            }
            image.draw(pos)

            // Stop Kotlin from complaining the blueprint could change
            val blueprint = this.blueprint ?: return

            when (blueprint) {
                is SystemBlueprint -> {
                    systemNameFont.drawString(
                        pos.x + 48f,
                        pos.y + 26f,
                        blueprint.translateTitle(game),
                        textColour
                    )

                    val icon = game.getImg(blueprint.onIconPath)
                    icon.draw(
                        pos.x - SystemBlueprint.ICON_GLOW + 6f,
                        pos.y - SystemBlueprint.ICON_GLOW + 7f
                    )
                }

                is ShipWeaponBlueprint -> {
                    // Draw the weapon name
                    val name = blueprint.translateShort(game)
                    val nameWindowWidth = 96
                    val nameX = (nameWindowWidth - weaponNameFont.getWidth(name)) / 2

                    weaponNameFont.drawString(
                        pos.x + 11f + nameX,
                        pos.y + 70f,
                        name,
                        textColour
                    )

                    // Draw the weapon icon
                    val iconWindowWidth = 96
                    val iconWindowHeight = 45
                    val icon = blueprint.getLauncher(game).chargedImage

                    // The sprite is rotated 90°, so swap the width and height.
                    val iconX = (iconWindowWidth - icon.height) / 2
                    val iconY = (iconWindowHeight - icon.width) / 2

                    blueprint.drawLauncherUI(game, pos.x + iconX + 11f, pos.y + iconY + 11f)
                }

                is DroneBlueprint -> {
                    // Draw the drone name
                    // TODO deduplicate the name drawing?
                    val name = blueprint.translateShort(game)
                    val nameWindowWidth = 96
                    val nameX = (nameWindowWidth - weaponNameFont.getWidth(name)) / 2

                    weaponNameFont.drawString(
                        pos.x + 11f + nameX,
                        pos.y + 70f,
                        name,
                        textColour
                    )

                    // Draw the drone icon
                    blueprint.drawIconUI(game, pos + ConstPoint(60, 35))
                }

                else -> throw Exception("Can't draw blueprint button for $blueprint")
            }
        }

        // Leave click for child classes to override.
    }

    open class DragDropBlueprintButton(
        homePos: IPoint, game: SlickGame,
        image: ButtonImageSet?, size: IPoint,
        val compatible: (Blueprint) -> Boolean,
        override val blueprint: Blueprint?, val callback: () -> Unit
    ) : BlueprintButton(homePos, size, game, image) {

        constructor(
            homePos: IPoint, game: SlickGame, image: ButtonImageSet,
            compatible: (Blueprint) -> Boolean,
            blueprint: Blueprint?, callback: () -> Unit
        ) : this(homePos, game, image, image.normal.imageSize, compatible, blueprint, callback)

        override val makesHoverNoise: Boolean get() = false

        private val overlay = game.getImg("img/upgradeUI/Equipment/box_overlay_red.png")

        // Null means we're not dragging, non-null indicates the cursor position
        var dragPosition: IPoint? = null

        // If a blueprint is being dragged from another blueprint, this is set to
        // highlight which cells it can and can't be dropped into (this is for
        // drones and weapons).
        var currentlyDraggedBlueprint: Blueprint? = null

        override val disabled: Boolean
            get() {
                // Un-disable the button when a blueprint is being dragged.
                // Otherwise, hovered will always be false and dropping won't work.
                val bp = currentlyDraggedBlueprint
                if (bp != null && compatible(bp)) {
                    return false
                }

                return super.disabled
            }

        override fun draw(g: Graphics) {
            val isCompatible: Boolean? = currentlyDraggedBlueprint?.let(compatible)

            if (dragPosition == null && !empty) {
                super.draw(g)
            } else {
                // Hide the card when the item is being dragged, but use the selected
                // image if the user is dragging an item over us.
                // This is why we also run this path if this button is empty, to get
                // the highlighting.
                val img = when {
                    hovered && isCompatible == true -> image.offHover ?: image.hover
                    else -> image.off
                }
                img.draw(pos)
            }

            val colour = when (isCompatible) {
                null -> return // Nothing being dragged
                true -> Color(100, 255, 100, 127) // Transparent SYS_ENERGY_ACTIVE
                false -> Color(255, 50, 50, 127) // Transparent SYS_ENERGY_BROKEN
            }
            overlay.draw(pos.x.f, pos.y.f, colour)
        }

        open fun drawDrag(g: Graphics) {
            // Stop dragPosition from changing under us (it actually won't,
            // but Kotlin doesn't know that).
            val dragPosition = dragPosition ?: return

            // Draw only the item itself being dragged, without the whole card.
            when (val blueprint = blueprint) {
                is ShipWeaponBlueprint -> {
                    val icon = blueprint.getLauncher(game).chargedImage

                    // The sprite is rotated 90°, so swap the width and height.
                    val iconX = -icon.height / 2
                    val iconY = -icon.width / 2

                    blueprint.drawLauncherUI(game, dragPosition.x + iconX.f, dragPosition.y + iconY.f)
                }

                is DroneBlueprint -> {
                    blueprint.drawIconUI(game, dragPosition)
                }

                else -> throw Exception("Can't draw dragged blueprint for $blueprint")
            }
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (blueprint == null)
                return

            callback()
        }
    }
}
