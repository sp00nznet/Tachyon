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

class MissileBlueprint(xml: Element) : AbstractWeaponBlueprint(xml) {
    override val explosion: String = super.explosion ?: "explosion_random"

    override fun buildInstance(ship: Ship): AbstractWeaponInstance {
        return MissileInstance(ship)
    }

    override fun loadProjectileFromXML(
        game: InGameState,
        elem: Element, refs: RefLoader,
        callback: ProjectileLoadCallback
    ) {
        SaveUtil.getRoomRef(elem, "target", refs) { target ->
            val projectile = MissileProjectile(target)
            projectile.loadPropertiesFromXML(elem, refs)
            callback(projectile)
        }
    }

    inner class MissileInstance(ship: Ship) : AbstractProjectileWeaponInstance(this, ship) {
        override fun buildProjectile(target: Room) = MissileProjectile(target)
    }

    inner class MissileProjectile(room: Room) : AbstractWeaponProjectile(this@MissileBlueprint, room) {
        override val defaultSpeed: Int get() = 35

        override val isMissileForDD: Boolean get() = true

        override fun renderPreTranslated(g: Graphics) {
            g.rotate(0f, 0f, 90f)

            val img = target.ship.sys.animations[projectile!!]
            val spr = img.spriteAt(0)

            // TODO is the quarter length translation the same as vanilla FTL?
            spr.draw(-spr.width.f / 2, -spr.height.f / 4)
        }

        override fun saveToXML(elem: Element, refs: ObjectRefs) {
            super.saveToXML(elem, refs)
            SaveUtil.addRoomRef(elem, "target", refs, target)
        }
    }
}
