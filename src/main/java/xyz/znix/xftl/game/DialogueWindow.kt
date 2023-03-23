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

class DialogueWindow(val game: SlickGame, startingEvent: Event, val close: () -> Unit) : Window(ConstPoint(100, 100)) {
    override val size: IPoint get() = ConstPoint(602, 377)
    override val outlineImage = game.getImg("img/window_base.png")

    private val resourceNumFont = game.getFont("JustinFont10")
    private val font = game.getFont("JustinFont11Bold")

    private lateinit var currentEvent: EvaluatedEvent
    private lateinit var options: List<EvaluatedEvent>

    private val optionBoundingBoxes = ArrayList<Rectangle>()
    private var hoveredOption: Int? = null

    init {
        loadEvent(EvaluatedEvent(startingEvent, game, null))
    }

    private fun loadEvent(event: EvaluatedEvent) {
        game.loadEventShip(event.event)

        if (event.event.isStore && !game.currentBeacon.hasStore) {
            // This does a few things: show the store on the map, enable the store button
            // and cause the store window to open when this window is closed.
            // Don't do this though if a store is already open, since we'd reset its contents.
            game.currentBeacon.store = StoreData()
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
            val boxWidth = boxSize.x
            val boxX = position.x.f + (size.x - boxWidth) / 2

            g.color = Constants.REWARDS_BACKGROUND
            g.fillRect(boxX, textY.f, boxWidth.f, boxSize.y.f + 2f)

            g.color = Color.white
            g.drawRect(boxX, textY.f, boxWidth - 1f, boxSize.y + 1f)
            g.drawRect(boxX + 1, textY.f + 1, boxWidth - 3f, boxSize.y - 1f)

            for ((i, pair) in resourcesGained.toList().sortedBy { it.first.ordinal }.withIndex()) {
                val x = boxX + 5 + 45 * i
                pair.first.getIcon(game).draw(x, textY.f, Constants.REWARDS_ICONS)
                resourceNumFont.drawString(x + 30, textY + 21f, pair.second.toString(), Color.white)
            }

            if (resourcesGained.isNotEmpty()) textY += 23

            for (bp in resourcesGained.items) {
                textY = drawRewardBlueprint(bp, boxX, textY)
            }
        }

        textY += 60

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
            textY = drawText(textY, prefix + option.choiceText, colour, rebuildBBs)
            textY += 32
        }
    }

    private fun findResourceBoxSize(resourceSet: ResourceSet): IPoint {
        var width = resourceSet.size * 45 + 10
        var height = 0

        // TODO is this right? Check with a weapon-only reward
        if (resourceSet.isNotEmpty())
            height += 30

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

    private fun drawText(y: Int, msg: String, colour: Color = Color.white, addOption: Boolean = false): Int {
        val textX = position.x + 25f
        val textFarX = position.x + size.x - 25f

        var lineText = ""
        var textY = y
        var longestLine = 0
        for (word in msg.split(" ")) {
            if (font.getWidth(lineText + word) + textX > textFarX) {
                longestLine = longestLine.coerceAtLeast(font.getWidth(lineText))
                font.drawString(textX, textY.f, lineText, colour)
                lineText = ""
                textY += 20
            }

            lineText += "$word "
        }
        longestLine = longestLine.coerceAtLeast(font.getWidth(lineText))
        font.drawString(textX, textY.f, lineText, colour)

        if (addOption) {
            optionBoundingBoxes += Rectangle(textX, y.f - 11, longestLine.f, textY - y + 14f)
        }

        return textY
    }

    private fun drawRewardBlueprint(bp: Blueprint, boxX: Float, textY: Int): Int {
        when (bp) {
            is ShipWeaponBlueprint -> {
                val anim = game.animations.weaponAnimations.getValue(bp.launcher)

                // Flip and rotate the sprite appropriately to make it loop like it's mounted above
                // a horizontal surface.
                val spr = anim.spriteAt(anim.chargedFrame).getFlippedCopy(true, false)
                spr.setCenterOfRotation(0f, 0f)
                spr.rotate(90f)

                // Note we have to add the width (height, but we've rotated it 90°) to fix
                // up the offset caused by the rotation
                spr.draw(boxX + spr.height + 10f, textY + 9f)

                // Draw the name
                val name = game.translator[bp.title!!]
                resourceNumFont.drawString(boxX + spr.height + 20f, textY.f + 31, name, Color.white)

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
}
