package xyz.znix.xftl

/**
 * Global developer settings, written by the dev menu's Tuning window and
 * read by the engine. They persist across games within a session; the
 * world-generation values take effect on newly generated sectors.
 */
object DevSettings {
    /** Simulation speed multiplier - 1.0 is normal, lower is slow-motion. */
    var gameSpeed: Float = 1f

    // ---- autopilot: the engine's AI runs parts of the player ship ----

    /** Auto-fire weapons and deploy drones at the enemy. */
    var autoWeapons: Boolean = false

    /** Auto-command the player's crew - man, repair, fight fires and boarders. */
    var autoCrew: Boolean = false

    /** Auto-power systems, and use hacking / mind control / cloaking. */
    var autoSystems: Boolean = false

    /** Pause the game when combat starts or an event appears. */
    var autoPause: Boolean = false

    /** Automatically pick the last (usually safe) choice on event dialogues. */
    var autoResolveEvents: Boolean = false

    /** Jump onward automatically once a beacon is safe. */
    var autoJump: Boolean = false

    /**
     * Arena mode - full autopilot, and a fresh enemy is spawned (and the
     * player ship repaired) whenever there is none, for endless AI battles.
     */
    var arenaMode: Boolean = false

    /** True if [which] autopilot part should run, also forced on by arena mode. */
    fun autoWeaponsActive() = autoWeapons || arenaMode
    fun autoCrewActive() = autoCrew || arenaMode
    fun autoSystemsActive() = autoSystems || arenaMode

    /** Multiplier on the base 20% chance that a sector is a nebula. */
    var nebulaFrequency: Float = 1f

    /** Multiplier on how densely a sector is packed with beacons. */
    var beaconDensity: Float = 1f

    /** Chance (0..1) for an ordinary beacon to gain an environmental hazard. */
    var hazardFrequency: Float = 0f

    /** Chance (0..1) for an ordinary beacon to gain a store. */
    var storeFrequency: Float = 0f
}
