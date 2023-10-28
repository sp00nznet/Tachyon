package xyz.znix.xftl.weapons

/**
 * Represents some amount of damage which is applied to the ship.
 */
class Damage() {
    /**
     * Create a [Damage] instance from the properties of a weapon.
     */
    constructor(type: AbstractWeaponBlueprint) : this() {
        this.hullDamage = type.damage
        this.ionDamage = type.ionDamage
        this.pureSysDamage = type.sysDamage
        this.pureCrewDamage = type.personnelDamage * 15
        this.emptyRoomBonus = type.hullBust

        this.fireChance = type.fireChance * 10
        this.breachChance = type.breachChance * 10
        this.stunChance = type.stunChance * 10

        this.noSysDamage = type.noSysDamage
        this.noCrewDamage = type.noPersonnelDamage
    }

    /**
     * The base amount of damage this weapon does.
     */
    var hullDamage: Int = 0

    var ionDamage: Int = 0

    // The reason why pureSysDamage and pureCrewDamage are required is that
    // some stuff specifically uses them, so we need to track them to properly
    // match up with vanilla.

    /**
     * The amount of system damage this weapon does, excluding the
     * contribution from the hull damage.
     */
    var pureSysDamage: Int = 0

    /**
     * The amount of crew damage this weapon does, excluding the
     * contribution from the hull damage.
     *
     * This is in hp, not units of 15 hp that the weapon XML uses.
     */
    var pureCrewDamage: Int = 0

    val effectiveSysDamage: Int
        get() = when (noSysDamage) {
            true -> pureSysDamage
            false -> pureSysDamage + hullDamage
        }

    val effectiveCrewDamage: Int
        get() = when (noCrewDamage) {
            true -> pureCrewDamage
            false -> pureCrewDamage + hullDamage * 15
        }

    /**
     * The additional hull damage that's dealt against empty rooms.
     */
    var emptyRoomBonus: Int = 0

    // The fire/breach/stun chances, in percent
    var fireChance: Int = 0
    var breachChance: Int = 0
    var stunChance: Int = 0

    // When these are set, hullDamage isn't automatically added to
    // the system and crew damage.
    var noSysDamage: Boolean = false
    var noCrewDamage: Boolean = false

    // lockdown also belongs here once implemented

    /**
     * Apply the effect of a weapon that uses damage-chaining.
     */
    fun applyWeaponChaining(chainDamage: Int) {
        if (chainDamage <= 0)
            return

        when {
            hullDamage > 0 -> hullDamage += chainDamage
            ionDamage > 0 -> ionDamage += chainDamage
            pureCrewDamage > 0 -> pureCrewDamage += chainDamage
            pureSysDamage > 0 -> pureSysDamage += chainDamage
        }
    }
}
