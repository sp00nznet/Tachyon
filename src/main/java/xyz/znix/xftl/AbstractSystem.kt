package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.opengl.renderer.Renderer
import org.newdawn.slick.opengl.renderer.SGL
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.systems.Hacking
import xyz.znix.xftl.systems.MainSystem
import xyz.znix.xftl.systems.SystemBlueprint
import kotlin.math.*
import kotlin.reflect.KProperty

abstract class AbstractSystem(val blueprint: SystemBlueprint, elem: Element) {
    val codename: String get() = blueprint.name

    // TODO handle this properly
    var room: Room? = null

    protected val ship: Ship get() = room!!.ship

    var energyLevels: Int = 1
    var damagedEnergyLevels: Int = 0
    val damaged: Boolean get() = damagedEnergyLevels > 0
    val broken: Boolean get() = damagedEnergyLevels >= energyLevels

    private val onInitValues = ArrayList<OnInitWrapper<*>>()

    /**
     * The power limit applied by a <status/> effect at the current beacon,
     * or null if no limit is imposed.
     */
    var scriptedPowerLimit: Int? = null
        set(value) {
            field = value?.coerceIn(0..energyLevels)
            powerStateChanged()
        }

    /**
     * The time remaining until the ion effect wears off.
     */
    var ionTimer: Float = 0f
        set(value) {
            // The UI can't display more than nine levels since there isn't
            // an image for that long.
            field = min(value, 9 * TIME_PER_ION)
        }

    /**
     * The ion damage taken. Note this can be greater than the system power!
     */
    var ionDamage: Int = 0

    // Note that system cooldowns set the ion timer without adding
    // any ion damage, to lock in the power. Hence we need to check
    // the timer instead of the damage.
    val isIonised: Boolean get() = ionTimer > 0

    // Used for calculations by the ship generator.
    // The flagship notably doesn't have it's maximum power set, so
    // use the maximum specified in the system blueprint in that case.
    val aiMaxPower: Int = elem.getAttributeValue("max")?.toInt() ?: blueprint.maxPower

    /**
     * If non-null, this is the hacking system that's attacking this system.
     */
    var hackedBy: Hacking? = null

    /**
     * True if this system is being hacked by a hacking system currently
     * running its hacking pulse.
     */
    val isHackActive: Boolean get() = hackedBy?.active == true

    /**
     * The number of intact energy bars in the system. Ion damage is subtracted from this.
     */
    open val undamagedEnergy: Int
        get() {
            var available = energyLevels

            // Subtract of normal damage *before* applying the power limit,
            // since the unavailable levels act as buffer points.
            available -= damagedEnergyLevels

            scriptedPowerLimit?.let { limit ->
                available = min(limit, available)
            }

            // Ion damage doesn't work similarly to normal damage,
            // and any ion damage will immediately reduce the power even if
            // there is a scripted limit.
            available -= ionDamage

            return max(available, 0)
        }

    var repairProgress: Float = 0f
        private set(value) {
            field = if (damaged) value else 0f
        }

    var sabotageProgress: Float = 0f
        private set(value) {
            field = if (broken) 0f else value
        }

    open fun update(dt: Float) {
        if (!damaged || room?.crew?.none { it.mode == AbstractCrew.SlotType.CREW } == true)
            repairProgress = 0f

        if (broken || room?.crew?.none { it.mode == AbstractCrew.SlotType.INTRUDER } == true)
            sabotageProgress = 0f

        // Check both the damage and timer to avoid somehow getting stuck where
        // one of them is zero and the other isn't.
        if (ionDamage > 0 || ionTimer > 0f) {
            ionTimer -= dt
            if (ionTimer <= 0) {
                ionDamage = 0
                ionTimer = 0f
            }
        }

        // Check if the hacking probe is still in place.
        // This is checked here rather than the hacking system
        // un-setting hackedBy to make the game more robust
        // if one of the ships jumps away or something similar.
        hackedBy?.let {
            if (!it.checkStillAttacking(this))
                hackedBy = null
        }
    }

    open fun drawBackground(g: Graphics) {
    }

    open fun drawForeground(g: Graphics) {
    }

    open fun initialise(ship: Ship) {
        for (item in onInitValues) {
            item.doInit(ship.sys)
        }
    }

    fun drawRoom(g: Graphics) {
        // Draw the system icon
        val room = room!!
        val img = room.ship.sys.getImg(icon)

        val colour = when {
            damagedEnergyLevels == energyLevels -> Constants.SYSTEM_BROKEN
            damagedEnergyLevels > 0 -> Constants.SYSTEM_DAMAGED
            else -> Constants.SYSTEM_NORMAL
        }

        g.drawImage(
            img,
            (room.offsetX + (room.pixelWidth / 2f - img.width / 2f).toInt()).f,
            (room.offsetY + (room.pixelHeight / 2f - img.height / 2f).toInt()).f,
            colour
        )
    }

    open fun dealDamage(damage: Int, ionDamage: Int) {
        // Add the specified amount of damage, but avoid having more damage than we have power (which
        // would come to a negative amount of available power)
        damagedEnergyLevels = (damagedEnergyLevels + damage).coerceAtMost(energyLevels)

        // Apply ion damage
        this.ionDamage += ionDamage
        ionTimer += 5f * ionDamage

        powerStateChanged()
    }

    // Something - anything - happened to the system's power level.
    // Systems should generally override this rather than dealDamage, to include stuff like ionisation or
    // a Zoltan leaving the room.
    protected open fun powerStateChanged() {
    }

    fun repair(progress: Float) {
        var modifiedProgress = progress

        // If a system is hacked, repairs run at half-speed
        if (hackedBy?.isPoweredUp == true) {
            modifiedProgress /= 2f
        }

        repairProgress += modifiedProgress

        if (repairProgress < 1f)
            return

        repairProgress = 0f

        damagedEnergyLevels--
    }

    /**
     * Attack this room with sabotage or fire damage.
     */
    fun attack(damage: Float) {
        if (broken) {
            sabotageProgress = 0f
            return
        }

        sabotageProgress += damage

        if (sabotageProgress < 1f)
            return

        sabotageProgress = 0f

        // When the system breaks, it takes a hull point
        ship.damage(room!!, 1, 1, 0)

        // TODO explosion animation
    }

    open val icon: String = "img/icons/s_${codename}_overlay.png"

    val img: String? = elem.getAttributeValue("img")?.let { i -> "img/ship/interior/$i.png" }

    open val iconColourName: String
        get() = when {
            damagedEnergyLevels == energyLevels -> "red"
            damagedEnergyLevels > 0 -> "orange"
            this is MainSystem && powerSelected == 0 -> "grey"
            else -> "green"
        }

    open fun drawIconAndPower(game: SlickGame, g: Graphics, x: Int, baseY: Int) {
        if (!isIonised) {
            // TODO flash blue when hacking/mind control/cloaking/backup battery is active
            game.getImg("img/icons/s_${codename}_${iconColourName}1.png").draw(x.f, baseY.f)
        } else {
            // Levels are rounded up, so <5s shows 1, <10s shows 2, etc.
            val levels = min(9, ceil(ionTimer / TIME_PER_ION).toInt())

            // Use the appropriate image to match whether this is a subsystem or not
            val imgType = if (this is MainSystem) "ring" else "octa"

            game.getImg("img/icons/locking/s_${imgType}_${levels}_base.png").draw(x.f, baseY.f)

            // Draw the ring around the outside - because this involves drawing
            // a pie-slice from the ring image, it's a bit messy.
            val timerImg = game.getImg("img/icons/locking/s_${imgType}_timer.png")

            // Use a transform to save having to add x/baseY to everything
            g.pushTransform()
            g.translate(x.f, baseY.f)

            val gl = Renderer.get()

            timerImg.bind()
            Color.white.bind()
            gl.glBegin(SGL.GL_TRIANGLES)

            // Figure out the angle around this image we need to cut it at
            val numSegments = 12 // The ring is split up into 12 segments
            val progress = (ionTimer / TIME_PER_ION).rem(1f) // How full the ring is, 0-1
            var segments = ceil(numSegments * progress).toInt() // There's always at least 1 segment visible

            // If the timer is an exact multiple of five seconds,
            // progress will be zero and thus no segments will be
            // displayed. Fix that, as it should be showing all the
            // segments.
            if (segments == 0)
                segments = numSegments

            val angle = Math.PI.toFloat() * 2 * segments / numSegments

            // Find the position of the point that changes with the angle
            val size = timerImg.width.f
            val half = size / 2

            fun point(x: Float, y: Float) {
                gl.glTexCoord2f(
                    x / timerImg.width * timerImg.textureWidth,
                    y / timerImg.height * timerImg.textureHeight
                )
                gl.glVertex3f(x, y, 0f)
            }

            // Draw a bunch of triangles covering different parts of the image,
            // each one covering a specific angle.
            fun drawTriangle(minAngleDeg: Int, maxAngleDeg: Int) {
                val minAngle = Math.toRadians(minAngleDeg.toDouble()).toFloat()
                val maxAngle = Math.toRadians(maxAngleDeg.toDouble()).toFloat()

                val thisAngle = when {
                    angle < minAngle -> return
                    angle > maxAngle -> maxAngle
                    else -> angle
                }

                val px = (sin(thisAngle) * half + half).coerceIn(0f, size)
                val py = (-cos(thisAngle) * half + half).coerceIn(0f, size)

                val minX = (sin(minAngle) * half + half).coerceIn(0f, size)
                val minY = (-cos(minAngle) * half + half).coerceIn(0f, size)

                point(half, half) // The centre
                point(px, py) // The moving corner, or maximum angle
                point(minX, minY) // The point at the minimum angle
            }

            // Draw a bunch of triangles. The constraint with how
            // large they can each be is that the line between
            // their two points can't cut through the middle of the image.
            drawTriangle(0, 45)
            drawTriangle(45, 135)
            drawTriangle(135, 225)
            drawTriangle(225, 270)
            drawTriangle(270, 360)

            gl.glEnd()

            g.popTransform()
        }

        val barX = x + 24f
        val topBarY = baseY + 8f - (energyLevels - 1) * 8

        // Need to grab this as a local so the compiler knows it won't change
        val scriptedPowerLimit = this.scriptedPowerLimit

        for (i in 0 until energyLevels) {
            val y = baseY + 8 - i * 8

            when {
                i >= energyLevels - damagedEnergyLevels -> {
                    // System damaged/broken
                    g.color = Constants.SYS_ENERGY_BROKEN
                    g.drawRect(barX, y.f, (16 - 1).f, (6 - 1).f)
                    g.drawLine(barX, (y + 6).f, barX + 16f, y.f)
                }

                scriptedPowerLimit != null && i >= scriptedPowerLimit -> {
                    // System power limited by a scripted event
                    g.color = Constants.SYS_ENERGY_EVENT_LOCKED
                    g.drawRect(barX, y.f, (16 - 1).f, (6 - 1).f)
                    g.drawLine(barX, (y + 6).f, barX + 16f, y.f)
                }

                this is MainSystem && i >= powerSelected -> {
                    // System depowered
                    g.color = Constants.SYS_ENERGY_DEPOWERED
                    g.drawRect(barX, y.f, (16 - 1).f, (6 - 1).f)
                }

                isIonised -> {
                    // The system is powered at this level (or it's
                    // a subsystem), but ion damage is applied.
                    // This changes the colour of all the remaining power.
                    g.color = Constants.SYSTEM_IONISED
                    g.fillRect(barX, y.f, 16f, 6f)
                }

                else -> {
                    // System powered, or a subsystem that doesn't need powering
                    g.color = Constants.SYS_ENERGY_ACTIVE
                    g.fillRect(barX, y.f, 16f, 6f)
                }
            }

            // The repair bar
            if (i == energyLevels - damagedEnergyLevels) {
                g.color = Constants.SYS_ENERGY_REPAIR
                val width = (16 * repairProgress).toInt()
                g.fillRect(barX + 16 - width, y.f, width.f, 6f)
            }

            // The sabotage bar
            if (i == energyLevels - damagedEnergyLevels - 1) {
                g.color = Constants.SYS_ENERGY_SABOTAGE
                val width = (16 * sabotageProgress).toInt()
                g.fillRect(barX, y.f, width.f, 6f)
            }
        }

        // If the system is ionised, draw the 'locked' bar around it
        if (isIonised) {
            val barsHeight = energyLevels * 8 - 2

            // Two-pixel-wide rectangle around the energy bars
            g.color = Constants.SYSTEM_IONISED
            g.drawRect(barX - 3f, topBarY - 3f, 3f + 16f + 2f, 3f + barsHeight + 2f)
            g.drawRect(barX - 2f, topBarY - 2f, 2f + 16f + 1f, 2f + barsHeight + 1f)

            // Draw the padlock icon at the top
            val lockImg = game.getImg("img/icons/locking/s_lock.png")
            lockImg.draw(barX + 2f - 6f, topBarY - 21f - 6f)
        }

        // If the system is being sabotaged, draw the fist above it
        if (sabotageProgress != 0f) {
            val sabotageIcon = game.getImg("img/icons/s_sabatoge.png") // (sic)
            sabotageIcon.draw(barX - 9, topBarY - 29)
        }
    }

    /**
     * Create any system-specific buttons that go next to the power button in the main UI.
     *
     * For example, this is for stuff like the cloak and teleport buttons on those systems.
     */
    open fun makeExtraButtons(powerPos: IPoint): List<Button> {
        return emptyList()
    }

    /**
     * This is used to create a delegated property that's initialised
     * when the system is initialised. This can be used to load stuff
     * from SlickGame, which is passed as the argument to the lambda.
     *
     * This is used very similarly to [lazy]:
     *
     * val myImage by onInit { it.getImg("...") }
     *
     * This should be preferred to loading images and sounds when
     * required, as this means missing resources will fail when
     * the ship is loaded rather than when the system is used.
     * Obviously this makes finding these issues a lot quicker.
     */
    protected fun <T> onInit(initializer: (SlickGame) -> T): OnInitWrapper<T> {
        val wrapper = OnInitWrapper(initializer)
        onInitValues += wrapper
        return wrapper
    }

    protected class OnInitWrapper<T>(private val fn: (SlickGame) -> T) {
        private var storedValue: T? = null

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return storedValue ?: error("On-init property used before initialisation!")
        }

        fun doInit(game: SlickGame) {
            storedValue = fn(game)
        }
    }

    companion object {
        /**
         * The time in seconds a system is locked per ion damage.
         */
        const val TIME_PER_ION = 5f
    }
}
