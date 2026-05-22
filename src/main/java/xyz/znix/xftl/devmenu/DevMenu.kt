package xyz.znix.xftl.devmenu

import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.game.GameOverWindow
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.Input
import xyz.znix.xftl.sys.InputOverlay
import xyz.znix.xftl.sys.LWJGLGameContainer
import xyz.znix.xftl.sys.ResourceContext

/**
 * The developer menu: an always-visible menu bar across the top of the window,
 * with File / Graphics / Audio / Debug / About menus, plus the floating
 * windows they open.
 *
 * It is drawn entirely with the engine's own renderer and registered as the
 * [InputOverlay] on the [GameContainer], so it gets first chance at the mouse
 * before the active game state.
 */
class DevMenu(val mainGame: MainGame) : InputOverlay {
    private val context = ResourceContext()

    /** A standalone copy of Roboto, so the menu works before FTL's assets load. */
    val font: SILFontLoader = run {
        val data = javaClass.classLoader
            .getResourceAsStream("baked/roboto.font")!!
            .readAllBytes()
        SILFontLoader(context, null, data)
    }

    private val ui = DevUI(font)

    // ---- floating windows ----
    private val audioWindow = AudioWindow()
    private val aboutWindow = AboutWindow()
    private val inspectorWindow = InspectorWindow()
    private val loadWindow = LoadGameWindow()
    private val outfitterWindow = OutfitterWindow()
    private val windows = listOf(inspectorWindow, outfitterWindow, loadWindow, audioWindow, aboutWindow)

    // ---- menu bar state ----
    private var openMenu: String? = null
    private var dropdownRect: IntArray? = null

    // ---- input snapshot, populated by the InputOverlay callbacks ----
    private var leftHeld = false
    private var queuedPress = false
    private var lastMouseX = 0
    private var lastMouseY = 0

    // ---- options / status ----
    private var showFps = true
    private var statusMessage: String? = null
    private var statusTimer = 0f

    // ---- FPS measurement ----
    private var fpsAccum = 0f
    private var fpsFrames = 0
    private var fps = 0

    init {
        for (window in windows)
            window.menu = this
    }

    /** The active game, or null if no game is in progress. */
    fun currentInGame(): InGameState? = mainGame.currentState as? InGameState

    /** Show a short-lived message in the menu bar. */
    fun setStatus(message: String) {
        statusMessage = message
        statusTimer = 4f
    }

    fun update(@Suppress("UNUSED_PARAMETER") gc: GameContainer, dt: Float) {
        fpsAccum += dt
        fpsFrames++
        if (fpsAccum >= 0.5f) {
            fps = (fpsFrames / fpsAccum).toInt()
            fpsAccum = 0f
            fpsFrames = 0
        }

        if (statusTimer > 0f) {
            statusTimer -= dt
            if (statusTimer <= 0f)
                statusMessage = null
        }
    }

    fun render(gc: GameContainer, g: Graphics) {
        // The container reports the cursor in the game's coordinate space,
        // which is shifted down by the bar. Shift it back into the menu's
        // own space, where the bar sits at the very top.
        val input = gc.input
        lastMouseX = input.mouseX
        lastMouseY = input.mouseY + BAR_HEIGHT

        ui.begin(g, lastMouseX, lastMouseY, leftHeld, queuedPress)
        queuedPress = false

        // ---- menu bar background ----
        ui.fill(0, 0, gc.width, BAR_HEIGHT, DevUI.BAR_BG)
        ui.fill(0, BAR_HEIGHT, gc.width, 1, DevUI.BORDER)

        // ---- brand ----
        ui.text(10, 0, BAR_HEIGHT, "Tachyon", DevUI.ACCENT)
        var x = 16 + ui.textWidth("Tachyon") + 14

        // ---- top-level menu titles ----
        x = title(gc, "File", x, hasDropdown = true)
        x = title(gc, "Graphics", x, hasDropdown = true)
        x = title(gc, "Audio", x, hasDropdown = false) { audioWindow.toggle() }
        x = title(gc, "Debug", x, hasDropdown = true)
        title(gc, "About", x, hasDropdown = false) { aboutWindow.toggle() }

        // ---- status message and FPS, right-aligned ----
        var rightX = gc.width - 8
        if (showFps) {
            val fpsText = "$fps FPS"
            rightX -= ui.textWidth(fpsText)
            ui.text(rightX, 0, BAR_HEIGHT, fpsText, DevUI.TEXT_DIM)
            rightX -= 16
        }
        statusMessage?.let { message ->
            rightX -= ui.textWidth(message)
            ui.text(rightX, 0, BAR_HEIGHT, message, DevUI.ACCENT)
        }

        // ---- floating windows ----
        for (window in windows) {
            if (window.open)
                window.render(ui)
        }

        // ---- the open dropdown, on top of everything ----
        dropdownRect = null
        when (openMenu) {
            "File" -> renderDropdown(gc, fileMenu(), menuX("File"))
            "Graphics" -> renderDropdown(gc, graphicsMenu(gc), menuX("Graphics"))
            "Debug" -> renderDropdown(gc, debugMenu(), menuX("Debug"))
        }

        // A click that nothing else consumed closes any open menu.
        if (ui.hasPress && openMenu != null) {
            ui.consumePress()
            openMenu = null
        }
    }

    // ---- menu bar titles ----

    private val titleX = HashMap<String, Int>()

    private fun menuX(name: String) = titleX[name] ?: 0

    private inline fun title(
        gc: GameContainer,
        name: String,
        x: Int,
        hasDropdown: Boolean,
        onClick: () -> Unit = {}
    ): Int {
        val w = ui.textWidth(name) + 20
        titleX[name] = x

        val active = openMenu == name
        val hot = ui.hovered(x, 0, w, BAR_HEIGHT)
        if (active || hot)
            ui.fill(x, 0, w, BAR_HEIGHT, if (active) DevUI.TITLE_BG else DevUI.HOVER_BG)
        ui.text(x + 10, 0, BAR_HEIGHT, name, DevUI.TEXT)

        if (ui.pressedIn(x, 0, w, BAR_HEIGHT)) {
            ui.consumePress()
            if (hasDropdown) {
                openMenu = if (openMenu == name) null else name
            } else {
                openMenu = null
                onClick()
            }
        } else if (hasDropdown && openMenu != null && openMenu != name && hot) {
            // Slide between menus by hovering, like a normal menu bar.
            openMenu = name
        }

        return x + w
    }

    private fun DevWindow.toggle() {
        open = !open
    }

    // ---- dropdown rendering ----

    private fun renderDropdown(gc: GameContainer, items: List<DropItem>, anchorX: Int) {
        val rowH = 22
        val sepH = 8

        var width = 170
        for (item in items) {
            if (item.kind != DropItem.Kind.SEPARATOR)
                width = maxOf(width, ui.textWidth(item.label) + 46)
        }

        var height = 8
        for (item in items)
            height += if (item.kind == DropItem.Kind.SEPARATOR) sepH else rowH

        val startX = anchorX.coerceAtMost(gc.width - width)
        dropdownRect = intArrayOf(startX, BAR_HEIGHT, width, height)

        ui.fill(startX, BAR_HEIGHT, width, height, DevUI.DROPDOWN_BG)
        ui.outline(startX, BAR_HEIGHT, width, height, DevUI.BORDER)

        var iy = BAR_HEIGHT + 4
        for (item in items) {
            if (item.kind == DropItem.Kind.SEPARATOR) {
                ui.fill(startX + 6, iy + 3, width - 12, 1, DevUI.SEPARATOR)
                iy += sepH
                continue
            }

            val hot = item.enabled && ui.hovered(startX, iy, width, rowH)
            if (hot)
                ui.fill(startX, iy, width, rowH, DevUI.HOVER_BG)

            if (item.kind == DropItem.Kind.TOGGLE) {
                val box = rowH - 10
                ui.fill(startX + 10, iy + 5, box, box, if (item.checked) DevUI.ACCENT else DevUI.CONTROL_BG)
                ui.outline(startX + 10, iy + 5, box, box, DevUI.BORDER)
            }

            ui.text(
                startX + 30, iy, rowH, item.label,
                if (item.enabled) DevUI.TEXT else DevUI.TEXT_DISABLED
            )

            if (item.enabled && ui.pressedIn(startX, iy, width, rowH)) {
                ui.consumePress()
                item.onClick()
                // Actions close the menu; toggles leave it open for more changes.
                if (item.kind == DropItem.Kind.ACTION)
                    openMenu = null
            }

            iy += rowH
        }
    }

    // ---- menu definitions ----

    private fun fileMenu(): List<DropItem> {
        val game = currentInGame()
        return listOf(
            DropItem.action("New Game") { mainGame.switchToShipSelect() },
            DropItem.action("Save Game", enabled = game != null) {
                try {
                    val file = DevActions.saveGame(game!!)
                    setStatus("Saved ${file.fileName}")
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    setStatus("Save failed - see the log")
                }
            },
            DropItem.action("Load Game...") { loadWindow.open = true },
            DropItem.separator(),
            DropItem.action("Quit") { mainGame.quitGame() },
        )
    }

    private fun graphicsMenu(gc: GameContainer): List<DropItem> {
        val container = gc as? LWJGLGameContainer
            ?: return listOf(DropItem.action("unavailable", enabled = false) {})

        // The window is the game area plus the menu bar; the labels name the
        // game area, which is what the player actually cares about.
        // Window sizes name the game area; the window is that plus the menu
        // bar. setWindowSize clamps anything too big for the monitor.
        fun sizeItem(label: String, winW: Int, winH: Int) =
            DropItem.action(label) { container.setWindowSize(winW, winH) }

        return listOf(
            DropItem.toggle("V-Sync", container.vSyncEnabled) {
                container.setVSyncEnabled(!container.vSyncEnabled)
            },
            DropItem.toggle("Show FPS", showFps) { showFps = !showFps },
            DropItem.separator(),
            sizeItem("1280 x 720", 1280, 742),
            sizeItem("1600 x 900", 1600, 928),
            sizeItem("1920 x 1080", 1920, 1113),
            sizeItem("2560 x 1440", 2560, 1484),
            sizeItem("3840 x 2160  (4K)", 3840, 2226),
            DropItem.action("Fill Screen") { container.fitWindowToScreen() },
            DropItem.separator(),
            DropItem.toggle("Fullscreen", container.fullscreen) {
                container.setFullscreen(!container.fullscreen)
            },
        )
    }

    private fun debugMenu(): List<DropItem> {
        val game = currentInGame()
        val flags = game?.debugFlags
        val inGame = game != null

        val items = ArrayList<DropItem>()
        if (flags != null) {
            items += DropItem.toggle("Ship Invincible", flags.noDmg.set) { flags.noDmg.set = !flags.noDmg.set }
            items += DropItem.toggle("Crew Invincible", flags.noCrewDamage.set) {
                flags.noCrewDamage.set = !flags.noCrewDamage.set
            }
            items += DropItem.toggle("Infinite Missiles", flags.infiniteMissiles.set) {
                flags.infiniteMissiles.set = !flags.infiniteMissiles.set
            }
            items += DropItem.toggle("Infinite Drones", flags.infiniteDrones.set) {
                flags.infiniteDrones.set = !flags.infiniteDrones.set
            }
            items += DropItem.toggle("Fast Weapon Charge", flags.fastWeaponCharge.set) {
                flags.fastWeaponCharge.set = !flags.fastWeaponCharge.set
            }
            items += DropItem.toggle("No Enemy Weapons", flags.noEnemyFire.set) {
                flags.noEnemyFire.set = !flags.noEnemyFire.set
            }
            items += DropItem.toggle("Jump Anywhere", flags.anyJump.set) {
                flags.anyJump.set = !flags.anyJump.set
            }
            items += DropItem.toggle("Reveal Map - Full Sensors", flags.showEverything.set) {
                flags.showEverything.set = !flags.showEverything.set
            }
        } else {
            for (label in DISABLED_FLAG_LABELS)
                items += DropItem.toggle(label, false, enabled = false) {}
        }

        items += DropItem.separator()
        items += DropItem.action("Repair Player Ship", enabled = inGame) {
            DevActions.repairPlayerShip(game!!)
            setStatus("Player ship repaired")
        }
        items += DropItem.action("Max Resources", enabled = inGame) {
            DevActions.maxResources(game!!)
            setStatus("Resources maxed out")
        }
        items += DropItem.action("Upgrade All Systems", enabled = inGame) {
            DevActions.upgradeAllSystems(game!!)
            setStatus("All systems upgraded")
        }
        items += DropItem.action("Heal All Crew", enabled = inGame) {
            DevActions.healAllCrew(game!!)
            setStatus("All crew healed")
        }
        items += DropItem.action("Destroy Enemy Ship", enabled = inGame && game?.enemy != null) {
            DevActions.destroyEnemyShip(game!!)
            setStatus("Enemy ship destroyed")
        }
        items += DropItem.separator()
        items += DropItem.action("End Run - Victory", enabled = inGame) {
            game!!.shipUI.showGameOverScreen(GameOverWindow.Outcome.WIN)
        }
        items += DropItem.action("End Run - Defeat", enabled = inGame) {
            game!!.shipUI.showGameOverScreen(GameOverWindow.Outcome.LOOSE_HULL)
        }
        items += DropItem.action("Outfitter...", enabled = inGame) { outfitterWindow.open = true }
        items += DropItem.action("Game Inspector...") { inspectorWindow.open = true }
        return items
    }

    // ---- InputOverlay ----

    override fun isCapturingMouse(x: Int, y: Int): Boolean {
        if (y < BAR_HEIGHT)
            return true

        dropdownRect?.let { r ->
            if (x in r[0] until r[0] + r[2] && y in r[1] until r[1] + r[3])
                return true
        }

        for (window in windows) {
            if (window.open && window.contains(x, y))
                return true
        }
        return false
    }

    override fun overlayMousePressed(button: Int, x: Int, y: Int): Boolean {
        lastMouseX = x
        lastMouseY = y

        // While a menu is open the bar behaves modally: any click is captured,
        // so a click elsewhere just closes the menu instead of hitting the game.
        if (!isCapturingMouse(x, y) && openMenu == null)
            return false

        if (button == Input.MOUSE_LEFT_BUTTON) {
            leftHeld = true
            queuedPress = true
        }
        return true
    }

    override fun overlayMouseReleased(button: Int, x: Int, y: Int) {
        if (button == Input.MOUSE_LEFT_BUTTON)
            leftHeld = false
    }

    override fun overlayMouseWheel(change: Int): Boolean {
        for (window in windows) {
            if (window.open && window.contains(lastMouseX, lastMouseY)) {
                window.onScroll(change)
                return true
            }
        }
        return isCapturingMouse(lastMouseX, lastMouseY)
    }

    companion object {
        /** Height of the menu bar, in logical pixels. */
        const val BAR_HEIGHT = 22

        /** Total logical canvas height: the game's 720 plus the menu bar. */
        const val CANVAS_HEIGHT = 720 + BAR_HEIGHT

        private val DISABLED_FLAG_LABELS = listOf(
            "Ship Invincible", "Crew Invincible", "Infinite Missiles",
            "Infinite Drones", "Fast Weapon Charge", "No Enemy Weapons",
            "Jump Anywhere", "Reveal Map - Full Sensors"
        )
    }
}

/** A single entry in a dropdown menu. */
data class DropItem(
    val label: String,
    val kind: Kind,
    val enabled: Boolean,
    val checked: Boolean,
    val onClick: () -> Unit
) {
    enum class Kind { ACTION, TOGGLE, SEPARATOR }

    companion object {
        fun action(label: String, enabled: Boolean = true, onClick: () -> Unit) =
            DropItem(label, Kind.ACTION, enabled, false, onClick)

        fun toggle(label: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) =
            DropItem(label, Kind.TOGGLE, enabled, checked, onClick)

        fun separator() = DropItem("", Kind.SEPARATOR, false, false, {})
    }
}
