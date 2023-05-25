package xyz.znix.xftl.devutil

class DebugFlagManager {
    val showProjectileHitboxes = DebugFlag(
        "phb", "Projectile HitBoxes", true,
        "Draw a red circle around projectile hitboxes (which are actually circles)"
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
        fastWeaponCharge,
        noEnemyFire,
        noDmg,
        continuousSaveLoad,
        infiniteMissiles,
        infiniteDrones
    )

    class DebugFlag(val shortName: String, val fullName: String, val isVisual: Boolean, val description: String) {
        var set = false
    }
}
