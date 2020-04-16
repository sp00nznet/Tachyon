package xyz.znix.xftl.game

import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.sector.SectorType
import xyz.znix.xftl.weapons.ShipWeaponBlueprint

class LootPool(bpManager: BlueprintManager, sector: SectorType?) {
    val pool: List<Blueprint>

    init {
        pool = ArrayList()

        val overrides = sector?.rarityOverrides ?: emptyMap()

        for (bp in bpManager.blueprints.values) {
            if (bp !is Blueprint) continue

            // First check if a BP's rarity has been overridden by the sector. If not, use
            // it's standard rarity. If that's not defined then it's inaccessible.
            val rarity = overrides[bp.name] ?: bp.rarity ?: continue

            // See https://subsetgames.com/forum/viewtopic.php?t=33471
            check(rarity in 0..5) { "Invalid rarity $rarity for blueprint ${bp.name}" }
            if (rarity == 0) continue
            val entries = 6 - rarity

            for (i in 1..entries) {
                pool += bp
            }
        }
    }

    fun getWeapon() = getRandom { it is ShipWeaponBlueprint } ?: error("No available weapons!")

    fun getRandom(filter: (Blueprint) -> Boolean): Blueprint? {
        val candidates = pool.asSequence().filter(filter).toList()
        if (candidates.isEmpty()) return null
        return candidates.random()
    }
}
