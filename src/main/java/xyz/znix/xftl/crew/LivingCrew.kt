package xyz.znix.xftl.crew

import org.jdom2.Element
import org.newdawn.slick.Image
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.f
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

    /**
     * The player facing name of this crew, eg 'Slocknog' or some
     * other name that's selected by the player, by an event or
     * from the default list of names.
     */
    var selectedName: String

    /**
     * The ship that 'owns' this crewmember, or null if they were
     * spawned in by an intruder event.
     */
    var ownerShip: Ship? = room.ship

    /**
     * The number of colour variations this crewmember has.
     *
     * This is open so that humans can override it to double it, for the male/female variants.
     */
    open val numberOfColours: Int = blueprint.colourFilters.map { it.size }.max() ?: 0

    /**
     * The index of this crew's colour selection. This is used to select
     * what colours are applied to the layer filter.
     *
     * This is how different crewmembers can have different colours.
     */
    var crewColour: Int = run {
        if (numberOfColours == 0)
            return@run 0

        (0 until numberOfColours).random()
    }

    protected val layerImages: List<Image>

    override val suffocationMultiplier: Float
        get() {
            if (hasAugment(AugmentBlueprint.OXYGEN_MASKS)) {
                return 0.5f
            }
            return 1f
        }

    init {
        // TODO make this a bit cleaner
        selectedName = room.ship.sys.nameManager.getForGender(null, "en", Random.Default)

        // Load all the layer images for this race
        layerImages = ArrayList()
        for (i in 1..blueprint.colourFilters.size) {
            layerImages += room.ship.sys.getImg("img/people/${codename}_layer$i.png")
        }
    }

    override fun drawImage(x0: Float, y0: Float, x1: Float, y1: Float, baseFrame: Image, alpha: Float) {
        val texX0 = (baseFrame.textureOffsetX * baseFrame.texture.textureWidth).toInt()
        val texY0 = (baseFrame.textureOffsetY * baseFrame.texture.textureHeight).toInt()
        val texX1 = texX0 + baseFrame.width
        val texY1 = texY0 + baseFrame.height

        val baseImage = icon.spec.sheet.sheet

        // Draw the base image, without any tinting
        baseImage.filter = Image.FILTER_NEAREST
        baseImage.alpha = alpha
        baseImage.draw(x0, y0, x1, y1, texX0.f, texY0.f, texX1.f, texY1.f)
        baseImage.alpha = 1f

        // Draw all the layers
        for ((index, layer) in layerImages.withIndex()) {
            val colours = blueprint.colourFilters[index]
            val filter = colours[crewColour % colours.size]

            layer.filter = Image.FILTER_NEAREST
            layer.alpha = alpha
            layer.draw(x0, y0, x1, y1, texX0.f, texY0.f, texX1.f, texY1.f, filter)
            layer.alpha = 1f
        }
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

        SaveUtil.addAttr(elem, "selectedName", selectedName)
        SaveUtil.addAttrRef(elem, "ownerShip", refs, ownerShip)
        SaveUtil.addAttrInt(elem, "colour", crewColour)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        super.loadFromXML(elem, refs)

        selectedName = SaveUtil.getAttr(elem, "selectedName")
        SaveUtil.getAttrRef(elem, "ownerShip", refs, Ship::class.java) { ownerShip = it }
        crewColour = SaveUtil.getAttrInt(elem, "colour")
    }
}
