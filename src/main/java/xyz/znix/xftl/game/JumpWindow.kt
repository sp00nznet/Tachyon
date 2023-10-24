package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.PIf
import xyz.znix.xftl.Utils
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Sector
import xyz.znix.xftl.sys.Input
import kotlin.math.*

// Note that the actual window appears at 340, if we want to be resizable we'll have to fix
// that (and the height). Currently we run much smaller than FTL so their size doesn't fit
// for us atm.
class JumpWindow(val game: InGameState, showSectorMap: () -> Unit, val jump: (Beacon?) -> Unit) : Window() {
    override val size = ConstPoint(766, 548)

    private val sectorInfoTab = game.getImg("img/map/side_sector.png")
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val nextSectorTab = game.getImg("img/map/side_nextsector.png")
    private val font = game.getFont("HL2", 3f)
    private val cancelButtonOutline = game.getImg("img/main_menus/button_cancel_base.png")
    private val sectorInfoFont = game.getFont("c&cnew", 2f)
    private val beaconLabelFont = game.getFont("HL1")

    private val outlineImage = game.getImg("img/window_outline.png")

    private val lineImg = game.getImg("img/map/dotted_line.png")
    private val targetBox = game.getImg("img/map/map_targetbox.png")

    private val fleetAdvanceImg = game.getImg("img/map/map_warningcircle_point.png")
    private val fleetAdvanceColour = Colour(246, 128, 125, 21)
    private val fleetControlImg = game.getImg("img/map/map_warningcircle.png")
    private val fleetControlTile = game.getImg("img/map/map_warningcircle_tile.png")

    private val beaconShadow = game.getImg("img/map/map_icon_diamond_shadow.png")
    private val beaconEnvironmentHazard = game.getImg("img/map/map_icon_hazard.png")
    private val beaconYellow = game.getImg("img/map/map_icon_diamond_yellow.png")
    private val beaconBlue = game.getImg("img/map/map_icon_diamond_blue.png")
    private val beaconDanger = game.getImg("img/map/map_icon_triangle_red.png")
    private val beaconShipPresent = game.getImg("img/map/map_icon_triangle_yellow.png")
    private val beaconWillOvertakeCircle = game.getImg("img/tutorial/player_circle.png") // This is the right image!
    private val beaconOvertaken = game.getImg("img/map/map_icon_warning.png")

    private val playerShip = game.getImg("img/map/map_icon_ship.png")
    private val playerShipNoFuel = game.getImg("img/map/map_icon_ship_fuel.png")
    private val flagshipIcon = game.getImg("img/map/map_icon_boss.png")

    private val beaconOffset = Point(-beaconYellow.width / 2, -beaconYellow.height / 2)

    // The offsets to the coordinate system the beacons are positioned on
    private val mapBase = Point(0, 0)

    private val sector = game.currentBeacon.sector

    private val flashTimerBase = System.nanoTime()

    // The set of all the beacons we've visited a neighbour of - this
    // is used to figure out which beacons to display information about.
    private val neighbourVisSet = sector.beacons.filter { it.visited }.flatMap { it.neighbours }.toSet()

    private val labelWhite = (1..3).map { game.getImg("img/map/map_box_white_$it.png") }
    private val labelGreen = (1..3).map { game.getImg("img/map/map_box_green_$it.png") }
    private val labelPurple = (1..3).map { game.getImg("img/map/map_box_purple_$it.png") }

    val cancelButton = Buttons.BasicButton(
        game, size + ConstPoint(10 - cancelButtonOutline.width, 1),
        ConstPoint(124, 30), game.translator["button_cancel"],
        3, font, 24,
        ::cancelClicked
    )

    val nextSectorButton = Buttons.BasicButton(
        game, ConstPoint(size.x - nextSectorTab.width - 11 + 19, 11 + 8),
        ConstPoint(226, 36), game.translator["button_nextsector"],
        3, font, 27, showSectorMap
    )

    val background = game.getImg("img/map/zone_1.png")

    var hovered: Beacon? = null

    init {
        buttons += cancelButton

        if (game.currentBeacon.isExit)
            buttons += nextSectorButton
    }

    /**
     * Draw the insides of the window. This must be done with a stencil.
     */
    private fun drawMapContent(g: Graphics) {
        // Draw the background image
        background.draw(position.x + 11f, position.y + 11f)

        mapBase.x = position.x + GLOW + Sector.OFFSET.x
        mapBase.y = position.y + GLOW + Sector.OFFSET.y

        // In the last stand, the danger stripes sit behind everything else.
        // TODO draw the player and boss ships below this.
        if (sector.isLastStand) {
            drawLastStandDangerZone(g)
        }

        // Draw the connections to the adjacent beacons.
        drawBeaconLinesTo(g, game.currentBeacon, Constants.BEACON_LINE_PLAYER) { true }

        // Draw the line showing where the flagship will next jump
        if (sector.flagshipNextBeacon != null) {
            if (!sector.flagshipJumping) {
                // Draw a dotted line if the flagship isn't jumping this turn
                drawBeaconLine(g, sector.flagshipBeacon!!, sector.flagshipNextBeacon!!, Constants.BEACON_LINE_FLAGSHIP)
            } else {
                // Draw a wide, continuous line if the flagship is jumping this turn.
                val a = mapBase + sector.flagshipBeacon!!.pos
                val b = mapBase + sector.flagshipNextBeacon!!.pos

                val width = 10f
                val angle = atan2(a.y.f - b.y, a.x.f - b.x)
                val tangentX = cos(angle + PIf / 2f) * width / 2
                val tangentY = sin(angle + PIf / 2f) * width / 2

                g.drawCustomQuads { renderer ->
                    renderer.pushVert(a.x + tangentX, a.y + tangentY, Constants.BEACON_LINE_FLAGSHIP)
                    renderer.pushVert(a.x - tangentX, a.y - tangentY, Constants.BEACON_LINE_FLAGSHIP)
                    renderer.pushVert(b.x - tangentX, b.y - tangentY, Constants.BEACON_LINE_FLAGSHIP)
                    renderer.pushVert(b.x + tangentX, b.y + tangentY, Constants.BEACON_LINE_FLAGSHIP)
                }

                // Draw the animated flagship on top of it.
                // Note these numbers are approximate.
                val period = 1_500_000_000
                val progress: Float = (System.nanoTime() % period).toFloat() / period
                val movement = 20f + progress * 20f
                val alpha = min(1f, 2f - progress * 2f)

                g.pushTransform()
                g.rotate(a.x.f, a.y.f, Math.toDegrees(angle.toDouble()).toFloat() - 90f)
                flagshipIcon.draw(a.x - 32f, a.y - 32f - movement, Colour(1f, 1f, 1f, alpha))
                g.popTransform()
            }
        }

        // Make a local immutable copy to avoid nullability errors
        val hovered = hovered

        if (hovered != null && hovered != game.currentBeacon) {
            // Draw the lines between the hovered beacon and it's neighbours
            drawBeaconLinesTo(g, hovered, Constants.BEACON_LINE_HOVER) { it != game.currentBeacon }
        }

        val nextFleetPos = Point(sector.dangerZoneCentre)
        nextFleetPos.x += sector.getFleetAdvanceFor(game.currentBeacon)

        // Whether or not the ship is equipped with long-range scanners
        val hasLRS = game.player.hasAugment(AugmentBlueprint.LONG_RANGE_SCANNERS)

        // Draw the beacons
        for (beacon in sector.beacons) {
            val centrePos = mapBase + beacon.pos
            val pos = centrePos + beaconOffset

            val isNeighbour = neighbourVisSet.contains(beacon)
            val showBasicInfo = beacon.visited || sector.mapRevealed || isNeighbour
            val showAdvInfo = beacon.visited || sector.mapRevealed || (isNeighbour && hasLRS)

            if (showAdvInfo && beacon.environmentType.isDangerous) {
                beaconEnvironmentHazard.draw(pos.x - 5, pos.y - 5)
            }

            // Draw the flashing background if this beacon
            // will be overtaken after this jump.
            val willBeOvertaken = beacon.pos.distToSq(nextFleetPos) < Sector.DANGER_ZONE_RADIUS_SQUARED
            if (willBeOvertaken && beacon.state != Beacon.State.OVERTAKEN) {
                val baseTimer = (System.nanoTime() - flashTimerBase) / 1000000000f
                val flashTimer = (baseTimer + beacon.overtakeFlashAnimationOffset).rem(2)

                val opacity = if (flashTimer < 1f) flashTimer else 2f - flashTimer

                beaconWillOvertakeCircle.draw(
                    pos.x + 1f, pos.y + 2f, pos.x + 31f, pos.y + 32f,
                    0f, 0f, 40f, 40f,
                    opacity, Colour.white
                )
            }

            beaconShadow.draw(pos)

            val beaconImg = when (beacon.state) {
                Beacon.State.UNVISITED -> when {
                    showAdvInfo && beacon.event.loadShipName != null -> beaconShipPresent
                    else -> beaconYellow
                }

                Beacon.State.VISITED_CLEAR -> beaconBlue
                Beacon.State.VISITED_DANGER -> beaconDanger
                Beacon.State.OVERTAKEN -> beaconOvertaken
            }
            beaconImg.draw(pos)

            if (beacon.event.isDistressBeacon && !beacon.visited && showBasicInfo && !willBeOvertaken)
                drawBeaconLabel(labelWhite, Constants.SECTOR_CUTOUT_TEXT, pos, game.translator["map_icon_distress"])

            // Note that quests and stores are cleared when the beacon is overrun,
            // so we don't have to check if willBeOvertaken is set.
            if (beacon.hasQuest && !beacon.visited)
                drawBeaconLabel(labelPurple, Constants.SECTOR_CUTOUT_TEXT_PURPLE, pos, game.translator["map_icon_quest"])

            if (beacon.hasStore && showBasicInfo)
                drawBeaconLabel(labelWhite, Constants.SECTOR_CUTOUT_TEXT, pos, game.translator["map_icon_store"])

            if (beacon.isExit)
                drawBeaconLabel(labelGreen, Constants.SECTOR_CUTOUT_TEXT_GREEN, pos, game.translator["map_icon_exit"])

            if (beacon.isBase)
                drawBeaconLabel(labelPurple, Constants.SECTOR_CUTOUT_TEXT_PURPLE, pos, game.translator["map_icon_base"])

            if (beacon == hovered && beacon != game.currentBeacon && game.currentBeacon.neighbours.contains(hovered)) {
                drawTargetBox(g, pos)
            }

            // Draw the player ship rotating around the beacon.

            // We go around once every 20 seconds.
            val periodNS = 20_000_000_000
            val timerNS = (System.nanoTime() % periodNS).toFloat()
            val rotation = timerNS / periodNS * 360f

            if (beacon == game.currentBeacon) {
                @Suppress("IntroduceWhenSubject")
                val icon = when {
                    game.player.fuelCount == 0 -> playerShipNoFuel
                    else -> playerShip
                }

                // These offsets are approximate
                g.pushTransform()
                g.rotate(centrePos.x.f, centrePos.y.f, -rotation)
                icon.draw(centrePos.x - 8, centrePos.y - 32)
                g.popTransform()
            }

            // Draw the flagship rotating around the beacon.
            if (beacon == sector.flagshipBeacon && !sector.flagshipJumping) {
                // These offsets are approximate.
                // Add an arbitrary rotation offset, so the player and boss
                // ships aren't at the same angle.
                g.pushTransform()
                g.rotate(centrePos.x.f, centrePos.y.f, -rotation + 123f)
                flagshipIcon.draw(centrePos.x - 8, centrePos.y - 32)
                g.popTransform()
            }
        }

        // TODO draw the fleet advance after the beacons and the circling ship,
        //  but before the labels (which we currently draw at the same time
        //  as the beacons).
        if (!sector.isLastStand) {
            drawDangerZone(g)
        }

        // For debugging, this can draw the grid the sectors fit in
        if (game.debugFlags.showMapGrid.set) {
            for (x in 0 until Sector.GRID_SIZE.x) {
                for (y in 0 until Sector.GRID_SIZE.y) {
                    g.colour = Colour.red
                    g.drawRect(mapBase.x + x * 110f, mapBase.y + y * 110f, 110f, 110f)
                }
            }
        }
    }

    private fun drawDangerZone(g: Graphics) {
        val dangerZoneRHS = sector.dangerZoneCentre.x + Sector.DANGER_ZONE_RADIUS
        val nextDangerZoneRHS = dangerZoneRHS + sector.getFleetAdvanceFor(game.currentBeacon)
        fleetAdvanceImg.draw(
            mapBase.x + nextDangerZoneRHS - 181f,
            mapBase.y + sector.dangerZoneCentre.y - 498f
        )
        g.colour = fleetAdvanceColour
        g.fillRect(
            position.x.f,
            position.y.f,
            (mapBase.x + nextDangerZoneRHS - 181f) - position.x,
            size.y.f
        )

        var fleetControlX = mapBase.x + dangerZoneRHS - 181f
        val fleetControlY = mapBase.y + sector.dangerZoneCentre.y - 498f
        fleetControlImg.draw(fleetControlX, fleetControlY)

        // Draw a bunch of tiles filling in the area controlled by
        // the fleet but not covered by the curved front image.
        while (fleetControlX >= mapBase.x) {
            fleetControlX -= fleetControlTile.width

            var tileY = fleetControlY
            while (tileY < position.y + size.y) {
                tileY += fleetControlTile.height

                fleetControlTile.draw(fleetControlX, tileY)
            }
        }
    }

    private fun drawLastStandDangerZone(g: Graphics) {
        // The last stand is entirely covered in stripes.
        g.colour = fleetAdvanceColour
        g.fillRect(position.x.f, position.y.f, size.x.f, size.y.f)

        for (tileX in position.x..position.x + size.x step fleetControlTile.width) {
            for (tileY in position.y..position.y + size.y step fleetControlTile.height) {
                fleetControlTile.draw(tileX, tileY)
            }
        }
    }

    override fun draw(g: Graphics) {
        Utils.drawStenciled(Utils.StencilMode.MASKING, {
            // Draw the stencil
            g.colour = Colour.red // Any non-transparent colour will work

            // The glow means the inside of the window has an 11-pixel boundary
            // This does leave a tiny area in the bevelled right-hand corner unstenciled,
            // but it seems unlikely anything will actually draw there.
            g.fillRect(position.x + 11f, position.y + 11f, size.x - 22f, size.y - 22f)
        }, {
            // Draw the contents of the map
            drawMapContent(g)
        })

        // Draw the top-left map label tab
        val tab = game.translator["map_title"]
        val tabWidth = UIUtils.drawTab(font, tab, titleTab, position.x.f, position.y.f, 20f, 38f)
        font.drawString(position.x + GLOW + 14f, position.y + GLOW + 25f, tab, Constants.JUMP_DISABLED_TEXT)

        // Draw the rest of the top, going clockwise to the right side
        drawSide(Direction.UP, tabWidth.toInt())
        drawCorner(Direction.UP_RIGHT)
        drawSide(Direction.RIGHT)

        // Draw the bottom line connecting
        // Note these have to be drawn before the cancel button - see below
        drawSide(Direction.DOWN, sectorInfoTab.width)
        drawCorner(Direction.RIGHT_DOWN)

        // Cancel button
        // This is done in a slightly weird way - the bottom line is drawn exactly how it usually is, but
        // the cancel button is drawn just below it, cutting off part of it's glow. The glow at the edges
        // of the cancel button image are modified specially to fit the glow of the line, so it looks
        // seamless. Note that this must be done after the lines are drawn, otherwise their glow would overlap
        // the cancel button frame.
        cancelButtonOutline.draw(position.x + size.x - cancelButtonOutline.width - 14, position.y + size.y - 7)
        cancelButton.draw(g)

        // Draw the 'next sector' button frame, which has a similar glow trick to the cancel button
        if (game.currentBeacon.isExit) {
            nextSectorTab.draw(position.x + size.x - nextSectorTab.width - 11, position.y + 11)
            nextSectorButton.draw(g)
        }

        // Draw the sector info
        sectorInfoTab.draw(position.x, position.y + size.y - 27)

        // Cutouts for text
        fun drawCutout(x: Int, y: Int, width: Int, text: String) {
            g.colour = Constants.SECTOR_CUTOUT
            g.fillRect(x + 3f, y.f, width - 6f, 2f)
            g.fillRect(x + 2f, y + 1f, width - 4f, 2f)
            g.fillRect(x + 1f, y + 2f, width - 2f, 2f)
            g.fillRect(x.f, y + 3f, width.f, 20f)
            g.fillRect(x + 1f, y + 22f + 0f, width - 2f, 2f)
            g.fillRect(x + 2f, y + 22f + 1f, width - 4f, 2f)
            g.fillRect(x + 3f, y + 22f + 2f, width - 6f, 2f)

            val txtFont = sectorInfoFont
            val textWidth = txtFont.getWidth(text)
            val tx = width / 2 - textWidth / 2
            txtFont.drawString(x + tx.f, y + 20f, text, Constants.SECTOR_CUTOUT_TEXT)
        }

        val sectorText = game.translator["map_sector"]
        val sectorTextY = position.y + size.y - GLOW + 22f
        font.drawString(position.x + 13f, sectorTextY, sectorText, Constants.JUMP_DISABLED_TEXT)

        val sectorName = game.translator["sectorname_" + sector.type.name]
        drawCutout(position.x + 141, position.y + size.y - 8, 38, (sector.sectorNumber + 1).toString())
        drawCutout(position.x + 190, position.y + size.y - 8, 276, sectorName)

        // The top and bottom tabs are slightly different sizes, this compensates for them
        drawSide(Direction.LEFT, 45, size.y - 27)
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        hovered = null

        val closest = sector.beacons.map {
            val bp = it.pos + mapBase
            val dist = bp.distToSq(ConstPoint(x, y))
            Pair(it, dist)
        }.minByOrNull { it.second } ?: return

        val hoverDist = 12
        if (closest.second > hoverDist * hoverDist)
            return

        hovered = closest.first
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        super.mouseClick(button, x, y)

        if (button != Input.MOUSE_LEFT_BUTTON)
            return

        val hovered = hovered ?: return

        // If the 'aj' (anyjump) flag is set, the player can jump to any
        // beacon on the map, which is obviously convenient for testing.
        if (!game.currentBeacon.neighbours.contains(hovered) && !game.debugFlags.anyJump.set)
            return

        jump(hovered)

        // Advance the fleet pursuit *before* changing the beacon, since
        // if we're in a nebula that slows down the fleet pursuit.
        game.advanceFleet()

        // Make sure we set the beacon after we've called the jump callback, so that the event
        // dialogue window doesn't get closed by the callback.
        game.currentBeacon = hovered
    }

    override fun escapePressed() {
        cancelClicked()
    }

    private fun drawTargetBox(g: Graphics, pos: IPoint) {
        drawTargetMarkers(g, targetBox, pos.x + targetBox.width / 2, pos.y + targetBox.height / 2)
    }

    private fun drawBeaconLinesTo(g: Graphics, beacon: Beacon, colour: Colour, predicate: (Beacon) -> Boolean) {
        for (neighbour in beacon.neighbours) {
            if (!predicate(neighbour))
                continue

            drawBeaconLine(g, beacon, neighbour, colour)
        }
    }

    private fun drawBeaconLine(g: Graphics, from: Beacon, to: Beacon, colour: Colour) {
        g.pushTransform()

        val fromPos = from.pos + mapBase + beaconOffset
        val toPos = to.pos + mapBase + beaconOffset

        // Find the delta vector between the two points we're drawing between, and the length of said vector
        val delta = toPos - fromPos
        val dist = sqrt(delta.distToSq(ConstPoint.ZERO).f).toInt()
        val angle = atan2(delta.y.f, delta.x.f) * 180 / PIf

        // Translate to the start position, and rotate so that positive x runs along our line.
        // The beacons are drawn at their image origins, and they're 32px² - so +16 for x,y.
        g.translate(fromPos.x + 16f, fromPos.y + 16f)
        g.rotate(0f, 1.5f, angle)

        val segmentWidth = 10

        // Step along the path of the vector, drawing images in each place
        for (i in 6..(dist - 5) step segmentWidth) {
            // Draw the line itself
            lineImg.drawSection(i, 0, segmentWidth, 4, 1, 0, colour = colour)
        }

        g.popTransform()
    }

    private fun drawBeaconLabel(images: List<Image>, colour: Colour, pos: IPoint, text: String) {
        // AFAIK the labels in FTL are drawn with alpha=204, TODO use that

        val strWidth = beaconLabelFont.getWidth(text)

        val boxTop = pos.y - 11
        val boxTopWithGlow = boxTop - 6

        images[0].draw(pos.x + 15, boxTopWithGlow)
        images[1].draw(pos.x + 34f, boxTopWithGlow.f, strWidth - 8f, 32f)
        images[2].draw(pos.x + 26 + strWidth, boxTopWithGlow)

        beaconLabelFont.drawString(pos.x + 30f, boxTop + 10f, text, colour)
    }

    private fun drawCorner(edge: Direction) {
        val x = position.x + edge.x.coerceAtLeast(0) * (size.x - 33)
        val y = position.y + edge.y.coerceAtLeast(0) * (size.y - 36)
        val tx = edge.x.coerceAtLeast(0) * outlineImage.width / 2
        val ty = edge.y.coerceAtLeast(0) * outlineImage.height / 2

        outlineImage.drawSection(x, y, 33, 36, tx, ty)
    }

    private fun drawSide(edge: Direction, start: Int? = null, stop: Int? = null) {
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

    private fun cancelClicked() {
        jump(null)
    }

    companion object {
        // The width of the glow around the edge of the window
        private const val GLOW = 7

        /**
         * This draws the little rounded corners that move in and out, which is used
         * both on the beacon and sector map.
         */
        fun drawTargetMarkers(g: Graphics, targetBox: Image, centreX: Int, centreY: Int) {
            val period = 1_000_000_000
            val timePoint = (System.nanoTime() % period) / period.toFloat()
            val distFactor = (1 + sin(timePoint * (Math.PI * 2))) / 2
            val spacing = (distFactor * 4).roundToInt()

            g.pushTransform()
            g.translate(centreX.f, centreY.f)

            val boxX = -spacing - targetBox.width / 2
            val boxY = -spacing - targetBox.height / 2

            targetBox.draw(boxX, boxY)
            g.rotate(0f, 0f, 90f)
            targetBox.draw(boxX, boxY)
            g.rotate(0f, 0f, 90f)
            targetBox.draw(boxX, boxY)
            g.rotate(0f, 0f, 90f)
            targetBox.draw(boxX, boxY)

            g.popTransform()
        }
    }
}
