package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room

class MissileBlueprint(xml: Element) : AbstractWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_random"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return MissileInstance(ship)
    }

    inner class MissileInstance(ship: Ship) : AbstractProjectileWeaponInstance(this, ship) {
        override fun buildProjectile(target: Room): AbstractProjectile = MissileProjectile(target)
    }

    inner class MissileProjectile(room: Room) : AbstractWeaponProjectile(this, room) {
        override val defaultSpeed: Int get() = 35

        override fun renderPreTranslated(g: Graphics) {
            g.rotate(0f, 0f, 90f)

            val img = target.ship.sys.animations[projectile!!]
            val spr = img.spriteAt(0)

            // TODO is the quarter length translation the same as vanilla FTL?
            spr.draw(-spr.width.f / 2, -spr.height.f / 4)
        }
    }
}
