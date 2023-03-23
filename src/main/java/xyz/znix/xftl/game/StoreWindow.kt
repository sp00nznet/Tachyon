package xyz.znix.xftl.game

import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.draw
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

class StoreWindow(val game: SlickGame, val ship: Ship, val store: StoreData, private val close: () -> Unit) :
    Window(ConstPoint(100, 130)) {

    override val size = ConstPoint(587, 423)
    override val outlineImage get() = error("Store uses a pre-made background image")

    private val buyImage = game.getImg("img/storeUI/store_buy_main.png")
    private val sellImage = game.getImg("img/storeUI/store_sell_main.png")
    private val closeButtonOutline = game.getImg("img/storeUI/store_close_base.png")

    private val buySellTabFont = game.getFont("HL2", 3f)
    private val sectionFont = game.getFont("HL2", 2f)
    private val numberFont = game.getFont("num_font")

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

    private val fuelButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 76), Resource.FUEL)
    private val missilesButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 126), Resource.MISSILES)
    private val dronesButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 175), Resource.DRONES)

    private val closeButton = Buttons.BasicButton(
        position + ConstPoint(466, 472),
        ConstPoint(103, 32), game.translator["button_close"], game, 5, this::escapePressed
    )

    private var sellTab: Boolean = false
        set(value) {
            field = value
            updateButtons()
        }

    init {
        updateButtons()
    }

    private fun updateButtons() {
        buttons.add(closeButton)

        if (sellTab) {
            buttons.add(buyTabButton)
        } else {
            buttons.add(sellTabButton)
            buttons.add(fuelButton)
            buttons.add(missilesButton)
            buttons.add(dronesButton)
        }
    }

    override fun draw(g: Graphics) {
        if (sellTab) {
            drawSell(g)
        } else {
            drawBuy(g)
        }

        // See JumpWindow
        // Note the corners should have one step more roundness than the current
        // buttons, so the current ones look a bit ugly.
        closeButtonOutline.draw(closeButton.pos + ConstPoint(-23, -7))
        closeButton.draw(g)

        buySellTabFont.drawString(
            position.x + GLOW_WIDTH + 47f,
            position.y + GLOW_WIDTH + 25f,
            game.translator["store_tab_buy"],
            Constants.JUMP_DISABLED_TEXT
        )

        buySellTabFont.drawString(
            position.x + GLOW_WIDTH + 47f + 161f,
            position.y + GLOW_WIDTH + 25f,
            game.translator["store_tab_sell"],
            Constants.JUMP_DISABLED_TEXT
        )
    }

    private fun drawBuy(g: Graphics) {
        buyImage.draw(position)

        sellTabButton.draw(g)

        // Draw the consumable items section (fuel, missiles, drones)
        sectionFont.drawString(
            position.x + GLOW_WIDTH + 11f,
            position.y + GLOW_WIDTH + 54f,
            game.translator["store_title_items"],
            Constants.JUMP_DISABLED_TEXT
        )

        fuelButton.draw(g)
        missilesButton.draw(g)
        dronesButton.draw(g)

        // Draw the hull repair section
        sectionFont.drawString(
            position.x + GLOW_WIDTH + 11f,
            position.y + GLOW_WIDTH + 54f + 209f,
            game.translator["store_title_repair"],
            Constants.JUMP_DISABLED_TEXT
        )
    }

    private fun drawSell(g: Graphics) {
        sellImage.draw(position)

        buyTabButton.draw(g)
    }

    override fun escapePressed() {
        close()
    }

    inner class ResourceButton(pos: IPoint, val resource: Resource) :
        Button(position + pos, ConstPoint(169, 40)) {

        private val textureName: String
            get() = when (resource) {
                Resource.FUEL -> "fuel"
                Resource.MISSILES -> "missiles"
                Resource.DRONES -> "drones"
                Resource.SCRAP -> error("Can't buy resource $resource")
            }

        val normal = game.getImg("img/storeUI/store_items_${textureName}_on.png")
        val off = game.getImg("img/storeUI/store_items_${textureName}_off.png")
        val hover = game.getImg("img/storeUI/store_items_${textureName}_select2.png")

        val numAvailable: Int get() = store.availableResources[resource] ?: 0

        override fun draw(g: Graphics) {
            val image = when {
                numAvailable == 0 -> off
                hovered -> hover
                else -> normal
            }
            image.draw(pos)

            // Don't draw the number and price text if we've sold out.
            if (numAvailable == 0)
                return

            val textColour = when {
                hovered -> Constants.STORE_BUY_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }

            numberFont.drawString(pos.x + 70f, pos.y + 26f, numAvailable.toString(), textColour)

            val price = 5 // TODO

            numberFont.drawString(pos.x + 130f, pos.y + 26f, price.toString(), textColour)
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (numAvailable == 0)
                return

            // TODO deduct scrap

            val resourceSet = ResourceSet()
            resourceSet[resource] = 1
            game.givePlayerResources(resourceSet)

            store.availableResources[resource] = numAvailable - 1
        }
    }

    companion object {
        const val GLOW_WIDTH: Int = 7
    }
}
