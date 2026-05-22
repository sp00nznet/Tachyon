package xyz.znix.xftl

/**
 * Global developer settings, written by the dev menu's Tuning window and
 * read by the engine. They persist across games within a session; the
 * world-generation values take effect on newly generated sectors.
 */
object DevSettings {
    /** Simulation speed multiplier - 1.0 is normal, lower is slow-motion. */
    var gameSpeed: Float = 1f

    /**
     * When true, the engine's own ship and crew AI run the player ship -
     * firing weapons, hacking, mind control, manning systems, repairs and
     * fighting boarders.
     */
    var autopilot: Boolean = false

    /** Multiplier on the base 20% chance that a sector is a nebula. */
    var nebulaFrequency: Float = 1f

    /** Multiplier on how densely a sector is packed with beacons. */
    var beaconDensity: Float = 1f

    /** Chance (0..1) for an ordinary beacon to gain an environmental hazard. */
    var hazardFrequency: Float = 0f

    /** Chance (0..1) for an ordinary beacon to gain a store. */
    var storeFrequency: Float = 0f
}
