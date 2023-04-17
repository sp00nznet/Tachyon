package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import org.newdawn.slick.geom.Rectangle
import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.sector.Choice
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.ShipWeaponBlueprint
import kotlin.math.max

class DialogueWindow(val game: SlickGame, val playerShip: Ship, startingEvent: Event, val close: () -> Unit) :
    Window() {

    // We have to include the margin from the glow
    // around the window, which is 7 pixels per side.
    override val size: IPoint get() = ConstPoint(602 + 7 * 2, 377 + 7 * 2)

    override val outlineImage = game.getImg("img/window_base.png")

    private val resourceNumFont = game.getFont("JustinFont10")
    private val font = game.getFont("JustinFont11Bold")

    private val augmentStringWidth = font.getWidth(game.translator["augment"])

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

        val newOptions = ArrayList<EvaluatedEvent>()
        for (choice in event.event.choices)
            newOptions.add(EvaluatedEvent(choice.event.resolve(), game, choice.text.resolve(), choice))

        // Walk backwards from the end of the options list, removing
        // anything that's not suitable. Aside from iteration order,
        // we specifically need to go backwards to keep the last suitable
        // option among many with max_group set.
        val maxGroupHit = HashSet<Int>()
        for (i in newOptions.size - 1 downTo 0) {
            val option = newOptions[i]
            val choice = option.choice!!

            // If we don't have the stuff for this event and it's
            // hidden flag is set, then make it disappear.
            // This is what stops you from seeing blue options you
            // can't select.
            if (choice.hidden && !isConditionsSatisfied(option)) {
                newOptions.removeAt(i)
                continue
            }

            // If multiple options with the same max_group are set,
            // only the last one (first one we see in this iteration
            // order) is kept.
            if (choice.maxGroup == null)
                continue

            if (maxGroupHit.contains(choice.maxGroup)) {
                newOptions.removeAt(i)
                continue
            }
            maxGroupHit.add(choice.maxGroup)
        }

        options = newOptions

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
        if (resourcesGained.hasAnything) {
            // The box is centred 43 pixels below the baseline of the last line of
            // the event text.
            val boxMiddleY = textY + 43

            val boxSize = findResourceBoxSize(resourcesGained)
            val boxX = position.x + (size.x - boxSize.x) / 2
            val boxY = boxMiddleY - boxSize.y / 2
            val boxPos = ConstPoint(boxX, boxY)
            drawResourceBox(g, resourcesGained, boxPos, boxSize, Color.white)

            // The spacing between the event text and the options is constant, regardless
            // of the size of the rewards.
            textY += 95
        } else {
            textY += 45
        }

        val rebuildBBs = optionBoundingBoxes.isEmpty()

        // You can think of each option having a top and bottom Y. For text
        // events there's a spacing between the top of the space and the baseline
        // of the text (TEXT_OPTION_TOP_OFFSET), and then another offset from
        // the baseline to the bottom of the option (which is the top of the next
        // option) - TEXT_OPTION_BOTTOM_MARGIN.
        // When resources are involved, the top of the resource box sits exactly
        // at the option's Y top, and there's 10 pixels (RESOURCE_BOTTOM_MARGIN)
        // between the bottom of the box and the start of the next option.
        for ((i, option) in options.withIndex()) {
            val choice = option.choice
            val enabled = isConditionsSatisfied(option)
            val colour = when {
                !enabled -> Constants.TEXT_OPTION_DISABLED
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
            if (option.choiceResources.hasAnything) {
                val boxSize = findResourceBoxSize(option.choiceResources)

                val textResourceMargin = 10

                // To support text wrapping, we do this in three parts:
                // 1. Figure out the maximum width of the text
                // 2. Draw the text, which tells us how wide it is
                // 3. Draw the resource box to line up with the text, or
                //    at it's maximum x position if the text is multi-line.
                // This can be tested with the ENGI_REFUGEES or
                // ZOLTAN_TRADE_HUB events (the latter with a blue option).
                // (note ENGI_REFUGEES isn't part of the final game, so
                //  you can't test it there).
                // This logic might not exactly match the base game for
                // other events, if so it should be fixed but it's not
                // likely to be a big problem.

                // Find the maximum box position
                val maxBoxRHS = position.x + size.x - 34 - 7
                val maxBoxX = maxBoxRHS - boxSize.x
                val maxTextX = maxBoxX - textResourceMargin

                // Align the text with the middle of the box
                val textTop = textY + (boxSize.y - FONT_HEIGHT) / 2
                val lineY = textTop + FONT_HEIGHT
                val textEndY = drawText(lineY, text, colour, rebuildBBs, maxTextX)

                if (textEndY == lineY) {
                    // The event is only one line long, draw the resource
                    // box after the text.
                    val boxX = textX + font.getWidth(text) + textResourceMargin
                    val boxPos = ConstPoint(boxX, textY)
                    drawResourceBox(g, option.choiceResources, boxPos, boxSize, colour)
                } else {
                    // The text was multiple lines long, draw the resource box
                    // on the right-mode side and centred on the text.
                    // This does leave a notably larger spacing between the line
                    // above as space was made for the box, but that's there in
                    // vanilla FTL too (though slightly shorter - though it's
                    // close enough, and I've had enough of fiddling with
                    // layouts to care about this particular inconsistency).
                    val firstLineTop = lineY - FONT_HEIGHT
                    val textHeight = textEndY - firstLineTop
                    val centreY = firstLineTop + textHeight / 2 - boxSize.y / 2
                    val boxPos = ConstPoint(maxBoxX, centreY)
                    drawResourceBox(g, option.choiceResources, boxPos, boxSize, colour)
                }

                textY = max(textEndY + TEXT_OPTION_BOTTOM_MARGIN, boxSize.y + RESOURCE_BOTTOM_MARGIN)
            } else {
                textY += TEXT_OPTION_TOP_OFFSET
                textY = drawText(textY, text, colour, rebuildBBs)
                textY += TEXT_OPTION_BOTTOM_MARGIN
            }
        }
    }

    private fun findResourceBoxSize(resourceSet: ResourceSet): IPoint {
        var width = RESOURCE_LEFT_START

        // Add up the width used by each of fuel/scrap/drones/missiles
        for ((_, amount) in resourceSet.entries) {
            // For the resource icon and it's padding to the number
            width += RESOURCE_ICON_SPACING

            width += resourceNumFont.getWidth(amount.toString())

            // The padding until the next number, or the end of the box
            width += RESOURCE_RIGHT_SPACING
        }

        var height = 0

        // TODO check this on an image with both scrap and resources
        if (resourceSet.isNotEmpty())
            height += 32

        for (bp in resourceSet.items) {
            val bpWidth: Int = when (bp) {
                is ShipWeaponBlueprint -> {
                    val nameWidth = resourceNumFont.getWidth(game.translator[bp.title!!])
                    val img = bp.getLauncher(game).chargedImage
                    height += img.width + 9
                    10 + img.height + 10 + nameWidth + 16
                }

                is DroneBlueprint -> {
                    val nameWidth = resourceNumFont.getWidth(game.translator[bp.title!!])

                    // Not sure if +9 is appropriate, or iconSize should be bigger.
                    height += bp.iconSize.y + 9

                    10 + bp.iconSize.x + 8 + nameWidth + 12 // Approximate numbers
                }

                is AugmentBlueprint -> {
                    height += 32

                    val nameWidth = resourceNumFont.getWidth(bp.translateTitle(game))
                    10 + augmentStringWidth + 8 + nameWidth + 11
                }

                else -> TODO("Can't draw non-ship/drone/augment blueprint: $bp")
            }

            width = max(width, bpWidth)
        }

        for (crew in resourceSet.crew) {
            // There's 30 pixels for the crew member to fit on the left of their name
            val crewWidth = 30 + resourceNumFont.getWidth(crew.name) + 14
            width = max(width, crewWidth)

            // Always 32 pixels high
            height += 32
        }

        for (crew in resourceSet.lostCrew) {
            val messageKey = if (crew.info.turnHostile) "traitor_crew" else "dead_crew"
            val message = game.translator[messageKey].replace("\\1", crew.crew.selectedName)

            // There's 30 pixels for the crew member to fit on the left
            // of the 'so-and-so is gone' message.
            val crewWidth = 30 + resourceNumFont.getWidth(message) + 14
            width = max(width, crewWidth)

            // Always 32 pixels high
            height += 32
        }

        if (resourceSet.intruders.isNotEmpty()) {
            val message = game.translator["intruder_alert"]
            width = max(width, 32 + resourceNumFont.getWidth(message) + 30)
            height += 32
        }

        return ConstPoint(width, height)
    }

    private fun drawResourceBox(g: Graphics, resourceSet: ResourceSet, pos: IPoint, size: IPoint, textColour: Color) {
        var y = pos.y

        g.color = Constants.REWARDS_BACKGROUND
        g.fillRect(pos.x.f, y.f, size.x.f, size.y.f)

        g.color = textColour
        g.drawRect(pos.x.f, y.f, size.x - 1f, size.y - 1f)
        g.drawRect(pos.x.f + 1, y + 1f, size.x - 3f, size.y - 3f)

        var resourceX = pos.x + RESOURCE_LEFT_START

        for (pair in resourceSet.toList().sortedBy { it.first.ordinal }) {
            // Draw the icon
            val colour = if (pair.second > 0) Constants.REWARDS_ICONS else Constants.REWARDS_NEGATIVE_ICONS
            pair.first.getIcon(game).draw(resourceX.f, y.f, colour)
            resourceX += RESOURCE_ICON_SPACING

            // Draw the text
            val numText = pair.second.toString()
            resourceNumFont.drawString(resourceX.f, y + 21f, numText, textColour)
            resourceX += resourceNumFont.getWidth(numText)

            // Add some padding before the next resource
            resourceX += RESOURCE_RIGHT_SPACING
        }

        // If we drew some resources, move the blueprints down so they don't overlap
        if (resourceSet.isNotEmpty()) {
            y += 32
        }

        for (crew in resourceSet.crew) {
            y = drawRewardCrew(crew, pos.x, y, textColour)
        }

        for (crew in resourceSet.lostCrew) {
            y = drawLostCrew(crew, pos.x, y)
        }

        for (bp in resourceSet.items) {
            y = drawRewardBlueprint(bp, pos.x, y, textColour)
        }

        if (resourceSet.intruders.isNotEmpty()) {
            val message = game.translator["intruder_alert"]
            resourceNumFont.drawString(pos.x + 32f, y + 21f, message, Constants.SYS_ENERGY_BROKEN)
            y += 32
        }
    }

    private fun drawText(
        y: Int, msg: String, colour: Color = Color.white,
        addOption: Boolean = false, maxX: Int = -1
    ): Int {
        // The x coordinate where line wrapping is triggered
        val textFarX = if (maxX != -1) maxX.f else position.x + size.x - 25f

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

    private fun drawRewardBlueprint(bp: Blueprint, boxX: Int, textY: Int, textColour: Color): Int {
        when (bp) {
            is ShipWeaponBlueprint -> {
                val anim = bp.getLauncher(game)

                bp.drawLauncherUI(game, boxX + 10f, textY + 2f)

                // Draw the name
                val name = game.translator[bp.title!!]
                resourceNumFont.drawString(boxX + anim.chargedImage.height + 20f, textY + 22f, name, textColour)

                return textY + 42
            }

            is DroneBlueprint -> {
                // Draw the drone icon - the Y here isn't right, but it's good enough for now. TODO do it properly.
                val height = bp.iconSize.y + 9
                val imgCentreY = textY + height / 2
                bp.drawIconUI(game, ConstPoint(boxX + 21, imgCentreY))

                // Draw the name
                val name = game.translator[bp.title!!]
                val textX = boxX + 10 + bp.iconSize.x + 8
                resourceNumFont.drawString(textX.f, imgCentreY.f + FONT_HEIGHT / 2, name, textColour)

                return 20
            }

            is AugmentBlueprint -> {
                val nameX = boxX + 10 + augmentStringWidth + 8
                val nameY = textY + 21f

                font.drawString(boxX + 10f, nameY, game.translator["augment"], textColour)
                resourceNumFont.drawString(nameX.f, nameY, bp.translateTitle(game), textColour)

                return textY + 32
            }

            else -> TODO("Can't draw non-ship/drone/augment blueprint: $bp")
        }
    }

    private fun drawRewardCrew(crew: AddCrewEval, x: Int, y: Int, textColour: Color): Int {
        // TODO layers, and unify the crew drawing with stores and ShipWindow
        val portrait = game.animations["${crew.race.name}_portrait"].spriteAt(0)

        portrait.draw(x - 2f, y - 2f)

        resourceNumFont.drawString(x + 30f, y + 21f, crew.name, textColour)

        return 32
    }

    private fun drawLostCrew(crew: RemoveCrewEval, x: Int, y: Int): Int {
        // TODO layers, and unify the crew drawing with stores and ShipWindow
        val portrait = game.animations["${crew.crew.codename}_portrait"].spriteAt(0)

        portrait.draw(x - 2f, y - 2f)

        val messageKey = if (crew.info.turnHostile) "traitor_crew" else "dead_crew"
        val message = game.translator[messageKey].replace("\\1", crew.crew.selectedName)
        resourceNumFont.drawString(x + 30f, y + 21f, message, Constants.SYS_ENERGY_BROKEN)

        return 32
    }

    /**
     * Checks if we've got all the stuff required for an event (both
     * consumables and scrap if they're subtracted by the event, and
     * also equipment/crew used for blue options).
     */
    private fun isConditionsSatisfied(event: EvaluatedEvent): Boolean {
        if (event.isContinue)
            return true

        val res = event.resources

        // Check we have enough stuff, if any of the resources are negative
        if (playerShip.scrap < -res.scrap)
            return false
        if (playerShip.missilesCount < -res.missiles)
            return false
        if (playerShip.dronesCount < -res.droneParts)
            return false
        if (playerShip.fuelCount < -res.fuel)
            return false

        // Check for the option requirement, which is usually used
        // for blue-option requirements.
        if (event.choice != null) {
            return checkReq(event.choice)
        }

        return true
    }

    private fun checkReq(choice: Choice): Boolean {
        val req = choice.req ?: return true

        // Req is the requirement name of an event choice. Per Choice.req, it is:
        // "any race, weapon, drone, augmentation or system/subsystem"

        // Check for matching races
        if (playerShip.crew.any { it.codename == req }) {
            return true
        }

        // FIXME until we've implemented all the crew, allow any crew-gated option.
        if (CrewBlueprint.PLAYABLE_RACE_NAMES.contains(req))
            return true

        // Check for matching weapons, drones and augments. We search through all
        // the places a blueprint could be, but none of these checks are specific
        // to that type of item. For example, we could find a weapon in the cargo
        // hold just the same as if it's in the weapons slots.
        fun hasBlueprint(name: String): Boolean {
            if (playerShip.hardpoints.any { it.weapon?.type?.name == name }) {
                return true
            }
            if (playerShip.drones?.drones?.any { it?.type?.type?.name == name } == true) {
                return true
            }
            if (playerShip.augments.any { it.name == name }) {
                return true
            }
            return playerShip.cargoBlueprints.any { it?.name == name }
        }
        if (hasBlueprint(req))
            return true

        // Req can be a blueprint list, in which case match anything from that.
        // For example, DISTRESS_SATELLITE_DEFENSE's ion weapon option.
        val bpList = game.blueprintManager.blueprints[req] as? BlueprintList
        if (bpList != null) {
            for (bp in bpList.list()) {
                if (hasBlueprint(bp.name))
                    return true
            }
        }

        // Check for any matching systems
        val matchingSystem = playerShip.rooms.firstOrNull { it.system?.codename == req }?.system
        if (matchingSystem != null) {
            // Check if we're outside the level bounds of this system
            choice.minLevel?.let { minLevel ->
                if (matchingSystem.energyLevels < minLevel)
                    return false
            }
            choice.maxLevel?.let { maxLevel ->
                if (maxLevel < matchingSystem.energyLevels)
                    return false
            }

            // Note that choice.maxGroup is processed during event setup.

            return true
        }

        // If the system is 'reactor', we just have to check it's
        // between the minimum and maximum level.
        if (req == "reactor") {
            choice.minLevel?.let { minLevel ->
                if (playerShip.purchasedReactorPower < minLevel)
                    return false
            }
            choice.maxLevel?.let { maxLevel ->
                if (maxLevel < playerShip.purchasedReactorPower)
                    return false
            }
            return true
        }

        return false
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

        val choice = options[idx]

        // Block the selection of disabled options
        if (!isConditionsSatisfied(choice))
            return

        // Add any resources the currently-visible event dropped.
        addResources(currentEvent)

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
    private class EvaluatedEvent(
        private val eventInt: Event?,
        game: SlickGame,
        val choiceText: String?,
        val choice: Choice? = null
    ) {
        val isContinue: Boolean = eventInt == null
        val event: Event get() = eventInt ?: throw Exception("Continue does not have an event")
        val resources by lazy { event.resolveResources(game) }
        val text by lazy { event.text?.resolve() }

        /**
         * The resources that should be displayed in a choice. This filters out
         * crew losses, hidden items, and anything else that shouldn't show up.
         */
        val choiceResources: ResourceSet by lazy {
            val result = ResourceSet()

            // If hidden is set, don't show any resources.
            if (choice?.hidden == true)
                return@lazy result

            result += resources
            result.lostCrew.clear()

            if (event.itemsModifySteal) {
                result.scrap = 0
                result.fuel = 0
                result.droneParts = 0
                result.missiles = 0
            }

            return@lazy result
        }
    }

    companion object {
        private const val FONT_HEIGHT = 11

        // Text-only options (no resources) are spaced 32 pixels apart
        private const val TEXT_OPTION_SPACING = 32

        // The pixels between the top of an option and where it's text is placed
        private const val TEXT_OPTION_TOP_OFFSET = 15
        private const val TEXT_OPTION_BOTTOM_MARGIN = TEXT_OPTION_SPACING - TEXT_OPTION_TOP_OFFSET

        private const val RESOURCE_BOTTOM_MARGIN = 10

        private const val RESOURCE_LEFT_START = 5
        private const val RESOURCE_ICON_SPACING = 30
        private const val RESOURCE_RIGHT_SPACING = 9
    }
}
