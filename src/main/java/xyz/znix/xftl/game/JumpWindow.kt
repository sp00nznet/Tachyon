package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.Input
import xyz.znix.xftl.Constants
import xyz.znix.xftl.draw
import xyz.znix.xftl.drawSection
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.sector.Beacon
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Note that the actual window appears at 340, if we want to be resizable we'll have to fix
// that (and the height). Currently we run much smaller than FTL so their size doesn't fit
// for us atm.
class JumpWindow(val game: SlickGame, val jump: (Beacon?) -> Unit) : Window() {
    override val size = ConstPoint(766, 548)
    override val outlineImage = game.getImg("img/window_outline.png")

    private val sectorInfoTab = game.getImg("img/map/side_sector.png")
    private val titleTab = game.getImg("img/map/side_beaconmap.png")
    private val font = game.getFont("HL2", 3f)
    private val cancelButtonOutline = game.getImg("img/main_menus/button_cancel_base.png")
    private val sectorInfoFont = game.getFont("c&cnew", 2f)
    private val beaconLabelFont = game.getFont("HL1")

    private val lineImg = game.getImg("img/map/dotted_line.png")
    private val targetBox = game.getImg("img/map/map_targetbox.png")

    private val beaconShadow = game.getImg("img/map/map_icon_diamond_shadow.png")
    private val beaconYellow = game.getImg("img/map/map_icon_diamond_yellow.png")
    private val beaconBlue = game.getImg("img/map/map_icon_diamond_blue.png")
    private val beaconDanger = game.getImg("img/map/map_icon_triangle_red.png")
    private val beaconOffset = Point(11, 11) - Point(beaconYellow.width / 2, beaconYellow.height / 2)

    private val labelWhite = (1..3).map { "img/map/map_box_white_$it.png" }.map {
        game.getImg(it).copy().apply {
            filter = Image.FILTER_NEAREST
        }
    }

    val cancelButton = Buttons.BasicButton(
        position + size + ConstPoint(10 - cancelButtonOutline.width, 1),
        ConstPoint(124, 30), "CANCEL", game, 6, ::cancelClicked
    )

    val background = game.getImg("img/map/zone_1.png")

    var hovered: Beacon? = null

    init {
        buttons += cancelButton
    }

    override fun draw(g: Graphics) {
        // Draw the background image
        // TODO use a stencil to cut off the bits poking outside the window
        background.draw(position.x + 11, position.y + 11)

        // Test drawing the beacon path
        drawBeaconLinesTo(game.currentBeacon, Constants.WEAPONS_ITEM_CHARGED) { true }

        // Make a local immutable copy to avoid nullability errors
        val hovered = hovered

        if (hovered != null && hovered != game.currentBeacon) {
            // Draw the lines between the hovered beacon and it's neighbours
            // TODO set colour
            drawBeaconLinesTo(hovered, Color.yellow) { it != game.currentBeacon }
        }

        // Draw the beacons
        for (beacon in game.currentBeacon.sector.beacons) {
            val pos = position + beacon.pos + beaconOffset
            beaconShadow.draw(pos)

            val beaconImg = when (beacon.state) {
                Beacon.State.UNVISITED -> beaconYellow
                Beacon.State.VISITED_CLEAR -> beaconBlue
                Beacon.State.VISITED_DANGER -> beaconDanger
            }
            beaconImg.draw(pos)

            if (beacon.event.isDistressBeacon)
                drawBeaconLabel(pos, game.translator["map_icon_distress"])

            if (beacon.hasStore)
                drawBeaconLabel(pos, game.translator["map_icon_store"])

            if (beacon == hovered && beacon != game.currentBeacon && game.currentBeacon.neighbours.contains(hovered)) {
                drawTargetBox(pos)
            }
        }

        // Draw the top-left map label tab
        val tab = "BEACON MAP"
        val tabWidth = UIUtils.drawTab(font, tab, titleTab, position.x.f, position.y.f, 20f, 38f)
        font.drawStringLegacy(position.x + 21f, position.y + 26f, tab, Constants.JUMP_DISABLED_TEXT)

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

        // Draw the sector info
        sectorInfoTab.draw(position.x, position.y + size.y - 27)

        // Cutouts for text
        fun drawCutout(x: Int, y: Int, width: Int, text: String) {
            g.color = Constants.SECTOR_CUTOUT
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

        font.drawStringLegacy(position.x + 13f, position.y + size.y + 8f, "SECTOR", Constants.JUMP_DISABLED_TEXT)

        // TODO use the real sector number once multiple sectors are implemented
        val sectorName = game.translator["sectorname_" + game.currentBeacon.sector.type.name]
        drawCutout(position.x + 141, position.y + size.y - 8, 38, "1")
        drawCutout(position.x + 190, position.y + size.y - 8, 276, sectorName)

        // The top and bottom tabs are slightly different sizes, this compensates for them
        drawSide(Direction.LEFT, 45, size.y - 27)
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        hovered = null

        val closest = game.currentBeacon.sector.beacons.map {
            val bp = it.pos + position + beaconOffset + ConstPoint(16, 16)
            val dist = bp.distToSq(ConstPoint(x, y))
            Pair(it, dist)
        }.minBy { it.second } ?: return

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

        if (!game.currentBeacon.neighbours.contains(hovered))
            return

        jump(hovered)

        // Make sure we set the beacon after we've called the jump callback, so that the event
        // dialogue window doesn't get closed by the callback.
        game.currentBeacon = hovered
    }

    private fun drawTargetBox(pos: IPoint) {
        val secs = System.nanoTime() / 1_000_000_000f
        val timePoint = 6 * secs
        val distFactor = (1 + sin(timePoint % (Math.PI * 2))) / 2
        val spacing = (distFactor * 4).roundToInt()

        targetBox.rotation = 0f
        targetBox.draw(pos.x - spacing, pos.y - spacing)
        targetBox.rotation = 90f
        targetBox.draw(pos.x + spacing, pos.y - spacing)
        targetBox.rotation = 180f
        targetBox.draw(pos.x + spacing, pos.y + spacing)
        targetBox.rotation = 270f
        targetBox.draw(pos.x - spacing, pos.y + spacing)
    }

    private fun drawBeaconLinesTo(beacon: Beacon, colour: Color, predicate: (Beacon) -> Boolean) {
        for (neighbour in beacon.neighbours) {
            if (!predicate(neighbour))
                continue

            drawBeaconLine(beacon, neighbour, colour)
        }
    }

    private fun drawBeaconLine(from: Beacon, to: Beacon, colour: Color) {
        val fromPos = from.pos + position + beaconOffset
        val toPos = to.pos + position + beaconOffset

        // Find the delta vector between the two points we're drawing between, and the length of said vector
        val delta = toPos - fromPos
        val dist = sqrt(delta.distToSq(ConstPoint.ZERO).f).toInt()

        // Setup the rotation settings of the line segment, so it runs along the delta vector
        lineImg.setCenterOfRotation(0f, 1.5f)
        lineImg.rotation = atan2(delta.y.f, delta.x.f) * 180 / Math.PI.toFloat()

        val segmentWidth = 10

        // Step along the path of the vector, drawing images in each place
        for (i in 6..(dist - 5) step segmentWidth) {
            // Find the proportion of how far along we are
            val factor = 1f * i / dist

            // ... and use that to find the position along the lien
            val lPos = Point(fromPos)
            lPos.x += (delta.x * factor).toInt()
            lPos.y += (delta.y * factor).toInt()

            // The beacons are drawn at their image origins, and they're 32px²
            lPos += ConstPoint(16, 16)

            // Draw the line itself
            lineImg.drawSection(lPos.x, lPos.y, segmentWidth, 4, 1, 0, colour)
        }
    }

    private fun drawBeaconLabel(pos: IPoint, text: String) {
        // AFAIK the labels in FTL are drawn with alpha=204, TODO use that

        val strWidth = beaconLabelFont.getWidth(text)

        labelWhite[0].draw(pos.x + 15, pos.y - 17)
        labelWhite[1].draw(pos.x + 34f, pos.y - 17f, strWidth - 8f, 32f)
        labelWhite[2].draw(pos.x + 26 + strWidth, pos.y - 17)

        beaconLabelFont.drawStringLegacy(pos.x + 30f, pos.y - 4f, text, Constants.SECTOR_CUTOUT_TEXT)
    }

    private fun cancelClicked() {
        jump(null)
    }
}
