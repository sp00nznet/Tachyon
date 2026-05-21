package xyz.znix.xftl.devmenu

import org.lwjgl.glfw.GLFW
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.rendering.Colour
import java.nio.file.Path

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
class LoadGameWindow : DevWindow("Load Game", 420) {
    private var saves = emptyList<Path>()
    private var scroll = 0

    private val rowsShown = 9

    override val contentHeight = rowsShown * 24 + 22

    override fun onShown() {
        saves = try {
            DevActions.listSaves()
        } catch (ex: Exception) {
            ex.printStackTrace()
            emptyList()
        }
        scroll = 0
    }

    override fun onScroll(change: Int) {
        // 'change' is positive when scrolling up.
        val maxScroll = (saves.size - rowsShown).coerceAtLeast(0)
        scroll = (scroll - change / 40).coerceIn(0, maxScroll)
    }

    override fun content(ui: DevUI, cx: Int, cy: Int) {
        if (saves.isEmpty()) {
            ui.text(cx, cy, 18, "No saves found in the debug-saves folder.", DevUI.TEXT_DIM)
            return
        }

        var ry = cy
        val rowW = width - 24
        for (i in scroll until minOf(saves.size, scroll + rowsShown)) {
            val file = saves[i]
            val name = file.fileName.toString().removeSuffix(".xml")
            val hot = ui.hovered(cx, ry, rowW, 22)
            ui.fill(cx, ry, rowW, 22, if (hot) DevUI.HOVER_BG else DevUI.CONTROL_BG)
            ui.outline(cx, ry, rowW, 22, DevUI.BORDER)
            ui.text(cx + 8, ry, 22, name, DevUI.TEXT)
            if (ui.pressedIn(cx, ry, rowW, 22)) {
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
            ry += 24
        }

        if (saves.size > rowsShown) {
            ui.text(cx, ry + 4, 14, "Scroll for more - ${saves.size} saves", DevUI.TEXT_DIM)
        }
    }
}
