package xyz.znix.xftl.crew

import org.jdom2.Element
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import java.util.*
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Represents a crew member that can be hired by the player.
 */
abstract class LivingCrew(blueprint: CrewBlueprint, anims: Animations, room: Room, mode: SlotType) :
    AbstractCrew(blueprint, anims, room, mode) {

    override val repairSpeed: Float
        get() = when (getSkillLevel(Skill.REPAIRS)) {
            SkillLevel.BASE -> 1f
            SkillLevel.PARTIAL -> 1.1f
            SkillLevel.MAX -> 1.2f
        }

    override val attackDamageMult: Float
        get() = when (getSkillLevel(Skill.COMBAT)) {
            SkillLevel.BASE -> 1f
            SkillLevel.PARTIAL -> 1.1f
            SkillLevel.MAX -> 1.2f
        }

    var info: LivingCrewInfo = LivingCrewInfo.generateRandom(blueprint, room.ship.sys)

    /**
     * The ship that 'owns' this crewmember, or null if they were
     * spawned in by an intruder event.
     */
    var ownerShip: Ship? = room.ship

    override val suffocationMultiplier: Float
        get() {
            if (hasAugment(AugmentBlueprint.OXYGEN_MASKS)) {
                return 0.5f
            }
            return 1f
        }

    override fun drawImage(x0: Float, y0: Float, x1: Float, y1: Float, baseFrame: Image, alpha: Float) {
        val baseImage = icon.spec.sheet.sheet

        info.drawImage(room.ship.sys, x0, y0, x1, y1, baseFrame, baseImage, alpha)
    }

    override fun onMidTeleport() {
        super.onMidTeleport()

        // Apply the reconstructive teleport effect now, since this
        // is both the mid-point of our animation, and we can't take
        // any more damage on the enemy ship.
        if (currentAction != Action.DYING && hasAugment(AugmentBlueprint.RECONSTRUCTIVE_TELEPORT)) {
            health = maxHealth
        }
    }

    override fun onDied() {
        super.onDied()

        val clonebay = ownerShip?.clonebay ?: return
        clonebay.addDeadCrew(this)
    }

    override fun onCloned() {
        super.onCloned()

        // TODO deduct skills
    }

    override fun onFinishedRepair(sys: AbstractSystem) {
        super.onFinishedRepair(sys)
        addSkillPoint(Skill.REPAIRS)
    }

    override fun onFinishedExtinguishing() {
        super.onFinishedExtinguishing()
        addSkillPoint(Skill.REPAIRS)
    }

    override fun onKilledCrew(enemy: AbstractCrew) {
        super.onKilledCrew(enemy)
        addSkillPoint(Skill.COMBAT)
    }

    override fun onSabotagedSystem(system: AbstractSystem) {
        super.onSabotagedSystem(system)

        // Damaging systems isn't any faster with the combat skill, but
        // it still awards experience.
        addSkillPoint(Skill.COMBAT)
    }

    /**
     * A quick way for systems to credit the crewmember with performing a single action.
     */
    fun addSkillPoint(skill: Skill) {
        // Divide by 2f since the skill level from 0-1 represents the range
        // of no skills to yellow (with green being 0.5), while actionsPerLevel
        // is the number of actions to make one colour change (or 0.5 progress).
        val amount = 1f / (skill.actionsPerLevel * 2f)

        val oldProgress = info.skills.getValue(skill)
        val newProgress = (oldProgress + amount).coerceIn(0f..1f)
        info.skills[skill] = newProgress
    }

    // Helper function
    fun getSkillLevel(skill: Skill): SkillLevel = info.getSkillLevel(skill)

    protected fun hasAugment(name: String): Boolean {
        return ownerShip?.hasAugment(name) == true
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        super.saveToXML(elem, refs)

        SaveUtil.addAttrRef(elem, "ownerShip", refs, ownerShip)
        info.saveToXML(elem, false)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)

        SaveUtil.getAttrRef(elem, "ownerShip", refs, Ship::class.java) { ownerShip = it }
        info = LivingCrewInfo.loadFromXMLWithRace(elem, blueprint)
    }
}

/**
 * Attributes about a crewmember that isn't spawned into the world.
 *
 * This is used for things like events and shops, to show you a player
 * without spawning them.
 */
class LivingCrewInfo(
    val race: CrewBlueprint,

    /**
     * The player facing name of this crew, eg 'Slocknog' or some
     * other name that's selected by the player, by an event or
     * from the default list of names.
     */
    var name: String,

    /**
     * The index of this crew's colour selection. This is used to select
     * what colours are applied to the layer filter.
     *
     * This is how different crewmembers can have different colours.
     */
    var colour: Int
) {
    /**
     * The crew's skills. 0 means no skill, 1 means fully yellow (max level).
     *
     * 0.5 is where the crew's skill turns from white to green, and starts showing
     * a yellow progress bar.
     */
    val skills = EnumMap<Skill, Float>(Skill.values().associate { Pair(it, 0f) })

    /**
     * Draw this crew's portrait, without having to construct the crewmember instance.
     *
     * Useful for events and shops.
     */
    fun drawPortrait(game: InGameState, x: Int, y: Int, scale: Float = 1f) {
        val animation = game.animations["${race.name}_portrait"]
        val baseImage = animation.sheet.sheet
        val portrait = animation.spriteAt(0)

        drawImage(
            game,
            x.f, y.f,
            x.f + portrait.width * scale, y.f + portrait.height * scale,
            portrait, baseImage,
            1f
        )
    }

    // This is moved out of LivingCrew so it can be used in drawPortrait.
    fun drawImage(
        game: InGameState,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        baseFrame: Image, fullBaseImage: Image,
        alpha: Float
    ) {
        val texX0 = (baseFrame.textureOffsetX * baseFrame.texture.textureWidth).toInt()
        val texY0 = (baseFrame.textureOffsetY * baseFrame.texture.textureHeight).toInt()
        val texX1 = texX0 + baseFrame.width
        val texY1 = texY0 + baseFrame.height

        // Draw the base image, without any tinting
        fullBaseImage.filter = Image.FILTER_NEAREST
        fullBaseImage.alpha = alpha
        fullBaseImage.draw(x0, y0, x1, y1, texX0.f, texY0.f, texX1.f, texY1.f)
        fullBaseImage.alpha = 1f

        // Draw all the layers
        for ((index, path) in race.layerImageNames.withIndex()) {
            val layer = game.getImg(path)

            val colours = race.colourFilters[index]
            val filter = colours[colour % colours.size]

            layer.filter = Image.FILTER_NEAREST
            layer.alpha = alpha
            layer.draw(x0, y0, x1, y1, texX0.f, texY0.f, texX1.f, texY1.f, filter)
            layer.alpha = 1f
        }
    }

    fun getSkillLevel(skill: Skill): SkillLevel {
        val progress = skills.getValue(skill)
        return when {
            progress == 1f -> SkillLevel.MAX
            progress >= 0.5f -> SkillLevel.PARTIAL
            else -> SkillLevel.BASE
        }
    }

    fun drawSkillProgressBar(g: Graphics, x: Int, y: Int, width: Int, height: Int, skill: Skill) {
        val level = getSkillLevel(skill)

        val baseColour = when (level) {
            SkillLevel.MAX -> Constants.SYS_ENERGY_REPAIR
            SkillLevel.PARTIAL -> Constants.SYS_ENERGY_ACTIVE
            else -> Color.transparent
        }
        val barColour = when (level) {
            // Max doesn't draw a bar
            SkillLevel.PARTIAL -> Constants.SYS_ENERGY_REPAIR
            else -> Constants.SYS_ENERGY_ACTIVE
        }

        // Convert the 0-1 progress amount (where the green level is 0.5)
        // to 0-1 over the range of a single colour.
        val rawSkillProgress = skills.getValue(skill)
        val progress = when (level) {
            SkillLevel.MAX -> 0f
            SkillLevel.PARTIAL -> (rawSkillProgress - 0.5f) * 2f
            else -> rawSkillProgress * 2f
        }

        // Draw the outer box around the bar.
        // Note that drawRect draws its lower and right lines outside
        // the specified region, so -1 from width and height.
        g.color = Color.white
        g.drawRect(x.f, y.f, width - 1f, height - 1f)

        val innerBarWidth = width - 2
        val progressWidth = (innerBarWidth * progress).roundToInt()

        g.color = baseColour
        g.fillRect(x + 1f, y + 1f, innerBarWidth.f, height - 2f)

        g.color = barColour
        g.fillRect(x + 1f, y + 1f, progressWidth.f, height - 2f)

        // Draw the white divider line between yellow and green sections
        if (level == SkillLevel.PARTIAL) {
            g.color = Color.white
            g.fillRect(x + 1f + progressWidth, y + 1f, 1f, height - 2f)
        }
    }

    fun saveToXML(elem: Element, includeRace: Boolean = true) {
        if (includeRace) {
            SaveUtil.addAttr(elem, "race", race.name)
        }
        SaveUtil.addAttr(elem, "selectedName", name)
        SaveUtil.addAttrInt(elem, "colour", colour)

        for ((skill, value) in skills) {
            if (value == 0f)
                continue

            val skillElem = Element("skill")
            SaveUtil.addAttr(skillElem, "id", skill.name)
            SaveUtil.addAttrFloat(skillElem, "value", value)
            elem.addContent(skillElem)
        }
    }

    companion object {
        @JvmStatic
        fun generateRandom(race: CrewBlueprint, game: InGameState): LivingCrewInfo {
            // TODO language and gender
            val name = game.nameManager.getForGender(null, "en", Random.Default)
            return generateWithName(race, name)
        }

        fun generateWithName(race: CrewBlueprint, name: String): LivingCrewInfo {
            val colour: Int = when (val max = race.numberOfColours) {
                0 -> 0
                else -> Random.nextInt(max)
            }
            return LivingCrewInfo(race, name, colour)
        }

        fun loadFromXML(elem: Element, game: InGameState): LivingCrewInfo {
            val raceName = SaveUtil.getAttr(elem, "race")
            val race = game.blueprintManager[raceName] as CrewBlueprint
            return loadFromXMLWithRace(elem, race)
        }

        fun loadFromXMLWithRace(elem: Element, race: CrewBlueprint): LivingCrewInfo {
            val name = SaveUtil.getAttr(elem, "selectedName")
            val colour = SaveUtil.getAttrInt(elem, "colour")
            val info = LivingCrewInfo(race, name, colour)

            // Skills are omitted if they're set to zero, which is what the default is anyway.
            for (skillElem in elem.getChildren("skill")) {
                val skillName = SaveUtil.getAttr(skillElem, "id")
                val value = SaveUtil.getAttrFloat(skillElem, "value")

                val skill = Skill.valueOf(skillName)
                info.skills[skill] = value
            }

            return info
        }
    }
}

enum class Skill(
    iconName: String,

    /**
     * The number of actions a non-human (no 10% boost) has to perform
     * to increase their skill level from 0->50% or 50->100% (a single
     * colour change).
     */
    val actionsPerLevel: Int
) {
    PILOTING("pilot", 15),
    ENGINES("engines", 15),
    SHIELDS("shields", 55),
    WEAPONS("weapons", 65),
    REPAIRS("repair", 18),
    COMBAT("combat", 8),
    ;

    val iconPath: String = "img/people/skill_${iconName}_white.png"
}

enum class SkillLevel {
    BASE, // No advantage
    PARTIAL, // Medium advantage
    MAX; // Skill maxed
}
