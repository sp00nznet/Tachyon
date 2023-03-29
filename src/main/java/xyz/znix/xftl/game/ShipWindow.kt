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
import xyz.znix.xftl.weapons.ShipWeaponBlueprint

class ShipWindow(val game: SlickGame, val ship: Ship, private val close: () -> Unit) :
    Window() {
    override val size = ConstPoint(587, 464)

    override val outlineImage get() = error("Ship UI uses a pre-made background image")

    private val sectionFont = game.getFont("hl2", 2f)
    private val shipNameFont = game.getFont("c&c", 3f)
    private val numberFont = game.getFont("num_font")
    private val reactorFont = game.getFont("hl1", 2f)
    private val crewNameFont = game.getFont("JustinFont8")
    private val dismissFont = game.getFont("hl1")

    private var tab: Tab = Tab.UPGRADES

    private var draggingBlueprint: Buttons.DragDropBlueprintButton? = null
        set(value) {
            field = value
            for (button in buttons) {
                if (button !is Buttons.DragDropBlueprintButton)
                    continue
                button.currentlyDraggedBlueprint = value?.blueprint
            }
        }

    private val weaponButtons = ArrayList<Buttons.DragDropBlueprintButton>()
    private val cargoButtons = ArrayList<Buttons.DragDropBlueprintButton>()

    // If true, the drawing code should re-create any buttons it added
    private var updatingButtons = false

    private val closeButton = Buttons.BasicButton(
        // As an interesting note, in the base game it's one pixel too far right so
        // where it meets up with the side of the ship panel has a small ledge.
        position + ConstPoint(428, 471),
        ConstPoint(132, 32), game.translator["button_accept"], game,
        4, game.getFont("hl2", 3f), 25,
        this::escapePressed
    )

    init {
        updateButtons()
    }

    private fun updateButtons() {
        buttons.clear()

        buttons += closeButton

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

        val acceptImage = game.getImg("img/upgradeUI/buttons_accept_base.png")
        acceptImage.draw(position.x + 405f, position.y + 464f)

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

        // Draw the dragged bit of equipment above everything else
        draggingBlueprint?.drawDrag()
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
        // First row
        drawCrewBox(g, ConstPoint(68 + 170 * 0, 88 + 133 * 0), 0)
        drawCrewBox(g, ConstPoint(68 + 170 * 1, 88 + 133 * 0), 1)
        drawCrewBox(g, ConstPoint(68 + 170 * 2, 88 + 133 * 0), 2)

        // Second row
        drawCrewBox(g, ConstPoint(68 + 170 * 0, 88 + 133 * 1), 3)
        drawCrewBox(g, ConstPoint(68 + 170 * 1, 88 + 133 * 1), 4)
        drawCrewBox(g, ConstPoint(68 + 170 * 2, 88 + 133 * 1), 5)

        // Third row
        // TODO use the same pattern as before for the too-many-crewmembers screen
        drawCrewBox(g, ConstPoint(154, 88 + 133 * 2), 6)
        drawCrewBox(g, ConstPoint(324, 88 + 133 * 2), 7)
    }

    private fun drawCrewBox(g: Graphics, boxPos: ConstPoint, id: Int) {
        if (ship.crew.size <= id) {
            val boxEmpty = game.getImg("img/upgradeUI/Equipment/box_crew_off.png")
            boxEmpty.draw(boxPos + position)
            return
        }
        val crew = ship.crew[id]

        val dismissButtonBox = game.getImg("img/customizeUI/box_crewcustom_on.png")
        val dismissButtonBoxHover = game.getImg("img/customizeUI/box_crewcustom_selected.png")

        val boxNormal = game.getImg("img/upgradeUI/Equipment/box_crew_on.png")
        val boxHover = game.getImg("img/upgradeUI/Equipment/box_crew_selected.png")

        if (!updatingButtons)
            return

        buttons += object : Button(boxPos, boxNormal.imageSize) {
            override fun draw(g: Graphics) {
                val box = if (hovered) boxHover else boxNormal
                box.draw(pos)

                val dismissBox = if (hovered) dismissButtonBoxHover else dismissButtonBox
                dismissBox.draw(pos.x.f, pos.y + 62f)

                // Draw the name
                val name = "Crew name"
                val nameX = (96 - crewNameFont.getWidth(name)) / 2
                crewNameFont.drawString(pos.x + 2f + nameX, pos.y + 61f, name, Color.white)

                // Draw the crewmember portrait
                // TODO layer rendering
                val portrait = game.animations["${crew.codename}_portrait"].spriteAt(0)

                portrait.filter = Image.FILTER_NEAREST

                // TODO align properly
                portrait.draw(
                    pos.x + 2f + (96 - portrait.width * 2) / 2,
                    pos.y + 2f + (45 - portrait.height * 2) / 2,
                    2f
                )
            }

            override fun click(button: Int) {
                // TODO renaming
            }
        }

        buttons += Buttons.BasicButton(
            boxPos + ConstPoint(4, 70), ConstPoint(92, 12),
            game.translator["crewbox_dismiss"], game,
            2, dismissFont, 9
        ) {
            // TODO show the crewmember dismiss warning box
        }
    }

    private fun drawEquipment(g: Graphics) {
        sectionFont.drawString(
            position.x + 11f,
            position.y + 60f,
            game.translator["equipment_frame_weapons"],
            Constants.JUMP_DISABLED_TEXT
        )
        sectionFont.drawString(
            position.x + 11f,
            position.y + 170f,
            game.translator["equipment_frame_drones"],
            Constants.JUMP_DISABLED_TEXT
        )
        sectionFont.drawString(
            position.x + 11f,
            position.y + 280f,
            game.translator["equipment_frame_cargo"],
            Constants.JUMP_DISABLED_TEXT
        )
        sectionFont.drawString(
            position.x + 300f,
            position.y + 280f,
            game.translator["equipment_frame_augments"],
            Constants.JUMP_DISABLED_TEXT
        )

        if (!updatingButtons)
            return

        // Draw the weapons
        // Note: I think all the playable ships have weaponSlots set?
        weaponButtons.clear()
        for (i in 0 until ship.weaponSlots!!) {
            val weapon = ship.hardpoints[i].weapon

            val images = ButtonImageSet.selected(game, "img/upgradeUI/Equipment/box_weapons", true)
            val buttonPos = ConstPoint(56 + i * 117, 70)

            // Use a separate variable so we can use the button in it's callback.
            lateinit var button: Buttons.DragDropBlueprintButton
            button = Buttons.DragDropBlueprintButton(
                buttonPos, game, images,
                { it is ShipWeaponBlueprint },
                weapon?.type
            ) {
                draggingBlueprint = button
            }
            buttons += button
            weaponButtons += button
        }

        // TODO draw the drones

        // Draw the cargo area
        cargoButtons.clear()
        for (i in 0 until 4) {
            val blueprint = ship.cargoBlueprints[i]

            val weapons = ButtonImageSet.selected(game, "img/upgradeUI/Equipment/box_weapons", true)
            val drones = ButtonImageSet.selected(game, "img/upgradeUI/Equipment/box_drones", true)

            val base = ButtonImageSet(
                game.getImg("img/upgradeUI/Equipment/box_base_off.png"),
                game.getImg("img/upgradeUI/Equipment/box_base_off.png"), // There's no 'on' image
                game.getImg("img/upgradeUI/Equipment/box_base_off_selected.png")
            )

            val buttonPos = ConstPoint(
                23 + 130 * (i % 2),
                293 + 80 * (i / 2)
            )

            // Use a separate variable so we can use the button in it's callback.
            lateinit var button: Buttons.DragDropBlueprintButton
            button = object : Buttons.DragDropBlueprintButton(
                buttonPos, game, base,
                { true /* TODO block augments */ },
                blueprint,
                { draggingBlueprint = button }) {

                // Change the card image depending on our contents
                override val image: ButtonImageSet
                    get() {
                        // If our contents is being dragged, don't keep the weapon/drone
                        // icon on the card.
                        if (dragPosition != null)
                            return base

                        return when (this.blueprint) {
                            is ShipWeaponBlueprint -> weapons
                            // TODO drones
                            else -> base
                        }
                    }
            }
            buttons += button
            cargoButtons += button
        }
    }

    override fun updateUI(x: Int, y: Int) {
        super.updateUI(x, y)

        // If we're dragging a blueprint around, update it.
        draggingBlueprint?.dragPosition = ConstPoint(x, y)
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        super.mouseReleased(button, x, y)

        if (draggingBlueprint != null)
            dropBlueprint()
    }

    private fun dropBlueprint() {
        val drag = draggingBlueprint!!
        drag.dragPosition = null
        draggingBlueprint = null

        data class SlotAccess(
            val get: () -> Blueprint?,
            val set: (Blueprint?) -> Unit
        )

        fun getAccess(target: Buttons.DragDropBlueprintButton): SlotAccess? {
            for ((i, button) in weaponButtons.withIndex()) {
                if (button != target)
                    continue

                return SlotAccess(
                    { ship.hardpoints[i].weapon?.type },
                    { ship.hardpoints[i].weapon = (it as? ShipWeaponBlueprint)?.buildInstance(ship) }
                )
            }

            // Check if we can drop it into the cargo bay
            for ((i, button) in cargoButtons.withIndex()) {
                if (button != target)
                    continue

                return SlotAccess(
                    { ship.cargoBlueprints[i] },
                    { ship.cargoBlueprints[i] = it }
                )
            }

            return null
        }

        // Find the blueprint the user is trying to drop the item into
        val hovered = buttons.mapNotNull { it as? Buttons.DragDropBlueprintButton }.first { it.hovered }

        // Get access to where this blueprint is being dragged to and from
        val src = getAccess(drag) ?: return
        val dst = getAccess(hovered) ?: return

        // Make sure something crazy hasn't happened
        require(drag.blueprint == src.get())

        // Check if the blueprint will fit
        if (!hovered.compatible(drag.blueprint!!))
            return

        // If the user drops one blueprint onto another, they swap.
        // Make sure that'll work.
        val replacing = dst.get()
        if (replacing != null && !drag.compatible(replacing))
            return

        // Swap them
        dst.set(drag.blueprint)
        src.set(replacing)

        updateButtons()
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
