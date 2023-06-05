package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants
import xyz.znix.xftl.f
import xyz.znix.xftl.math.IPoint
import kotlin.random.Random

/**
 * Displays a message with an optional line pointing to it.
 */
class WarningFlasher(
    val game: InGameState,
    var centre: IPoint,
    key: String,
    val warningWarning: Boolean = true,
    val linePoints: List<IPoint> = emptyList(),
    val colour: WarningColour = WarningColour.RED,
    val animated: Boolean = true
) {
    private val textLines: List<String> = game.translator[key].split("\n")
    private val warningText: String = game.translator["warning_warning"]

    private val glowImage = game.getImg("img/warnings/backglow_warning_${colour.bgName}.png")

    private val font = game.getFont("HL1", 2f)

    private var stopTime: Long = 0

    // Stop all the warnings flashing in sync.
    private val flashOffset = Random.nextLong()

    var timeRemaining: Float = 0f
        private set

    val isRunning: Boolean get() = timeRemaining > 0f

    /**
     * If this flasher is flashing, this checks if it's at it's stronger intensity.
     *
     * This is used for flashing other components when this warning running.
     */
    var isFlashingHigh: Boolean = false
        private set

    fun draw(g: Graphics) {
        update()

        if (!isRunning)
            return

        val alpha = when {
            !animated -> 1f // Not flashing
            isFlashingHigh -> 0.9f
            else -> 0.4f
        }

        val colour = Color(this.colour.colour)
        colour.a = alpha

        val lineSpacing = 18
        val topY = centre.y - lineSpacing * (textLines.size - 1)

        for ((index, line) in textLines.withIndex()) {
            drawStringWithGlow(line, topY + lineSpacing * index, colour, alpha)
        }

        if (warningWarning) {
            val spacing = if (linePoints.isEmpty()) 24 else 23

            drawStringWithGlow(warningText, centre.y - spacing, colour, alpha)
        }

        g.color = colour
        for (i in 0 until linePoints.size - 1) {
            val a = linePoints[i]
            val b = linePoints[i + 1]

            // TODO make the lines properly match FTL.

            g.lineWidth = 2f
            g.drawLine(a.x.f, a.y.f, b.x.f, b.y.f)
            g.lineWidth = 1f
        }
    }

    private fun drawStringWithGlow(text: String, y: Int, colour: Color, alpha: Float) {
        val width = font.getWidth(text)
        val baseX = centre.x - width / 2

        val spaceWidth = font.getWidth(" ")

        // Account for the less-glowing bits of each side of the glow
        val glowMargin = 1

        glowImage.alpha = alpha

        // Draw on the text, with each word glowing separately.
        var x = baseX
        for (word in text.split(' ')) {
            val wordWidth = font.getWidth(word)
            font.drawString(x.f, y.f, word, colour)
            drawGlow(x - glowMargin, y, wordWidth + glowMargin * 2)
            x += wordWidth

            // The space doesn't glow
            x += spaceWidth
        }
    }

    private fun drawGlow(x: Int, y: Int, width: Int) {
        // The glow image is huge, we have to scale it down.
        val scale = 1 / 9f

        // How wide the ends of the glow image are, in the glow image's
        // coordinate system.
        val endWidthImg = 100f

        val endWidthScreen = endWidthImg * scale

        val glowHeight = glowImage.height * scale

        val textMiddle = y - 6
        val glowTopY = textMiddle - glowHeight / 2
        val glowBottomY = glowTopY + glowHeight

        // Left side
        glowImage.draw(
            x.f, glowTopY, x.f + endWidthScreen, glowBottomY,
            0f, 0f, endWidthImg, glowImage.height.f
        )

        // Middle
        glowImage.draw(
            x + endWidthScreen, glowTopY, x.f + width - endWidthScreen, glowBottomY,
            100f, 0f, 200f, glowImage.height.f
        )

        // Right side
        glowImage.draw(
            x.f + width - endWidthScreen, glowTopY, x.f + width, glowBottomY,
            glowImage.width.f - endWidthImg, 0f, glowImage.width.f, glowImage.height.f
        )
    }

    private fun update() {
        val current = System.nanoTime()
        val remainingNS = stopTime - current

        timeRemaining = when {
            remainingNS < 0 -> 0f

            // Guard against overflow, however unlikely that might be.
            remainingNS > (1L shl 50) -> 0f

            else -> remainingNS / 1_000_000_000f
        }

        if (!isRunning) {
            isFlashingHigh = false
        } else {
            // Calculate our flash timer.
            // Don't use the time remaining, as that might be constantly reset.
            val period = 1_000_000_000
            var timeNS = (current + flashOffset) % period
            if (timeNS < 0)
                timeNS += period
            val time = timeNS.toFloat() / period

            isFlashingHigh = time > 0.5f
        }
    }

    fun startFor(time: Float) {
        val current = System.nanoTime()
        stopTime = current + (time * 1_000_000_000f).toLong()
    }

    fun stop() {
        stopTime = 0
    }

    enum class WarningColour(val bgName: String, val colour: Color) {
        RED("red", Constants.WARNING_COLOUR_RED),
        WHITE("white", Constants.WARNING_COLOUR_WHITE),
        GREEN("green", Constants.WARNING_COLOUR_RED), // TODO set the right colour
    }
}
