package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.Input
import xyz.znix.xftl.*
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.systems.SubSystem
import xyz.znix.xftl.systems.SystemBlueprint

private typealias UndoFn = () -> Unit

class ShipWindow(val game: InGameState, val ship: Ship, private val close: () -> Unit) :
    Window() {
    override val size = ConstPoint(587, 464)

    override val outlineImage get() = error("Ship UI uses a pre-made background image")

    private val acceptButtonImage = game.getImg("img/upgradeUI/buttons_accept_base.png")
    private val undoButtonImage = game.getImg("img/upgradeUI/buttons_undo_base.png")

    private val shipNameFont = game.getFont("c&c", 3f)
    private val numberFont = game.getFont("num_font")
    private val reactorFont = game.getFont("hl1", 2f)
    private val crewNameFont = game.getFont("JustinFont8")
    private val dismissFont = game.getFont("hl1")

    private val upgradeSystemSound = game.sounds.getSample("upgradeSystem")
    private val downgradeSystemSound = game.sounds.getSample("downgradeSystem")

    private var tab: Tab = Tab.UPGRADES

    private val equipmentPanel = ShipEquipmentPanel(game, ship)

    // These store functions that each undo the last
    // upgrade of a system or the reactor.
    private val systemUpgradeUndos = HashMap<AbstractSystem, ArrayList<UndoFn>>()
    private val reactorUpgradeUndo = ArrayList<UndoFn>()

    // If true, the drawing code should re-create any buttons it added
    private var updatingButtons = false

    private val closeButton = Buttons.BasicButton(
        // As an interesting note, in the base game it's one pixel too far right so
        // where it meets up with the side of the ship panel has a small ledge.
        game, position + ConstPoint(428, 471),
        ConstPoint(132, 32), game.translator["button_accept"],
        4, game.getFont("hl2", 3f), 25,
        this::escapePressed
    )

    init {
        updateButtons()
    }

    private fun updateButtons() {
        buttons.clear()

        equipmentPanel.position = position

        buttons += closeButton

        // The buttons can change their icon depending on which of the other two
        // tabs are selected, so re-create them whenever a different tab is selected.

        fun createTab(tab: Tab, pos: ConstPoint, size: ConstPoint): SimpleButton {
            val current = this.tab.textureName
            val new = tab.textureName
            return SimpleButton(
                game, pos, size, ConstPoint.ZERO,
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

        // Switching tabs away from upgrades commits the system upgrades
        if (tab != Tab.UPGRADES) {
            systemUpgradeUndos.clear()
            reactorUpgradeUndo.clear()
        }

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

        acceptButtonImage.draw(position.x + 405f, position.y + 464f)

        when (tab) {
            Tab.UPGRADES -> drawUpgrades(g)
            Tab.CREW -> drawCrew(g)
            Tab.EQUIPMENT -> equipmentPanel.draw(g)
        }

        if (updatingButtons) {
            // Update any newly-created button positions
            positionUpdated()
            updatingButtons = false
        }

        for (button in buttons) {
            button.draw(g)
        }

        equipmentPanel.drawDrag(g)
    }

    override fun shipModified() {
        super.shipModified()
        equipmentPanel.shipModified()
    }

    private fun drawUpgrades(g: Graphics) {
        // Draw the ship name
        val name = game.translator[ship.shipTitle!!]

        val nameWidth = shipNameFont.getWidth(name)

        shipNameFont.drawString(
            position.x + 138f + (327f - nameWidth) / 2,
            position.y + 75f,
            name,
            Color.white
        )

        undoButtonImage.draw(position.x + 3f, position.y + 464f)
        if (updatingButtons) {
            buttons += object : Buttons.BasicButton(
                game, ConstPoint(26, 471), ConstPoint(97, 32),
                game.translator["button_undo"],
                4, game.getFont("hl2", 3f), 25,
                this::undoAllSystems
            ) {
                // Grey the button out when there's nothing to undo
                override val disabled: Boolean get() = systemUpgradeUndos.all { it.value.isEmpty() } && reactorUpgradeUndo.isEmpty()
            }
        }

        // Draw the systems
        for (i in 0 until 8) {
            if (!updatingButtons)
                continue

            val system = ship.mainSystems.getOrNull(i)

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
            buttons += object : Button(game, ConstPoint(298, 327), reactorImg.imageSize) {
                override val disabled: Boolean get() = ship.purchasedReactorPower >= ship.maxReactorPower

                val currentPrice: Int
                    get() {
                        // Expensive first power on the Cernkov
                        if (ship.purchasedReactorPower < 5)
                            return 30

                        return (ship.purchasedReactorPower / 5) * 5 + 15
                    }

                override fun draw(g: Graphics) {
                    if (hovered) {
                        reactorHighlight.draw(pos)
                    } else {
                        reactorImg.draw(pos)
                    }

                    // Figure out the power range that should show as downgrades
                    val lastNonRefundablePower = ship.purchasedReactorPower - reactorUpgradeUndo.size
                    val refundRange = lastNonRefundablePower until ship.purchasedReactorPower

                    // Draw the energy bars
                    for (level in 0 until ship.maxReactorPower) {
                        g.color = when {
                            level in refundRange -> Constants.SYS_ENERGY_PURCHASE_UNDOABLE
                            ship.purchasedReactorPower > level -> Constants.SYS_ENERGY_ACTIVE
                            hovered -> Constants.SYS_ENERGY_PURCHASE_HOVER
                            else -> Constants.SYS_ENERGY_PURCHASE
                        }

                        g.fillRect(
                            pos.x + 30f + (level / 5) * 44,
                            pos.y + 66f - (level % 5) * 13,
                            32f, 8f
                        )
                    }

                    // Draw the current price. TODO show it as maxed when the reactor power is full.
                    numberFont.drawString(
                        pos.x + 235f, pos.y + 105f,
                        currentPrice.toString(), Constants.SECTOR_CUTOUT_TEXT
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
                    if (button == Input.MOUSE_RIGHT_BUTTON) {
                        val undoLast = reactorUpgradeUndo.lastOrNull() ?: return
                        reactorUpgradeUndo.remove(undoLast)
                        undoLast()
                        downgradeSystemSound.play()
                        return
                    }

                    if (button != Input.MOUSE_LEFT_BUTTON)
                        return

                    if (disabled)
                        return

                    // Store the price so undos work properly.
                    val price = currentPrice

                    if (ship.scrap < price) {
                        game.shipUI.playInsufficientScrapAnimation()
                        return
                    }
                    ship.scrap -= price

                    ship.purchasedReactorPower++

                    // This is how the user can revert an upgrade
                    reactorUpgradeUndo += {
                        ship.scrap += price
                        ship.purchasedReactorPower--
                    }

                    upgradeSystemSound.play()
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
    ) : Button(game, pos, size) {
        override val disabled: Boolean get() = system == null

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

            // Figure out the power range that should show as downgrades
            val undoablePower = systemUpgradeUndos[system]?.size ?: 0
            val lastNonRefundablePower = system.energyLevels - undoablePower + 1 // Power here is 1-indexed
            val refundRange = lastNonRefundablePower..system.energyLevels

            // Draw the energy bars
            for (level in 1..system.blueprint.maxPower) {
                g.color = when {
                    level in refundRange -> Constants.SYS_ENERGY_PURCHASE_UNDOABLE
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
            if (button == Input.MOUSE_RIGHT_BUTTON) {
                val undos = systemUpgradeUndos[system] ?: return
                val undoLast = undos.lastOrNull() ?: return
                undos.remove(undoLast)
                undoLast()
                downgradeSystemSound.play()
                return
            }

            if (button != Input.MOUSE_LEFT_BUTTON)
                return

            if (upgradePrice == null)
                return

            if (ship.scrap < upgradePrice) {
                game.shipUI.playInsufficientScrapAnimation()
                return
            }
            ship.scrap -= upgradePrice

            system!!.energyLevels++

            val undos = systemUpgradeUndos.getOrPut(system) { ArrayList() }
            undos += {
                system.energyLevels--
                ship.scrap += upgradePrice
                updateButtons()
            }

            // Replace this button to reflect the upgrade
            updateButtons()

            upgradeSystemSound.play()
        }
    }

    private fun drawCrew(g: Graphics) {
        // Find all the crew that belong to us - this way we exclude mind-control.
        // TODO show crew that have teleported to the enemy ship.

        val crew = ship.crew.mapNotNull { it as? LivingCrew }.filter { it.ownerShip == ship }

        // First row
        drawCrewBox(g, crew, ConstPoint(68 + 170 * 0, 88 + 133 * 0), 0)
        drawCrewBox(g, crew, ConstPoint(68 + 170 * 1, 88 + 133 * 0), 1)
        drawCrewBox(g, crew, ConstPoint(68 + 170 * 2, 88 + 133 * 0), 2)

        // Second row
        drawCrewBox(g, crew, ConstPoint(68 + 170 * 0, 88 + 133 * 1), 3)
        drawCrewBox(g, crew, ConstPoint(68 + 170 * 1, 88 + 133 * 1), 4)
        drawCrewBox(g, crew, ConstPoint(68 + 170 * 2, 88 + 133 * 1), 5)

        // Third row
        // TODO use the same pattern as before for the too-many-crewmembers screen
        drawCrewBox(g, crew, ConstPoint(154, 88 + 133 * 2), 6)
        drawCrewBox(g, crew, ConstPoint(324, 88 + 133 * 2), 7)
    }

    private fun drawCrewBox(g: Graphics, crewList: List<LivingCrew>, boxPos: ConstPoint, id: Int) {
        val crew = crewList.getOrNull(id)

        if (crew == null) {
            val boxEmpty = game.getImg("img/upgradeUI/Equipment/box_crew_off.png")
            boxEmpty.draw(boxPos + position)
            return
        }

        val dismissButtonBox = game.getImg("img/customizeUI/box_crewcustom_on.png")
        val dismissButtonBoxHover = game.getImg("img/customizeUI/box_crewcustom_selected.png")

        val boxNormal = game.getImg("img/upgradeUI/Equipment/box_crew_on.png")
        val boxHover = game.getImg("img/upgradeUI/Equipment/box_crew_selected.png")

        if (!updatingButtons)
            return

        buttons += object : Button(game, boxPos, boxNormal.imageSize) {
            override val makesHoverNoise: Boolean get() = false

            override fun draw(g: Graphics) {
                val box = if (hovered) boxHover else boxNormal
                box.draw(pos)

                val dismissBox = if (hovered) dismissButtonBoxHover else dismissButtonBox
                dismissBox.draw(pos.x.f, pos.y + 62f)

                // Draw the name
                val name = crew.selectedName
                val nameX = (96 - crewNameFont.getWidth(name)) / 2
                crewNameFont.drawString(pos.x + 2f + nameX, pos.y + 61f, name, Color.white)

                // Draw the crewmember portrait
                // TODO align properly
                val portrait = crew.icon.currentFrame
                crew.drawPortrait(
                    pos.x + 2 + (96 - portrait.width * 2) / 2,
                    pos.y + 2 + (45 - portrait.height * 2) / 2,
                    2f
                )
            }

            override fun click(button: Int) {
                // TODO renaming
            }
        }

        buttons += Buttons.BasicButton(
            game, boxPos + ConstPoint(4, 70), ConstPoint(92, 12),
            game.translator["crewbox_dismiss"],
            2, dismissFont, 9
        ) {
            // TODO show the crewmember dismiss warning box
        }
    }

    override fun mouseClick(button: Int, x: Int, y: Int) {
        super.mouseClick(button, x, y)

        if (tab == Tab.EQUIPMENT)
            equipmentPanel.mouseClick(button, x, y)
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        super.mouseReleased(button, x, y)

        if (tab == Tab.EQUIPMENT)
            equipmentPanel.mouseReleased(button, x, y)
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        if (tab == Tab.EQUIPMENT)
            equipmentPanel.updateUI(x, y)
    }

    override fun escapePressed() {
        close()
    }

    private fun undoAllSystems() {
        for (undos in systemUpgradeUndos.values) {
            for (undo in undos) {
                undo()
            }
        }
        for (undo in reactorUpgradeUndo) {
            undo()
        }
        systemUpgradeUndos.clear()
        reactorUpgradeUndo.clear()

        downgradeSystemSound.play()
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
