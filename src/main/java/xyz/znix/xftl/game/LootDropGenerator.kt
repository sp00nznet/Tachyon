package xyz.znix.xftl.game

object LootDropGenerator {
    fun generateResource(resource: Resource, tier: RewardTier, sector: Int): ResourceSet {
        val count = when (resource) {
            Resource.FUEL -> generateFuel(tier)
            Resource.MISSILES -> generateMissiles(tier)
            Resource.DRONES -> generateDrones(tier)
            Resource.SCRAP -> generateScrap(tier, sector)
        }
        return ResourceSet.of(resource, count)
    }

    fun generateFuel(tier: RewardTier): Int = FUEL_DROPS[tier.resolve()].random()
    fun generateMissiles(tier: RewardTier): Int = MISSILE_DROPS[tier.resolve()].random()
    fun generateDrones(tier: RewardTier): Int = DRONE_DROPS[tier.resolve()].random()

    /**
     * Generate a scrap reward for a given tier. Note that [sector] starts at zero for the first sector.
     */
    fun generateScrap(tier: RewardTier, sector: Int): Int {
        // Same reward system as FTL
        // Note: it seems that when calculating the scrap rewards, add one sector for easy mode and -1 for hard
        val range = SCRAP_DROP_MULTS[tier.resolve()]
        val rand = range.start + Math.random() * (range.endInclusive - range.start)
        val base = 15 + sector * 6
        return (base * rand).toInt()
    }

    fun generateRewards(game: InGameState, tier: RewardTier, type: RewardType, sector: Int): ResourceSet {
        val resources = ResourceSet()
        var remainingType = type

        val fmd = listOf(Resource.FUEL, Resource.MISSILES, Resource.DRONES).shuffled()

        if (remainingType == RewardType.STANDARD) {
            resources.scrap += generateScrap(tier, sector)
            resources += generateResource(fmd[0], RewardTier.LOW, sector)
            resources += generateResource(fmd[1], RewardTier.LOW, sector)
            if (Math.random() * 100 < 3) remainingType = RewardType.ITEM
        }

        if (remainingType == RewardType.STUFF) {
            resources.scrap += generateScrap(RewardTier.LOW, sector)
            resources += generateResource(fmd[0], tier, sector)
            resources += generateResource(fmd[1], tier, sector)
            if (Math.random() * 100 < 6) remainingType = RewardType.ITEM
        }

        if (listOf(RewardType.FUEL, RewardType.DRONEPARTS, RewardType.MISSILES).contains(remainingType)) {
            remainingType = RewardType.valueOf(remainingType.name + "_ONLY")
            resources.scrap += generateScrap(tier, sector)
        }

        RewardType.ONLY_RESOURCE_MAPPINGS[remainingType]?.let { resource ->
            resources += generateResource(resource, tier, sector)
        }

        if (remainingType == RewardType.ITEM) {
            remainingType = RewardType.ITEMS.random()
        }

        if (RewardType.ITEMS.contains(remainingType)) {
            resources.scrap += generateScrap(tier, sector)
            resources.items += when (remainingType) {
                RewardType.WEAPON -> game.lootPool.getWeapon()
                RewardType.DRONE -> game.lootPool.getDrone()
                RewardType.AUGMENT -> game.lootPool.getAugment()
                else -> error("Unknown autoReward type: $remainingType")
            }
        }

        return resources
    }

    // These drop tables were... acquired from the FTL binary
    val FUEL_DROPS = listOf(1..3, 2..4, 3..6)
    val MISSILE_DROPS = listOf(1..2, 2..4, 4..8)
    val DRONE_DROPS = listOf(1..1, 1..1, 1..2)
    val SCRAP_DROP_MULTS = listOf(0.5..0.7, 0.8..1.3, 1.3..1.55)
}

enum class RewardTier {
    LOW,
    MEDIUM,
    HIGH,
    RANDOM {
        override fun resolve(): Int = (LOW.resolve()..HIGH.resolve()).random()
    };

    open fun resolve(): Int = ordinal

    companion object {
        fun fromName(name: String): RewardTier {
            val processed = name.toUpperCase()
            if (processed == "MED") return MEDIUM
            return valueOf(processed)
        }
    }
}

// See https://www.ftlwiki.com/wiki/Events_file_structure
// Note that many reward types give differenet levels for different items - eg,
// the STANDARD reward type gives the specified level in scrap and two resources
// at a low level.
// Thus each reward is specified with 'low', 'medium' or 'high' for a fixed
// reward, or 'spec' if it should use the tier specified by autoReward.
// Also note that 'FMD' stands for Fuel, Missile and Drone - the standard
// resources except scrap.
enum class RewardType {
    STANDARD, // spec scrap, two low FMD resources, 3% item chance
    STUFF, // low scrap, two spec FMD resources, 6% item chance

    // Spec their respective resource, spec scrap
    FUEL,
    MISSILES,
    DRONEPARTS,

    // Spec their respective resource
    FUEL_ONLY,
    MISSILES_ONLY,
    DRONEPARTS_ONLY,
    SCRAP_ONLY,

    // A random item (weapon, augment, drone schematic)
    ITEM,

    // An item of their respective types, spec scrap
    WEAPON,
    AUGMENT,
    DRONE; // (schematic)

    companion object {
        val ONLY_RESOURCE_MAPPINGS = mapOf(
            Pair(FUEL_ONLY, Resource.FUEL),
            Pair(MISSILES_ONLY, Resource.MISSILES),
            Pair(DRONEPARTS_ONLY, Resource.DRONES),
            Pair(SCRAP_ONLY, Resource.SCRAP)
        )

        val ITEMS = listOf(WEAPON, DRONE, AUGMENT)
    }
}
