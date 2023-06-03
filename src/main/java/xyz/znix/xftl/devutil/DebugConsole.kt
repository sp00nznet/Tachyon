package xyz.znix.xftl.devutil

import org.jdom2.Document
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.drones.AbstractIndoorsDrone
import xyz.znix.xftl.f
import xyz.znix.xftl.game.*
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.sector.*
import xyz.znix.xftl.shipgen.EnemyShipSpec
import xyz.znix.xftl.shipgen.ShipGenerator
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A development console, to quickly do stuff like load events or get scrap.
 */
class DebugConsole(var game: InGameState) {
    private val history = ArrayList<String>()
    private var historyCursor: Int = -1

    private var continued: ContinuedCommand? = null
    private var input: String = ""

    private val lines = ArrayList<ILine>()

    private val font = game.getFont("c&c")

    private val ship: Ship get() = game.player

    private var flashTimer: Float = 0f

    private var lineScroll: Float = 0f

    private val maxScroll: Int get() = max(0, lines.size - 15)

    // TODO move this to somewhere in appdata (or platform equivalent) when we pick
    //  somewhere to store the regular savegames.
    private val debugSaveDir = Path.of("debug-saves")

    // Used for event searches - put them here so they persiste between
    // searches.
    var eventSearchIds = true
    var eventSearchMessageText = true
    var eventSearchOptions = true

    private val commands: List<Cmd> = listOf(
        Cmd("rich", 0, this::cmdRich, "Get a huge amount of scrap, fuel, drones, and missiles"),
        Cmd("weapon", 0, this::cmdWeapon, "Select a weapon, and add it to the ship's cargo area"),
        Cmd("drone", 0, this::cmdDrone, "Select a drone, and add it to the ship's cargo area"),
        Cmd("aug", 0, this::cmdAugment, "Select an augment, and add it to the ship's cargo area"),
        Cmd("store", 0, this::cmdStore, "Create a store at this beacon"),
        Cmd("event", 0, this::cmdEvent, "Load an event at this beacon"),
        Cmd("fix", null, this::cmdFix, "Fix the ship's hull and all systems, clearing ion damage"),
        Cmd("cld", 0, this::cmdClearDrones, "CLear all Drones - destroys all currently-deployed drone instances"),
        Cmd("crew", 1, this::cmdCrew, "Spawn a new crewmember - one argument, the crew race or 'races'"),
        Cmd("kill", 0, this::cmdKill, "Destroy the enemy ship"),
        Cmd("killcrew", 0, this::cmdKillCrew, "Kill one all of your crewmembers"),
        Cmd("sectors", 0, this::cmdSectors, "Open the sector map, regardless of the current beacon"),
        Cmd("system", 1, this::cmdSystem, "Unlock a system on the current ship, or 'list' or 'all'"),
        Cmd("spawn-ship", 2, this::cmdSpawnShip, "Spawn an enemy ship directly from a seed"),
        Cmd("enemy-weapon", null, this::cmdEnemyWeapon, "Add or remove (the the remove argument) an enemy weapon"),
        Cmd("upall", 0, this::cmdUpgradeAll, "UPgrade ALL systems on the player ship to the maximum level"),
        Cmd("downall", 0, this::cmdDowngradeAll, "Downgrade all systems on the player ship to their starting level"),
        Cmd("set", 1, this::cmdSet, "Turn on or off debug flags"),
        Cmd("damage", 1, this::cmdDamage, "Apply a given amount of damage to the player ship (or negative to heal)"),
        Cmd("force-hack", 1, this::cmdForceHack, "Forces the enemy to hack a given player system"),
        Cmd("super-shield", null, this::cmdSuperShield, "Give the player (or enemy) a super-shield (see help sub-cmd)"),
        Cmd("dump-save", null, this::cmdDumpSave, "Save the game to XML, and print it to standard output"),
        Cmd("save-load", null, this::cmdSaveLoad, "Save the game to XML, and load it back in."),
        Cmd("gc", null, this::cmdGC, "Manually trigger Java's Garbage Collector."),
        Cmd("save", 1, this::cmdSave, "Save the game to a file of a custom name."),
        Cmd("load", 0, this::cmdLoad, "Load a game saved via the 'save' command."),
        Cmd("reload-console", 0, this::cmdReloadConsole, "Reload the console (useful with Java HotSwap)"),
        Cmd("reload-flags", 0, this::cmdReloadFlags, "Reload the debug flags (useful with Java HotSwap)"),
        Cmd("help", 0, this::cmdHelp, "Show the available commands")
    )

    private val prompt: String get() = continued?.prompt ?: PROMPT

    private val currentLine: String
        get() = if (historyCursor == -1) input
        else history[historyCursor]

    fun render(gc: GameContainer, g: Graphics) {
        val height = gc.height / 2

        g.color = Color(127, 127, 127, 180)
        g.fillRect(0f, 0f, gc.width.f, height.f)

        var y = height - 6

        val fontHeight = 7
        val lineSpacing = fontHeight + 5 // Some letters are a bit outside the font height

        // Draw the prompt line
        var inputLine = prompt + currentLine
        if (flashTimer.rem(FLASH_TIME) > FLASH_TIME / 2) {
            inputLine += "_"
        }
        font.drawString(20f, y.f, inputLine, Color.white)
        y -= lineSpacing + 4

        // Draw all the history lines, which can be scrolled.
        // The scroll is only stored as a float to make the scrolling smoother.
        val offset = lineScroll.roundToInt()

        for (i in lines.size - 1 - offset downTo 0) {
            val line = lines[i]
            line.draw(20, y)

            y -= lineSpacing
            if (y < 0)
                break
        }

        // Draw a warning if we're scrolled, so the player knows they're not
        // at the latest message.
        if (offset != 0) {
            val lineY = height - 6 - fontHeight - 4
            g.color = Color.red
            g.drawLine(0f, lineY.f, gc.width.f, lineY.f)

            font.drawStringLeftAligned(gc.width - 5f, lineY - 2f, "$offset lines scrolled past", Color.red)
        }

        // Cut down the history so it doesn't get too crazy.
        while (lines.size > 1000) {
            lines.removeAt(0)
        }

        continued?.render(gc, g, height.f)
    }

    fun update(@Suppress("UNUSED_PARAMETER") gc: GameContainer, dt: Float) {
        flashTimer += dt
    }

    fun keyPressed(key: Int, c: Char) {
        // Check if the continuation UI wants to handle the keypress
        if (continued?.keyPressed(key, c) == true)
            return

        when (key) {
            Input.KEY_ENTER -> {
                selectHistory()
                runCommand()
            }

            Input.KEY_UP -> {
                if (historyCursor == -1) {
                    historyCursor = history.size - 1
                } else {
                    historyCursor--
                    if (historyCursor < 0)
                        historyCursor = 0
                }
            }

            Input.KEY_DOWN -> {
                if (historyCursor != -1)
                    historyCursor++
                if (historyCursor >= history.size)
                    historyCursor = -1
            }

            Input.KEY_BACK -> {
                selectHistory()
                if (input != "") {
                    input = input.substring(0, input.length - 1)
                }
            }

            // On a desktop keyboard, delete is very close to enter so it's
            // easy to press it to clear the current line.
            Input.KEY_DELETE -> {
                historyCursor = -1
                input = ""
            }

            Input.KEY_GRAVE -> {
                // This key opens and closes the console, so ignore it to prevent
                // it ending up in commands.
            }

            else -> {
                // Ignore characters our font doesn't support, which
                // includes any odd ASCII characters that could be
                // somehow generated.
                if (!font.supportsCharacter(c))
                    return

                selectHistory()
                input += c
            }
        }
    }

    fun mouseWheelMoved(amount: Int) {
        lineScroll += amount * 0.025f
        lineScroll = lineScroll.coerceIn(0f..maxScroll.f)
    }

    /**
     * If the user has scrolled back to a previous command, copy
     * that to the buffer.
     */
    private fun selectHistory() {
        if (historyCursor != -1) {
            input = history[historyCursor]
            historyCursor = -1
        }
    }

    /**
     * Print a line of output to the bottom of the console.
     */
    private fun addLine(line: String) {
        lines += SimpleLine(line)
    }

    private fun runCommand() {
        // Jump the scroll to the bottom when the player runs something.
        lineScroll = 0f

        // Clear out the input
        val input = this.input
        this.input = ""

        // Don't add blank lines or the same command multiple times in a row
        if (history.lastOrNull() != input && input.isNotBlank())
            history.add(input)

        addLine(prompt + input)

        // If we're part way through a command that takes multiple
        // lines of input, run that.
        continued?.let { cmd ->
            continued = null
            cmd.run(input.trim())
            return
        }

        // Ignore empty commands
        if (input.isBlank())
            return

        // Build the arguments list, ignoring subsequent spaces
        val args = input.split(' ').filter { it.isNotEmpty() }
        val command = args[0]

        val cmd = commands.firstOrNull { it.name == command }
        if (cmd == null) {
            addLine("Unknown command '${command}', see the help command")
            return
        }

        val numArgs = args.size - 1 // Exclude the command itself
        if (cmd.argCount != null && cmd.argCount != numArgs) {
            addLine("Command '${command}' takes ${cmd.argCount} arguments, but $numArgs were supplied.")
            return
        }

        cmd.func(args)
    }

    private fun cmdHelp(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        addLine("Available commands:")
        for (cmd in commands) {
            addLine("  ${cmd.name.padEnd(15)} ${cmd.helpText}")
        }
    }

    private fun cmdRich(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        addLine("Resources added.")

        ship.scrap = 5000
        ship.fuelCount = 99
        ship.missilesCount = 99
        ship.dronesCount = 99
    }

    private fun cmdWeapon(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        getWeapon { weapon ->
            if (ship.addBlueprint(weapon, false)) {
                addLine("Added weapon ${weapon.translateTitle(game)} to ship inventory.")
            } else {
                addLine("No space in cargo hold, can't add weapon.")
            }
        }
    }

    private fun cmdDrone(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        getDrone { drone ->
            if (ship.addBlueprint(drone, false)) {
                addLine("Added drone ${drone.translateTitle(game)} to ship inventory.")
            } else {
                addLine("No space in cargo hold, can't add drone.")
            }
        }
    }

    private fun cmdAugment(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        getAugment { augment ->
            if (ship.addBlueprint(augment, false)) {
                addLine("Added augment ${augment.translateTitle(game)} to ship inventory.")
            } else {
                addLine("No space in cargo hold, can't add augment.")
            }
        }
    }

    private fun cmdStore(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        game.currentBeacon.hasStore = true
        game.shipUI.updateButtons()

        addLine("A store is now available at this beacon.")
    }

    private fun cmdEvent(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        getEvent { event ->
            // Clear any previously-set beacon power limits, left over
            // from a previous event.
            game.currentBeacon.powerLimitEffects.clear()
            game.player.updateScriptedPowerLimits()

            game.shipUI.showEventDialogue(event.resolve())
        }
    }

    private fun cmdFix(args: List<String>) {
        val targetShip: Ship

        if (args.size == 1) {
            targetShip = ship
        } else if (args.size == 2 && args[1].toLowerCase(Locale.UK) == "enemy") {
            targetShip = game.enemy ?: run {
                addLine("No enemy ship present.")
                return
            }
        } else {
            addLine("Invalid arguments for 'fix' - takes either one argument 'enemy' or no arguments (for the player)")
            return
        }

        for (system in targetShip.systems) {
            system.damagedEnergyLevels = 0
            system.ionTimer = 0f
            system.ionPowerLimit = null
        }

        targetShip.health = targetShip.maxHealth

        addLine("The ship has been repaired, all regular and ion damage was removed.")
    }

    private fun cmdClearDrones(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        fun clearFor(target: Ship) {
            val drones = target.drones
            if (drones != null) {
                for (info in drones.drones) {
                    info?.instance?.destroy()
                }
            }

            target.orphanedDrones.clear()

            // Remove the visuals for drones that somehow got stuck.
            // This can happen if you spawn a new ship at the same
            // beacon while the old one had some drones deployed.
            target.externalDrones.clear()

            // Clone the crew list, since we'll be modifying it if
            // we find any drones.
            for (crew in ArrayList(target.crew)) {
                if (crew !is AbstractIndoorsDrone.Pawn)
                    continue

                crew.removeFromShip()
            }

            // Kill the hacking drone, since it's not technically a drone here
            target.hacking?.removeProbe()
        }

        clearFor(ship)
        game.enemy?.let { clearFor(it) }

        addLine("All drones (including orphan drones) have been cleared from all ships")
    }

    private fun cmdCrew(args: List<String>) {
        val race = args[1]

        val races = game.blueprintManager.blueprints.values.mapNotNull { (it as? CrewBlueprint)?.name }

        if (race == "races") {
            addLine("Supported crew races:")
            for (r in races) {
                addLine("  $r")
            }
            return
        }

        if (!races.contains(race)) {
            addLine("Unknown crew race '$race', try 'crew races' for a list.")
            return
        }

        // If it's an unknown race, this will replace it with a human.
        // This saves us from having to maintain two lists of the supported crew.
        ship.addCrewMember(race, false)
    }

    private fun cmdKill(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        val enemy = game.enemy
        if (enemy == null) {
            addLine("No enemy ship")
            return
        }

        enemy.damage(enemy.rooms.random(), 100, 0, 0)

        // In case nodmg is set, manually set the health to zero.
        enemy.health = 0

        addLine("Added 100 points of damage to the enemy ship")
    }

    private fun cmdKillCrew(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        val options = ArrayList<Pair<String, () -> Unit>>()

        val allCrew = ship.crew.mapNotNull { it as? LivingCrew }

        if (allCrew.isEmpty()) {
            addLine("No living crew on this ship.")
            return
        }

        options += Pair("Kill all player crew") {
            for (crew in allCrew) {
                if (crew.ownerShip != ship)
                    continue
                crew.health = 0f
            }
            addLine("Killed all player crew.")
            return@Pair
        }

        if (allCrew.any { it.ownerShip != ship }) {
            options += Pair("Kill all boarders") {
                for (crew in allCrew) {
                    if (crew.ownerShip == ship)
                        continue
                    crew.health = 0f
                }
                addLine("Killed all enemy boarders on player ship.")
                return@Pair
            }
        }

        options += allCrew.map {
            var name = "${it.blueprint.name} - ${it.selectedName}"

            if (it.ownerShip != ship) {
                name += " (boarder)"
            }

            Pair(name) {
                it.health = 0f
                addLine("Killed crewmember ${it.selectedName} (${it.blueprint.name})")
                return@Pair
            }
        }

        pickFromList("KILL CREW", options) { it() }
    }

    private fun cmdSectors(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        game.shipUI.openSectorMap()
        addLine("Sector map window opened.")
    }

    private fun cmdSystem(args: List<String>) {
        val systemName = args[1]

        if (systemName == "list") {
            addLine("Systems on the player ship:")
            for (room in ship.rooms) {
                val system = room.system
                if (system != null) {
                    addLine("  ${system.codename} (purchased)")
                }

                for (slot in room.systemSlots) {
                    val installed = ship.systems.any { it.blueprint == slot.system }
                    if (!installed) {
                        addLine("  ${slot.system.type}")
                    }
                }
            }
            return
        }

        if (systemName == "all") {
            // Note: ignore the eight-system limit
            for (room in ship.rooms) {
                if (room.system != null)
                    continue

                // If there's multiple systems available (eg medbay and clonebay),
                // just pick the first one.
                val system = room.systemSlots.firstOrNull() ?: continue
                room.setSystem(system)
            }
            addLine("Unlocked all systems on the player ship.")
            return
        }

        val selectedSystem = ship.systemSlots.firstOrNull { it.system.name == systemName }

        if (selectedSystem == null) {
            addLine("No such system named '$systemName' on the current ship, try using 'system list'.")
            return
        }

        if (selectedSystem.isInstalled) {
            addLine("System $systemName is already purchased.")
            return
        }

        selectedSystem.room.setSystem(selectedSystem)

        addLine("Unlocked system $systemName")
    }

    private fun cmdSpawnShip(args: List<String>) {
        val specName = args[1]
        val seedB64 = args[2]

        if (!game.eventManager.hasShip(specName)) {
            addLine("Unknown ship spec '$specName'.")
            return
        }
        val spec: EnemyShipSpec = game.eventManager.getShip(specName)

        if (seedB64.getOrNull(1) == '.') {
            // sector.difficulty mode, with a random seed

            // Make the sector one-indexed
            val sector = seedB64[0].toString().toInt() - 1
            if (sector < 0) {
                addLine("In sector.difficulty mode, the sector must be one-indexed.")
                return
            }

            val difficultyStr = seedB64.substring(2)
            val difficulty = Difficulty.values().firstOrNull { it.name.equals(difficultyStr, ignoreCase = true) }

            if (difficulty == null) {
                val difficultyList = Difficulty.values().joinToString(", ") { it.name }
                addLine("Invalid difficulty name '$difficultyStr', should be one of: $difficultyList")
                return
            }

            val seed = Random.nextInt()

            game.debugSpawnShip(spec, difficulty, sector, seed)

            val seedStr = ShipGenerator.seedToString(sector, difficulty, seed)
            addLine("Spawning ship from spec '${spec.name}' with seed '$seedStr'")
            return
        }

        val seedBytes = try {
            Base64.getDecoder().decode(seedB64)
        } catch (ex: IllegalArgumentException) {
            addLine("Invalid base64 seed '$seedB64': ${ex.localizedMessage}")
            return
        }

        if (seedBytes.size != 6) {
            addLine("Non-six-byte seed: ${seedBytes.size}")
            return
        }

        val buf = ByteBuffer.wrap(seedBytes)
        val sector = buf.get().toInt()
        val difficulty = Difficulty.values()[buf.get().toInt()]
        val seed = buf.getInt()

        addLine("Spawning ship, and setting it as hostile.")
        game.debugSpawnShip(spec, difficulty, sector, seed)
    }

    private fun cmdEnemyWeapon(args: List<String>) {
        if (args.size > 2) {
            addLine("Too many arguments - see the 'help' subcommand.")
            return
        }

        val enemy = game.enemy
        if (enemy == null) {
            addLine("No enemy ship present.")
            return
        }

        if (args.size == 1 || args[1] == "add") {
            // Pick a weapon and add it to the enemy cargo
            getWeapon { weapon ->
                for (i in 0 until (enemy.weaponSlots ?: enemy.hardpoints.size)) {
                    val hp = enemy.hardpoints[i]

                    if (hp.weapon != null)
                        continue

                    hp.weapon = weapon.buildInstance(enemy)
                    enemy.cargoUpdated()
                    addLine("Added weapon ${weapon.name} to enemy hardpoint $i.")
                    return@getWeapon
                }

                addLine("No free hardpoints on the enemy ship")
            }
        } else if (args[1] == "remove") {
            val weapons = enemy.hardpoints.mapNotNull { it.weapon }
            val namedWeapons = weapons.map { Pair(it.type.name, it) }
            pickFromList("TO REMOVE", namedWeapons) { toRemove ->
                for (hp in enemy.hardpoints) {
                    if (hp.weapon != toRemove)
                        continue

                    hp.weapon = null
                    enemy.cargoUpdated()
                    addLine("Removed weapon ${toRemove.type.name} from the enemy ship.")
                    return@pickFromList
                }

                addLine("The weapon has already disappeared!?")
            }
        } else if (args[1] == "clear") {
            // Remove all the enemy weapons
            for (hp in enemy.hardpoints) {
                hp.weapon = null
            }
            enemy.cargoUpdated()
            addLine("Removed all enemy weapons")
            return
        } else {
            addLine("Usage: ${args[0]} [add|clear]")
            addLine("The add mode (default if no arguments are set) lets you select a weapon")
            addLine("to give the enemy ship.")
            addLine("The remove mode lets you remove enemy weapons via a list.")
            addLine("The clear mode removes all the enemy weapons.")
            return
        }
    }

    private fun cmdUpgradeAll(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        for (system in ship.systems) {
            system.energyLevels = system.blueprint.maxPower
        }
        ship.purchasedReactorPower = ship.maxReactorPower
        addLine("Upgraded all systems to maximum level")
    }

    private fun cmdDowngradeAll(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        for (system in ship.systems) {
            system.energyLevels = system.blueprint.startPower
        }
        ship.purchasedReactorPower = 5
        addLine("Downgraded all systems to their starting level")
    }

    private fun cmdSet(args: List<String>) {
        var name = args[1]

        val flagManager = game.debugFlags

        if (name == "help") {
            addLine("Use 'set <name>' to enable a debug flag, or 'set !<name>' to disable it.")
            addLine("Or use 'set all' or 'set !all' to turn everything on or off.")
            addLine("Use 'set vis' or 'set !vis' to turn all debug visuals on or off.")
            addLine("Valid names:")
            for (flag in flagManager.all) {
                addLine("  ${flag.shortName.padEnd(20)} ${flag.set}   ${flag.fullName} - ${flag.description}")
            }
            return
        }

        // If the name starts with '!', then it means to turn the effect off
        val status = name.getOrNull(0) != '!'
        name = name.trimStart('!')

        when (name) {
            "all" -> {
                for (flag in flagManager.all) {
                    flag.set = status
                }
                addLine("Set all debug flags to $status")
                return
            }

            "vis" -> {
                for (flag in flagManager.all) {
                    if (!flag.isVisual)
                        continue
                    flag.set = status
                }
                addLine("Set all debug visuals to $status")
                return
            }
        }

        for (flag in flagManager.all) {
            if (flag.shortName != name)
                continue

            flag.set = status
            addLine("Set debug flag '$name' (${flag.fullName}) to $status")
            return
        }

        addLine("Unknown debug flag name '$name', see 'set help' for more information.")
    }

    private fun cmdDamage(args: List<String>) {
        val amountStr = args[1]
        val amount = amountStr.toIntOrNull()

        if (amount == null) {
            addLine("Invalid amount of damage number '$amount'.")
            return
        }

        ship.health -= amount

        addLine("Applied $amount points of damage to the player ship")
    }

    private fun cmdForceHack(args: List<String>) {
        val enemy = game.enemy
        if (enemy == null) {
            addLine("No enemy ship.")
            return
        }

        val hacking = enemy.hacking
        if (hacking == null) {
            addLine("The enemy ship doesn't have a hacking system.")
            return
        }

        val sysName = args[1]
        val system = ship.systems.firstOrNull { it.codename == sysName }
        if (system == null) {
            addLine("No player system '$sysName'.")
            return
        }

        // Clear the current hacking probe, if it's already been fired.
        hacking.removeProbe()

        hacking.selectTarget(system.room!!)

        // Force the drone to launch, so the AI doesn't get a chance
        // to change the target, in case it updates before
        // the hacking system does.
        hacking.update(0f)

        addLine("Launched hacking probe at player system $sysName")
    }

    private fun cmdSuperShield(args: List<String>) {
        var amount: Int = 5
        var max: Int = 5

        var target: Ship = ship

        // With no arguments, give the player a normal super-shield

        if (args.getOrNull(1) == "help") {
            addLine("Usage: ${args[0]} [amount[/max]] [player|enemy]")
            addLine("The amount should be either a single number (the shield strength), or")
            addLine("two numbers in the form amount/max to set the max super-shield level.")
            addLine("The target ship can be optionally specified, but defaults to the player ship.")
            addLine("When run without arguments, it gives the player a regular (level-5) super-shield.")
            return
        }

        if (args.size >= 2) {
            val parts = args[1].split("/")

            if (parts.size > 2) {
                addLine("Invalid super-shield amount '${args[1]}' - 'see ${args[0]} help'.")
                return
            }

            amount = parts[0].toIntOrNull() ?: run {
                addLine("Invalid super-shield amount '${args[1]}' - see '${args[0]} help'")
                return
            }

            if (parts.size == 2) {
                max = parts[1].toIntOrNull() ?: run {
                    addLine("Invalid super-shield max amount '${args[1]}' - see '${args[0]} help'")
                    return
                }
            }
        }

        if (args.size >= 3) {
            target = when (args[2]) {
                "player" -> ship

                "enemy" -> game.enemy ?: run {
                    addLine("No enemy ship present.")
                    return
                }

                else -> {
                    addLine("Invalid target ship '${args[1]}' (should be 'player' or 'enemy') - see '${args[0]} help'")
                    return
                }
            }
        }

        // Set the max super-shield first, since the
        // normal value is clamped to it.
        target.maxSuperShield = max
        target.superShield = amount

        addLine("Added $amount/$max super-shield to ship ${target.name}.")
    }

    private fun cmdDumpSave(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        val doc = try {
            game.saveGameState()
        } catch (ex: Exception) {
            addLine("Exception saving game (more in stdout): $ex")
            ex.printStackTrace()
            return
        }

        val xmlOutput = XMLOutputter(Format.getPrettyFormat())
        val xmlString = xmlOutput.outputString(doc)

        println("Savegame dump:")
        println(xmlString.trim())

        addLine("Savegame dumped to standard output.")
    }

    private fun cmdSaveLoad(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        val successful = game.mainGame.doSaveLoadGame()
        if (!successful) {
            addLine("Failed to reload game, more details are in the console.")
        }
    }

    private fun cmdGC(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        System.gc()
        addLine("Finished Java GC operation.")
    }

    private fun cmdSave(args: List<String>) {
        // First, try serialising the game.
        val doc = try {
            game.saveGameState()
        } catch (ex: Exception) {
            addLine("Exception saving game (more in stdout): $ex")
            ex.printStackTrace()
            return
        }

        // Create the saves directory if it doesn't already exist
        if (!Files.exists(debugSaveDir)) {
            try {
                Files.createDirectory(debugSaveDir)
            } catch (ex: Exception) {
                ex.printStackTrace()
                addLine("Exception while creating debug save directory!")
                return
            }
        }

        val file = debugSaveDir.resolve(args[1] + ".xml")

        fun doSave() {
            val xmlOutput = XMLOutputter(Format.getPrettyFormat())

            try {
                Files.newBufferedWriter(file, Charsets.UTF_8).use { writer ->
                    xmlOutput.output(doc, writer)
                }
            } catch (ex: Exception) {
                println("While writing save to $file")
                ex.printStackTrace()
                addLine("Exception while writing save!")
            }
        }

        // Now check if a file of the same name already exists.
        if (Files.exists(file)) {
            val options = listOf(
                Pair("Yes, overwrite the save", true),
                Pair("No, cancel and don't overwrite the save.", false)
            )
            pickFromList("A save with this name exists, overwrite it?", options) {
                if (it) {
                    addLine("Overwriting save '${args[1]}'")
                    doSave()
                } else {
                    addLine("Save cancelled.")
                }
            }
        } else {
            addLine("Writing save '${args[1]}'")
            doSave()
        }
    }

    private fun cmdLoad(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        val files: List<Path> = try {
            Files.list(debugSaveDir).filter { it.fileName.toString().endsWith(".xml") }.collect(Collectors.toList())
        } catch (ex: IOException) {
            ex.printStackTrace()
            addLine("Exception while listing debug saves, see the console for details.")
            addLine("(This may occur if you've never made a debug save, so the folder doesn't exist)")
            return
        }

        if (files.isEmpty()) {
            addLine("No debug saves available.")
            return
        }

        val items = files.map { Pair(it.fileName.toString().removeSuffix(".xml"), it) }

        pickFromList("Select save", items) { path ->
            val doc: Document = try {
                Files.newBufferedReader(path).use { reader ->
                    val builder = SAXBuilder()
                    builder.expandEntities = false
                    builder.build(reader)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                addLine("Exception while reading save, see the console for details.")
                return@pickFromList
            }

            try {
                game.mainGame.loadSavedGame(doc)
            } catch (ex: Exception) {
                ex.printStackTrace()
                addLine("Exception while loading save, see the console for details.")
                return@pickFromList
            }
        }
    }

    private fun cmdReloadConsole(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        // This is useful for development if you add a new console command
        // and don't want to restart the game - you can use HotSwap to add
        // your changes, but the command map is only created when the debug
        // console is first opened so new commands won't show up.
        //
        // Note the new instance is not created until you re-open the console,
        // so you can use it if that's in some way useful.
        //
        // I would recommend using DCEVM, which is a set of patches to OpenJDK
        // that greatly enhances what you can hot-reload (in particular, you
        // can add, modify and remove fields). The easiest way to use it
        // is to download the JetBrans Runtime (JBR) build of OpenJDK, and
        // use it with the '-XX:+AllowEnhancedClassRedefinition' VM flag.

        game.reloadDebugConsole();

        // Don't bother adding a line, it'll immediately be lost.
    }

    private fun cmdReloadFlags(@Suppress("UNUSED_PARAMETER") args: List<String>) {
        // Same idea and notes as the reload-console command. Read its comment.
        game.reloadDebugFlags();
        addLine("Reloaded debug flags");
    }

    private fun getWeapon(callback: (AbstractWeaponBlueprint) -> Unit) {
        continued = object : ContinuedCommand() {
            // A little caching for the search
            var lastInput: String? = null
            val visibleWeapons = ArrayList<Buttons.BlueprintButton>()

            override val prompt: String get() = "WEAPON> "

            var lastLeftClick = false

            override fun run(line: String) {
                val weapon = game.blueprintManager.blueprints[line]
                if (weapon == null) {
                    addLine("No such blueprint '$line'")
                    return
                }
                if (weapon !is AbstractWeaponBlueprint) {
                    addLine("Blueprint '$line' is not a weapon - ${weapon.javaClass.name}")
                    return
                }
                callback(weapon)
            }

            override fun render(gc: GameContainer, g: Graphics, height: Float) {
                updateSearch()

                val mouseX = gc.input.mouseX
                val mouseY = gc.input.mouseY

                val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
                val clicking = leftDown && !lastLeftClick
                lastLeftClick = leftDown

                for ((i, button) in visibleWeapons.withIndex()) {
                    val x = 10 + (button.image.normal.width + 5) * i
                    val y = height.toInt() + 10

                    if (x > gc.width)
                        break

                    button.windowOffset = ConstPoint(x, y)
                    button.update(mouseX, mouseY)
                    button.draw(g)

                    if (clicking) {
                        button.mouseDown(Input.MOUSE_LEFT_BUTTON, mouseX, mouseY)
                    }
                }
            }

            private fun updateSearch() {
                val line = currentLine

                if (lastInput == line)
                    return
                lastInput = line

                val searcher = FuzzySearcher(line)

                val names = game.blueprintManager.blueprints.keys.mapNotNull {
                    val score = searcher.rank(it)

                    return@mapNotNull if (score == 0) {
                        null
                    } else {
                        Pair(it, score)
                    }
                }.sortedByDescending { it.second }.map { it.first }

                val images = ButtonImageSet.select2(game, "img/storeUI/store_buy_weapons")

                // Use BlueprintButton's rendering
                class DummyButton(override val blueprint: Blueprint) :
                    Buttons.BlueprintButton(ConstPoint.ZERO, game, images) {
                    override fun click(button: Int) {
                        historyCursor = -1
                        input = blueprint.name
                        runCommand()
                    }
                }

                val weapons: List<DummyButton> = names.mapNotNull {
                    val bp = game.blueprintManager.blueprints[it]
                    val weapon = bp as? AbstractWeaponBlueprint ?: return@mapNotNull null
                    return@mapNotNull DummyButton(weapon)
                }

                visibleWeapons.clear()
                visibleWeapons.addAll(weapons)
            }
        }
    }

    private fun getDrone(callback: (DroneBlueprint) -> Unit) {
        // FIXME this is mostly copy-pasted from getWeapon
        continued = object : ContinuedCommand() {
            // A little caching for the search
            var lastInput: String? = null
            val visibleDrones = ArrayList<Buttons.BlueprintButton>()

            override val prompt: String get() = "DRONE> "

            var lastLeftClick = false

            override fun run(line: String) {
                val drone = game.blueprintManager.blueprints[line]
                if (drone == null) {
                    addLine("No such blueprint '$line'")
                    return
                }
                if (drone !is DroneBlueprint) {
                    addLine("Blueprint '$line' is not a drone - ${drone.javaClass.name}")
                    return
                }
                callback(drone)
            }

            override fun render(gc: GameContainer, g: Graphics, height: Float) {
                updateSearch()

                val mouseX = gc.input.mouseX
                val mouseY = gc.input.mouseY

                val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
                val clicking = leftDown && !lastLeftClick
                lastLeftClick = leftDown

                for ((i, button) in visibleDrones.withIndex()) {
                    val x = 10 + (button.image.normal.width + 5) * i
                    val y = height.toInt() + 10

                    if (x > gc.width)
                        break

                    button.windowOffset = ConstPoint(x, y)
                    button.update(mouseX, mouseY)
                    button.draw(g)

                    if (clicking) {
                        button.mouseDown(Input.MOUSE_LEFT_BUTTON, mouseX, mouseY)
                    }
                }
            }

            private fun updateSearch() {
                val line = currentLine

                if (lastInput == line)
                    return
                lastInput = line

                val searcher = FuzzySearcher(line)

                val names = game.blueprintManager.blueprints.keys.mapNotNull {
                    val score = searcher.rank(it)

                    return@mapNotNull if (score == 0) {
                        null
                    } else {
                        Pair(it, score)
                    }
                }.sortedByDescending { it.second }.map { it.first }

                val images = ButtonImageSet.select2(game, "img/storeUI/store_buy_drones")

                // Use BlueprintButton's rendering
                class DummyButton(override val blueprint: Blueprint) :
                    Buttons.BlueprintButton(ConstPoint.ZERO, game, images) {
                    override fun click(button: Int) {
                        historyCursor = -1
                        input = blueprint.name
                        runCommand()
                    }
                }

                val drones: List<DummyButton> = names.mapNotNull {
                    val bp = game.blueprintManager.blueprints[it]
                    val drone = bp as? DroneBlueprint ?: return@mapNotNull null
                    return@mapNotNull DummyButton(drone)
                }

                visibleDrones.clear()
                visibleDrones.addAll(drones)
            }
        }
    }

    private fun getAugment(callback: (AugmentBlueprint) -> Unit) {
        val augmentSize = ConstPoint(235, 40)
        val margin = 5
        val augmentFont = game.getFont("JustinFont12Bold")

        val allAugments = game.blueprintManager.blueprints.values.mapNotNull { it as? AugmentBlueprint }

        class AugmentButton(val aug: Blueprint) : Button(game, ConstPoint.ZERO, augmentSize) {
            override fun click(button: Int) {
                historyCursor = -1
                input = aug.name
                runCommand()
            }

            override fun draw(g: Graphics) {
                // FIXME this is copied from ShipEquipmentPanel.

                // Draw the empty box
                g.color = Constants.AUGMENT_EMPTY_OUTLINE
                g.fillRect(pos.x.f, pos.y.f, size.x.f, size.y.f)
                g.color = Constants.AUGMENT_EMPTY_INSIDE
                g.fillRect(pos.x + 3f, pos.y + 3f, size.x - 6f, size.y - 6f)

                // Draw the semi-transparent augment on top of it

                // Draw the borders. Since the middle is semi-transparent, we can't
                // just fill in the whole thing twice to get our border easily.
                g.color = when {
                    // dragPosition != null -> Constants.AUGMENT_BOX_OUTLINE
                    hovered -> Constants.AUGMENT_BOX_OUTLINE_HOVER
                    else -> Constants.AUGMENT_BOX_OUTLINE
                }
                // Left and right
                g.fillRect(pos.x + 0f, pos.y + 0f, 3f, size.y.f)
                g.fillRect(pos.x + size.x - 3f, pos.y + 0f, 3f, size.y.f)

                // Top and bottom
                g.fillRect(pos.x + 3f, pos.y + 0f, size.x - 6f, 3f)
                g.fillRect(pos.x + 3f, pos.y + size.y - 3f, size.x - 6f, 3f)

                // Fill in the background
                g.color = Constants.AUGMENT_BOX_INSIDE
                g.fillRect(pos.x + 3f, pos.y + 3f, size.x - 6f, size.y - 6f)

                // Draw the name
                val name = aug.translateTitle(game)
                augmentFont.drawStringCentred(pos.x.f, pos.y.f + 27f, size.x.f, name, Constants.AUGMENT_NAME_TEXT)
            }
        }

        // FIXME this is partially copy-pasted from getWeapon
        continued = object : ContinuedCommand() {
            // A little caching for the search
            var lastInput: String? = null
            val visibleAugments = ArrayList<AugmentButton>()

            override val prompt: String get() = "AUGMENT> "

            var lastLeftClick = false

            override fun run(line: String) {
                val bp = game.blueprintManager.blueprints[line]
                if (bp == null) {
                    addLine("No such blueprint '$line'")
                    return
                }
                if (bp !is AugmentBlueprint) {
                    addLine("Blueprint '$line' is not an augment - ${bp.javaClass.name}")
                    return
                }
                callback(bp)
            }

            override fun render(gc: GameContainer, g: Graphics, height: Float) {
                updateSearch()

                val mouseX = gc.input.mouseX
                val mouseY = gc.input.mouseY

                val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
                val clicking = leftDown && !lastLeftClick
                lastLeftClick = leftDown

                var row = 0
                var column = 0

                val basePos = ConstPoint(10, height.toInt() + 10)
                val effectiveSize = augmentSize + ConstPoint(margin, margin)

                for (button in visibleAugments) {
                    var x = basePos.x + effectiveSize.x * column
                    var y = basePos.y + effectiveSize.y * row

                    column++

                    if (x + effectiveSize.x > gc.width) {
                        x = basePos.x
                        y += effectiveSize.y
                        column = 0
                        row++
                    }

                    if (y > gc.height) {
                        break
                    }

                    button.windowOffset = ConstPoint(x, y)
                    button.update(mouseX, mouseY)
                    button.draw(g)

                    if (clicking) {
                        button.mouseDown(Input.MOUSE_LEFT_BUTTON, mouseX, mouseY)
                    }
                }
            }

            private fun updateSearch() {
                val line = currentLine

                if (lastInput == line)
                    return
                lastInput = line

                visibleAugments.clear()

                // If no search is entered, show all the augments alphabetically
                if (line.isBlank()) {
                    visibleAugments += allAugments.sortedBy { it.translateTitle(game) }.map { AugmentButton(it) }
                    return
                }

                val searcher = FuzzySearcher(line)

                val blueprints = allAugments.mapNotNull {
                    val score = searcher.rank(it.translateTitle(game))

                    return@mapNotNull if (score == 0) {
                        null
                    } else {
                        Pair(it, score)
                    }
                }.sortedByDescending { it.second }.map { it.first }

                visibleAugments.addAll(blueprints.map { AugmentButton(it) })
            }
        }
    }

    private fun getEvent(callback: (IEvent) -> Unit) {
        data class EventInfo(val event: IEvent, val text: String?)

        continued = object : ContinuedCommand() {
            // A little caching for the search
            var lastInput: String? = null
            val visibleEvents = ArrayList<EventInfo>()

            override val prompt: String get() = "EVENT> "

            var lastLeftClick = false

            override fun run(line: String) {
                val names = game.eventManager.eventNames
                if (!names.contains(line)) {
                    addLine("No such event '$line'")
                    return
                }
                val event = game.eventManager[line]

                callback(event)
            }

            override fun render(gc: GameContainer, g: Graphics, height: Float) {
                updateSearch()

                val mouseX = gc.input.mouseX
                val mouseY = gc.input.mouseY

                val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
                val clicking = leftDown && !lastLeftClick
                lastLeftClick = leftDown

                val x = 30
                val blockHeight = 15
                val width = gc.width - x - 40

                val idWidth = 125

                // Draw the search category options
                val optionHeight = blockHeight + 2
                var optionX = x
                fun drawOption(value: Boolean, text: String) {
                    val boxWidth = 5 + font.getWidth(text) + 5
                    g.color = Color(55, if (value) 180 else 55, 55, 180)

                    val y = height + 10f
                    g.fillRect(optionX.f, y, boxWidth.f, optionHeight.f)
                    font.drawString(optionX + 5f, y + 10f, text, Color.white)

                    optionX += boxWidth
                }
                drawOption(eventSearchIds, "Search IDs (F1)")
                drawOption(eventSearchMessageText, "Search event text (F2)")
                drawOption(eventSearchOptions, "Search choice text (F3)")

                for ((i, event) in visibleEvents.withIndex()) {
                    val y = height.toInt() + 10 + optionHeight + i * blockHeight

                    if (y > gc.height)
                        break

                    val hovering = mouseX in x until x + width && mouseY in y until y + blockHeight

                    val shade = if (hovering) 140 else 100
                    g.color = Color(shade, shade, shade, 180)
                    g.fillRect(x.f, y.f, width.f, blockHeight.f)

                    font.drawStringTruncated(x + 5f, y + 10f, idWidth.f, event.event.debugId, Color.white)

                    val descriptionX = x + idWidth + 10
                    val descriptionWidth = width - descriptionX - 5f
                    val text = event.text ?: "<event list>"
                    font.drawStringTruncated(
                        descriptionX.f, y + 10f, descriptionWidth,
                        text.replace("\n", " \\n "),
                        Color.white
                    )

                    if (clicking && hovering) {
                        // Note the debug ID matches the event name for top-level events (we don't
                        // display nested events).
                        historyCursor = -1
                        input = event.event.debugId
                        runCommand()
                    }
                }
            }

            override fun keyPressed(key: Int, c: Char): Boolean {
                val handled = when (key) {
                    Input.KEY_F1 -> {
                        eventSearchIds = !eventSearchIds
                        true
                    }

                    Input.KEY_F2 -> {
                        eventSearchMessageText = !eventSearchMessageText
                        true
                    }

                    Input.KEY_F3 -> {
                        eventSearchOptions = !eventSearchOptions
                        true
                    }

                    else -> false
                }

                // Update the search entries
                if (handled)
                    lastInput = null

                return handled
            }

            private fun updateSearch() {
                val line = currentLine

                if (lastInput == line)
                    return
                lastInput = line

                val searcher = FuzzySearcher(line)

                val events = game.eventManager.eventNames.mapNotNull { name ->
                    val event = game.eventManager[name]

                    var score = 0

                    // Search the name, and consider it to be more important
                    // than the body text. This makes it easier to find a specific
                    // event.
                    if (eventSearchIds)
                        score += searcher.rank(name) * 10

                    // If this is an event list, we can't search it's text
                    if (event !is Event)
                        return@mapNotNull Pair(EventInfo(event, null), score)

                    // And the body of the event text - just search the first
                    // available one, it'd get a bit unmanageable otherwise.
                    val textBody = event.text?.let(::getTextBody) ?: return@mapNotNull null
                    if (eventSearchMessageText)
                        score += searcher.rank(textBody)

                    // Rank all the options, too.
                    if (eventSearchOptions) {
                        for (choice in event.choices) {
                            val text = getTextBody(choice.text) ?: continue
                            score += searcher.rank(text)
                        }
                    }

                    Pair(EventInfo(event, textBody), score)
                }.filter { it.second != 0 }.sortedByDescending { it.second }

                visibleEvents.clear()
                visibleEvents.addAll(events.map { it.first })
            }

            private fun getTextBody(text: IEventText): String? {
                if (text is EventText)
                    return text.localised

                require(text is TextList)

                for (item in text.items) {
                    getTextBody(item)?.let { return it }
                }
                return null
            }
        }
    }

    private fun <T> pickFromList(prompt: String, items: List<Pair<String, T>>, callback: (T) -> Unit) {
        continued = object : ContinuedCommand() {
            // A little caching for the search
            var lastInput: String? = null
            val sortedEntries = ArrayList<Pair<String, T>>()

            override val prompt: String = "$prompt> "

            var lastLeftClick = false

            override fun run(line: String) {
                val item = items.firstOrNull { it.first == line }
                if (item == null) {
                    addLine("No such option '$line'")
                    return
                }
                callback(item.second)
            }

            override fun render(gc: GameContainer, g: Graphics, height: Float) {
                updateSearch()

                val mouseX = gc.input.mouseX
                val mouseY = gc.input.mouseY

                val leftDown = gc.input.isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)
                val clicking = leftDown && !lastLeftClick
                lastLeftClick = leftDown

                val x = 30
                val blockHeight = 15
                val width = gc.width - x - 40

                for ((i, item) in sortedEntries.withIndex()) {
                    val y = height.toInt() + 10 + i * blockHeight

                    if (y > gc.height)
                        break

                    val hovering = mouseX in x until x + width && mouseY in y until y + blockHeight

                    val shade = if (hovering) 140 else 100
                    g.color = Color(shade, shade, shade, 180)
                    g.fillRect(x.f, y.f, width.f, blockHeight.f)

                    font.drawString(x + 5f, y + 10f, item.first, Color.white)

                    if (clicking && hovering) {
                        historyCursor = -1
                        input = item.first
                        runCommand()
                    }
                }
            }

            private fun updateSearch() {
                val line = currentLine

                if (lastInput == line)
                    return
                lastInput = line

                sortedEntries.clear()
                sortedEntries.addAll(items)

                if (line.isBlank()) {
                    // If there's no search term, leave the items in their original order.
                    return
                }

                // Display all the entries - sorting only orders them
                val searcher = FuzzySearcher(line)
                sortedEntries.sortByDescending { searcher.rank(it.first) }
            }
        }
    }

    fun onFailedSaveRestore() {
        addLine("Continuous save/restore - exception during serialisation!")
        addLine("Details are in the console, the game has been paused.")
    }

    private data class Cmd(val name: String, val argCount: Int?, val func: (List<String>) -> Unit, val helpText: String)

    private class FuzzySearcher(query: String) {
        // Split up the words
        private val query = query.toLowerCase(Locale.UK)
        private val inputParts = query.split(" ", "_").filter { it.isNotBlank() }

        fun rank(target: String): Int {
            val lower = target.toLowerCase(Locale.UK)

            // Only match words once, as otherwise a word that's repeated a lot could
            // cause one entry to very frequently appear at the top of the list.
            val parts = lower
                .replace("_", " ")
                .split(" ")
                .toSet()

            var score = 0

            for (part in parts) {
                for (ip in inputParts) {
                    if (part == ip) {
                        score += 20 + ip.length * 3
                    } else if (part.contains(ip)) {
                        // Add a per-word weight
                        score += 10 + ip.length
                    }
                }
            }

            // Add a bonus if the search matches literally (though case-insensitively).
            if (target.contains(query)) {
                score += 4 * query.length
            }

            return score
        }
    }

    private abstract class ContinuedCommand {
        abstract val prompt: String
        open fun render(gc: GameContainer, g: Graphics, height: Float) {}
        open fun keyPressed(key: Int, c: Char): Boolean = false
        abstract fun run(line: String)
    }

    // Represents a line of console output
    private interface ILine {
        fun draw(x: Int, y: Int)
    }

    private inner class SimpleLine(val line: String) : ILine {
        override fun draw(x: Int, y: Int) {
            font.drawString(x.f, y.f, line, Color.white)
        }
    }

    companion object {
        private const val PROMPT = "> "
        private const val FLASH_TIME = 0.65f
    }
}
