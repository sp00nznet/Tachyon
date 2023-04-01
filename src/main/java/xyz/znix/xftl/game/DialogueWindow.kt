package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import org.newdawn.slick.geom.Rectangle
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Constants
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.weapons.ShipWeaponBlueprint
import kotlin.math.max

class DialogueWindow(val game: SlickGame, startingEvent: Event, val close: () -> Unit) : Window() {
    override val size: IPoint get() = ConstPoint(602, 377)
    override val outlineImage = game.getImg("img/window_base.png")

    private val resourceNumFont = game.getFont("JustinFont10")
    private val font = game.getFont("JustinFont11Bold")

    private lateinit var currentEvent: EvaluatedEvent
    private lateinit var options: List<EvaluatedEvent>

    private val optionBoundingBoxes = ArrayList<Rectangle>()
    private var hoveredOption: Int? = null

    private val textX get() = position.x + 25

    init {
        loadEvent(EvaluatedEvent(startingEvent, game, null))
    }

    private fun loadEvent(event: EvaluatedEvent) {
        game.loadEventShip(event.event)

        if (event.event.isStore) {
            // This does a few things: show the store on the map, enable the store button
            // and cause the store window to open when this window is closed.
            game.currentBeacon.hasStore = true
        }

        // Events that have no text are valid, usually they are the result of a choice
        // Eg, to give the player some items and close the menu
        if (event.text == null) {
            // Events that have text and give resources don't add the resources
            // until they're closed. That means the addResources call is made
            // when an option is selected. For these events that'll never happen,
            // so add the resources now.
            addResources(event)

            close()
            return
        }

        currentEvent = event
        options = event.event.choices.map { EvaluatedEvent(it.event.resolve(), game, it.text.resolve()) }

        if (options.isEmpty()) {
            options = listOf(EvaluatedEvent(null, game, game.translator["continue"]))
        }

        optionBoundingBoxes.clear()
    }

    override fun draw(g: Graphics) {
        // Draw the frame
        // TODO fix up the stretched bits
        for (dir in Direction.values()) {
            if (dir.isDiagonal) {
                drawCorner(dir)
            } else {
                drawSide(dir)
            }
        }

        outlineImage.draw(
            position.x + 33f, position.y + 36f, position.x + size.x - 33f, position.y + size.y - 36f,
            33f, 36f, 34f, 37f
        )

        // Event text
        //val ev = "The space station here has a traveling merchant who shows you his wares."

        var textY = position.y + 42
        textY = drawText(textY, currentEvent.text!!)

        val resourcesGained = currentEvent.resources
        if (resourcesGained.isNotEmpty() || resourcesGained.items.isNotEmpty()) {
            textY += 27
            val boxSize = findResourceBoxSize(resourcesGained)
            val boxX = position.x + (size.x - boxSize.x) / 2
            val boxPos = ConstPoint(boxX, textY)
            drawResourceBox(g, resourcesGained, boxPos, boxSize)
            textY += boxSize.y

            textY += 51
        } else {
            textY += 60
        }

        val rebuildBBs = optionBoundingBoxes.isEmpty()

        for ((i, option) in options.withIndex()) {
            val choice = if (currentEvent.event.choices.isNotEmpty()) currentEvent.event.choices[i] else null
            val colour = when {
                hoveredOption == i -> Constants.TEXT_OPTION_HOVER
                choice == null -> Color.white
                choice.blue -> Constants.TEXT_OPTION_BLUE
                else -> Color.white
            }

            val prefix = "${i + 1}. "
            val text = prefix + option.choiceText

            // Handle the 'continue' option, since it fails if
            // we try to read its resources.
            if (option.isContinue) {
                drawText(textY, text, colour, rebuildBBs)
                continue
            }

            // Draw the resources for this event, if appropriate
            val hasResources = (option.resources.isNotEmpty() || option.resources.items.isNotEmpty())
            if (hasResources && choice != null && !choice.hidden && !option.event.itemsModifySteal) {
                val boxSize = findResourceBoxSize(option.resources)

                // A normal line of text is 32 pixels high including padding. Thus find
                // out what the intended top of this option's area is.
                val normalPadding = (OPTION_SPACING - FONT_HEIGHT) / 2
                val topY = textY - FONT_HEIGHT - normalPadding

                // Figure out the height of this entry, including padding
                val padding = 8
                val height = boxSize.y + padding

                val boxX = textX + font.getWidth(text) + 10
                val boxY = topY + padding / 2
                val boxPos = ConstPoint(boxX, boxY)
                drawResourceBox(g, option.resources, boxPos, boxSize)

                // Align the text with the middle of the box
                // TODO multiline support - this can be tested with the ENGI_REFUGEES event
                // Since the font is referenced at it's baseline, we go half way down
                // the box to reach it's middle, then down another half-line-width
                // so that the middle of the text will align with the middle of the box.
                val lineY = boxY + (boxSize.y + FONT_HEIGHT) / 2
                drawText(lineY, text, colour, rebuildBBs)
                textY += height
            } else {
                textY = drawText(textY, text, colour, rebuildBBs)
                textY += OPTION_SPACING
            }
        }
    }

    private fun findResourceBoxSize(resourceSet: ResourceSet): IPoint {
        var width = resourceSet.size * 45 + 10
        var height = 0

        // TODO check this on an image with both scrap and resources
        if (resourceSet.isNotEmpty())
            height += 32

        for (bp in resourceSet.items) {
            val bpWidth = when (bp) {
                is ShipWeaponBlueprint -> {
                    val nameWidth = resourceNumFont.getWidth(game.translator[bp.title!!])
                    val img = bp.getLauncher(game).chargedImage
                    height += img.width + 9
                    10 + img.height + 10 + nameWidth + 16
                }

                else -> TODO()
            }

            width = max(width, bpWidth)
        }

        return ConstPoint(width, height)
    }

    private fun drawResourceBox(g: Graphics, resourceSet: ResourceSet, pos: IPoint, size: IPoint) {
        var y = pos.y

        g.color = Constants.REWARDS_BACKGROUND
        g.fillRect(pos.x.f, y.f, size.x.f, size.y.f)

        g.color = Color.white
        g.drawRect(pos.x.f, y.f, size.x - 1f, size.y - 1f)
        g.drawRect(pos.x.f + 1, y + 1f, size.x - 3f, size.y - 3f)

        for ((i, pair) in resourceSet.toList().sortedBy { it.first.ordinal }.withIndex()) {
            val x = pos.x + 5 + 45 * i
            val colour = if (pair.second > 0) Constants.REWARDS_ICONS else Constants.REWARDS_NEGATIVE_ICONS
            pair.first.getIcon(game).draw(x.f, y.f, colour)
            resourceNumFont.drawString(x + 30f, y + 21f, pair.second.toString(), Color.white)
        }

        // If we drew some resources, move the blueprints down so they don't overlap
        if (resourceSet.isNotEmpty()) {
            y += 30
        }

        for (bp in resourceSet.items) {
            y = drawRewardBlueprint(bp, pos.x.f, y)
        }
    }

    private fun drawText(y: Int, msg: String, colour: Color = Color.white, addOption: Boolean = false): Int {
        val textFarX = position.x + size.x - 25f

        var lineText = ""
        var textY = y
        var longestLine = 0
        for (word in msg.split(" ")) {
            if (font.getWidth(lineText + word) + textX > textFarX) {
                longestLine = longestLine.coerceAtLeast(font.getWidth(lineText))
                font.drawString(textX.f, textY.f, lineText, colour)
                lineText = ""
                textY += 20
            }

            lineText += "$word "
        }
        longestLine = longestLine.coerceAtLeast(font.getWidth(lineText))
        font.drawString(textX.f, textY.f, lineText, colour)

        if (addOption) {
            optionBoundingBoxes += Rectangle(textX.f, y.f - 11, longestLine.f, textY - y + 14f)
        }

        return textY
    }

    private fun drawRewardBlueprint(bp: Blueprint, boxX: Float, textY: Int): Int {
        when (bp) {
            is ShipWeaponBlueprint -> {
                val anim = bp.getLauncher(game)

                bp.drawLauncherUI(game, boxX + 10f, textY + 4f)

                // Draw the name
                val name = game.translator[bp.title!!]
                resourceNumFont.drawString(boxX + anim.chargedImage.height + 20f, textY + 24f, name, Color.white)

                return textY + 44
            }

            else -> TODO()
        }
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        hoveredOption = null
        for ((i, bb) in optionBoundingBoxes.withIndex()) {
            if (bb.contains(x.f, y.f)) {
                hoveredOption = i
            }
        }
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        if (button != Input.MOUSE_LEFT_BUTTON) return

        val idx = hoveredOption ?: return

        selectOption(idx)
    }

    fun selectOption(idx: Int) {
        if (idx < 0 || idx >= options.size)
            return

        // Add any resources the currently-visible event dropped.
        addResources(currentEvent)

        val choice = options[idx]

        if (choice.isContinue) {
            close()
            return
        }

        loadEvent(choice)
    }

    private fun addResources(event: EvaluatedEvent) {
        game.givePlayerResources(event.resources)
    }

    /**
     * Represents an event with all the random stuff resolved, such as the title, text and choice text (if applicable)
     */
    private class EvaluatedEvent(private val eventInt: Event?, game: SlickGame, val choiceText: String?) {
        val isContinue: Boolean = eventInt == null
        val event: Event get() = eventInt ?: throw Exception("Continue does not have an event")
        val resources by lazy { event.resolveResources(game) }
        val text by lazy { event.text?.resolve() }
    }

    companion object {
        private const val FONT_HEIGHT = 11
        private const val OPTION_SPACING = 32
    }
}
