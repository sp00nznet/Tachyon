package xyz.znix.xftl.weapons

import org.jdom2.Element
import xyz.znix.xftl.Ship
import xyz.znix.xftl.f
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil

class LaserBlueprint(xml: Element) : AbstractWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_missile1"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return LaserInstance(ship)
    }

    override fun loadProjectileFromXML(
        game: InGameState,
        elem: Element, refs: RefLoader,
        callback: ProjectileLoadCallback
    ) {
        SaveUtil.getRoomRef(elem, "target", refs) { target ->
            val projectile = LaserProjectile(target)
            projectile.loadPropertiesFromXML(elem, refs)
            callback(projectile)
        }
    }

    inner class LaserInstance(ship: Ship) : AbstractProjectileWeaponInstance(this, ship) {
        override fun buildProjectile(target: Room) = LaserProjectile(target)
    }

    inner class LaserProjectile(room: Room) : AbstractWeaponProjectile(this@LaserBlueprint, room) {
        override val defaultSpeed: Int get() = 60

        override val isLaserForDD: Boolean get() = true

        override fun renderPreTranslated(g: Graphics) {
            val img = target.ship.sys.animations[projectile!!]
            val spr = img.spriteAt(0)

            spr.draw(-spr.width.f, -spr.height.f / 2)
        }

        override fun saveToXML(elem: Element, refs: ObjectRefs) {
            super.saveToXML(elem, refs)
            SaveUtil.addRoomRef(elem, "target", refs, target)
        }
    }
}
