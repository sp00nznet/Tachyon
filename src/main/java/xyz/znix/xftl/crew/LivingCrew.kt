package xyz.znix.xftl.crew

import org.jdom2.Element
import org.newdawn.slick.Image
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.f
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import kotlin.random.Random

/**
 * Represents a crew member that can be hired by the player.
 */
abstract class LivingCrew(blueprint: CrewBlueprint, anims: Animations, room: Room, mode: SlotType) :
    AbstractCrew(blueprint, anims, room, mode) {

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

    fun saveToXML(elem: Element, includeRace: Boolean = true) {
        if (includeRace) {
            SaveUtil.addAttr(elem, "race", race.name)
        }
        SaveUtil.addAttr(elem, "selectedName", name)
        SaveUtil.addAttrInt(elem, "colour", colour)
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
            return LivingCrewInfo(race, name, colour)
        }
    }
}
