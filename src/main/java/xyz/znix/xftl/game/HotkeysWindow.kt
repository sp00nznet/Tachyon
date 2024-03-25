package xyz.znix.xftl.game

import xyz.znix.xftl.Constants
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.pop
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import kotlin.math.max
import kotlin.math.roundToInt

class HotkeysWindow(val game: InGameState, val close: () -> Unit) : Window() {
    override val size: IPoint get() = ConstPoint(1050, 650)

    private val nameFont = game.getFont("JustinFont11Bold")
    private val titleFont = game.getFont("HL2", 2f)
    private val titleTab = game.getImg("img/map/side_beaconmap.png")

    private var scroll: Float = 0f
    private var maxY: Int = 0

    private val groups: List<Pair<HotkeyGroup, HotkeyGroup?>>

    init {
        // Group single-column categories together (while wasting as little
        // space as possible) so we can show them side-by-side.
        val byCount = game.hotkeyManager.groups
            .filter { it.columns == 1 }
            .sortedBy { it.hotkeys.size }

        // Split them into pairs
        val singleColumnPairs = HashMap<HotkeyGroup, HotkeyGroup>()
        for (i in 0 until byCount.size - 1 step 2) {
            val a = byCount[i]
            val b = byCount[i + 1]
            singleColumnPairs[a] = b
            singleColumnPairs[b] = a
        }

        // Build the final ordered list
        val remaining = ArrayList(game.hotkeyManager.groups)
        remaining.reverse()

        groups = ArrayList()
        while (remaining.isNotEmpty()) {
            val group = remaining.pop()
            val paired = singleColumnPairs[group]
            if (paired != null) {
                remaining.remove(paired)
            }

            groups.add(Pair(group, paired))
        }
    }

    override fun draw(g: Graphics) {
        val titleText = game.translator["configure_controls_tab"]

        val startWidth = 33
        val endWidth = 45
        val textWidth = titleFont.getWidth(titleText)
        val tabWidth = startWidth + textWidth + endWidth

        val intScroll = scroll.roundToInt()

        game.windowRenderer.renderMasked(position.x, position.y, size.x, size.y, 0, intScroll, {
            g.colour = Colour.red // Anything non-transparent will do
            g.fillRect(position.x - 7, position.y - 7, tabWidth, titleTab.height)
        }, {
            g.pushTransform()
            g.translate(position.x.f, position.y + intScroll + 30f)
            drawContent(g)
            g.popTransform()
        })

        UIUtils.drawTab(
            titleFont,
            titleText,
            titleTab,
            position.x - 7f,
            position.y - 7f,
            startWidth.f,
            endWidth.f
        )
        titleFont.drawString(position.x.f - 7f + startWidth, position.y + 22f, titleText, Constants.JUMP_DISABLED_TEXT)

        // TODO implement rebinding - for now show users a warning that they can't change it
        titleFont.drawStringCentred(
            position.x.f,
            position.y + 25f,
            size.x.f,
            "REBINDING NOT YET IMPLEMENTED!",
            Colour.red
        )
    }

    private fun drawContent(g: Graphics) {
        var y = 30

        for ((left, right) in groups) {
            // If we've got a pair of groups, draw them
            if (right != null) {
                val leftY = drawGroup(g, left, y, Layout.LEFT)
                val rightY = drawGroup(g, right, y, Layout.RIGHT)
                y = max(leftY, rightY)
                continue
            }

            val layout = when {
                left.columns == 1 -> Layout.LEFT
                else -> Layout.BOTH
            }
            y = drawGroup(g, left, y, layout)
        }

        maxY = y
    }

    private fun drawGroup(g: Graphics, group: HotkeyGroup, startY: Int, layout: Layout): Int {
        var y = startY

        val hotkeySpacing = 32

        // Sets the colour of the box the text sits on
        g.colour = Constants.SECTOR_CUTOUT_TEXT

        val titleSplitX = calcBoxX(0, 2) + KEY_BOX_WIDTH + PADDING

        when (layout) {
            Layout.BOTH -> {
                Buttons.drawRounded(g, PADDING, y - 17, size.x - PADDING * 2, 22, 4)
                titleFont.drawStringCentred(
                    0f, y.f, size.x.f,
                    game.translator[group.name],
                    Constants.JUMP_DISABLED_TEXT
                )
            }

            Layout.LEFT -> {
                Buttons.drawRounded(g, PADDING, y - 17, titleSplitX - PADDING * 2, 22, 4)
                titleFont.drawStringCentred(
                    0f, y.f, size.x / 2f,
                    game.translator[group.name],
                    Constants.JUMP_DISABLED_TEXT
                )
            }

            Layout.RIGHT -> {
                Buttons.drawRounded(
                    g,
                    titleSplitX + PADDING, y - 17,
                    size.x - titleSplitX - PADDING * 2, 22,
                    4
                )
                titleFont.drawStringCentred(
                    size.x / 2f, y.f, size.x / 2f,
                    game.translator[group.name],
                    Constants.JUMP_DISABLED_TEXT
                )
            }
        }

        y += 30

        var nextColumn = 0

        for (key in group.hotkeys) {
            if (layout != Layout.BOTH) {
                val colX = calcBoxX(if (layout == Layout.LEFT) 0 else 1, 2)
                drawHotkey(g, key, y, colX)
                y += hotkeySpacing
                continue
            }

            val colX = calcBoxX(nextColumn, group.columns)

            if (nextColumn == 0) {
                // Every time we start a new row, advance the Y
                drawHotkey(g, key, y, colX)
                y += hotkeySpacing
            } else {
                // Otherwise we're adding to an exising row
                drawHotkey(g, key, y - hotkeySpacing, colX)
            }

            nextColumn = (nextColumn + 1) % group.columns
        }

        return y + 30
    }

    private fun drawHotkey(g: Graphics, key: Hotkey, y: Int, boxX: Int) {
        nameFont.drawStringLeftAligned(
            boxX - 15f,
            y.f,
            game.translator[key.name],
            Constants.SECTOR_CUTOUT_TEXT
        )

        val middleOfText = y - nameFont.baselineToTop / 2
        val boxY = middleOfText - KEY_BOX_HEIGHT / 2

        g.colour = Constants.REWARDS_BACKGROUND
        g.fillRect(boxX, boxY, KEY_BOX_WIDTH, KEY_BOX_HEIGHT)
        g.colour = Colour.white
        g.drawRect(boxX, boxY, KEY_BOX_WIDTH - 1, KEY_BOX_HEIGHT - 1)
        g.drawRect(boxX + 1, boxY + 1, KEY_BOX_WIDTH - 3, KEY_BOX_HEIGHT - 3)

        // TODO actually implement re-binding
        val keyName = key.default?.locKey?.let { game.translator[it] } ?: "..."
        nameFont.drawStringCentred(boxX.f, boxY + 17f, KEY_BOX_WIDTH.f, keyName, Colour.white)
    }

    private fun calcBoxX(column: Int, count: Int): Int {
        val width = size.x - PADDING * 2
        val rightEdgeX = PADDING + width * (column + 1) / count
        return rightEdgeX - KEY_BOX_WIDTH
    }

    override fun escapePressed() {
        close()
    }

    override fun mouseScroll(change: Int) {
        scroll += change / 10f

        // Can't scroll too far down
        val maxDownScroll = size.y - maxY
        if (scroll < maxDownScroll) {
            scroll = maxDownScroll.f
        }

        // Can't scroll too far up
        if (scroll > 0f) {
            scroll = 0f
        }
    }

    companion object {
        private const val KEY_BOX_WIDTH = 120
        private const val KEY_BOX_HEIGHT = 22
        private const val PADDING = 15
    }

    /**
     * Groups can either be on the left or right side, or mixed between them.
     */
    private enum class Layout {
        LEFT,
        RIGHT,
        BOTH;
    }
}
