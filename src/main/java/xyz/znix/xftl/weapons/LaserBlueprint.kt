package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.Weapons

class LaserBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_missile1"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance? {
        return LaserInstance(ship)
    }

    inner class LaserInstance(ship: Ship) : AbstractProjectileWeaponInstance(this, ship) {
        fun fire(hp: Ship.Hardpoint, weapons: Weapons, target: Room) {
            fire()
            weapons.launchProjectile(hp, LaserProjectile(target))
        }
    }

    inner class LaserProjectile(room: Room) : AbstractProjectile(this, room, 500f) {
        override fun render(g: Graphics, x: Float, y: Float, rotation: Float) {
            val img = target.ship.sys.animations[projectile]
            val spr = img.spriteAt(0)
            g.pushTransform()
            g.translate(x, y)
            g.rotate(0f, 0f, rotation)

            g.translate(-spr.width.toFloat(), -spr.height.toFloat() / 2)
            spr.draw(0f, 0f)

            g.popTransform()
        }
    }
}
