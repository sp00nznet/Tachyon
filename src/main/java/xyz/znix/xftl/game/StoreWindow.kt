package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input
import xyz.znix.xftl.Constants
import xyz.znix.xftl.Ship
import xyz.znix.xftl.draw
import xyz.znix.xftl.imageSize
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint

class StoreWindow(val game: SlickGame, val ship: Ship, val store: StoreData, private val close: () -> Unit) : Window() {

    override val size = ConstPoint(587, 423)
    override val outlineImage get() = error("Store uses a pre-made background image")

    private val buyImage = game.getImg("img/storeUI/store_buy_main.png")
    private val sellImage = game.getImg("img/storeUI/store_sell_main.png")
    private val closeButtonOutline = game.getImg("img/storeUI/store_close_base.png")

    private val buySellTabFont = game.getFont("HL2", 3f)
    private val sectionFont = game.getFont("HL2", 2f)
    private val numberFont = game.getFont("num_font")
    private val systemNameFont = game.getFont("c&c", 2f)
    private val weaponNameFont = game.getFont("JustinFont8")

    private val buyTabButton = SimpleButton(
        ConstPoint(0, 0), ConstPoint(170, 46), ConstPoint(0, 0),
        game.getImg("img/upgradeUI/Equipment/tabButtons/sell_buy_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/sell_buy_select2.png")
    ) {
        sellTab = false
    }

    private val sellTabButton = SimpleButton(
        ConstPoint(163, 0), ConstPoint(179, 46), ConstPoint(0, 0),
        game.getImg("img/upgradeUI/Equipment/tabButtons/buy_sell_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/buy_sell_select2.png")
    ) {
        sellTab = true
    }

    private val buySubTab1Button = SimpleButton.byRegion(
        ConstPoint(GLOW_WIDTH + 348 - 3, GLOW_WIDTH + 9 - 5),
        ConstPoint(5, 5), ConstPoint(111, 24),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page2_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page2_select2.png")
    ) {
        secondBuyTab = false
    }
    private val buySubTab2Button = SimpleButton.byRegion(
        ConstPoint(GLOW_WIDTH + 348 - 3, GLOW_WIDTH + 9 - 5),
        ConstPoint(119, 5), ConstPoint(111, 24),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page1_on.png"),
        game.getImg("img/upgradeUI/Equipment/tabButtons/store_page1_select2.png")
    ) {
        secondBuyTab = true
    }

    private val fuelButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 76), Resource.FUEL)
    private val missilesButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 126), Resource.MISSILES)
    private val dronesButton = ResourceButton(ConstPoint(GLOW_WIDTH + 11, GLOW_WIDTH + 175), Resource.DRONES)

    // To avoid having separate fields for all the different things that
    // can be bought, have lists of buttons for the top and bottom buy panels.
    // These will be populated during drawing which is a bit ugly, but means
    // we can keep all the section-related stuff together.
    private val buyButtonsUpper = ArrayList<BuyButton>()
    private val buyButtonsLower = ArrayList<BuyButton>()
    private var updatingBuyButtons = false

    private val closeButton = Buttons.BasicButton(
        position + ConstPoint(466, 472),
        ConstPoint(103, 32), game.translator["button_close"], game, 5, this::escapePressed
    )

    private var sellTab: Boolean = false
        set(value) {
            field = value
            updateButtons()
        }

    // If there's two pages of stuff to buy, this is true if the second one is selected.
    private var secondBuyTab: Boolean = false
        set(value) {
            field = value
            updateButtons()
        }

    init {
        updateButtons()
    }

    private fun updateButtons() {
        buttons.clear()
        buttons.add(closeButton)

        if (sellTab) {
            buttons.add(buyTabButton)
        } else {
            buttons.add(sellTabButton)
            buttons.add(fuelButton)
            buttons.add(missilesButton)
            buttons.add(dronesButton)

            if (store.sections.size > 2) {
                buttons.add(if (secondBuyTab) buySubTab1Button else buySubTab2Button)
            }

            // Re-add the top and bottom buttons next draw
            updatingBuyButtons = true
        }

        // Re-position all the buttons
        positionUpdated()
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

        // If there's more than two sections, show the tab buttons to swap between them.
        if (store.sections.size > 2) {
            val swapButton = if (secondBuyTab) buySubTab1Button else buySubTab2Button
            swapButton.draw(g)

            sectionFont.drawString(
                position.x + GLOW_WIDTH + 374f,
                position.y + GLOW_WIDTH + 27f,
                game.translator["store_tab_page1"],
                Constants.JUMP_DISABLED_TEXT
            )
            sectionFont.drawString(
                position.x + GLOW_WIDTH + 374f + 104f,
                position.y + GLOW_WIDTH + 27f,
                game.translator["store_tab_page2"],
                Constants.JUMP_DISABLED_TEXT
            )
        }

        // Draw the two (top and bottom) main purchase sections
        val sectionOffset = if (secondBuyTab) 2 else 0
        drawBuySection(g, store.sections[sectionOffset + 0], 59, buyButtonsUpper)
        drawBuySection(g, store.sections[sectionOffset + 1], 268, buyButtonsLower)

        // We've just drawn both buy sections, so if we're supposed to
        // re-add the buy buttons, that's now done.
        updatingBuyButtons = false
    }

    private fun drawBuySection(g: Graphics, section: StoreData.Section, y: Int, buyButtons: ArrayList<BuyButton>) {
        val pos = ConstPoint(GLOW_WIDTH + 191, GLOW_WIDTH + y)

        sectionFont.drawString(
            position.x + pos.x + 7f,
            position.y + pos.y - 5f,
            game.translator[section.localisationKey],
            Constants.JUMP_DISABLED_TEXT
        )

        if (updatingBuyButtons)
            buyButtons.clear()

        when (section) {
            StoreData.Section.AUGMENTS -> drawBuyAugments(pos, buyButtons)
            StoreData.Section.CREW -> drawBuyCrew(pos, buyButtons)
            StoreData.Section.DRONES -> TODO()
            StoreData.Section.SYSTEMS -> drawBuySystems(pos, buyButtons)
            StoreData.Section.WEAPONS -> drawBuyWeapons(pos, buyButtons)
        }

        for (button in buyButtons) {
            if (updatingBuyButtons) {
                buttons.add(button)

                // Be sure to set the window position before drawing the button,
                // to avoid it flickering in the wrong position for the first
                // frame it's drawn.
                button.windowOffset = position
            }

            button.draw(g)
        }
    }

    private fun drawBuySystems(pos: IPoint, buyButtons: ArrayList<BuyButton>) {
        // We only need to add buy buttons, no custom drawing.
        if (!updatingBuyButtons)
            return

        for ((i, system) in store.systems.withIndex()) {
            val buttonPos = pos + ConstPoint(5, 17 + i * 53)
            buyButtons.add(object : BuyButton(buttonPos, "store_systems", ConstPoint(345, 27)) {
                override var available: Boolean = system != null
                override val price: Int get() = system?.cost ?: 0

                // The sold-out image still has the cutout for the system icon, so
                // use the sold-out weapon image instead.
                override val off get() = game.getImg("img/storeUI/store_weapons_off.png")

                override fun buy() {
                    available = false

                    // TODO add it to the ship
                }

                override fun draw(g: Graphics) {
                    super.draw(g)

                    if (!available)
                        return

                    systemNameFont.drawString(
                        this.pos.x + 48f,
                        this.pos.y + 26f,
                        game.translator[system!!.title!!],
                        textColour
                    )

                    val icon = game.getImg("img/icons/s_${system.name}_green1.png")
                    val iconGlow = 19 // Padding inside the icon image file, which contains the glow
                    icon.draw(this.pos.x - iconGlow + 6f, this.pos.y - iconGlow + 7f)
                }
            })
        }
    }

    private fun drawBuyCrew(pos: IPoint, buttons: ArrayList<BuyButton>) {
        // TODO implement
    }

    private fun drawBuyWeapons(pos: ConstPoint, buyButtons: ArrayList<BuyButton>) {
        // We only need to add buy buttons, no custom drawing.
        if (!updatingBuyButtons)
            return

        for ((i, weapon) in store.weapons.withIndex()) {
            val buttonPos = pos + ConstPoint(10 + i * 125, 37)
            buyButtons.add(object : BuyButton(buttonPos, "store_buy_weapons", ConstPoint(42, 98)) {
                override var available: Boolean = weapon != null
                override val price: Int get() = weapon?.cost ?: 0

                override fun buy() {
                    available = false

                    // TODO add it to the ship
                }

                override fun draw(g: Graphics) {
                    super.draw(g)

                    if (!available)
                        return

                    // Draw the weapon name
                    val name = game.translator[weapon!!.short!!]
                    val nameWindowWidth = 96
                    val nameX = (nameWindowWidth - weaponNameFont.getWidth(name)) / 2

                    weaponNameFont.drawString(
                        this.pos.x + 11f + nameX,
                        this.pos.y + 70f,
                        name,
                        textColour
                    )

                    // Draw the weapon icon
                    val iconWindowWidth = 96
                    val iconWindowHeight = 45
                    val icon = weapon.getLauncher(game).chargedImage

                    // The sprite is rotated 90°, so swap the width and height.
                    val iconX = (iconWindowWidth - icon.height) / 2
                    val iconY = (iconWindowHeight - icon.width) / 2

                    weapon.drawLauncherUI(game, this.pos.x + iconX + 11f, this.pos.y + iconY + 11f)
                }
            })
        }
    }

    private fun drawBuyAugments(pos: ConstPoint, buyButtons: ArrayList<BuyButton>) {
        // TODO implement
    }

    private fun drawSell(g: Graphics) {
        sellImage.draw(position)

        buyTabButton.draw(g)
    }

    override fun escapePressed() {
        close()
    }

    abstract inner class BuyButton(pos: IPoint, val textureName: String, val priceOffset: IPoint) :
        Button(pos, game.getImg("img/storeUI/${textureName}_on.png").imageSize) {

        open val normal get() = game.getImg("img/storeUI/${textureName}_on.png")
        open val off get() = game.getImg("img/storeUI/${textureName}_off.png")
        open val hover get() = game.getImg("img/storeUI/${textureName}_select2.png")

        abstract val available: Boolean
        abstract val price: Int
        abstract fun buy()

        protected val textColour: Color
            get() = when {
                hovered -> Constants.STORE_BUY_HOVER
                else -> Constants.SECTOR_CUTOUT_TEXT
            }

        override fun draw(g: Graphics) {
            val image = when {
                !available -> off
                hovered -> hover
                else -> normal
            }
            image.draw(pos)

            // Don't draw the price text if we've already sold it
            // (or in the case of resources, sold out).
            if (!available)
                return

            numberFont.drawString(
                pos.x + priceOffset.x.toFloat(),
                pos.y + priceOffset.y.toFloat(),
                price.toString(),
                textColour
            )
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (!available)
                return

            // TODO check and deduct scrap

            buy()
        }
    }

    inner class ResourceButton(pos: IPoint, val resource: Resource) :
        BuyButton(pos, "store_items_${resourceTextureName(resource)}", ConstPoint(130, 26)) {

        val numAvailable: Int get() = store.availableResources[resource] ?: 0

        override val available: Boolean get() = numAvailable > 0
        override val price: Int get() = 5 // TODO implement the price

        override fun draw(g: Graphics) {
            super.draw(g)

            // Don't draw the available quantity if we've sold out.
            if (numAvailable == 0)
                return

            numberFont.drawString(pos.x + 70f, pos.y + 26f, numAvailable.toString(), textColour)
        }

        override fun buy() {
            val resourceSet = ResourceSet()
            resourceSet[resource] = 1
            game.givePlayerResources(resourceSet)

            store.availableResources[resource] = numAvailable - 1
        }
    }

    companion object {
        const val GLOW_WIDTH: Int = 7

        private fun resourceTextureName(resource: Resource): String {
            return when (resource) {
                Resource.FUEL -> "fuel"
                Resource.MISSILES -> "missiles"
                Resource.DRONES -> "drones"
                Resource.SCRAP -> error("Can't buy resource $resource")
            }
        }
    }
}
