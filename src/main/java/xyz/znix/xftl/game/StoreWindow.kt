package xyz.znix.xftl.game

import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants
import xyz.znix.xftl.draw
import xyz.znix.xftl.math.ConstPoint

class StoreWindow(val game: SlickGame, private val close: () -> Unit) : Window(ConstPoint(100, 130)) {
    override val size = ConstPoint(587, 423)
    override val outlineImage get() = error("Store uses a pre-made background image")

    private val buyImage = game.getImg("img/storeUI/store_buy_main.png")
    private val sellImage = game.getImg("img/storeUI/store_sell_main.png")
    private val closeButtonOutline = game.getImg("img/storeUI/store_close_base.png")

    private val font = game.getFont("HL2", 3f)

    private val buyTabButton = SimpleButton(
        position + ConstPoint(0, 0), ConstPoint(170, 46), ConstPoint(0, 0),
        game.getImg("img/upgradeUI/Equipment/tabButtons/sell_buy_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/sell_buy_select2.png")
    ) {
        sellTab = false
    }

    private val sellTabButton = SimpleButton(
        position + ConstPoint(163, 0), ConstPoint(179, 46), ConstPoint(0, 0),
        game.getImg("img/upgradeUI/Equipment/tabButtons/buy_sell_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/buy_sell_select2.png")
    ) {
        sellTab = true
    }

    // TODO finish fiddling with these to make it line up properly
    private val closeButton = Buttons.BasicButton(
        position + ConstPoint(467, 473),
        ConstPoint(103, 32), game.translator["button_close"], game, 5, this::escapePressed
    )

    private var sellTab: Boolean = false
        set(value) {
            field = value
            buttons[1] = if (value) buyTabButton else sellTabButton
        }

    init {
        buttons.add(closeButton)
        buttons.add(sellTabButton)
    }

    override fun draw(g: Graphics) {
        if (sellTab) {
            drawSell(g)
        } else {
            drawBuy(g)
        }

        // See JumpWindow
        closeButtonOutline.draw(closeButton.pos + ConstPoint(-24, -8))
        closeButton.draw(g)

        font.drawString(
            position.x + 7f + 47f,
            position.y + 7f + 25f,
            game.translator["store_tab_buy"],
            Constants.JUMP_DISABLED_TEXT
        )

        font.drawString(
            position.x + 7f + 47f + 161f,
            position.y + 7f + 25f,
            game.translator["store_tab_sell"],
            Constants.JUMP_DISABLED_TEXT
        )
    }

    private fun drawBuy(g: Graphics) {
        buyImage.draw(position)

        sellTabButton.draw(g)
    }

    private fun drawSell(g: Graphics) {
        sellImage.draw(position)

        buyTabButton.draw(g)
    }

    override fun escapePressed() {
        close()
    }
}
