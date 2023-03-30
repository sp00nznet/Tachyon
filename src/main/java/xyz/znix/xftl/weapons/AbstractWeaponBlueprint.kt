package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.math.ConstPoint

abstract class AbstractWeaponBlueprint(xml: Element) : Blueprint(xml) {
    val launcher: String = xml.getChildTextTrim("weaponArt")
    val projectile: String? = xml.getChildTextTrim("image")
    open val explosion: String? = xml.getChildTextTrim("explosion")
    val shots = xml.getChildTextTrim("shots")?.toInt() ?: 1
    val damage = xml.getChildTextTrim("damage").toInt()
    val sysDamage = xml.getChildTextTrim("sysDamage")?.toInt() ?: damage
    open val shieldPiercing: Boolean get() = false

    val power = xml.getChildTextTrim("power").toInt()

    fun getLauncher(game: SlickGame): Animations.WeaponAnimationSpec {
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
    fun drawLauncherUI(game: SlickGame, x: Float, y: Float) {
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
}
