package xyz.znix.xftl.crew

import xyz.znix.xftl.weapons.AbstractWeaponBlueprint

/**
 * This defines a unit of damage given to a crew.
 *
 * This is written with an eye to modding, as one might expect mods
 * would want to adjust damage in various ways, which requires them
 * to know what's causing it.
 */
abstract class AbstractCrewDamage(var amount: Float)

class SuffocationDamage(amount: Float) : AbstractCrewDamage(amount)
class FireDamage(amount: Float) : AbstractCrewDamage(amount)
class CombatDamage(amount: Float, val attacker: AbstractCrew) : AbstractCrewDamage(amount)
class WeaponDamage(amount: Float, val weapon: AbstractWeaponBlueprint) : AbstractCrewDamage(amount)
class ZoltanDeathDamage(amount: Float, val crew: CrewZoltan) : AbstractCrewDamage(amount)
