package xyz.znix.xftl.devmenu

import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics

/**
 * A tiny immediate-mode UI toolkit for the developer menu, drawn entirely with
 * the engine's own [Graphics] and a [SILFontLoader] - no external dependencies.
 *
 * Widgets activate on mouse-press, which keeps the toolkit almost stateless:
 * the only retained state is [activeWidget] (the slider knob or window title
 * bar currently being dragged) and the per-frame input snapshot from [begin].
 */
class DevUI(val font: SILFontLoader) {
    lateinit var g: Graphics
        private set

    var mouseX = 0
        private set
    var mouseY = 0
        private set

    /** True while the left mouse button is held down. */
    var mouseHeld = false
        private set

    private var pendingPress = false

    /** The widget currently being dragged (a slider knob or a window). */
    var activeWidget: Any? = null

    /** Set up the per-frame input snapshot. Call once at the start of rendering. */
    fun begin(g: Graphics, mouseX: Int, mouseY: Int, mouseHeld: Boolean, pressed: Boolean) {
        this.g = g
        this.mouseX = mouseX
        this.mouseY = mouseY
        this.mouseHeld = mouseHeld
        this.pendingPress = pressed

        // The button was released - nothing is being dragged any more.
        if (!mouseHeld)
            activeWidget = null
    }

    /** True if there is an unconsumed left-button press this frame. */
    val hasPress get() = pendingPress

    /** Mark the pending press as handled so later widgets ignore it. */
    fun consumePress() {
        pendingPress = false
    }

    fun hovered(x: Int, y: Int, w: Int, h: Int) =
        mouseX in x until x + w && mouseY in y until y + h

    fun pressedIn(x: Int, y: Int, w: Int, h: Int) =
        pendingPress && hovered(x, y, w, h)

    // ---- drawing primitives ----

    fun fill(x: Int, y: Int, w: Int, h: Int, colour: Colour) {
        g.colour = colour
        g.fillRect(x, y, w, h)
    }

    fun outline(x: Int, y: Int, w: Int, h: Int, colour: Colour) {
        g.colour = colour
        g.drawRect(x, y, w - 1, h - 1)
    }

    /** Draw a string vertically centred within a row of the given height. */
    fun text(x: Int, rowY: Int, rowH: Int, s: String, colour: Colour) {
        font.drawString(x.toFloat(), (rowY + rowH - 6).toFloat(), s, colour)
    }

    fun textWidth(s: String) = font.getWidth(s)

    // ---- widgets ----

    /** A clickable text button. Returns true on the frame it is clicked. */
    fun button(x: Int, y: Int, w: Int, h: Int, label: String, enabled: Boolean = true): Boolean {
        val hot = enabled && hovered(x, y, w, h)
        fill(
            x, y, w, h, when {
                !enabled -> DISABLED_BG
                hot -> ACCENT
                else -> CONTROL_BG
            }
        )
        outline(x, y, w, h, BORDER)
        val tw = textWidth(label)
        text(x + (w - tw) / 2, y, h, label, if (enabled) TEXT else TEXT_DISABLED)

        if (enabled && pressedIn(x, y, w, h)) {
            consumePress()
            return true
        }
        return false
    }

    /**
     * A small square button drawn with a plus or minus icon, used for steppers.
     * The icons are drawn as shapes since the baked font lacks a '+' glyph.
     */
    fun glyphButton(x: Int, y: Int, w: Int, h: Int, plus: Boolean, enabled: Boolean = true): Boolean {
        val hot = enabled && hovered(x, y, w, h)
        fill(
            x, y, w, h, when {
                !enabled -> DISABLED_BG
                hot -> ACCENT
                else -> CONTROL_BG
            }
        )
        outline(x, y, w, h, BORDER)

        g.colour = if (enabled) TEXT else TEXT_DISABLED
        val cx = x + w / 2
        val cy = y + h / 2
        g.fillRect(cx - 5, cy - 1, 10, 2)
        if (plus)
            g.fillRect(cx - 1, cy - 5, 2, 10)

        if (enabled && pressedIn(x, y, w, h)) {
            consumePress()
            return true
        }
        return false
    }

    /** A labelled checkbox. Returns true on the frame it is toggled. */
    fun checkbox(x: Int, y: Int, w: Int, h: Int, label: String, checked: Boolean, enabled: Boolean = true): Boolean {
        if (enabled && hovered(x, y, w, h))
            fill(x, y, w, h, HOVER_BG)

        val box = h - 8
        val boxY = y + 4
        fill(x + 4, boxY, box, box, if (checked && enabled) ACCENT else CONTROL_BG)
        outline(x + 4, boxY, box, box, BORDER)

        text(x + box + 12, y, h, label, if (enabled) TEXT else TEXT_DISABLED)

        if (enabled && pressedIn(x, y, w, h)) {
            consumePress()
            return true
        }
        return false
    }

    /**
     * A horizontal slider. [key] uniquely identifies this slider so it can be
     * tracked while dragged. Returns the (possibly updated) value, in 0..1.
     */
    fun slider(key: Any, x: Int, y: Int, w: Int, h: Int, value: Float): Float {
        val trackY = y + h / 2 - 2
        fill(x, trackY, w, 4, CONTROL_BG)
        outline(x, trackY, w, 4, BORDER)

        if (pressedIn(x, y, w, h)) {
            activeWidget = key
            consumePress()
        }

        var v = value
        if (activeWidget === key && mouseHeld) {
            v = ((mouseX - x).toFloat() / w).coerceIn(0f, 1f)
        }

        val knobX = x + (v * w).toInt()
        fill(knobX - 4, y + 2, 8, h - 4, if (activeWidget === key) ACCENT else CONTROL_LIGHT)
        outline(knobX - 4, y + 2, 8, h - 4, BORDER)
        return v
    }

    companion object {
        val BAR_BG = Colour(28, 33, 44)
        val DROPDOWN_BG = Colour(40, 46, 60)
        val CONTROL_BG = Colour(52, 58, 74)
        val CONTROL_LIGHT = Colour(120, 128, 145)
        val DISABLED_BG = Colour(38, 42, 52)
        val HOVER_BG = Colour(64, 72, 92)
        val BORDER = Colour(15, 18, 24)
        val ACCENT = Colour(0, 160, 175)
        val TEXT = Colour(225, 228, 234)
        val TEXT_DIM = Colour(160, 166, 178)
        val TEXT_DISABLED = Colour(104, 110, 122)
        val TITLE_BG = Colour(0, 122, 134)
        val WINDOW_BG = Colour(34, 39, 51)
        val SEPARATOR = Colour(70, 78, 96)
    }
}
