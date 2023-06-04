package xyz.znix.xftl.devutil

class DebugFlagManager {
    val showProjectileHitboxes = DebugFlag(
        "phb", "Projectile HitBoxes", true,
        "Draw a red circle around projectile hitboxes (which are actually circles)"
    )

    val showHardpoints = DebugFlag(
        "showhp", "Show hardpoints", true,
        "Draw a red marker on all player and enemy weapon mounts"
    )

    val showRoomNumbers = DebugFlag(
        "srn", "Show room numbers", true,
        "Draw the room ID of each room, which is useful for understanding debugger data"
    )

    val fastWeaponCharge = DebugFlag(
        "fwc", "Fast Weapon Charge", false,
        "Weapons charge at ten times their normal speed"
    )

    val noEnemyFire = DebugFlag(
        "noef", "No Enemy weapon Firing", false,
        "The enemy ship can't fire their weapons"
    )

    val noDmg = DebugFlag(
        "nodmg", "No Damage", false,
        "Ships don't take damage"
    )

    val continuousSaveLoad = DebugFlag(
        "cont-save-load", "Continuous save and load", false,
        "Save and load the game state every update, to test the serialisation logic"
    )

    // This is useful for working on the clonebay UI
    val noClone = DebugFlag(
        "noclone", "Don't clone or kill crew", false,
        "When the clonebay finishes it's clone or kill animation, don't do anything."
    )

    val anyJump = DebugFlag(
        "aj", "Any Jumps", false,
        "Allow the player to jump to any beacon or sector in the map"
    )

    val infiniteMissiles = DebugFlag(
        "infm", "Infinite Missiles", false,
        "Ships (most importantly enemy ships) don't subtract missiles when they fire things."
    )

    val infiniteDrones = DebugFlag(
        "infd", "Infinite Drones", false,
        "Ships (most importantly enemy ships) have infinite drones."
    )

    val all = listOf(
        showProjectileHitboxes,
        showHardpoints,
        showRoomNumbers,
        fastWeaponCharge,
        noEnemyFire,
        noDmg,
        continuousSaveLoad,
        noClone,
        anyJump,
        infiniteMissiles,
        infiniteDrones
    )

    class DebugFlag(val shortName: String, val fullName: String, val isVisual: Boolean, val description: String) {
        var set = false
    }
}
