package xyz.znix.xftl.systems

import org.jdom2.Element
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.Ship
import xyz.znix.xftl.draw
import xyz.znix.xftl.f
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.BeamBlueprint
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

class Artillery(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    // TODO copy the title and desc from the weapon, as specified in the system blueprint.

    private lateinit var weapon: AbstractWeaponInstance

    // Charging appears to work in vanilla based on preserving your charge
    // progress fraction while changing power. This means that if you charge
    // the bar to half way on one power then finish it on four power, it's
    // going to take the average of 50 and 20 seconds.
    private var chargeProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f..1f)
        }

    override val sortingType: SortingType get() = SortingType.ARTILLERY

    private val barImages: List<Image> by onInit { game ->
        (1..4).map { game.getImg("img/systemUI/button_artillery_$it.png") }
    }

    private val cooldown: Float get() = 60f - powerSelected * 10f

    private val beamDestPos by lazy {
        // Players fire to the right, enemies fire upwards.
        ship.shieldOrigin + if (ship.isPlayerShip) ConstPoint(5000, 0) else ConstPoint(0, -5000)
    }

    override fun initialise(ship: Ship) {
        super.initialise(ship)

        weapon = configuration.weapon!!.buildInstance(ship)
    }

    override fun update(dt: Float) {
        super.update(dt)

        if (powerSelected <= 0) {
            chargeProgress -= dt / DISCHARGE_TIME
            return
        }

        var amount = dt / cooldown
        if (ship.sys.debugFlags.fastWeaponCharge.set)
            amount *= 5
        chargeProgress += amount

        if (chargeProgress >= 1f) {
            val targetShip = ship.sys.getEnemyOf(ship)
            if (targetShip != null) {
                fireAt(targetShip)
                chargeProgress = 0f
            }
        }

        weapon.forceSetPowered(true)
        weapon.update(dt, true, false)
    }

    override fun drawBackground(g: Graphics) {
        super.drawBackground(g)

        (weapon as? BeamBlueprint.BeamInstance)?.drawArtilleryBeam(ship.shieldOrigin, beamDestPos)
    }

    override fun drawIconAndPower(game: InGameState, g: Graphics, x: Int, baseY: Int) {
        super.drawIconAndPower(game, g, x, baseY)

        val boxX = x + 35
        val boxY = baseY - 40

        val imageId = max(0, powerSelected - 1)

        // Draw the image of the box the charge bar sits in
        barImages[imageId].draw(boxX, boxY)

        val barX = boxX + 9
        val barBaseY = boxY + 59

        val maxHeight = 50 - imageId * 10 // one pixel per second of charging
        val barHeight = (chargeProgress * maxHeight).toInt()

        g.color = Color.white
        g.fillRect(barX.f, barBaseY.f - barHeight, 5f, barHeight.f)
    }

    private fun fireAt(targetShip: Ship) {
        // Allow smart-casting, since weapon is mutable.
        val weapon = this.weapon

        when (weapon) {
            is BeamBlueprint.BeamInstance -> {
                val startRoom = targetShip.rooms.random()
                val furthestRoom = targetShip.rooms.maxBy { it.pixelCentre.distToSq(startRoom.pixelCentre) }

                // We know there must be at least one room, since we already picked one.
                requireNotNull(furthestRoom)

                val aim = SelectedTarget.BeamAim(weapon, -1, targetShip, startRoom.pixelCentre)
                aim.angle = atan2(
                    furthestRoom.pixelCentre.y.f - startRoom.pixelCentre.y,
                    furthestRoom.pixelCentre.x.f - startRoom.pixelCentre.x
                )
                aim.updateHitRooms()

                val length = sqrt(startRoom.pixelCentre.distToSq(furthestRoom.pixelCentre).toFloat()).toInt()

                weapon.fireFromArtillery(aim, length)
            }

            // TODO burst weapons (flak)
            // TODO lasers and missiles (pick unique targets per projectile)

            else -> error("Artillery system doesn't support weapon ${weapon.type.name} instance $weapon")
        }
    }

    override fun saveSystem(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttrFloat(elem, "charge", chargeProgress)

        val weaponElem = Element("weapon")
        weapon.saveToXML(weaponElem, refs)
        elem.addContent(weaponElem)
    }

    override fun loadSystem(elem: Element, refs: RefLoader) {
        chargeProgress = SaveUtil.getAttrFloat(elem, "charge")

        val weaponElem = elem.getChild("weapon")
        weapon.loadFromXML(weaponElem, refs)
    }

    companion object {
        const val NAME = "artillery"

        private const val DISCHARGE_TIME = 10f
    }
}
