package xyz.znix.xftl.devmenu

import org.lwjgl.glfw.GLFW
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.DevSettings
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.net.Multiplayer
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.shipgen.EnemyShipSpec
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import java.nio.file.Path
import kotlin.random.Random

/**
 * A draggable, closeable floating panel owned by the [DevMenu].
 */
abstract class DevWindow(val title: String, val width: Int) {
    /** Back-reference to the owning menu, set by [DevMenu] on construction. */
    lateinit var menu: DevMenu

    var open = false
        set(value) {
            if (value && !field) onShown()
            field = value
        }

    var x = 160
    var y = 60

    private var dragOffX = 0
    private var dragOffY = 0

    protected val titleBarHeight = 22

    /** The height of the window's content area, below the title bar. */
    protected abstract val contentHeight: Int

    private val totalHeight get() = titleBarHeight + contentHeight

    fun contains(mx: Int, my: Int) =
        mx in x until x + width && my in y until y + totalHeight

    /** Called when the window transitions from closed to open. */
    protected open fun onShown() {}

    /** Called for scroll-wheel events while the cursor is over the window. */
    open fun onScroll(change: Int) {}

    fun render(ui: DevUI) {
        // Start a drag if the title bar (excluding the close button) was pressed.
        if (ui.pressedIn(x, y, width - titleBarHeight, titleBarHeight)) {
            ui.activeWidget = this
            dragOffX = ui.mouseX - x
            dragOffY = ui.mouseY - y
            ui.consumePress()
        }
        if (ui.activeWidget === this && ui.mouseHeld) {
            x = ui.mouseX - dragOffX
            y = ui.mouseY - dragOffY
        }

        // Keep the window on-screen.
        x = x.coerceIn(0, 1280 - width)
        y = y.coerceIn(DevMenu.BAR_HEIGHT, DevMenu.CANVAS_HEIGHT - totalHeight)

        // Body and title bar.
        ui.fill(x, y, width, totalHeight, DevUI.WINDOW_BG)
        ui.fill(x, y, width, titleBarHeight, DevUI.TITLE_BG)
        ui.text(x + 8, y, titleBarHeight, title, DevUI.TEXT)
        ui.outline(x, y, width, totalHeight, DevUI.BORDER)

        // Close button.
        val cb = titleBarHeight
        val closeX = x + width - cb
        if (ui.hovered(closeX, y, cb, cb))
            ui.fill(closeX, y, cb, cb, Colour(196, 64, 64))
        val xw = ui.textWidth("x")
        ui.text(closeX + (cb - xw) / 2, y, cb, "x", DevUI.TEXT)
        if (ui.pressedIn(closeX, y, cb, cb)) {
            ui.consumePress()
            open = false
        }

        content(ui, x + 12, y + titleBarHeight + 10)
    }

    protected abstract fun content(ui: DevUI, cx: Int, cy: Int)

    /** Draw a minus / value / plus stepper. Returns -1, 0 or +1. */
    protected fun stepper(ui: DevUI, x: Int, y: Int, w: Int, value: String): Int {
        val h = 20
        var delta = 0
        if (ui.glyphButton(x, y, h, h, plus = false)) delta = -1
        ui.fill(x + h, y, w - 2 * h, h, DevUI.CONTROL_BG)
        ui.outline(x + h, y, w - 2 * h, h, DevUI.BORDER)
        val vw = ui.textWidth(value)
        ui.text(x + h + (w - 2 * h - vw) / 2, y, h, value, DevUI.TEXT)
        if (ui.glyphButton(x + w - h, y, h, h, plus = true)) delta = 1
        return delta
    }
}

/** Sound-effect and music volume sliders, bound to the save profile. */
class AudioWindow : DevWindow("Audio", 300) {
    override val contentHeight = 96

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        val profile = menu.mainGame.profile
        val sliderW = width - 24

        ui.text(cx, cy, 18, "Sound effects", DevUI.TEXT)
        ui.text(cx + sliderW - 32, cy, 18, "${(profile.soundVolume * 100).toInt()}", DevUI.TEXT_DIM)
        val newSound = ui.slider("dev-sound", cx, cy + 20, sliderW, 16, profile.soundVolume)
        if (newSound != profile.soundVolume) {
            profile.soundVolume = newSound
            profile.markDirty()
        }

        ui.text(cx, cy + 48, 18, "Music", DevUI.TEXT)
        ui.text(cx + sliderW - 32, cy + 48, 18, "${(profile.musicVolume * 100).toInt()}", DevUI.TEXT_DIM)
        val newMusic = ui.slider("dev-music", cx, cy + 68, sliderW, 16, profile.musicVolume)
        if (newMusic != profile.musicVolume) {
            profile.musicVolume = newMusic
            profile.markDirty()
        }
    }
}

/** Project information, credits, license and repository links. */
class AboutWindow : DevWindow("About Tachyon", 440) {
    private val lines = listOf(
        "Tachyon" to DevUI.ACCENT,
        "A developer-tooling fork of Project Wormhole - an" to DevUI.TEXT,
        "open-source, clean-sheet re-implementation of the" to DevUI.TEXT,
        "FTL: Faster Than Light game engine." to DevUI.TEXT,
        "" to DevUI.TEXT,
        "Upstream:  Project Wormhole, by Campbell Suter / ZNix" to DevUI.TEXT_DIM,
        "Contributors:  Campbell Suter, Kommandant_Julk," to DevUI.TEXT_DIM,
        "               The Dumb Dino" to DevUI.TEXT_DIM,
        "License:  GNU GPL v2.0-or-later" to DevUI.TEXT_DIM,
        "" to DevUI.TEXT,
        "Fork:      github.com/sp00nznet/Tachyon" to DevUI.TEXT,
        "Upstream:  gitlab.com/znixian/xftl" to DevUI.TEXT,
    )

    private val repoUrl = "https://github.com/sp00nznet/Tachyon"

    override val contentHeight = lines.size * 18 + 46

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        var ly = cy
        for ((line, colour) in lines) {
            ui.text(cx, ly, 18, line, colour)
            ly += 18
        }

        ly += 8
        if (ui.button(cx, ly, 200, 22, "Copy repository URL")) {
            GLFW.glfwSetClipboardString(GLFW.glfwGetCurrentContext(), repoUrl)
            menu.setStatus("Repository URL copied to clipboard")
        }
    }
}

/**
 * A live view of the player ship - the dev menu's equivalent of a memory
 * searcher, but working on the game's own objects rather than raw memory.
 */
class InspectorWindow : DevWindow("Game Inspector", 320) {
    override val contentHeight: Int
        get() {
            val game = menu.currentInGame() ?: return 38
            return 5 * 26 + 30 + game.player.crew.size * 24 + 18
        }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        val game = menu.currentInGame()
        if (game == null) {
            ui.text(cx, cy, 18, "Start a game to inspect it.", DevUI.TEXT_DIM)
            return
        }

        val ship = game.player
        val labelW = 96
        val ctrlW = width - 24 - labelW
        var ry = cy

        fun row(label: String, value: String, step: Int, apply: (Int) -> Unit) {
            ui.text(cx, ry, 20, label, DevUI.TEXT)
            val delta = stepper(ui, cx + labelW, ry, ctrlW, value)
            if (delta != 0) apply(delta * step)
            ry += 26
        }

        row("Hull", "${ship.health} / ${ship.maxHealth}", 1) {
            ship.health = (ship.health + it).coerceIn(1, ship.maxHealth)
        }
        row("Scrap", ship.scrap.toString(), 25) {
            ship.scrap = (ship.scrap + it).coerceAtLeast(0)
        }
        row("Fuel", ship.fuelCount.toString(), 1) {
            ship.fuelCount = (ship.fuelCount + it).coerceAtLeast(0)
        }
        row("Missiles", ship.missilesCount.toString(), 1) {
            ship.missilesCount = (ship.missilesCount + it).coerceAtLeast(0)
        }
        row("Drone parts", ship.dronesCount.toString(), 1) {
            ship.dronesCount = (ship.dronesCount + it).coerceAtLeast(0)
        }

        ui.fill(cx, ry + 2, width - 24, 1, DevUI.SEPARATOR)
        ry += 10
        ui.text(cx, ry, 18, "Crew", DevUI.TEXT_DIM)
        ry += 20

        for (crew in ship.crew) {
            val name = (crew as? LivingCrew)?.info?.name ?: crew.codename
            val hp = "${crew.health.toInt()} / ${crew.maxHealth.toInt()}"
            ui.text(cx, ry, 20, name, DevUI.TEXT)
            ui.text(cx + 150, ry, 20, hp, DevUI.TEXT_DIM)
            if (ui.button(cx + 210, ry, 86, 20, "Heal")) {
                crew.health = crew.maxHealth
            }
            ry += 24
        }
    }
}

/** Browse and load saves written by the dev menu or the debug console. */
/** Browse, load, create and delete saved games. */
class LoadGameWindow : DevWindow("Saved Games", 430) {
    private var saves = emptyList<Path>()
    private var scroll = 0

    private val rowsShown = 9

    override val contentHeight = 30 + rowsShown * 24 + 20

    private fun refresh() {
        saves = try {
            DevActions.listSaves()
        } catch (ex: Exception) {
            ex.printStackTrace()
            emptyList()
        }
    }

    override fun onShown() {
        refresh()
        scroll = 0
    }

    override fun onScroll(change: Int) {
        val maxScroll = (saves.size - rowsShown).coerceAtLeast(0)
        scroll = (scroll - change / 40).coerceIn(0, maxScroll)
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        val rowW = width - 24

        // Save the current game into a new slot.
        val game = menu.currentInGame()
        if (ui.button(cx, cy, 170, 22, "Save Current Game", enabled = game != null)) {
            try {
                val file = DevActions.saveGame(game!!)
                menu.setStatus("Saved ${file.fileName}")
                refresh()
            } catch (ex: Exception) {
                ex.printStackTrace()
                menu.setStatus("Save failed - see the log")
            }
        }

        var ry = cy + 30

        if (saves.isEmpty()) {
            ui.text(cx, ry, 18, "No saves yet.", DevUI.TEXT_DIM)
            return
        }

        scroll = scroll.coerceIn(0, (saves.size - rowsShown).coerceAtLeast(0))
        for (i in scroll until minOf(saves.size, scroll + rowsShown)) {
            val file = saves[i]
            val name = file.fileName.toString().removeSuffix(".xml")
            val nameW = rowW - 52

            // Name area - click to load.
            val hot = ui.hovered(cx, ry, nameW, 22)
            ui.fill(cx, ry, nameW, 22, if (hot) DevUI.HOVER_BG else DevUI.CONTROL_BG)
            ui.outline(cx, ry, nameW, 22, DevUI.BORDER)
            ui.text(cx + 8, ry, 22, name, DevUI.TEXT)
            if (ui.pressedIn(cx, ry, nameW, 22)) {
                ui.consumePress()
                try {
                    DevActions.loadGame(menu.mainGame, file)
                    menu.setStatus("Loaded $name")
                    open = false
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    menu.setStatus("Failed to load $name - see the log")
                }
            }

            // Delete button.
            if (ui.button(cx + nameW + 4, ry, 48, 22, "Del")) {
                try {
                    DevActions.deleteSave(file)
                    menu.setStatus("Deleted $name")
                    refresh()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    menu.setStatus("Delete failed - see the log")
                }
            }
            ry += 24
        }

        ui.text(cx, ry + 2, 14, "${saves.size} saves - click a name to load", DevUI.TEXT_DIM)
    }
}

/**
 * Grants crew, weapons, drones, systems and augments straight to the player
 * ship - a stand-in for finding a store. The grant logic mirrors the debug
 * console's crew/weapon/drone/aug/system commands.
 */
class OutfitterWindow : DevWindow("Outfitter", 380) {
    private enum class Tab(val label: String) {
        CREW("Crew"), WEAPONS("Weapons"), DRONES("Drones"),
        SYSTEMS("Systems"), AUGMENTS("Augments")
    }

    private class Entry(val label: String, val grant: () -> Unit)

    private var tab = Tab.CREW
    private var scroll = 0
    private val rowsShown = 12

    private var cache: List<Entry>? = null
    private var cacheTab: Tab? = null

    override val contentHeight = 22 + 6 + rowsShown * 20 + 22

    override fun onShown() {
        cache = null
        scroll = 0
    }

    override fun onScroll(change: Int) {
        // 'change' is positive when scrolling up.
        scroll -= change / 40
    }

    /** A readable name for a blueprint, falling back to its id if untranslated. */
    private fun displayName(game: InGameState, bp: Blueprint): String {
        val title = bp.translateTitle(game)
        return if (title.startsWith("MISSING")) bp.name else title
    }

    private fun systemName(system: SystemBlueprint): String =
        system.type.replaceFirstChar { it.uppercaseChar() }

    /** An entry that drops a blueprint into the ship's cargo hold. */
    private fun cargoEntry(game: InGameState, bp: Blueprint): Entry {
        val name = displayName(game, bp)
        return Entry(name) {
            if (game.player.addBlueprint(bp, false))
                menu.setStatus("Added $name")
            else
                menu.setStatus("No free cargo space")
        }
    }

    private fun buildEntries(game: InGameState): List<Entry> {
        val ship = game.player
        val all = game.blueprintManager.blueprints.values
        return when (tab) {
            Tab.CREW -> all.filterIsInstance<CrewBlueprint>()
                .map { bp ->
                    Entry(displayName(game, bp)) {
                        ship.addCrewMember(LivingCrewInfo.generateRandom(bp, game), false)
                        menu.setStatus("Added crew: ${displayName(game, bp)}")
                    }
                }
                .sortedBy { it.label }

            Tab.WEAPONS -> all.filterIsInstance<AbstractWeaponBlueprint>()
                .map { cargoEntry(game, it) }.sortedBy { it.label }

            Tab.DRONES -> all.filterIsInstance<DroneBlueprint>()
                .map { cargoEntry(game, it) }.sortedBy { it.label }

            Tab.AUGMENTS -> all.filterIsInstance<AugmentBlueprint>()
                .map { cargoEntry(game, it) }.sortedBy { it.label }

            // Systems can only go in the slots the ship was designed with.
            Tab.SYSTEMS -> ship.systemSlots.filter { !it.isInstalled }
                .map { slot ->
                    Entry(systemName(slot.system)) {
                        slot.room.setSystem(slot)
                        menu.setStatus("Installed ${systemName(slot.system)}")
                    }
                }
                .sortedBy { it.label }
        }
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        val game = menu.currentInGame()
        if (game == null) {
            ui.text(cx, cy, 18, "Start a game to outfit your ship.", DevUI.TEXT_DIM)
            return
        }

        // Category tabs
        val tabW = (width - 24) / Tab.entries.size
        for ((i, t) in Tab.entries.withIndex()) {
            val tx = cx + i * tabW
            ui.fill(tx, cy, tabW - 2, 22, if (t == tab) DevUI.ACCENT else DevUI.CONTROL_BG)
            ui.outline(tx, cy, tabW - 2, 22, DevUI.BORDER)
            val tw = ui.textWidth(t.label)
            ui.text(tx + (tabW - 2 - tw) / 2, cy, 22, t.label, DevUI.TEXT)
            if (ui.pressedIn(tx, cy, tabW - 2, 22)) {
                ui.consumePress()
                if (tab != t) {
                    tab = t
                    scroll = 0
                    cache = null
                }
            }
        }

        // (Re)build the entry list for the current tab.
        if (cache == null || cacheTab != tab) {
            cache = buildEntries(game)
            cacheTab = tab
        }
        val entries = cache!!

        val listY = cy + 28
        val rowW = width - 24

        if (entries.isEmpty()) {
            ui.text(cx, listY, 18, "Nothing available here.", DevUI.TEXT_DIM)
            return
        }

        val maxScroll = (entries.size - rowsShown).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)

        for (i in scroll until minOf(entries.size, scroll + rowsShown)) {
            val entry = entries[i]
            val ry = listY + (i - scroll) * 20
            val hot = ui.hovered(cx, ry, rowW, 19)
            ui.fill(cx, ry, rowW, 19, if (hot) DevUI.HOVER_BG else DevUI.CONTROL_BG)
            ui.outline(cx, ry, rowW, 19, DevUI.BORDER)
            ui.text(cx + 8, ry, 19, entry.label, DevUI.TEXT)
            if (ui.pressedIn(cx, ry, rowW, 19)) {
                ui.consumePress()
                entry.grant()
                // Installing a system changes what's still available.
                cache = null
            }
        }

        val footY = listY + rowsShown * 20 + 2
        ui.text(cx, footY, 14, "${entries.size} available", DevUI.TEXT_DIM)
    }
}

/**
 * Cheats, with per-side (player / enemy) toggles plus one-shot actions.
 */
class CheatsWindow : DevWindow("Cheats", 340) {
    override val contentHeight = 340

    /** A 16px toggle box; returns true on the frame it's clicked. */
    private fun cell(ui: DevUI, x: Int, rowY: Int, checked: Boolean): Boolean {
        ui.fill(x, rowY + 2, 16, 16, if (checked) DevUI.ACCENT else DevUI.CONTROL_BG)
        ui.outline(x, rowY + 2, 16, 16, DevUI.BORDER)
        if (ui.pressedIn(x - 6, rowY, 28, 22)) {
            ui.consumePress()
            return true
        }
        return false
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        val game = menu.currentInGame()
        if (game == null) {
            ui.text(cx, cy, 18, "Start a game to use cheats.", DevUI.TEXT_DIM)
            return
        }

        val f = game.debugFlags
        val youX = cx + 210
        val enemyX = cx + 272
        var ry = cy

        // Column headers
        ui.text(youX - 6, ry, 18, "You", DevUI.TEXT_DIM)
        ui.text(enemyX - 10, ry, 18, "Enemy", DevUI.TEXT_DIM)
        ry += 20

        // Per-side toggles
        val rows = listOf(
            Triple("Ship Invincible", f.noDmg, f.noDmgEnemy),
            Triple("Crew Invincible", f.noCrewDamage, f.noCrewDamageEnemy),
            Triple("Fast Weapons", f.fastWeaponCharge, f.fastWeaponChargeEnemy),
            Triple("Infinite Missiles", f.infiniteMissiles, f.infiniteMissilesEnemy),
            Triple("Infinite Drones", f.infiniteDrones, f.infiniteDronesEnemy),
        )
        for ((label, playerFlag, enemyFlag) in rows) {
            ui.text(cx, ry, 20, label, DevUI.TEXT)
            if (cell(ui, youX, ry, playerFlag.set)) playerFlag.set = !playerFlag.set
            if (cell(ui, enemyX, ry, enemyFlag.set)) enemyFlag.set = !enemyFlag.set
            ry += 24
        }

        ui.fill(cx, ry + 2, width - 24, 1, DevUI.SEPARATOR)
        ry += 12

        // Single toggles that only make sense one way
        val singles = listOf(
            "Reveal Map" to f.showEverything,
            "Jump Anywhere" to f.anyJump,
            "No Enemy Weapons" to f.noEnemyFire,
        )
        for ((label, flag) in singles) {
            if (ui.checkbox(cx, ry, width - 24, 20, label, flag.set))
                flag.set = !flag.set
            ry += 22
        }

        ui.fill(cx, ry + 2, width - 24, 1, DevUI.SEPARATOR)
        ry += 12

        // One-shot actions
        val bw = (width - 24 - 8) / 2
        val hasEnemy = game.enemy != null

        if (ui.button(cx, ry, bw, 22, "Repair Your Ship")) {
            DevActions.repairPlayerShip(game)
            menu.setStatus("Your ship repaired")
        }
        if (ui.button(cx + bw + 8, ry, bw, 22, "Repair Enemy", hasEnemy)) {
            DevActions.repairEnemyShip(game)
            menu.setStatus("Enemy ship repaired")
        }
        ry += 26

        if (ui.button(cx, ry, bw, 22, "Heal Your Crew")) {
            DevActions.healAllCrew(game)
            menu.setStatus("Your crew healed")
        }
        if (ui.button(cx + bw + 8, ry, bw, 22, "Heal Enemy Crew", hasEnemy)) {
            DevActions.healEnemyCrew(game)
            menu.setStatus("Enemy crew healed")
        }
        ry += 26

        if (ui.button(cx, ry, bw, 22, "Max Resources")) {
            DevActions.maxResources(game)
            menu.setStatus("Resources maxed out")
        }
        if (ui.button(cx + bw + 8, ry, bw, 22, "Upgrade Systems")) {
            DevActions.upgradeAllSystems(game)
            menu.setStatus("All systems upgraded")
        }
        ry += 26

        if (ui.button(cx, ry, bw, 22, "Max Crew Skills")) {
            DevActions.maxCrewSkills(game)
            menu.setStatus("Crew skills maxed out")
        }
        if (ui.button(cx + bw + 8, ry, bw, 22, "Destroy Enemy Ship", hasEnemy)) {
            DevActions.destroyEnemyShip(game)
            menu.setStatus("Enemy ship destroyed")
        }
    }
}

/**
 * Game-speed and world-generation tuning sliders.
 */
class TuningWindow : DevWindow("Tuning", 320) {
    override val contentHeight = 5 * 38 + 56

    private fun sliderRow(
        ui: DevUI, key: String, cx: Int, ry: Int, label: String,
        value: Float, min: Float, max: Float, valueText: String
    ): Float {
        ui.text(cx, ry, 16, label, DevUI.TEXT)
        val vw = ui.textWidth(valueText)
        ui.text(cx + width - 24 - vw, ry, 16, valueText, DevUI.TEXT_DIM)

        val norm = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val newNorm = ui.slider(key, cx, ry + 17, width - 24, 14, norm)
        return min + newNorm * (max - min)
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        var ry = cy

        DevSettings.gameSpeed = sliderRow(
            ui, "tune-speed", cx, ry, "Game Speed",
            DevSettings.gameSpeed, 0.1f, 4f, "%.2fx".format(DevSettings.gameSpeed)
        )
        ry += 38

        DevSettings.nebulaFrequency = sliderRow(
            ui, "tune-neb", cx, ry, "Nebula Sector Frequency",
            DevSettings.nebulaFrequency, 0f, 4f, "%.1fx".format(DevSettings.nebulaFrequency)
        )
        ry += 38

        DevSettings.beaconDensity = sliderRow(
            ui, "tune-den", cx, ry, "Beacon Density",
            DevSettings.beaconDensity, 0.5f, 2.5f, "%.1fx".format(DevSettings.beaconDensity)
        )
        ry += 38

        DevSettings.hazardFrequency = sliderRow(
            ui, "tune-haz", cx, ry, "Hazard Beacon Chance",
            DevSettings.hazardFrequency, 0f, 1f, "${(DevSettings.hazardFrequency * 100).toInt()} pct"
        )
        ry += 38

        DevSettings.storeFrequency = sliderRow(
            ui, "tune-sto", cx, ry, "Extra Store Chance",
            DevSettings.storeFrequency, 0f, 1f, "${(DevSettings.storeFrequency * 100).toInt()} pct"
        )
        ry += 42

        if (ui.button(cx, ry, 130, 22, "Reset to Default")) {
            DevSettings.gameSpeed = 1f
            DevSettings.nebulaFrequency = 1f
            DevSettings.beaconDensity = 1f
            DevSettings.hazardFrequency = 0f
            DevSettings.storeFrequency = 0f
        }
        ui.text(cx, ry + 28, 14, "World-gen tuning applies to newly visited sectors.", DevUI.TEXT_DIM)
    }
}

/** Spawn a chosen enemy ship at the current beacon. */
class SpawnShipWindow : DevWindow("Spawn Enemy Ship", 380) {
    private var specs: List<EnemyShipSpec> = emptyList()
    private var scroll = 0
    private val rowsShown = 12

    override val contentHeight = rowsShown * 20 + 24

    override fun onShown() {
        specs = menu.currentInGame()?.eventManager?.getShips()
            ?.sortedBy { it.name } ?: emptyList()
        scroll = 0
    }

    override fun onScroll(change: Int) {
        scroll -= change / 40
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        val game = menu.currentInGame()
        if (game == null) {
            ui.text(cx, cy, 18, "Start a game first.", DevUI.TEXT_DIM)
            return
        }
        if (specs.isEmpty())
            onShown()

        val rowW = width - 24
        val maxScroll = (specs.size - rowsShown).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)

        for (i in scroll until minOf(specs.size, scroll + rowsShown)) {
            val spec = specs[i]
            val ry = cy + (i - scroll) * 20
            val hot = ui.hovered(cx, ry, rowW, 19)
            ui.fill(cx, ry, rowW, 19, if (hot) DevUI.HOVER_BG else DevUI.CONTROL_BG)
            ui.outline(cx, ry, rowW, 19, DevUI.BORDER)
            ui.text(cx + 8, ry, 19, spec.name, DevUI.TEXT)
            if (ui.pressedIn(cx, ry, rowW, 19)) {
                ui.consumePress()
                val sectorNum = game.currentBeacon.sector.sectorNumber
                game.debugSpawnShip(spec, game.difficulty, sectorNum, Random.Default.nextInt())
                menu.setStatus("Spawned ${spec.name}")
            }
        }

        ui.text(cx, cy + rowsShown * 20 + 4, 14, "${specs.size} ship types", DevUI.TEXT_DIM)
    }
}

/** Load any event onto the current beacon. */
class EventPickerWindow : DevWindow("Load Event", 430) {
    private var names: List<String> = emptyList()
    private var scroll = 0
    private val rowsShown = 13

    override val contentHeight = rowsShown * 20 + 24

    override fun onShown() {
        names = menu.currentInGame()?.eventManager?.eventNames?.sorted() ?: emptyList()
        scroll = 0
    }

    override fun onScroll(change: Int) {
        scroll -= change / 40
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        val game = menu.currentInGame()
        if (game == null) {
            ui.text(cx, cy, 18, "Start a game first.", DevUI.TEXT_DIM)
            return
        }
        if (names.isEmpty())
            onShown()

        val rowW = width - 24
        val maxScroll = (names.size - rowsShown).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)

        for (i in scroll until minOf(names.size, scroll + rowsShown)) {
            val name = names[i]
            val ry = cy + (i - scroll) * 20
            val hot = ui.hovered(cx, ry, rowW, 19)
            ui.fill(cx, ry, rowW, 19, if (hot) DevUI.HOVER_BG else DevUI.CONTROL_BG)
            ui.outline(cx, ry, rowW, 19, DevUI.BORDER)
            ui.text(cx + 8, ry, 19, name, DevUI.TEXT)
            if (ui.pressedIn(cx, ry, rowW, 19)) {
                ui.consumePress()
                try {
                    DevActions.loadEvent(game, name)
                    menu.setStatus("Loaded event $name")
                    open = false
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    menu.setStatus("Failed to load $name - see the log")
                }
            }
        }

        ui.text(cx, cy + rowsShown * 20 + 4, 14, "${names.size} events - scroll for more", DevUI.TEXT_DIM)
    }
}

/**
 * Enable, disable and reorder Slipstream mods. Changes are written to
 * order.txt and take effect when the game is restarted.
 */
class ModsWindow : DevWindow("Mods", 430) {
    /** The enabled mods, in load order (a working copy). */
    private val order = ArrayList<String>()
    private var available: List<String> = emptyList()
    private var scroll = 0
    private val rowsShown = 9

    override val contentHeight = rowsShown * 24 + 56

    override fun onShown() {
        available = try {
            DevMods.availableMods()
        } catch (ex: Exception) {
            ex.printStackTrace()
            emptyList()
        }
        order.clear()
        // Keep only enabled mods that still exist on disk.
        order += DevMods.loadOrder().filter { it in available }
        scroll = 0
    }

    override fun onScroll(change: Int) {
        scroll -= change / 40
    }

    /** Enabled mods first (in load order), then the disabled ones. */
    private fun rows(): List<String> = order + available.filter { it !in order }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        if (available.isEmpty()) {
            ui.text(cx, cy, 16, "No mods found. Put .ftl mod files in:", DevUI.TEXT_DIM)
            ui.text(cx, cy + 18, 13, DevMods.modDirectory.toString(), DevUI.TEXT_DIM)
            return
        }

        val rows = rows()
        scroll = scroll.coerceIn(0, (rows.size - rowsShown).coerceAtLeast(0))

        val rowW = width - 24
        for (i in scroll until minOf(rows.size, scroll + rowsShown)) {
            val mod = rows[i]
            val enabled = mod in order
            val ry = cy + (i - scroll) * 24

            // Checkbox + name - click toggles enabled.
            val toggleW = rowW - 66
            if (ui.hovered(cx, ry, toggleW, 22))
                ui.fill(cx, ry, toggleW, 22, DevUI.HOVER_BG)
            ui.fill(cx + 4, ry + 4, 14, 14, if (enabled) DevUI.ACCENT else DevUI.CONTROL_BG)
            ui.outline(cx + 4, ry + 4, 14, 14, DevUI.BORDER)
            ui.text(cx + 26, ry, 22, mod, DevUI.TEXT)
            if (ui.pressedIn(cx, ry, toggleW, 22)) {
                ui.consumePress()
                if (enabled) order.remove(mod) else order.add(mod)
            }

            // Up / Down reorder, only for enabled mods.
            if (enabled) {
                val idx = order.indexOf(mod)
                if (ui.button(cx + rowW - 62, ry, 30, 22, "Up", idx > 0)) {
                    order[idx] = order.set(idx - 1, mod)
                }
                if (ui.button(cx + rowW - 30, ry, 30, 22, "Dn", idx < order.size - 1)) {
                    order[idx] = order.set(idx + 1, mod)
                }
            }
        }

        val footY = cy + rowsShown * 24 + 6
        if (ui.button(cx, footY, 150, 22, "Save Mod Order")) {
            try {
                DevMods.saveOrder(order)
                menu.setStatus("Saved - restart to apply mods")
            } catch (ex: Exception) {
                ex.printStackTrace()
                menu.setStatus("Failed to save order.txt - see the log")
            }
        }
        ui.text(cx, footY + 28, 14, "Restart the game to apply mod changes.", DevUI.TEXT_DIM)
    }
}

/** Toggles for the combat autopilot and the various automation helpers. */
class AutomationWindow : DevWindow("Automation", 330) {
    private class Toggle(val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit)

    override val contentHeight = 20 + 3 * 22 + 14 + 4 * 22 + 8

    private fun row(ui: DevUI, cx: Int, ry: Int, t: Toggle) {
        if (ui.checkbox(cx, ry, width - 24, 20, t.label, t.get()))
            t.set(!t.get())
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        var ry = cy
        ui.text(cx, ry, 18, "Autopilot - the engine's AI plays for you", DevUI.TEXT_DIM)
        ry += 20

        for (t in listOf(
            Toggle("Auto Weapons", { DevSettings.autoWeapons }, { DevSettings.autoWeapons = it }),
            Toggle("Auto Crew", { DevSettings.autoCrew }, { DevSettings.autoCrew = it }),
            Toggle("Auto Systems", { DevSettings.autoSystems }, { DevSettings.autoSystems = it }),
        )) {
            row(ui, cx, ry, t)
            ry += 22
        }

        ui.fill(cx, ry + 2, width - 24, 1, DevUI.SEPARATOR)
        ry += 14

        for (t in listOf(
            Toggle("Auto-Pause on combat / events", { DevSettings.autoPause }, { DevSettings.autoPause = it }),
            Toggle("Auto-Resolve Events", { DevSettings.autoResolveEvents }, { DevSettings.autoResolveEvents = it }),
            Toggle("Auto-Jump when safe", { DevSettings.autoJump }, { DevSettings.autoJump = it }),
            Toggle("Arena Mode - endless AI battles", { DevSettings.arenaMode }, { DevSettings.arenaMode = it }),
        )) {
            row(ui, cx, ry, t)
            ry += 22
        }
    }
}

/**
 * Co-operative multiplayer - host or join a game. This is the Step 1
 * handshake; game-state sync and shared control build on top.
 */
class MultiplayerWindow : DevWindow("Multiplayer", 390) {
    override val contentHeight = 168

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        ui.text(cx, cy, 18, "Status:  ${Multiplayer.status}", DevUI.TEXT)
        var ry = cy + 28

        when (Multiplayer.state) {
            Multiplayer.State.IDLE, Multiplayer.State.FAILED -> {
                if (ui.button(cx, ry, 175, 24, "Host a Game"))
                    Multiplayer.host()
                if (ui.button(cx + 185, ry, 175, 24, "Join on localhost"))
                    Multiplayer.join("127.0.0.1")
                ry += 30
                if (ui.button(cx, ry, 240, 24, "Join - address from clipboard")) {
                    val addr = GLFW.glfwGetClipboardString(GLFW.glfwGetCurrentContext())
                    if (addr.isNullOrBlank())
                        menu.setStatus("Clipboard is empty - copy the host's address first")
                    else
                        Multiplayer.join(addr.trim())
                }
            }

            Multiplayer.State.HOSTING -> {
                ui.text(cx, ry, 15, "Your address:  ${Multiplayer.localAddress()}", DevUI.TEXT_DIM)
                ry += 24
                if (ui.button(cx, ry, 170, 24, "Copy My Address")) {
                    GLFW.glfwSetClipboardString(GLFW.glfwGetCurrentContext(), Multiplayer.localAddress())
                    menu.setStatus("Address copied to clipboard")
                }
                if (ui.button(cx + 180, ry, 120, 24, "Cancel"))
                    Multiplayer.disconnect()
            }

            Multiplayer.State.CONNECTING -> {
                if (ui.button(cx, ry, 120, 24, "Cancel"))
                    Multiplayer.disconnect()
            }

            Multiplayer.State.CONNECTED -> {
                val role = if (Multiplayer.hosting) "You are hosting." else "You are connected as a client."
                ui.text(cx, ry, 16, role, DevUI.TEXT_DIM)
                ry += 26
                if (ui.button(cx, ry, 150, 24, "Disconnect"))
                    Multiplayer.disconnect()
            }
        }

        ui.text(cx, cy + contentHeight - 38, 13,
            "Handshake only so far - game sync is the next step.", DevUI.TEXT_DIM)
        ui.text(cx, cy + contentHeight - 22, 13,
            "Internet play: the host must forward TCP port ${Multiplayer.DEFAULT_PORT}.", DevUI.TEXT_DIM)
    }
}
