package xyz.znix.xftl.devmenu

import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import xyz.znix.xftl.devutil.DebugConsole
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.weapons.Damage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

/**
 * The one-shot cheat actions and save/load helpers exposed by the dev menu.
 *
 * The cheat logic mirrors the equivalent commands in
 * [xyz.znix.xftl.devutil.DebugCommands]; saves are written in the same XML
 * format as the debug console's `save` command, so the two are interchangeable.
 */
object DevActions {
    /** Repair a ship's hull, systems, ion damage, fires and breaches. */
    private fun repairShip(ship: xyz.znix.xftl.Ship) {
        for (system in ship.systems) {
            system.damagedEnergyLevels = 0
            system.ionTimer = 0f
            system.ionPowerLimit = null
            system.powerLimitChanged()
        }
        for (room in ship.rooms) {
            for (idx in room.fires.indices) {
                room.fires[idx] = null
                room.breaches[idx] = null
            }
        }
        ship.health = ship.maxHealth
    }

    /** Fully repair the player ship. */
    fun repairPlayerShip(game: InGameState) = repairShip(game.player)

    /** Fully repair the enemy ship, if there is one. */
    fun repairEnemyShip(game: InGameState) {
        game.enemy?.let { repairShip(it) }
    }

    /** Restore every crew member on the enemy ship to full health. */
    fun healEnemyCrew(game: InGameState) {
        for (crew in game.enemy?.crew ?: return) {
            crew.health = crew.maxHealth
        }
    }

    /** Replace the current beacon's event with the named one and show it. */
    fun loadEvent(game: InGameState, eventName: String) {
        val beacon = game.currentBeacon
        beacon.powerLimitEffects.clear()
        beacon.event = game.eventManager[eventName].resolve()
        beacon.clearEnvironment()
        game.player.updateScriptedPowerLimits()
        game.shipUI.showEventDialogue(beacon.event, kotlin.random.Random.Default.nextInt())
    }

    /** Set every skill of every player crew member to maximum. */
    fun maxCrewSkills(game: InGameState) {
        for (crew in game.player.crew) {
            if (crew is xyz.znix.xftl.crew.LivingCrew) {
                for (skill in xyz.znix.xftl.crew.Skill.entries) {
                    crew.info.skills[skill] = 1f
                }
            }
        }
    }

    /** Fill the player ship with scrap, fuel, missiles and drone parts. */
    fun maxResources(game: InGameState) {
        val ship = game.player
        ship.scrap = 5000
        ship.fuelCount = 99
        ship.missilesCount = 99
        ship.dronesCount = 99
    }

    /** Upgrade every player system, and the reactor, to its maximum level. */
    fun upgradeAllSystems(game: InGameState) {
        val ship = game.player
        for (system in ship.systems) {
            system.energyLevels = system.blueprint.maxPower
        }
        ship.purchasedReactorPower = ship.maxReactorPower
    }

    /** Instantly destroy the current enemy ship, if there is one. */
    fun destroyEnemyShip(game: InGameState) {
        val enemy = game.enemy ?: return
        enemy.damage(enemy.rooms.random(), Damage.hullOnly(100))
        enemy.health = 0
    }

    /** Restore every crew member on the player ship to full health. */
    fun healAllCrew(game: InGameState) {
        for (crew in game.player.crew) {
            crew.health = crew.maxHealth
        }
    }

    // ---- save / load ----

    /**
     * Save the current game to a timestamped XML file in the debug saves
     * directory. Returns the file written.
     */
    fun saveGame(game: InGameState): Path {
        val doc = game.saveGameState()

        val dir = DebugConsole.DEBUG_SAVE_DIR
        Files.createDirectories(dir)

        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val file = dir.resolve("quicksave-$stamp.xml")

        Files.newBufferedWriter(file, Charsets.UTF_8).use { writer ->
            XMLOutputter(Format.getPrettyFormat()).output(doc, writer)
        }
        return file
    }

    /** List every XML save in the debug saves directory, newest name first. */
    fun listSaves(): List<Path> {
        val dir = DebugConsole.DEBUG_SAVE_DIR
        if (!Files.isDirectory(dir))
            return emptyList()

        return Files.list(dir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".xml") }
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList())
        }
    }

    /** Load a save file written by [saveGame] (or the debug console). */
    fun loadGame(mainGame: MainGame, file: Path) {
        val doc = Files.newBufferedReader(file, Charsets.UTF_8).use { reader ->
            val builder = SAXBuilder()
            builder.expandEntities = false
            builder.build(reader)
        }
        mainGame.loadSavedGame(doc)
    }
}
