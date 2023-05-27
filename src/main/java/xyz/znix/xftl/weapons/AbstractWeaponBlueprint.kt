package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Ship
import xyz.znix.xftl.game.FTLSound
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.InGameState.GameContent
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.savegame.RefLoader

abstract class AbstractWeaponBlueprint(xml: Element) : Blueprint(xml) {
    val launcher: String = xml.getChildTextTrim("weaponArt")
    val projectile: String? = xml.getChildTextTrim("image")
    open val explosion: String? = xml.getChildTextTrim("explosion")
    val shots = xml.getChildTextTrim("shots")?.toInt() ?: 1
    val damage = xml.getChildTextTrim("damage").toInt()
    val sysDamage = xml.getChildTextTrim("sysDamage")?.toInt() ?: damage
    val ionDamage = xml.getChildTextTrim("ion")?.toInt() ?: 0
    val shieldPiercing: Int = xml.getChildTextTrim("sp")?.toInt() ?: 0
    val missilesUsed: Int = xml.getChildTextTrim("missiles")?.toInt() ?: 0
    val speed: Int? = xml.getChildTextTrim("speed")?.toInt()

    // Power, charge time and cost are null for drone blueprints.
    // Use some semi-sane defaults to avoid having to check everywhere.
    val power = xml.getChildTextTrim("power")?.toInt() ?: 1
    val chargeTime: Float = xml.getChildTextTrim("cooldown")?.toFloat() ?: 5f
    override val cost: Int = xml.getChildTextTrim("cost")?.toInt() ?: 0

    val launchSounds = xml.getChild("launchSounds")?.let { SoundList(it) }
    val hitShipSounds = xml.getChild("hitShipSounds")?.let { SoundList(it) }
    val hitShieldSounds = xml.getChild("hitShieldSounds")?.let { SoundList(it) }
    val missSounds = xml.getChild("missSounds")?.let { SoundList(it) }

    fun getLauncher(game: InGameState): Animations.WeaponAnimationSpec {
        game.animations.weaponAnimations[launcher]?.let { return it }

        // Missing animation?
        val missing = game.missingImage
        val sheet = SpriteSheet(missing, missing.width, missing.height)
        return Animations.WeaponAnimationSpec(
            sheet, 0, 0, 1, 0, 0,
            ConstPoint.ZERO, ConstPoint.ZERO, null
        )
    }

    /**
     * Draw the weapon's launcher image as it should be shown in UIs, for example
     * quest rewards or in a store.
     */
    fun drawLauncherUI(game: InGameState, x: Float, y: Float) {
        val anim = getLauncher(game)

        // Flip and rotate the sprite appropriately to make it loop like it's mounted above
        // a horizontal surface.
        val spr = anim.chargedImage.getFlippedCopy(true, false)
        spr.setCenterOfRotation(0f, 0f)
        spr.rotate(90f)

        // Note we have to add the width (height, but we've rotated it 90°) to fix
        // up the offset caused by the rotation
        spr.draw(x + spr.height, y)
    }

    override fun finishSetup(content: GameContent) {
        super.finishSetup(content)

        // Load all the sounds
        launchSounds?.load(content)
        hitShipSounds?.load(content)
        hitShieldSounds?.load(content)
        missSounds?.load(content)
    }

    abstract fun buildInstance(ship: Ship): AbstractWeaponInstance

    open fun loadProjectileFromXML(
        game: InGameState,
        elem: Element, refs: RefLoader,
        callback: ProjectileLoadCallback
    ) {
        throw UnsupportedOperationException("Weapon blueprint '$name' doesn't support weapon projectile deserialisation.")
    }

    class SoundList(elem: Element) {
        private val names: List<String> = elem.getChildren("sound").map { it.textTrim }
        private var soundsInternal: List<FTLSound>? = null

        val sounds: List<FTLSound> get() = soundsInternal ?: error("SoundList not yet loaded!")

        fun load(content: GameContent) {
            if (soundsInternal != null) {
                error("Cannot re-initialise sound list!")
            }

            soundsInternal = names.map { content.sounds.getSample(it) }
        }

        fun get(): FTLSound? {
            if (sounds.isEmpty())
                return null
            return sounds.random()
        }
    }
}
