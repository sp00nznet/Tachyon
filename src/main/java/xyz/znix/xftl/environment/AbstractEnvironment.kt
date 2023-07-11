package xyz.znix.xftl.environment

import org.jdom2.Element
import org.newdawn.slick.GameContainer
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.EnvironmentImage
import xyz.znix.xftl.sector.ImageList
import kotlin.random.Random

abstract class AbstractEnvironment(val game: InGameState, val beacon: Beacon) {
    abstract val type: Beacon.EnvironmentType

    protected var backgroundImage: EnvironmentImage? = null
    protected var planetImage: EnvironmentImage? = null

    // These are only for use by AbstractEnvironment
    protected var backgroundImageIndex: Int = Random.nextInt(999)
    protected var planetImageIndex: Int = Random.nextInt(999)

    // True if the backgroundImage/planetImage are used, and their indices
    // should thus be saved.
    protected open val serialiseImageIndexes: Boolean = true

    init {
        initImages()
    }

    private fun initImages() {
        // We have three goals here:
        // 1. If the environment or event changes, the background should too.
        // 3. We can't be random here, we need to deserialise to the same values each time.
        // 3. Make the planet/background information easy to serialise.
        // Thus pick the image list for the planet and background deterministically,
        // and serialise the index into that list. If the list changes and the index
        // becomes invalid, we can just pick a new one.
        // This does have the limitation that if the event changes to one with a new
        // image list that's larger than the previous one we won't be able to access
        // all it's images. To get around it, we actually serialise a large random number
        // which we then use as an index into the image list, modulo the list's size.

        var backgroundList: ImageList = game.eventManager.getImageList("BACKGROUND")
        var planetList: ImageList = game.eventManager.getImageList("PLANET")

        beacon.event.backImg?.let { backgroundList = it }
        beacon.event.planetImg?.let { planetList = it }

        backgroundImage = backgroundList.getRandom(backgroundImageIndex)
        planetImage = planetList.getRandom(planetImageIndex)

        // TODO show the rebel fleet in the background if we're at an overtaken beacon
        // TODO show the flagship rebel/fed mixed fight backgrounds
    }

    open fun renderBackground(gc: GameContainer, g: Graphics) {
        // Simple background and planet
        backgroundImage?.getImg(game)?.draw()
        planetImage?.getImg(game)?.draw()
    }

    /**
     * Render something on top of everything else, including the UI.
     *
     * Used for solar flares and pulsars.
     */
    open fun renderOverlay(gc: GameContainer, g: Graphics) {
    }

    open fun update(dt: Float) {
    }

    open fun saveToXML(elem: Element) {
        if (!serialiseImageIndexes)
            return

        val combined = backgroundImageIndex * 999 + planetImageIndex
        SaveUtil.addAttrInt(elem, "bgImageIdx", combined)
    }

    open fun loadFromXML(elem: Element) {
        if (!serialiseImageIndexes)
            return

        val combined = SaveUtil.getAttrInt(elem, "bgImageIdx")
        backgroundImageIndex = combined / 999
        planetImageIndex = combined % 999

        // The indices have changed, we need to fetch the new images for them
        initImages()
    }
}
