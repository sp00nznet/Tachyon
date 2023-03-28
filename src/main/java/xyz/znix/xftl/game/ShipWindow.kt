package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.Input
import xyz.znix.xftl.*
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.MainSystem
import xyz.znix.xftl.systems.SubSystem
import xyz.znix.xftl.systems.SystemBlueprint

class ShipWindow(val game: SlickGame, val ship: Ship, private val close: () -> Unit) :
    Window() {
    override val size = ConstPoint(587, 464)

    override val outlineImage get() = error("Ship UI uses a pre-made background image")

    private val shipNameFont = game.getFont("c&c", 3f)
    private val numberFont = game.getFont("num_font")
    private val reactorFont = game.getFont("hl1", 2f)

    private var tab: Tab = Tab.UPGRADES

    // If true, the drawing code should re-create any buttons it added
    private var updatingButtons = false

    init {
        updateButtons()
    }

    private fun updateButtons() {
        buttons.clear()

        // The buttons can change their icon depending on which of the other two
        // tabs are selected, so re-create them whenever a different tab is selected.

        fun createTab(tab: Tab, pos: ConstPoint, size: ConstPoint): SimpleButton {
            val current = this.tab.textureName
            val new = tab.textureName
            return SimpleButton(
                pos, size, ConstPoint.ZERO,
                game.getImg("img/upgradeUI/Equipment/tabButtons/${current}_${new}_on.png"),
                game.getImg("img/upgradeUI/Equipment/tabButtons/${current}_${new}_select2.png")
            ) {
                this.tab = tab
                updateButtons()
            }
        }

        if (tab != Tab.UPGRADES)
            buttons += createTab(Tab.UPGRADES, ConstPoint(-GLOW_WIDTH, -GLOW_WIDTH), ConstPoint(109, 48))
        if (tab != Tab.CREW)
            buttons += createTab(Tab.CREW, ConstPoint(75, -GLOW_WIDTH), ConstPoint(127, 48))
        if (tab != Tab.EQUIPMENT)
            buttons += createTab(Tab.EQUIPMENT, ConstPoint(175, -GLOW_WIDTH), ConstPoint(123, 48))

        // Update the button window offsets
        positionUpdated()

        // Update any buttons added in the drawing code
        updatingButtons = true
    }

    override fun draw(g: Graphics) {
        val backgroundImage = game.getImg("img/upgradeUI/Equipment/${tab.textureName}_main.png")

        backgroundImage.draw(
            position.x - GLOW_WIDTH,
            position.y - GLOW_WIDTH
        )

        when (tab) {
            Tab.UPGRADES -> drawUpgrades(g)
            Tab.CREW -> drawCrew(g)
            Tab.EQUIPMENT -> drawEquipment(g)
        }

        if (updatingButtons) {
            // Update any newly-created button positions
            positionUpdated()
            updatingButtons = false
        }

        for (button in buttons) {
            button.draw(g)
        }
    }

    private fun drawUpgrades(g: Graphics) {
        // Draw the ship name
        val name = "The Kestrel" // TODO

        val nameWidth = shipNameFont.getWidth(name)

        shipNameFont.drawString(
            position.x + 138f + (327f - nameWidth) / 2,
            position.y + 75f,
            name,
            Color.white
        )

        // Draw the systems
        val systems = ship.rooms.mapNotNull { it.system as? MainSystem }.sortedBy { it.sortingType }
        for (i in 0 until 8) {
            if (!updatingButtons)
                continue

            val system = if (i < systems.size) systems[i] else null

            val price: Int? = when {
                system == null -> null
                system.energyLevels == system.blueprint.maxPower -> null
                else -> system.blueprint.upgradeCost[system.energyLevels - 1]
            }

            val isFullyUpgraded = system != null && price == null

            val baseImage = when {
                system == null -> "img/upgradeUI/upgrade_system_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_system_bar_max_on.png"
                else -> "img/upgradeUI/upgrade_system_bar_on.png"
            }
            val selectImage = when {
                system == null -> "img/upgradeUI/upgrade_system_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_system_bar_max_select2.png"
                else -> "img/upgradeUI/upgrade_system_bar_select2.png"
            }

            buttons += UpgradeButton(
                ConstPoint(32 + 66 * i, 115), ConstPoint(66, 150),
                game.getImg(selectImage), game.getImg(baseImage),
                system, price
            )
        }

        // Draw the subsystems
        // TODO only go until 3 on non-AE?
        val subsystems = ship.rooms.mapNotNull { it.system as? SubSystem }.sortedBy { it.sortingType }
        for (i in 0 until 4) {
            // TODO deduplicate with the system upgrade code
            if (!updatingButtons)
                continue

            val system = if (i < subsystems.size) subsystems[i] else null

            val price: Int? = when {
                system == null -> null
                system.energyLevels == system.blueprint.maxPower -> null
                else -> system.blueprint.upgradeCost[system.energyLevels - 1]
            }

            val isFullyUpgraded = system != null && price == null

            val baseImage = when {
                system == null -> "img/upgradeUI/upgrade_subsystem_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_subsystem_bar_max_on.png"
                else -> "img/upgradeUI/upgrade_subsystem_bar_on.png"
            }
            val selectImage = when {
                system == null -> "img/upgradeUI/upgrade_subsystem_bar_none.png"
                isFullyUpgraded -> "img/upgradeUI/upgrade_subsystem_bar_max_select2.png"
                else -> "img/upgradeUI/upgrade_subsystem_bar_select2.png"
            }

            buttons += UpgradeButton(
                ConstPoint(9 + 66 * i, 330), ConstPoint(66, 113),
                game.getImg(selectImage), game.getImg(baseImage),
                system, price
            )
        }

        // Add the reactor button
        if (updatingButtons) {
            val reactorImg = game.getImg("img/upgradeUI/Equipment/equipment_reactor_on.png")
            val reactorHighlight = game.getImg("img/upgradeUI/Equipment/equipment_reactor_select2.png")
            buttons += object : Button(ConstPoint(298, 327), reactorImg.imageSize) {
                override fun draw(g: Graphics) {
                    if (hovered) {
                        reactorHighlight.draw(pos)
                    } else {
                        reactorImg.draw(pos)
                    }

                    // Draw the energy bars
                    for (level in 0 until 25) {
                        g.color = when {
                            ship.purchasedReactorPower >= (level + 1) -> Constants.SYS_ENERGY_ACTIVE
                            hovered -> Constants.SYS_ENERGY_PURCHASE_HOVER
                            else -> Constants.SYS_ENERGY_PURCHASE
                        }

                        g.fillRect(
                            pos.x + 30f + (level / 5) * 44,
                            pos.y + 66f - (level % 5) * 13,
                            32f, 8f
                        )
                    }

                    // Draw the current price
                    val price = 20
                    numberFont.drawString(
                        pos.x + 235f, pos.y + 105f,
                        price.toString(), Constants.SECTOR_CUTOUT_TEXT
                    )

                    // Draw the 'n power bars' text - this is annoyingly mixed between two fonts
                    // TODO mix them properly to work in languages where the power number doesn't
                    //  come first - is this something FTL does?
                    val text = game.translator["upgrade_reactor_power"].replace("\\1", "")
                    reactorFont.drawStringLeftAligned(pos.x + 179f, pos.y + 105f, text, Constants.SECTOR_CUTOUT_TEXT)
                    numberFont.drawStringLeftAligned(
                        pos.x + 47f,
                        pos.y + 105f,
                        ship.purchasedReactorPower.toString(),
                        Constants.SECTOR_CUTOUT_TEXT
                    )
                }

                override fun click(button: Int) {
                    if (button != Input.MOUSE_LEFT_BUTTON)
                        return

                    if (ship.purchasedReactorPower >= 25)
                        return

                    // TODO scrap check

                    ship.purchasedReactorPower++
                }
            }
        }
    }

    inner class UpgradeButton(
        pos: ConstPoint, size: ConstPoint,
        private val selectImage: Image,
        private val baseImage: Image,
        private val system: AbstractSystem?,
        private val upgradePrice: Int?
    ) : Button(pos, size) {
        override fun draw(g: Graphics) {
            // Draw the main button image
            val image = if (hovered) selectImage else baseImage
            image.draw(pos.x, pos.y)

            if (system == null)
                return

            // Draw the system icon
            val systemIcon = game.getImg(system.blueprint.onIconPath)
            systemIcon.draw(
                pos.x - SystemBlueprint.ICON_GLOW + 19f,
                pos.y + size.y - SystemBlueprint.ICON_GLOW - 65f
            )

            // Draw the energy bars
            for (level in 1..system.blueprint.maxPower) {
                g.color = when {
                    system.energyLevels >= level -> Constants.SYS_ENERGY_ACTIVE
                    hovered -> Constants.SYS_ENERGY_PURCHASE_HOVER
                    else -> Constants.SYS_ENERGY_PURCHASE
                }

                g.fillRect(
                    pos.x + 24f,
                    pos.y + size.y - 76f - (level - 1) * 8f,
                    15f, 6f
                )
            }

            // Draw the scrap amount
            if (upgradePrice != null) {
                numberFont.drawString(
                    pos.x + 30f, pos.y + size.y - 8f,
                    upgradePrice.toString(),
                    Constants.SECTOR_CUTOUT_TEXT
                )
            }
        }

        override fun click(button: Int) {
            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (upgradePrice == null)
                return

            // TODO deduct scrap
            system!!.energyLevels++

            // Replace this button to reflect the upgrade
            updateButtons()
        }
    }

    private fun drawCrew(g: Graphics) {
        // TODO
    }

    private fun drawEquipment(g: Graphics) {
        // TODO
    }

    override fun escapePressed() {
        close()
    }

    private enum class Tab(val textureName: String) {
        UPGRADES("upgrades"),
        CREW("crew"),
        EQUIPMENT("equipment"),
    }

    companion object {
        const val GLOW_WIDTH: Int = 7
    }
}
