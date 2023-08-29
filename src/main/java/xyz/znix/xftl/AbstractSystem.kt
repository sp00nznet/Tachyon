package xyz.znix.xftl

import org.jdom2.Element
import org.lwjgl.opengl.GL11
import org.newdawn.slick.Color
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.crew.SkillLevel
import xyz.znix.xftl.game.Button
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.Direction
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.systems.*
import kotlin.math.*
import kotlin.reflect.KProperty

abstract class AbstractSystem(val blueprint: SystemBlueprint) {
    val codename: String get() = blueprint.type

    // The title and description of the system shown in the game window.
    // The Artillery system uses this to copy it's weapon's title/desc.
    open val title: GameText? get() = blueprint.title
    open val description: GameText? get() = blueprint.desc

    lateinit var configuration: SystemInstallConfiguration
    var room: Room? = null

    protected val ship: Ship get() = room!!.ship

    var energyLevels: Int = 1
    var damagedEnergyLevels: Int = 0
    val damaged: Boolean get() = damagedEnergyLevels > 0
    val broken: Boolean get() = damagedEnergyLevels >= energyLevels

    private val onInitValues = ArrayList<OnInitWrapper<*>>()

    val info: SystemInfo = blueprint.info ?: error("System $codename doesn't have system info set!")

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
     * If non-null, this is the ion-induced limit for how much
     * power this system can use.
     *
     * This is based on the power selected when the ion first hit.
     */
    var ionPowerLimit: Int? = null

    // Note that system cooldowns set the ion timer without adding
    // any ion damage, to lock in the power. Hence we need to check
    // the timer instead of the damage.
    val isIonised: Boolean get() = ionTimer > 0

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

            // If ion damage is applied, we can't increase the amount
            // of power beyond the limit it set.
            ionPowerLimit?.let { available = min(available, it) }

            return max(available, 0)
        }

    var repairProgress: Float = 0f
        private set(value) {
            field = if (damaged) value else 0f
        }

    var damageProgress: Float = 0f
        private set(value) {
            field = if (broken) 0f else value
        }

    /**
     * Get the crewmember that's currently manning this system.
     */
    val manningCrew: LivingCrew?
        get() {
            return room!!.crew.firstOrNull { it.currentAction == AbstractCrew.Action.MANNING } as? LivingCrew
        }

    open fun update(dt: Float) {
        if (!damaged || room?.crew?.none { it.mode == AbstractCrew.SlotType.CREW } == true)
            repairProgress = 0f

        // Damage is shared between fires and boarders, we can undo it
        // if there's neither of them here.
        val hasIntruders = room?.crew?.any { it.mode == AbstractCrew.SlotType.INTRUDER } == true
        val hasFire = room?.fires?.any { it != null } == true
        if (broken || !(hasIntruders || hasFire))
            damageProgress = 0f

        // Check both the damage and timer to avoid somehow getting stuck where
        // one of them is zero and the other isn't.
        if (ionPowerLimit != null || ionTimer > 0f) {
            if (ship.sys.debugFlags.noIon.set)
                ionTimer = 0f

            ionTimer -= dt
            if (ionTimer <= 0) {
                ionPowerLimit = null
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

    open fun drawRoom(g: Graphics) {
        // Draw the system icon
        val room = room!!
        val img = room.ship.sys.getImg(blueprint.roomIconPath)

        val colour = when {
            damagedEnergyLevels == energyLevels -> Constants.SYSTEM_BROKEN
            damagedEnergyLevels > 0 -> Constants.SYSTEM_DAMAGED
            else -> Constants.SYSTEM_NORMAL
        }

        img.draw(
            (room.offsetX + (room.pixelWidth / 2f - img.width / 2f).toInt()).f,
            (room.offsetY + (room.pixelHeight / 2f - img.height / 2f).toInt()).f,
            colour
        )
    }

    open fun dealDamage(damage: Int, ionDamage: Int) {
        // Add the specified amount of damage, but avoid having more damage than we have power (which
        // would come to a negative amount of available power)
        damagedEnergyLevels = (damagedEnergyLevels + damage).coerceAtMost(energyLevels)

        // Apply ion damage. If we're not already ion-locked, use
        // the current power (don't mind the ugly hack to get it).
        // Otherwise, reduce relative to the current ion limit which
        // is included in undamagedEnergy.
        // The reason we don't always use powerSelected is because
        // of things like weapons and shields, where ions would otherwise
        // always take down multiple power worth of stuff.
        var basePower = undamagedEnergy
        if (ionPowerLimit == null && this is MainSystem) {
            basePower = min(basePower, this.powerSelected)
        }
        ionPowerLimit = basePower - ionDamage
        ionTimer += 5f * ionDamage

        powerStateChanged()
    }

    open fun onJump() {
    }

    // Something - anything - happened to the system's power level.
    // Systems should generally override this rather than dealDamage, to include stuff like ionisation or
    // a Zoltan leaving the room.
    open fun powerStateChanged() {
    }

    /**
     * Contributes some progress towards repairing this system.
     *
     * Returns true if the system was repaired by a level, and
     * some repair experience should be awarded.
     */
    fun repair(progress: Float): Boolean {
        var modifiedProgress = progress

        // If a system is hacked, repairs run at half-speed
        if (hackedBy?.isPoweredUp == true) {
            modifiedProgress /= 2f
        }

        repairProgress += modifiedProgress

        if (repairProgress < 1f)
            return false

        repairProgress = 0f

        damagedEnergyLevels--

        return true
    }

    /**
     * Attack this room with sabotage or fire damage.
     *
     * Returns true if this bit of damage broke the system, and experience should be awarded.
     */
    fun attack(damage: Float): Boolean {
        if (broken) {
            damageProgress = 0f
            return false
        }

        damageProgress += damage

        if (damageProgress < 1f)
            return false

        damageProgress = 0f

        // This bar of the system is broken.
        ship.damage(room!!, 0, 1, 0)

        if (!broken) {
            return true
        }

        // When the system breaks, it does a hull point of damage.
        ship.damage(room!!, 1, 0, 0)

        // Play the explosion animation
        val animation = ship.sys.animations["explosion1"]
        ship.playCentredAnimation(animation, room!!.pixelCentre)

        return true
    }

    open val iconColourName: String
        get() = when {
            damagedEnergyLevels == energyLevels -> "red"
            damagedEnergyLevels > 0 -> "orange"
            this is MainSystem && powerSelected == 0 -> "grey"
            else -> "green"
        }

    /**
     * Draw the system icon and it's power bars.
     *
     * [x] and [y] specify the top-left corner of the box that contains
     * the system icon, NOT including the 19-pixel glow around the icon.
     */
    open fun drawIconAndPower(game: InGameState, g: Graphics, x: Int, y: Int) {
        // Account for the 19px of padding
        val iconX = x - 19
        val iconY = y - 19

        if (!isIonised) {
            // TODO flash blue when hacking/mind control/cloaking/backup battery is active
            game.getImg("img/icons/s_${codename}_${iconColourName}1.png").draw(iconX, iconY)
        } else {
            // Levels are rounded up, so <5s shows 1, <10s shows 2, etc.
            val levels = min(9, ceil(ionTimer / TIME_PER_ION).toInt())

            // Use the appropriate image to match whether this is a subsystem or not
            val imgType = if (this is MainSystem) "ring" else "octa"

            game.getImg("img/icons/locking/s_${imgType}_${levels}_base.png").draw(iconX, iconY)

            // Draw the ring around the outside - because this involves drawing
            // a pie-slice from the ring image, it's a bit messy.
            val timerImg = game.getImg("img/icons/locking/s_${imgType}_timer.png")

            // Use a transform to save having to add x/baseY to everything
            g.pushTransform()
            g.translate(iconX.f, iconY.f)

            timerImg.bind()
            Color.white.bind()
            GL11.glBegin(GL11.GL_TRIANGLES)

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

            val angle = TWO_PI * segments / numSegments

            // Find the position of the point that changes with the angle
            val size = timerImg.width.f
            val half = size / 2

            fun point(x: Float, y: Float) {
                GL11.glTexCoord2f(
                    x / timerImg.texture.rawTextureWidth,
                    y / timerImg.texture.rawTextureHeight
                )
                Graphics.glVertexTransformed(x, y)
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

            GL11.glEnd()

            g.popTransform()
        }

        val barX = x + 5

        // Draw the power bars, so that systems can override this relatively easily.
        val topBarY = drawPowerBars(g, barX, y - 11 + 6)

        // If a status icon (ion or hacking) is drawn, this is the Y of it's base.
        var statusIconY = topBarY - 3

        fun drawIonOrHackBar() {
            val barsHeight = energyLevels * 8 - 2

            // Two-pixel-wide rectangle around the energy bars
            g.drawRect(barX - 3f, topBarY - 3f, 3f + 16f + 2f, 3f + barsHeight + 2f)
            g.drawRect(barX - 2f, topBarY - 2f, 2f + 16f + 1f, 2f + barsHeight + 1f)

            statusIconY -= 3
        }

        if (isHackActive) {
            // When this system is actively being hacked, draw a purple
            // outline around it.
            g.colour = Constants.SYSTEM_HACKED
            drawIonOrHackBar()
        } else if (isIonised) {
            // If the system is ionised, draw the 'locked' bar around it
            g.colour = Constants.SYSTEM_IONISED
            drawIonOrHackBar()

            // Draw the padlock icon at the top
            val lockImg = game.getImg("img/icons/locking/s_lock.png")
            lockImg.draw(barX + 2f - 6f, statusIconY - 21f)

            // If we've got ion damage and a hacking probe connected,
            // the latter is shifted upwards.
            statusIconY -= 21
        }

        // If this room has a hacking probe attached and turned on - whether
        // or not it's in a hacking pulse - then draw the hacking laptop icon.
        if (hackedBy?.isPoweredUp == true) {
            // Draw the hacking icon at the top
            val lockImg = game.getImg("img/icons/s_hacked.png")
            lockImg.draw(barX + 1f - 9f, statusIconY - 24f)
        }

        // If the system is being burnt or sabotaged, draw the fist above it
        val hasIntruders = room!!.crew.any { it.mode == AbstractCrew.SlotType.INTRUDER }
        val hasFires = room!!.fires.any { it != null }

        if (damageProgress != 0f && hasIntruders) {
            val sabotageIcon = game.getImg("img/icons/s_sabatoge.png") // (sic)
            sabotageIcon.draw(barX - 9, statusIconY - 26)
            statusIconY -= 24
        }

        if (hasFires) {
            val fireIcon = game.getImg("img/icons/s_fire2.png")
            fireIcon.draw(barX - 9, statusIconY - 24)
        }
    }

    /**
     * Draw the power bars themselves, returning the Y of the top of the top bar.
     *
     * @param yOfBottom The Y of the bottom of the lowest bar in the stack.
     */
    protected abstract fun drawPowerBars(g: Graphics, x: Int, yOfBottom: Int): Int

    /**
     * Create any system-specific buttons that go next to the power button in the main UI.
     *
     * For example, this is for stuff like the cloak and teleport buttons on those systems.
     */
    open fun makeExtraButtons(powerPos: IPoint): List<Button> {
        return emptyList()
    }

    fun addSkillPoint(skill: Skill) {
        manningCrew?.addSkillPoint(skill)
    }

    /**
     * Get the level this system is manned to, or null if there's no-one manning it.
     *
     * This accounts for manning auto-scouts.
     */
    fun getSkillLevel(skill: Skill): SkillLevel? {
        // It seems there's a fake crewmember in every room?
        // https://www.reddit.com/r/ftlgame/comments/2e30zc/question_re_autoscouts/
        if (ship.isAutoScout)
            return SkillLevel.BASE

        return manningCrew?.getSkillLevel(skill)
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
    protected fun <T> onInit(initializer: (InGameState) -> T): OnInitWrapper<T> {
        val wrapper = OnInitWrapper(initializer)
        onInitValues += wrapper
        return wrapper
    }

    open fun saveToXML(elem: Element, refs: ObjectRefs) {
        elem.setAttribute("name", blueprint.name)

        // Add the level as an attribute, since most of the other tags
        // are optional we might be able to collapse the element (when
        // it takes the form of <thing/> rather than <thing></thing>).
        elem.setAttribute("level", energyLevels.toString())

        SaveUtil.addAttrInt(elem, "damage", damagedEnergyLevels)
        SaveUtil.addTagInt(elem, "scriptedLimit", scriptedPowerLimit, null)
        SaveUtil.addTagFloat(elem, "ionTimer", ionTimer, 0f)
        SaveUtil.addTagInt(elem, "ionPowerLimit", ionPowerLimit, null)
        SaveUtil.addTagFloat(elem, "repairProgress", repairProgress, 0f)
        SaveUtil.addTagFloat(elem, "damageProgress", damageProgress, 0f)

        // Don't save hackedBy, it'll be set by the enemy hacking system when it loads.

        saveSystem(elem, refs)
    }

    open fun loadFromXML(elem: Element, refs: RefLoader) {
        require(elem.getAttributeValue("name") == blueprint.name)

        energyLevels = elem.getAttributeValue("level")!!.toInt()

        damagedEnergyLevels = SaveUtil.getAttrInt(elem, "damage")
        scriptedPowerLimit = SaveUtil.getOptionalTagInt(elem, "scriptedLimit")
        ionTimer = SaveUtil.getOptionalTagFloat(elem, "ionTimer") ?: 0f
        ionPowerLimit = SaveUtil.getOptionalTagInt(elem, "ionPowerLimit")
        repairProgress = SaveUtil.getOptionalTagFloat(elem, "repairProgress") ?: 0f
        damageProgress = SaveUtil.getOptionalTagFloat(elem, "damageProgress") ?: 0f

        loadSystem(elem, refs)

        powerStateChanged()
    }

    /**
     * This is a version of [saveToXML] that saves the system-unique data.
     *
     * This is kept separate from [saveToXML] so it can be marked abstract
     * to force systems to implement it.
     */
    protected abstract fun saveSystem(elem: Element, refs: ObjectRefs)

    // Same thing as saveSystem
    protected abstract fun loadSystem(elem: Element, refs: RefLoader)

    protected class OnInitWrapper<T>(private val fn: (InGameState) -> T) {
        private var storedValue: T? = null

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return storedValue ?: error("On-init property used before initialisation!")
        }

        fun doInit(game: InGameState) {
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

/**
 * Information about how a system is installed into a room - this is both
 * the system and the location of its computer, along with any XML data
 * that's specified in the ship blueprint.
 *
 * This is based on [ShipBlueprint.ParsedSystem], but with information like
 * the computer position calculated based on the loaded ship.
 */
class SystemInstallConfiguration(
    val spec: ISystemConfiguration,
    game: InGameState,
    val room: Room
) {
    val system: SystemBlueprint = game.blueprintManager[spec.systemName] as SystemBlueprint

    val computerPoint: ConstPoint?
    val computerDirection: Direction?
    val obstructionPoint: ConstPoint?

    val isInstalled: Boolean get() = room.system?.blueprint == system

    // Parse out the computer point and direction
    init {
        val defaultCompDir: Direction?
        val defaultCompPoint: ConstPoint?

        // Load defaults
        // TODO what is this for? Kestrel seems to work fine, and I wrote this ages ago and forgot
        when (system.info) {
            Weapons.INFO -> {
                defaultCompPoint = ConstPoint(1, 0)
                defaultCompDir = Direction.UP
            }

            Engines.INFO -> {
                defaultCompPoint = ConstPoint(0, 1)
                defaultCompDir = Direction.DOWN
            }

            Shields.INFO -> {
                defaultCompPoint = ConstPoint(0, 0)
                defaultCompDir = Direction.LEFT
            }

            Piloting.INFO -> {
                defaultCompPoint = ConstPoint(0, 0)
                defaultCompDir = Direction.RIGHT
            }

            else -> {
                defaultCompDir = null
                defaultCompPoint = null
            }
        }

        var compDir = spec.slotDirection ?: defaultCompDir

        var compPoint = when (spec.slotNumber) {
            null -> defaultCompPoint
            0 -> ConstPoint(0, 0)
            1 -> ConstPoint(1, 0)
            2 -> ConstPoint(0, 1)
            3 -> ConstPoint(1, 1)
            // -2 appears to be used for the medbay to indicate no obstruction in 2-cell medbays
            -2 -> null
            else -> error("Invalid point value ${spec.slotNumber}")
        }

        // Pick a room with the invalid computer position if it's a mannable system
        // and the computer is not set.
        if (compPoint == null && system.info?.canBeManned == true) {
            compPoint = ConstPoint(999, 999)
        }

        // If the computer position is invalid (outside the room), just find a point
        // that makes sense (doesn't overlap a door).
        if (compPoint != null && !room.containsRelative(compPoint)) {
            // Take a range of the valid X values
            val validPlaces = (0 until room.width).asSequence().flatMap { x ->
                // Flatmap each of them to the valid positions in that column
                (0 until room.height).asSequence().map { y -> ConstPoint(x, y) }
            }.flatMap {
                // Expand each position into two valid edges
                // Note that in a 1x2/2x1 room this doesn't cover all edges - close enough though
                val horizontal = if (it.x == 0) Direction.LEFT else Direction.RIGHT
                val vertical = if (it.y == 0) Direction.UP else Direction.DOWN
                sequenceOf(Pair(it, horizontal), Pair(it, vertical))
            }.filter { pos ->
                // Filter out anything that intersects with a door
                room.doors.none { it.roomPos(room) posEq pos.first && it.dirFor(room) == pos.second }
            }.sortedBy { pos ->
                // Prefer things that aren't on the same tile as a door
                if (room.doors.none { it.roomPos(room) posEq pos.first }) 0 else 1
            }

            val place = validPlaces.first()
            compPoint = place.first
            compDir = place.second
        }

        // The medbay/clonebay uses the computer to represent a cell that is obstructed.
        if (system.info?.isComputerObstruction == true && compPoint != null) {
            obstructionPoint = compPoint
            compPoint = null
        } else {
            obstructionPoint = null
        }

        // Except for the medbay and clonebay - which are handled above - all systems
        // that specify a computer point must also specify a direction.
        if (compPoint != null) {
            requireNotNull(compDir) { "Cannot set computer point but not computer direction for system ${system.type}" }
        }

        // If the computer point isn't set, it doesn't make sense for the direction to be.
        if (compPoint == null && compDir != null) {
            error("Cannot set the computer direction but not point for system ${system.type}")
        }

        computerPoint = compPoint
        computerDirection = compDir
    }
}

/**
 * Describes this type of system, in a way that can be attached to the blueprint.
 *
 * This contains information that's coupled to the blueprint, but we don't want
 * to actually put into the [SystemBlueprint] class since it varies between systems.
 */
abstract class SystemInfo(
    val name: String
) {
    abstract val canBeManned: Boolean
    open val isComputerObstruction: Boolean get() = false

    abstract fun create(blueprint: SystemBlueprint): AbstractSystem

    abstract fun getLevelName(level: Int, translator: Translator): String
}

/**
 * Describes how a system can be placed in a room.
 *
 * This either comes from the ship blueprint (via [ShipBlueprint.ParsedSystem])
 * or from the ship editor ([xyz.znix.xftl.hangar.FinalisedEditableSystem]).
 */
interface ISystemConfiguration {
    val systemName: String

    val slotNumber: Int?
    val slotDirection: Direction?

    val startingPower: Int

    /**
     * The index of this configuration within the ship's list of systems.
     *
     * This is for figuring out the order the artillery systems are specified
     * in, as that determines what hardpoint they fire from.
     */
    val systemIndex: Int

    val availableByDefault: Boolean

    /**
     * Used for calculations by the ship generator.
     * The flagship notably doesn't have it's maximum power set, so
     * use the maximum specified in the system blueprint in that case.
     */
    val aiMaxPower: Int?

    /**
     * The room interior image
     */
    val interiorImage: String?

    /**
     *  For artillery weapons, this is the weapon they're using internally.
     */
    val weapon: String?
}
