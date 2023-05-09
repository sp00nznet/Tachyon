package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room

class LaserBlueprint(xml: Element) : AbstractWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_missile1"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return LaserInstance(ship)
    }

    inner class LaserInstance(ship: Ship) : AbstractProjectileWeaponInstance(this, ship) {
        override fun buildProjectile(target: Room): AbstractProjectile = LaserProjectile(target)
    }

    inner class LaserProjectile(room: Room) : AbstractWeaponProjectile(this, room) {
        override val defaultSpeed: Int get() = 60

        override val isLaserForDD: Boolean get() = true

        override fun renderPreTranslated(g: Graphics) {
            val img = target.ship.sys.animations[projectile!!]
            val spr = img.spriteAt(0)

            spr.draw(-spr.width.f, -spr.height.f / 2)
        }
    }
}
