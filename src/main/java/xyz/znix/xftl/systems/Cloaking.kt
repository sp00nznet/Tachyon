package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.f
import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.ButtonImageSet
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class Cloaking(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
    override val sortingType: SortingType get() = SortingType.CLOAKING

    /**
     * The time remaining on the cloak, or null if the cloak is inactive.
     */
    var timeRemaining: Float? = null

    val active: Boolean get() = timeRemaining != null

    // Note we clamp it to a minimum of one power, to avoid divide-by-zero errors
    // in unexpected conditions.
    val duration: Float get() = max(powerSelected, 1) * TIME_PER_POWER

    // Used for managing the fade-in and fade-out animations
    private var animationTimer: Float = 0f

    /**
     * Get the strength of the cloak hull image from 0-1.
     */
    val cloakFade: Float
        get() {
            // The part of the timer remaining, from 0-1
            val remaining = animationTimer / FADE_TIMER

            if (active) {
                return 1 - remaining
            } else {
                return remaining
            }
        }

    override fun makeExtraButtons(powerPos: IPoint): List<Button> {
        // The height represents how long the cloak runs for
        val height = 11 + powerSelected * 12

        val buttonPos = powerPos + ConstPoint(27, -3 - height)

        return listOf(CloakButton(powerPos, buttonPos, ConstPoint(24, height)))
    }

    override fun powerStateChanged() {
        super.powerStateChanged()

        // We have to update the UI, since the button height changes
        ship.sys.shipUI.updateButtons()
    }

    override fun update(dt: Float) {
        super.update(dt)

        animationTimer = max(0f, animationTimer - dt)

        if (timeRemaining != null) {
            timeRemaining = timeRemaining!! - dt

            // If the system is damaged, that caps the amount of remaining cloak time
            // Note that this means some damage won't have an effect - if
            // a power bar is damaged after it's contribution to the cloak is
            // over, it won't have any effect.
            timeRemaining = min(timeRemaining!!, duration)

            // We have to check for powerSelected here, since the above
            // won't work if the system is fully broken since duration is
            // always at least 5 seconds.
            if (timeRemaining!! <= 0 || powerSelected == 0) {
                timeRemaining = null

                // Don't apply ion damage - this means we hold onto
                // the power until the cooldown is finished (or
                // the system is damaged)
                ionTimer += COOLDOWN

                animationTimer = FADE_TIMER
            }
        }
    }

    private inner class CloakButton(val powerPos: IPoint, pos: IPoint, size: IPoint) : Button(pos, size) {
        // If the system is unpowered, it shows the disabled level-1 image
        val pwr = max(powerSelected, 1)

        val base = ship.sys.getImg("img/systemUI/button_cloaking${pwr}_base.png")
        val buttonImage = ButtonImageSet.select2(ship.sys, "img/systemUI/button_cloaking${pwr}")
        val timerIcon = ship.sys.getImg("img/systemUI/button_cloaking${pwr}_charging_on.png")

        override fun draw(g: Graphics) {
            // Note all the images are the same size

            // 23px between the left-hand side (LHS) of the power button and the LHS of the cloak button background
            //  6px of padding inside the power button background image
            val imageX = powerPos.x + 23f - 6f

            // 17px between the top of the power icon and the bottom of the button background
            // 79px between the bottom of the button background and it's top
            //  7px of padding between the top of the background and the top of it's image
            val imageY = powerPos.y + 17f - 79f - 7f

            // Draw the outline image
            base.draw(imageX, imageY)

            // Draw the button itself
            val image = when {
                active -> timerIcon
                powerSelected == 0 -> buttonImage.off
                hovered -> buttonImage.hover
                else -> buttonImage.normal
            }

            val time = timeRemaining
            val height = if (time != null) {
                // If we're cloaked, figure out how much of the image we should show.
                // The level 1,2,3,4 (there's four levels in the images) have 5,8,11,14 bars
                // The bars each represent a different amount of time, so combined
                // they represent the full cloak duration.
                // TODO what happens if the cloaking system is damaged while active?
                val totalBars = 2 + powerSelected * 3
                val timePerBar = duration / totalBars

                // Round up, so at least one bar is always visible.
                val visibleBars = ceil(time / timePerBar).toInt()
                26 + visibleBars * 4
            } else {
                image.height
            }

            // Draw the bottom {height} pixels of the image
            val topY = image.height - height
            image.draw(
                imageX, imageY + topY, imageX + image.width, imageY + image.height,
                0f, topY.f, image.width.f, image.height.f
            )

            // Debugging aid:
            // g.color = Color.red
            // g.drawRect(imageX, imageY + topY, 5f, height.f)
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            // Stop the clock from being activated when it's on cooldown
            if (isPowerLocked)
                return

            if (powerSelected == 0)
                return

            timeRemaining = duration
            animationTimer = FADE_TIMER
        }
    }

    companion object {
        const val COOLDOWN = 20f
        const val TIME_PER_POWER = 5f
        const val FADE_TIMER = 0.5f
    }
}
