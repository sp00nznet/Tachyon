package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.layout.Room

class MissileBlueprint(xml: Element) : ShipWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_random"
    override val shieldPiercing: Boolean get() = true

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return MissileInstance(ship)
    }

    inner class MissileInstance(ship: Ship) : AbstractProjectileWeaponInstance(this, ship) {
        override fun buildProjectile(target: Room): AbstractProjectile = MissileProjectile(target)
    }

    inner class MissileProjectile(room: Room) : AbstractProjectile(this, room, 500f) {
        override fun render(g: Graphics, x: Float, y: Float, rotation: Float) {
            val img = target.ship.sys.animations[projectile]
            val spr = img.spriteAt(0)
            g.pushTransform()
            g.translate(x, y)
            g.rotate(0f, 0f, rotation + 90f)

            // TODO is the quarter length translation the same as vanilla FTL?
            g.translate(-spr.width.f / 2, -spr.height.f / 4)
            spr.draw(0f, 0f)

            g.popTransform()
        }
    }
}
