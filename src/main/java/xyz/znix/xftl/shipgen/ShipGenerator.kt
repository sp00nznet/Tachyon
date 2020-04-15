package xyz.znix.xftl.shipgen

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.HumanCrew
import xyz.znix.xftl.game.SlickGame
import xyz.znix.xftl.weapons.ShipWeaponBlueprint
import kotlin.math.min

class ShipGenerator(val df: Datafile, val bp: BlueprintManager) {
    fun buildShip(sys: SlickGame, spec: EnemyShipSpec, sector: Int): Ship {
        val elem = spec.autoBlueprint.resolve().let { it as MiscBlueprint }.loadElem(df)

        val ship = Ship(df, elem, sys, spec)

        val crewCount = elem.getChild("crewCount")
        crewCount?.let {
            // TODO crew types
            @Suppress("UNUSED_VARIABLE")
            val type = crewCount.getAttributeValue("class") ?: "human"

            // See the link to the guide below, calculated same as system power
            val min = crewCount.requireAttributeValueInt("amount")
            val max = crewCount.requireAttributeValueInt("max")

            val maxExtra = if (sector < 2) 1 else 2
            val range = max - min
            val softMin = min + (range * sector / 8f).toInt()
            val softMax = min(softMin + maxExtra, max)
            val amount = (softMin..softMax).random()

            for (i in 1..amount) {
                val enemyCrew = HumanCrew(sys.animations, ship.rooms.random(), AbstractCrew.SlotType.CREW)
                ship.crew.add(enemyCrew)
            }
        }

        // Calculate the power levels for each system
        // See this fantastic Reddit post breaking down how the ship generation works:
        // https://www.reddit.com/r/ftlgame/comments/c8qpqk/enemy_power_scaling_infodiscussion/
        // Even if it's not perfectly accurate, it should be more than good enough.
        // Go read it before reading the rest of the generation code.

        // First pick the total bars over maximum
        val maxExtraPower = TOTAL_MAX_POWERS[sector]

        // Make it available as reactor power for the AI to reallocate. Note this is based off the
        // power specified by the <maxPower/> tag, so ion storm behaviour is the same - see the post.
        ship.purchasedReactorPower += maxExtraPower

        // Make a list of all the possible places we could put our extra power
        val possiblePoints = ArrayList<AbstractSystem>()
        for (system in ship.rooms.mapNotNull { it.system }) {
            checkNotNull(system.aiMaxPower)
            val range = system.aiMaxPower - system.energyLevels
            val offset = (range * sector / 8f).toInt()

            val maxExtra = if (sector < 2) 1 else 2

            // Note the count we come up with at the end is to be added to the existing system
            // power, hence we don't take the current power into account
            val softMax = min(offset + maxExtra, system.aiMaxPower)

            val count = (offset..softMax).random()
            for (i in 1..count) {
                possiblePoints += system
            }
        }

        possiblePoints.shuffle()

        // ... and add all the extra power in
        for (i in 1..maxExtraPower) {
            if (possiblePoints.isEmpty()) break
            val system = possiblePoints.removeAt(possiblePoints.size - 1)
            system.energyLevels++
        }

        // Build a list of all the weapons the ship is allowed to use
        val weaponBlueprints = ArrayList<ShipWeaponBlueprint>()
        val weaponList = elem.getChild("weaponList")
        weaponList?.getAttributeValue("load")?.let { listName ->
            weaponBlueprints += bp[listName].list().map { it as ShipWeaponBlueprint }
        }

        for (node in weaponList.children) {
            val name = node.getAttributeValue("name")
            weaponBlueprints += bp[name] as ShipWeaponBlueprint
        }

        // Build the random weapons
        ship.weapons?.let { system ->
            val weapons = generateWeapons(weaponBlueprints, ship.hardpoints.size, system.energyLevels)
            check(weapons.size <= ship.hardpoints.size)
            for ((weapon, hp) in weapons.zip(ship.hardpoints)) {
                hp.weapon = weapon.buildInstance(ship)
            }
        }

        return ship
    }

    private fun generateWeapons(list: List<ShipWeaponBlueprint>, slots: Int, targetPower: Int): List<ShipWeaponBlueprint> {
        for (i in 1..1000) {
            val weapons = ArrayList<ShipWeaponBlueprint>()
            var remainingPower = targetPower

            // While we have >1 slots remaining, and still have power remaining, select another weapon
            while (weapons.size < slots - 1 && remainingPower > 1) {
                val w = list.filter { it.power <= remainingPower }.random()
                weapons += w
                remainingPower -= w.power
            }

            // If we've run out of power to assign, yay we're done
            check(targetPower >= 0)
            if (targetPower == 0)
                return weapons

            // Otherwise pick one weapon to fill in the remaining power. If we can't find one, just
            // start the generation fresh.
            val candidates = list.filter { it.power == remainingPower }
            if (candidates.isEmpty()) continue

            weapons += candidates.random()

            check(targetPower >= 0)
            return weapons
        }

        error("Failed to generate weapon set")
    }

    companion object {
        // This is currently just on normal
        val TOTAL_MAX_POWERS = listOf(4, 6, 8, 11, 13, 15, 18, 20)
    }
}

class EnemyShipSpec(elem: Element, bp: BlueprintManager) {
    val name = elem.requireAttributeValue("name")

    // Two ships (TUTORIAL_PIRATE and IMPOSSIBLE_PIRATE) have 'blueprint' attributes instead. While it's
    // unlikely we'll care about them for a long time (if ever), it's nice to load all the ships without
    // having to carry around a list of exceptions.
    val autoBlueprint = bp[elem.getAttributeValue("blueprint") ?: elem.requireAttributeValue("auto_blueprint")]
}
