package xyz.znix.xftl.weapons

import org.jdom2.Element
import org.newdawn.slick.Image
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.drones.*
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

class DroneBlueprint(xml: Element) : Blueprint(xml) {
    val type: DroneType = DroneType.valueOf(xml.getChildTextTrim("type"))
    val tip: String? = xml.getChildTextTrim("tip") // Tooltip
    override val cost: Int? = xml.getChildTextTrim("cost")?.toInt()
    val power: Int = xml.getChildTextTrim("power").toInt()
    val speed: Int? = xml.getChildTextTrim("speed")?.toInt()
    val droneImage: String? = xml.getChildTextTrim("droneImage")
    val iconImage: String? = xml.getChildTextTrim("iconImage")

    // For defence drones (which includes anti-drones)
    val cooldown: Int? = xml.getChildTextTrim("cooldown")?.toInt() // In milliseconds
    val defenceTarget: String? = xml.getChildTextTrim("target") // Either DRONES or LASERS

    val weaponBlueprintName: String? = xml.getChildTextTrim("weaponBlueprint")
    var weaponBlueprint: AbstractWeaponBlueprint? = null
        private set

    init {
        require(power > 0) { "Drone $name has non-positive power $power" }
    }

    // The size of the icon, as it should appear in the UI. This is for roughly
    // positioning them, and obviously different drones are different sizes.
    // These are mostly guessed, I couldn't find anywhere to measure them and
    // disassembling the UI code to look for this seems a bit excessive.
    val iconSize: ConstPoint = when (type) {
        // TODO find some logic behind this - is it the laser arm
        //  in defence drones that make them taller?
        // DEFENSE_1 drones use 46 pixels of Y in the event option UI, while COMBAT_1 uses 38.
        DroneType.DEFENSE -> ConstPoint(29, 37)
        DroneType.COMBAT -> ConstPoint(29, 29)

        // TODO fill in all the drones properly
        else -> ConstPoint(25, 25)
    }

    fun drawIconUI(game: SlickGame, pos: IPoint) {
        var base: Image? = null

        // For indoor drones, use their portrait.
        // For some reason, the ion intruder drone (BOARDER_ION) doesn't set
        // it's iconImage such that we can access it - likely due to it being
        // hardcoded - so leave it null to use missingImage.
        if (iconImage != null) {
            val portrait = "${iconImage}_portrait"
            if (game.animations.animations.containsKey(portrait))
                base = game.animations[portrait].spriteAt(0)
        }

        // Special-case the hacking drone, though obviously it's probably
        // not great if the player is seeing this outside a debug menu.
        if (name == "DRONE_HACKING") {
            base = game.getImg("img/ship/drones/drone_hack_base.png")
        }

        // Otherwise use the outside charged image - this won't exist
        // for indoor drones, so only use it if there isn't a portrait.
        if (base == null && droneImage != null) {
            base = game.getImg("img/ship/drones/${droneImage}_charged.png")
        }

        if (base == null)
            base = game.missingImage

        base.draw(pos.x - base.width / 2f, pos.y - base.height / 2f)

        // TODO laser for defence drones
    }

    fun makeInstance(): AbstractDrone {
        return when (type) {
            DroneType.REPAIR -> RepairDrone(this)
            DroneType.BOARDER -> BoardingDrone(this)
            DroneType.COMBAT -> CombatDrone(this)
            DroneType.DEFENSE -> DefenceDrone(this)
            else -> DummyDrone(this)
        }
    }

    override fun finishSetup(game: SlickGame) {
        super.finishSetup(game)

        if (weaponBlueprintName != null) {
            weaponBlueprint = game.blueprintManager[weaponBlueprintName] as AbstractWeaponBlueprint
        }
    }

    enum class DroneType(val needsHostileShip: Boolean) {
        COMBAT(true),
        SHIP_REPAIR(false),
        DEFENSE(false),
        REPAIR(false),
        BATTLE(false),
        BOARDER(true),
        HACKING(true),
        SHIELD(false),
    }

    // A fake drone that does nothing, to avoid the game crashing
    // if an unsupported drone is deployed.
    // (This is more useful with enemy ships, as otherwise
    // unsupported drones being deployed by the enemy ship would trigger
    // a for-the-player unavoidable crash, with no apparent reason).
    private class DummyDrone(type: DroneBlueprint) : AbstractDrone(type) {
        init {
            println("WARNING: Initialising dummy drone ${type.name}, actual drone isn't implemented.")
        }
    }
}
