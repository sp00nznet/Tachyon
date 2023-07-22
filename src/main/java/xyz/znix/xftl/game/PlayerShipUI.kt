package xyz.znix.xftl.game

import org.jdom2.Element
import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Input.MOUSE_LEFT_BUTTON
import org.newdawn.slick.Input.MOUSE_RIGHT_BUTTON
import org.newdawn.slick.geom.Rectangle
import xyz.znix.xftl.*
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.crew.SkillLevel
import xyz.znix.xftl.environment.PulsarEnvironment
import xyz.znix.xftl.environment.SunEnvironment
import xyz.znix.xftl.game.InGameState.RoomClickListener
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import java.util.*
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

class PlayerShipUI(val ship: Ship, private val game: InGameState) {
    private val font = game.getFont("HL2", 2f)
    private val weaponNameText = game.getFont("JustinFont8")
    private val weaponNumberFont = game.getFont("c&c")
    private val numberFont = game.getFont("num_font")
    private val oxygenEvadeFont = game.getFont("JustinFont10")

    private val powerUpSound = game.sounds.getSample("powerUpSystem")
    private val powerUpFailSound = game.sounds.getSample("powerUpFail")
    private val powerDownSound = game.sounds.getSample("powerDownSystem")

    private var selectWeaponClickEvent: RoomClickListener? = null
    private var targetingSelectedWeapon: Int? = null

    private val selectedCrew: MutableList<AbstractCrew> = ArrayList()
    private val hoveredCrew: MutableList<AbstractCrew> = ArrayList()
    private var skillsHoveredCrew: AbstractCrew? = null

    private val buttons = ArrayList<Button>()

    /**
     * The currently shown window. When a window is shown, the main UI is darkened, buttons are locked
     * and all input is sent to the window.
     */
    private var currentWindow: Window? = null

    private val hullWarningLines = listOf(
        ConstPoint(361, 51),
        ConstPoint(393, 82),
        ConstPoint(482, 82)
    )
    private val hullWarning25 = WarningFlasher(game, ConstPoint(437, 100), "warning_hull_25", true, hullWarningLines)
    private val hullWarning50 = WarningFlasher(game, ConstPoint(437, 100), "warning_hull_50", true, hullWarningLines)
    private val hullWarning75 = WarningFlasher(game, ConstPoint(437, 100), "warning_hull_75", true, hullWarningLines)

    private val shieldsWarning = WarningFlasher(
        game, ConstPoint(204, 132), "warning_shields", true,
        listOf(
            ConstPoint(129, 93),
            ConstPoint(151, 114),
            ConstPoint(250, 114)
        )
    )

    private val sunWarning = WarningFlasher(
        game, ConstPoint(825, 64), "warning_solar_flare", false, alwaysOn = true
    )
    private val pulsarWarning = WarningFlasher(
        game, ConstPoint(825, 64), "warning_ion_pulse", false, alwaysOn = true
    )

    private var lastHealth: Int = -1

    val isWindowOpen: Boolean get() = currentWindow != null

    private var crewSelectionRectangle: Pair<ConstPoint, Point>? = null
    private val isCrewSelectionPoint: Boolean
        get() = crewSelectionRectangle?.let { it.first.distToSq(it.second) < SELECTION_BOX_SIZE } ?: false

    // Non-null when the player is aiming a beam weapon
    private var beamTargeting: SelectedTarget.BeamAim? = null
        set(value) {
            field = value

            // Show the aiming-in-progress beam on the enemy ship
            ship.weapons!!.selectedTargets.beamAiming = value
        }
    private val beamTargetingStartPos = Point(0, 0)

    // Set by render
    private var height: Int = 500

    // The time remaining on the insufficient scrap flash animation
    private var insufficientScrapTimer: Float = 0f

    // If true, buttons that are tightly intertwined with the rest of the UI
    // (for example, the weapon and drone buttons) will be re-populated.
    private var updatingButtons = false

    // The longest time it takes to charge a weapon - this is used for drawing
    // the weapon charge bars.
    private var maxWeaponChargeTime: Float = 1f

    // The position of the weapons box
    val weaponBoxY get() = height - 113

    // Whether or not we've previously seen a store UI at this sector.
    // This is used to auto-open the store when the dialogue is finished.
    private var storeAlreadyOpened = false

    private val crewX: Int = 10
    private var crewBaseY: Int = 0 // Set while rendering

    /**
     * If the user is selecting a room to teleport to/from, this is non-null.
     * True if sending crew to an enemy ship, false if receiving.
     */
    val teleportMode: Boolean? get() = (game.clickEvent as? TeleportRoomListener)?.send

    val isSelectingHackingTarget: Boolean get() = game.clickEvent is HackingRoomListener
    val isSelectingMindControlTarget: Boolean get() = game.clickEvent is MindControlRoomListener

    init {
        updateButtons()
    }

    fun updateButtons() {
        buttons.clear()
        updatingButtons = true

        var nextPos = ConstPoint(531, 29)

        val jump = Buttons.JumpButton(nextPos, ship, game) {
            currentWindow = JumpWindow(game, ::openSectorMap) {
                if (it != null) {
                    // Reset stuff after jumping
                    storeAlreadyOpened = false
                }

                currentWindow = null
                updateButtons() // A store may now be available
            }
        }
        nextPos += ConstPoint(101, 0)

        val ship = Buttons.ShipButton(nextPos, game) {
            showShipWindow()
        }
        nextPos += ConstPoint(ship.size.x + 17, 0)

        if (game.currentBeacon?.hasStore == true) {
            val store = Buttons.StoreButton(nextPos, game) {
                showStoreWindow()
            }
            nextPos += ConstPoint(store.size.x + 17, 0)
            buttons += store
        }

        val settings = SimpleButton(
            game, nextPos, ConstPoint(41, 41), ConstPoint(7, 7),
            game.getImg("img/statusUI/top_optionswrench_on.png"),
            game.getImg("img/statusUI/top_optionswrench_select2.png")
        ) {
            TODO("Settings menu not implemented")
        }

        buttons += jump
        buttons += ship
        buttons += settings
    }

    fun mouseClick(button: Int, x: Int, y: Int, playerShipPosition: IPoint) {
        currentWindow?.let { win ->
            win.mouseClick(button, x, y)
            return
        }

        // When we're in beam targeting mode, block other mouse input.
        if (beamTargeting != null) {
            if (button == MOUSE_LEFT_BUTTON) {
                targetBeamWeapon()
            } else if (button == MOUSE_RIGHT_BUTTON) {
                beamTargeting = null
            }
            return
        }

        for (btn in buttons) {
            val hit = btn.mouseDown(button, x, y)
            if (hit) return
        }

        // Check if we're clicking on a door
        if (button == MOUSE_LEFT_BUTTON) {
            for (door in ship.doors) {
                val hit = door.click(x - playerShipPosition.x, y - playerShipPosition.y)
                if (hit)
                    return
            }
        }

        // Select players
        if (button == MOUSE_LEFT_BUTTON) {
            crewSelectionRectangle = Pair(ConstPoint(x, y), Point(x, y))
        }

        // Move players if they're right-clicking somewhere
        val shipMousePos = Point(x, y)
        shipMousePos -= playerShipPosition

        val roomPoint = Point(shipMousePos)
        roomPoint.divideFloor(ROOM_SIZE)
        roomPoint -= ship.offset
        for (room in ship.rooms) {
            if (!room.containsAbsolute(roomPoint))
                continue

            if (button == MOUSE_RIGHT_BUTTON) {
                for (crew in selectedCrew) {
                    // Skip mind-controlled crew that remained selected.
                    if (!crew.playerControllable)
                        continue

                    // Exclude crew that are boarding an enemy ship, as
                    // this could cause a crash.
                    if (crew.room.ship == ship)
                        crew.setTargetRoom(room)
                }

                return
            }
        }
    }

    fun mouseUp(button: Int, x: Int, y: Int, playerShipPosition: IPoint) {
        currentWindow?.let { win ->
            win.mouseReleased(button, x, y)
            return
        }

        crewSelectionRectangle?.let {
            if (button != MOUSE_LEFT_BUTTON) return@let

            updateHoveredCrew(x, y, playerShipPosition)

            selectedCrew.clear()
            selectedCrew.addAll(hoveredCrew)

            crewSelectionRectangle = null
        }
    }

    fun crewScreenPos(crew: AbstractCrew, playerShipPosition: IPoint): IPoint {
        if (crew.room.ship == ship) {
            return ConstPoint(crew.screenX, crew.screenY) + playerShipPosition
        }

        // The crewmember must be on the enemy ship, add that position on
        return ConstPoint(crew.screenX, crew.screenY) + game.enemyPosition
    }

    fun isCrewHovered(crew: AbstractCrew, mouseX: Int, mouseY: Int, crewPos: IPoint): Boolean {
        // Check if the crewmember's box is being hovered
        val index = ship.crew.indexOf(crew)
        if (index != -1) {
            val boxY = crewBaseY + index * CREW_BOX_SPACING
            if (mouseX in crewX..crewX + CREW_BOX_WIDTH && mouseY in boxY..boxY + CREW_BOX_HEIGHT) {
                skillsHoveredCrew = crew
                return true
            }
        }

        // Check if the physical crewmember is being hovered
        return mouseX in crewPos.x..crewPos.x + crew.icon.width &&
                mouseY in crewPos.y..crewPos.y + crew.icon.height
    }

    // Called by SlickGame, used for controlling boarders.
    fun enemyRoomRightClicked(room: Room, enemyShip: Ship) {
        for (crew in selectedCrew) {
            // Skip mind-controlled crew that remained selected.
            if (!crew.playerControllable)
                continue

            if (crew.room.ship == enemyShip)
                crew.setTargetRoom(room)
        }
    }

    fun weaponHotkeyPressed(id: Int) {
        // Temporary hack to make the option hotkeys work
        (currentWindow as? DialogueWindow)?.let {
            it.selectOption(id)
            return
        }

        if (currentWindow != null) return

        // Block interactions with hacked weapons
        if (ship.weapons!!.isHackActive)
            return

        // If the user was previously targeting a beam, cancel that.
        beamTargeting = null

        val weapon = ship.hardpoints[id].weapon ?: return

        if (!weapon.isPowered) {
            if (ship.weapons!!.setWeaponPower(weapon, true)) {
                powerUpSound.play()
            } else {
                powerUpFailSound.play()
            }
            return
        }

        val targets = ship.weapons?.selectedTargets ?: return
        targets.unTarget(id)

        targetingSelectedWeapon = id
        selectWeaponClickEvent = RoomClickListener { room, gc ->
            if (weapon is IRoomTargetingWeapon) {
                targets.targetRoom(id, room)
            } else if (weapon is BeamBlueprint.BeamInstance) {
                val mousePos = ConstPoint(gc.input.mouseX, gc.input.mouseY)
                val shipPos = mousePos - game.enemyPosition
                beamTargeting = SelectedTarget.BeamAim(weapon, id, game.enemy, shipPos)
                beamTargetingStartPos.set(mousePos)
            }
        }
        game.clickEvent = selectWeaponClickEvent
    }

    fun systemPowerHotkeyPressed(type: Class<*>, powerUp: Boolean) {
        for (sys in ship.mainSystems) {
            if (sys.javaClass != type) continue

            changeSystemPower(sys, powerUp)
        }
    }

    private fun changeSystemPower(sys: MainSystem, increasePower: Boolean) {
        val prevPower = sys.powerSelected

        if (increasePower) {
            sys.increasePower()
        } else {
            sys.decreasePower()
        }

        if (sys.powerSelected > prevPower) {
            powerUpSound.play()
        } else if (sys.powerSelected < prevPower) {
            powerDownSound.play()
        } else if (increasePower) {
            powerUpFailSound.play()
        }
    }

    private fun updateBeamTargeting(mouseX: Int, mouseY: Int) {
        val beam = beamTargeting ?: return

        val point = Point(beamTargetingStartPos)

        val delta = ConstPoint(mouseX, mouseY) - point

        // If the mouse is too close to where the beam starts, no beam is drawn.
        beam.visible = delta.distToSq(ConstPoint.ZERO) > MIN_BEAM_LENGTH * MIN_BEAM_LENGTH
        if (!beam.visible)
            return

        beam.angle = atan2(delta.y.toDouble(), delta.x.toDouble()).toFloat()

        beam.updateHitRooms()
    }

    private fun targetBeamWeapon() {
        val beam = beamTargeting ?: error("Called targetBeamWeapon without beam")
        beamTargeting = null

        val weaponIndex = ship.hardpoints.indexOfFirst { it.weapon == beam.weapon }
        ship.weapons!!.selectedTargets.targetBeam(weaponIndex, beam)
    }

    // Dispatch actions from the UI - this is only called when the game is not paused, so dispatch
    // everything from here so a player can cancel their actions if they remain paused.
    fun update() {
        ship.weapons?.selectedTargets?.fireChargedWeapons()
    }

    fun updateAlways(dt: Float) {
        // Always called, whether or not the player is paused.
        // This is therefore only to be used for UI stuff.
        insufficientScrapTimer -= dt
        if (insufficientScrapTimer < 0)
            insufficientScrapTimer = 0f
    }

    fun render(gc: GameContainer, g: Graphics) {
        height = gc.height

        drawTopBar(g)
        drawSystems(g)
        drawSubSystems(gc)

        // Draw the buttons last, so they don't disappear for a frame
        // when they're updated.
        for (button in buttons) {
            button.draw(g)
        }

        updatingButtons = false
    }

    private fun drawSystems(g: Graphics) {
        val powerTreeX = 86 - 53
        val powerTreeY = height - 21 - 302
        val powerTreeMaskY = powerTreeY + 27 - (ship.purchasedReactorPower - 1) * 9

        Utils.drawStenciled(Utils.StencilMode.BLOCKING, {
            game.getImg("img/wire_left_mask.png").draw(powerTreeX.f + 4, powerTreeMaskY.f)
        }) {
            game.getImg("img/wireUI/wire_full.png").draw(powerTreeX.f, powerTreeY.f)
        }

        // Draw the power tree's energy bars
        val availablePower = ship.powerAvailable
        val totalPower = ship.reactorPower
        for (i in 0 until totalPower) {
            val x = 12
            val y = height - 34 - 9 * i

            if (i < availablePower) {
                g.colour = SYS_ENERGY_ACTIVE
                g.fillRect(x.f, y.f, 28f, 7f)
            } else {
                g.colour = SYS_ENERGY_DEPOWERED
                g.drawRect(x.f, y.f, 28f - 1f, 7f - 1f)
            }
        }

        // Draw the systems and the wires under them
        var weaponPowerX: Int? = null
        var dronePowerX: Int? = null
        var powerX = 58
        val powerY = height - 69
        for ((i, system) in ship.mainSystems.withIndex()) {
            // Draw the power bar to the next item
            val wireX = powerX + 31
            val wireY = powerY +
                    45 + // Top of the power button image to the bottom
                    12 + // Distance between the power button and the main horizontal wire
                    -26 // Go back up to the top of the wire image

            // Save the position of the weapon and drone power icons, since
            // they're used to position the weapon and drone selection boxes.
            if (system is Weapons)
                weaponPowerX = wireX
            if (system is Drones)
                dronePowerX = wireX

            if (updatingButtons) {
                val powerPos = ConstPoint(powerX + 19, powerY + 19)
                buttons += SystemPowerButton(powerPos, system)

                buttons += system.makeExtraButtons(powerPos)
            }

            powerX += when {
                system is Weapons -> 48 + ship.weaponSlots!! * 97
                system is Drones -> 48 + ship.droneSlots!! * 97
                system.insertButtonSpace -> 54
                else -> 36
            }

            // Don't draw the bar under the last item - since each item draws the
            // wire to the next item, this would result in a wire sticking out.
            if (i == ship.mainSystems.size - 1)
                continue

            val lastSystem = i == ship.mainSystems.size - 2

            val wireWidth = when {
                // Remember that we'll only run this if this isn't the last system,
                // so we won't be drawing a wire under weapons if drones isn't installed.
                system is Weapons -> {
                    // Draw the wire that goes under the weapons to the drone system
                    when (ship.weaponSlots) {
                        4 -> "456"
                        else -> "456_${ship.weaponSlots!!}weapon"
                    }
                }

                system.insertButtonSpace -> "54"
                else -> "36"
            }
            val image = when (lastSystem) {
                true -> game.getImg("img/wireUI/wire_${wireWidth}_cap.png")
                false -> game.getImg("img/wireUI/wire_$wireWidth.png")
            }
            image.draw(wireX.f, wireY.f)
        }

        // If a weapon is being targeted, check that our click event is still the game's active click
        // event. This ensures that if the click event is cancelled or overridden, targeting mode
        // is disabled.
        if (targetingSelectedWeapon != null && selectWeaponClickEvent != game.clickEvent) {
            targetingSelectedWeapon = null
            selectWeaponClickEvent = null
        }

        // Find the longest charge time of all equipped weapons
        maxWeaponChargeTime = ship.hardpoints.stream()
            .map { it.weapon }
            .filter { Objects.nonNull(it) }
            .map { it!!.chargeTime }
            .reduce { a, b -> Math.max(a, b) }
            .orElse(1f)

        // Draw the weapons box
        val weaponBoxX = weaponPowerX!! + 1
        drawWeaponBox(weaponBoxX, "weapons_label", ship.weaponSlots!!)

        for (i in 0 until ship.weaponSlots!!) {
            if (!updatingButtons)
                continue

            // The origin position of the weapon button
            val wx = weaponBoxX + WEAPON_BOX_GLOW + 12 + i * 97
            val wy = weaponBoxY + 12 + 4

            val weapon = ship.hardpoints[i].weapon

            buttons += object : WeaponDroneButton(ConstPoint(wx, wy), i, ConstPoint(87, 39)) {
                override val empty: Boolean get() = weapon == null
                override val name: String get() = weapon!!.type.translateShort(game)
                override val requiredPower: Int get() = weapon!!.type.power
                override val chargeTime: Float get() = weapon!!.chargeTime
                override val chargeProgress: Float get() = weapon!!.chargeProgress
                override val zoltanPower: Int get() = 0 // TODO
                override val isPowered: Boolean get() = weapon!!.isPowered
                override val isCharged: Boolean get() = weapon!!.isCharged
                override val isTargeted: Boolean get() = ship.weapons!!.selectedTargets.getTarget(i) != null
                override val isSelectingTarget: Boolean get() = targetingSelectedWeapon == i
                override val hasChargeBar: Boolean get() = true
                override val isBeingHacked: Boolean get() = ship.weapons!!.isHackActive

                override fun click(button: Int) {
                    if (button == MOUSE_LEFT_BUTTON) {
                        weaponHotkeyPressed(i)
                    }
                    if (weapon != null && weapon.isPowered && button == MOUSE_RIGHT_BUTTON) {
                        if (ship.weapons!!.setWeaponPower(weapon, false)) {
                            powerDownSound.play()
                        }
                    }
                }

                override fun draw(g: Graphics) {
                    super.draw(g)

                    if (weapon != null && weapon.type.chargeLevels != null) {
                        drawChargedShots()
                    }

                    if (weapon != null && weapon.type.boost != null) {
                        drawChainIcon()
                    }
                }

                private fun drawChargedShots() {
                    requireNotNull(weapon)

                    val filter = mainColour
                    val max = weapon.maxTotalCharges
                    val ready = weapon.totalReadyCharges
                    val width = 11
                    val baseX = pos.x + (size.x - max * 11) / 2
                    val y = pos.y + 25

                    for (charge in 0 until max) {
                        val suffix = when {
                            charge < ready -> "use"
                            else -> "on"
                        }

                        val img = game.getImg("img/combatUI/icon_chain_$suffix.png")
                        val x = baseX + charge * width
                        img.draw(x, y, filter)
                    }
                }

                private fun drawChainIcon() {
                    requireNotNull(weapon)
                    val boost = weapon.type.boost
                    requireNotNull(boost)

                    val clockOn = when {
                        weapon.chainCount == 0 -> false
                        !isPowered -> false
                        else -> true
                    }

                    val mode = when (clockOn) {
                        true -> "on"
                        false -> "off"
                    }
                    val iconName = when (boost.type) {
                        AbstractWeaponBlueprint.BoostType.COOLDOWN -> "img/combatUI/icon_clock_$mode.png"
                        AbstractWeaponBlueprint.BoostType.DAMAGE -> "img/combatUI/icon_arrow_$mode.png"
                    }
                    val icon = game.getImg(iconName)

                    val filter = when (clockOn) {
                        true -> mainColour
                        false -> Color.white // Don't filter the already-greyed image
                    }
                    icon.draw(pos.x + 32, pos.y + 20, filter)

                    if (clockOn) {
                        val text = "+" + weapon.chainCount
                        weaponNameText.drawString(pos.x + 50f, pos.y + 31f, text, filter)
                    }
                }
            }
        }

        // Draw the drone area, if it's installed
        val drones = ship.drones
        if (drones != null) {
            val droneBoxX = dronePowerX!! + 1
            drawWeaponBox(droneBoxX, "drones_label", ship.droneSlots!!)

            // Draw the in-operation drone boxes - this seems very similar to weapons
            for ((i, info) in drones.drones.withIndex()) {
                val drone = info?.type
                if (!updatingButtons)
                    break

                // The origin position of the drone button
                val pos = ConstPoint(
                    droneBoxX + WEAPON_BOX_GLOW + 4 + i * 97,
                    weaponBoxY + 12 + 4
                )

                buttons += object : WeaponDroneButton(pos, i, ConstPoint(95, 39)) {
                    override val empty: Boolean get() = drone == null
                    override val name: String get() = drone!!.translateShort(game)
                    override val requiredPower: Int get() = drone!!.power
                    override val chargeTime: Float get() = error("Can't get charge time for drone")
                    override val chargeProgress: Float get() = error("Can't get charge progress for drone")
                    override val zoltanPower: Int get() = 0 // TODO
                    override val isPowered: Boolean get() = info!!.instance?.isPowered == true
                    override val isCharged: Boolean get() = isPowered // Always use the charged colour
                    override val isTargeted: Boolean get() = false
                    override val isSelectingTarget: Boolean get() = false
                    override val hasChargeBar: Boolean get() = false
                    override val isBeingHacked: Boolean get() = drones.isHackActive && info?.instance != null

                    override fun click(button: Int) {
                        val wasPowered = info?.instance?.isPowered ?: false

                        if (button == MOUSE_LEFT_BUTTON) {
                            drones.setDronePower(i, true)
                        }
                        if (button == MOUSE_RIGHT_BUTTON) {
                            drones.setDronePower(i, false)
                        }

                        val nowPowered = info?.instance?.isPowered ?: false
                        when {
                            wasPowered && !nowPowered -> powerDownSound.play()
                            !wasPowered && nowPowered -> powerUpSound.play()
                            !wasPowered && button == MOUSE_LEFT_BUTTON -> powerUpFailSound.play()
                        }
                    }
                }
            }
        }

        // Draw the crew selection rectangle, if appropriate
        val csr = crewSelectionRectangle
        if (csr != null && !isCrewSelectionPoint) {
            g.colour = Color.white
            val size = csr.second - csr.first
            g.drawRect(csr.first.x.f, csr.first.y.f, size.x.f, size.y.f)
            g.drawRect(csr.first.x + 1f, csr.first.y + 1f, size.x - 2f, size.y - 2f)
        }
    }

    private fun drawSubSystems(gc: GameContainer) {
        // The box position, not including the glow.
        val boxX = gc.width - 252
        val boxY = gc.height - 47

        game.getImg("img/box_subsystems4.png").draw(boxX - 6, boxY - 6)

        // Draw the subsystems label
        val label = game.translator["subsystems_label"]
        font.drawString(boxX + 7f, boxY + 23f, label, UI_TEXT_COLOUR_1)

        // Add all the system buttons
        if (!updatingButtons) {
            return
        }

        fun addSubSys(x: Int, sys: SubSystem?) {
            if (sys == null)
                return

            val powerPos = ConstPoint(boxX + x, boxY - 21)
            buttons += SystemPowerButton(powerPos, sys)
            buttons += sys.makeExtraButtons(powerPos)
        }

        addSubSys(6, ship.piloting)
        addSubSys(42, ship.sensors)
        addSubSys(78, ship.doorsSystem)
        addSubSys(129, ship.backupBattery)
    }

    fun renderMenus(container: GameContainer, g: Graphics) {
        val window = currentWindow ?: return

        // Add the tint over all the regular game stuff to make the window clearer.
        g.colour = Color(0f, 0f, 0f, 0.65f)
        g.fillRect(0f, 0f, container.width.f, container.height.f)

        // Centre the window.
        window.position = window.windowCentreOffset + ConstPoint(
            (container.width - window.size.x) / 2,
            (container.height - window.size.y) / 2
        )

        window.draw(g)
    }

    private fun drawTopBar(g: Graphics) {
        val isHullBoxRed = hullWarning25.isFlashingHigh || hullWarning50.isFlashingHigh || hullWarning75.isFlashingHigh
        val hullBoxImage = when {
            isHullBoxRed -> game.getImg("img/statusUI/top_hull_red.png")
            else -> game.getImg("img/statusUI/top_hull.png")
        }
        hullBoxImage.draw(0f, 0f)

        val hullLabelImg = when {
            isHullBoxRed -> game.getImg("img/statusUI/top_hull_red_label.png")
            else -> game.getImg("img/statusUI/top_hull_label.png")
        }
        val hullText = game.translator["status_hull"]
        UIUtils.drawTab(font, hullText, hullLabelImg, 0f, 0f, 10f, 30f)
        font.drawString(9f, 25f, hullText, UI_TEXT_COLOUR_1)

        // Draw the hull bar

        // TODO support ships with more or less health
        check(ship.maxHealth == 30)

        val hpW = 12f * ship.health
        val healthColour = when {
            ship.health < 10 -> SHIP_HEALTH_LOW
            ship.health < 20 -> SHIP_HEALTH_MED
            else -> SHIP_HEALTH_HIGH
        }
        val mask = game.getImg("img/statusUI/top_hull_bar_mask.png")
        val hpH = mask.height.f
        mask.draw(11f, 0f, 11f + hpW, hpH, 0f, 0f, hpW, hpH, healthColour)

        val lastFraction = lastHealth.f / ship.maxHealth
        val newFraction = ship.health.f / ship.maxHealth
        lastHealth = ship.health

        if (newFraction < 0.25f) {
            // Constantly trigger the hull critical warning
            hullWarning25.startFor(5f)
        } else if (lastFraction > 0.50f && newFraction < 0.50f) {
            hullWarning50.startFor(9f)
        } else if (lastFraction > 0.75f && newFraction < 0.75f) {
            hullWarning75.startFor(9f)
        }

        // Stop animations when we're healed, or our health drops
        // enough to trigger the next animation.
        if (newFraction > 0.25f) {
            hullWarning25.stop()
        }
        if (newFraction !in 0.25f..0.5f) {
            hullWarning50.stop()
        }
        if (newFraction !in 0.5f..0.75f) {
            hullWarning75.stop()
        }

        hullWarning25.draw(g)
        hullWarning50.draw(g)
        hullWarning75.draw(g)

        // Draw the scrap indicator
        val isScrapRed = insufficientScrapTimer != 0f &&
                (insufficientScrapTimer / INSUFFICIENT_SCRAP_FLASH_TIME).toInt() % 2 == 0
        val scrapPath = if (isScrapRed) "img/statusUI/top_scrap_red.png" else "img/statusUI/top_scrap.png"
        game.getImg(scrapPath).draw(374f + 8 - 5, 0f)
        numberFont.drawStringCentred(416f, 35f, 87f, ship.scrap.toString(), UI_SCRAP_TEXT_COLOUR)

        // Going down the side, for the shields/oxygen
        val shieldY = 43

        ship.shields?.let { shields ->
            // Draw the shields indicator
            val broken = shields.energyLevels - shields.damagedEnergyLevels < 2
            val shieldBox = when {
                broken -> game.getImg("img/statusUI/top_shields4_red.png")
                shields.selectedShieldBars == 0 -> game.getImg("img/statusUI/top_shields4_off.png")
                else -> game.getImg("img/statusUI/top_shields4_on.png")
            }
            shieldBox.draw(0, shieldY)
            val hacked = shields.isHackActive
            if (hacked) {
                game.getImg("img/statusUI/top_shields4_purple.png").draw(31 - 8, shieldY)
            }

            if (ship.superShield != 0) {
                // Draw the super-shield bar where the recharge
                // bar would normally go.
                g.colour = SYS_ENERGY_ACTIVE
                var x = 33
                for (i in 0 until ship.superShield) {
                    // The middle bar is one pixel shorter to make everything
                    // else line up properly.
                    val width = if (i == 2) 16 else 17

                    g.fillRect(x.f, shieldY + 34f + 2f, width.f, 6f)
                    x += width + 2
                }
            } else if (shields.rechargeTimer != 0f) {
                // Draw the recharge bar
                val progress = (shields.rechargeTimer / shields.rechargeDelay).coerceIn(0f..1f)
                g.colour = when (hacked) {
                    true -> SHIELD_BAR_HACKED
                    false -> SHIELD_BAR_NORMAL
                }
                g.fillRect(
                    33f, shieldY + 34f + 2f,
                    92f * progress, 6f
                )
            }

            // Draw the shield bubble indicators
            val shieldOnImg = game.getImg("img/statusUI/top_shieldsquare1_on.png")
            val shieldOffImg = game.getImg("img/statusUI/top_shieldsquare1_off.png")
            val shieldHackedOnImg = game.getImg("img/statusUI/top_shieldsquare1_hacked_charged.png")
            val shieldHackedOffImg = game.getImg("img/statusUI/top_shieldsquare1_hacked.png")

            for (i in 0 until shields.selectedShieldBars) {
                val charged = shields.activeShields > i

                val img = when {
                    hacked && charged -> shieldHackedOnImg
                    hacked -> shieldHackedOffImg

                    charged -> shieldOnImg
                    else -> shieldOffImg
                }
                img.draw(30 + 23 * i, shieldY + 4)
            }

            when {
                broken -> shieldsWarning.startFor(1f)
                else -> shieldsWarning.stop()
            }
            shieldsWarning.draw(g)
        }

        fun drawSmallCounter(name: String, x: Int, numAreaOffset: Int, count: Int, redThreshold: Int = 0) {
            val suffix = when {
                count <= redThreshold -> "_red"
                else -> ""
            }
            game.getImg("img/statusUI/top_${name}_on$suffix.png").draw(x, shieldY)

            val areaWidth = 35
            val areaStart = x.f + 33 + numAreaOffset

            val textWidth = numberFont.getWidth(count.toString())
            val textInternalX = ceil((areaWidth - textWidth) / 2f).toInt()

            numberFont.drawString(areaStart + textInternalX, shieldY + 27f, count.toString(), Color.white)
        }

        val shieldsEndX = 122

        // Draw the environmental hazard imminent warnings (eg 'ion pulse imminent')
        when (val env = game.currentBeacon.getEnvironment(game)) {
            is SunEnvironment -> {
                if (env.showWarning)
                    sunWarning.draw(g)
            }

            is PulsarEnvironment -> {
                if (env.showWarning)
                    pulsarWarning.draw(g)
            }
        }

        // Fuel shows as red when there's three or less left.
        drawSmallCounter("fuel", shieldsEndX, 0, ship.fuelCount, 3)

        // Missiles only show as red when there's none left.
        drawSmallCounter("missiles", shieldsEndX + 66, 1, ship.missilesCount)

        // TODO drone red threshold.
        drawSmallCounter("drones", shieldsEndX + 66 + 70, -2, ship.dronesCount)

        // Oxygen and evasion indicator
        val oxyY = 96
        game.getImg("img/statusUI/top_evade_oxygen.png").draw(1f, oxyY - 7f)

        val evadeTextRight = 92f

        // Evade
        oxygenEvadeFont.drawStringLeftAligned(evadeTextRight, oxyY + 15f, "${ship.evasion}%", Color.white)

        // Oxygen
        val avgOxygen = round(ship.averageOxygen * 100).toInt()
        oxygenEvadeFont.drawStringLeftAligned(evadeTextRight, oxyY + 37f, "$avgOxygen%", Color.white)

        // Draw all the crew boxes
        // TODO filter out boarders etc
        // TODO draw cloning crew
        // FIXME don't leave gaps for drones etc
        crewBaseY = oxyY + 59
        for ((index, crew) in ship.crew.withIndex()) {
            // Filter out drones
            if (crew !is LivingCrew)
                continue

            // Filter out intruders, not including mind-controlled crew.
            if (crew.ownerShip != ship)
                continue

            val crewY = crewBaseY + index * CREW_BOX_SPACING

            drawCrewBox(g, crew, crewX, crewY)
        }
    }

    private fun drawCrewBox(g: Graphics, crew: LivingCrew, x: Int, y: Int) {
        val isMindControlled = crew.mindControlledBy != null
        val isStunned = false // TODO implement when stunning crew is added
        val isFlashingHealth = false // TODO

        val colour = when {
            isMindControlled -> CREW_BOX_MIND_CONTROLLED
            crew in selectedCrew -> CREW_BOX_SELECT
            isStunned -> CREW_BOX_STUNNED
            crew in hoveredCrew -> CREW_BOX_HOVER
            isFlashingHealth -> CREW_BOX_LOW_HEALTH
            else -> CREW_BOX_NORMAL
        }

        val drawSkills = crew == skillsHoveredCrew

        if (!drawSkills) {
            // Draw the semi-transparent background
            g.colour = Color(colour.r, colour.g, colour.b, 0.25f)
            g.fillRect(x.f, y.f, CREW_BOX_WIDTH.f, CREW_BOX_HEIGHT.f)

            // Draw the solid outline, via two unfilled rectangles.
            g.colour = colour
            g.drawRect(x.f, y.f, CREW_BOX_WIDTH - 1f, CREW_BOX_HEIGHT - 1f)
            g.drawRect(x + 1f, y + 1f, CREW_BOX_WIDTH - 3f, CREW_BOX_HEIGHT - 3f)

            // TODO show the animation when the crewmember gets a skill point.
        } else {
            // Draw the semi-transparent background
            g.colour = Color(colour.r, colour.g, colour.b, 0.25f)
            g.fillRect(x.f, y.f, 89f, CREW_BOX_HEIGHT.f)
            g.fillRect(x + 89f, y.f, 80f, 146f)

            // Draw the outline, which we do with line drawing by rectangles.
            g.colour = colour
            g.fillRect(x.f, y.f, 2f, CREW_BOX_HEIGHT.f)
            g.fillRect(x.f, y.f, 169f, 2f)
            g.fillRect(x.f, y + 25f, 91f, 2f)
            g.fillRect(x + 89f, y + 27f, 2f, 119f)
            g.fillRect(x + 167f, y.f, 2f, 146f)
            g.fillRect(x + 91f, y + 144f, 76f, 2f)

            drawSkillBar(g, x + 93, y, crew, Skill.PILOTING)
            drawSkillBar(g, x + 93, y, crew, Skill.ENGINES)
            drawSkillBar(g, x + 93, y, crew, Skill.SHIELDS)
            drawSkillBar(g, x + 93, y, crew, Skill.WEAPONS)
            drawSkillBar(g, x + 93, y, crew, Skill.REPAIRS)
            drawSkillBar(g, x + 93, y, crew, Skill.COMBAT)
        }

        // Draw the health bar
        val maxHpWidth = 49
        val hpFraction = crew.health / crew.maxHealth
        val hpWidth = (maxHpWidth * hpFraction).toInt().coerceAtLeast(1)

        if (crew.health == crew.maxHealth) {
            g.colour = Color.green
        } else {
            g.colour = Color(
                1f,
                (2f * hpFraction).coerceIn(0f..1f),
                0f
            )
        }
        g.fillRect(x + 33f, y + 19f, hpWidth.f, 4f)

        // Draw the crew portrait, mostly so you can see what race they are.
        crew.drawPortrait(x - 1, y - 3)

        // Draw the crew name
        weaponNameText.drawString(x + 33f, y + 14f, crew.info.name, CREW_BOX_NAME_COLOUR)
    }

    private fun drawSkillBar(
        g: Graphics,
        x: Int, baseY: Int,
        crew: LivingCrew, skill: Skill
    ) {
        val y = baseY + skill.ordinal * 24 // Not 26, which is the icon height
        val skillLevel = crew.getSkillLevel(skill)

        // Draw the icon
        val icon = game.getImg(skill.iconPath)
        val iconColour = when (skillLevel) {
            SkillLevel.MAX -> SYS_ENERGY_REPAIR
            SkillLevel.PARTIAL -> SYS_ENERGY_ACTIVE
            else -> UI_BACKGROUND_GLOW_COLOUR
        }
        icon.draw(x, y, iconColour)

        // Draw the progress bar
        crew.info.drawSkillProgressBar(g, x + 30, y + 9, 40, 8, skill)
    }

    // Draw the box containing the weapon or drone selection buttons
    private fun drawWeaponBox(x: Int, label: String, size: Int) {
        game.getImg("img/box_weapons_bottom$size.png").draw(x.f, weaponBoxY.f)

        val textX = x + 18
        val textY = weaponBoxY + 61

        // Draw the 'weapons' or 'drones' label
        val name = game.translator[label]
        val textWidth = font.getWidth(name)
        val img = game.getImg("img/box_weapons_bottom_label.png")

        // Stretch part of the label to cover the width of the text
        img.draw(
            textX.f, textY.f, textX + textWidth - 13f, textY + img.height.f,
            0f, 0f, 3f, img.height.f
        )

        // Draw the 'ramp' at the end of the label.
        img.draw((textX + textWidth - 13).f, textY.f)

        font.drawString(textX + 1f, textY + 15f, name, UI_TEXT_COLOUR_1)
    }

    fun updateUI(x: Int, y: Int, playerShipPosition: ConstPoint) {
        currentWindow?.let { win ->
            win.updateUI(x, y)
            return
        }

        for (button in buttons) {
            button.update(x, y)
        }

        if (beamTargeting != null) {
            updateBeamTargeting(x, y)
        }

        crewSelectionRectangle?.second?.set(x, y)

        updateHoveredCrew(x, y, playerShipPosition)

        // Update the door hover markers
        for (door in ship.doors) {
            door.updateMouseHover(x - playerShipPosition.x, y - playerShipPosition.y)
        }
    }

    fun showEventDialogue(event: Event) {
        var hasImmediatelyClosed = false

        currentWindow = DialogueWindow(game, ship, event) {
            hasImmediatelyClosed = true
            eventDialogueClosed()
        }

        // It's possible for DialogueWindow to call close in its constructor,
        // in which case we have to close it not to avoid it being rendered
        // while in an invalid state.
        // (This is because the constructor would run before currentWindow
        //  is set).
        // This can be tested with the TRADER_LIST event, or any other
        // event list whose purpose is to pick one of several events and
        // close without any text set.
        if (hasImmediatelyClosed) {
            currentWindow = null
        }
    }

    fun showSyntheticDialogue(syntheticEvent: DialogueWindow.SyntheticEvent) {
        (currentWindow as DialogueWindow).addSyntheticEvent(syntheticEvent)
    }

    private fun eventDialogueClosed() {
        currentWindow = null

        // If a store was made available by the dialogue, open it
        if (game.currentBeacon.hasStore && !storeAlreadyOpened) {
            updateButtons() // Make the store button show up
            showStoreWindow()
        }
    }

    private fun showShipWindow() {
        currentWindow = ShipWindow(game, ship) {
            currentWindow = null
        }
    }

    private fun showStoreWindow() {
        storeAlreadyOpened = true

        currentWindow = StoreWindow(game, ship, game.currentBeacon.getStore(game)!!) {
            currentWindow = null
        }
    }

    // Hack used to draw the selected crew from the ship, rather than some cleaner solution
    fun isCrewHighlighted(crew: AbstractCrew): Boolean {
        return selectedCrew.contains(crew) || hoveredCrew.contains(crew)
    }

    fun escapePressed() {
        currentWindow?.let { win ->
            win.escapePressed()
            return
        }

        // TODO open settings menu
    }

    fun playInsufficientScrapAnimation() {
        // Three flashes: on-off-on-off-on
        insufficientScrapTimer = INSUFFICIENT_SCRAP_FLASH_TIME * 5

        // I think this is the right sound?
        powerUpFailSound.play()
    }

    fun shipModified() {
        updateButtons()
        currentWindow?.shipModified()
    }

    /**
     * Called by [Teleporter] when one of its buttons are clicked.
     *
     * When the 'send to enemy ship' button is clicked, [send] is true.
     * Otherwise it's false.
     */
    fun teleportSelected(send: Boolean) {
        game.clickEvent = TeleportRoomListener(send)
    }

    /**
     * Called by [Hacking] when we're supposed to select the room to hack.
     */
    fun hackSelected() {
        game.clickEvent = HackingRoomListener()
    }

    /**
     * Called by [MindControl] when we're supposed to select the room to
     * apply mind control to.
     */
    fun mindControlSelected() {
        game.clickEvent = MindControlRoomListener()
    }

    fun openSectorMap() {
        currentWindow = SectorMapWindow(game) { sectorInfo ->
            currentWindow = null

            // Null just means the window was closed
            if (sectorInfo == null)
                return@SectorMapWindow

            val sector = game.gameMap.generateSector(sectorInfo)
            game.currentBeacon = sector.startBeacon

            // In case we were at a store
            // TODO move this into an on-jump handler function
            updateButtons()
        }
    }

    fun openAllDoors() {
        val internalDoors = ship.doors.filter { !it.isAirlock }
        val allInternalDoorsOpen = internalDoors.all { it.open }

        // Pressing the open key with all the internal doors open
        // opens all the airlocks, otherwise only the internal
        // doors are opened.
        for (door in ship.doors) {
            if (!allInternalDoorsOpen && door.isAirlock)
                continue

            door.open = true
        }
    }

    fun closeAllDoors() {
        for (door in ship.doors) {
            door.open = false
        }
    }

    /**
     * FOR DEBUG USE ONLY!
     *
     * See [InGameState.debugContinuousSaveRestore] for more information.
     */
    fun debugContinuousSaveRestore(prev: PlayerShipUI) {
        crewSelectionRectangle = prev.crewSelectionRectangle

        // Copy over the selected crew, based on the index into the ship
        // crew list. This is hacky but it's fine for a debug flag.
        for (prevCrew in prev.selectedCrew) {
            val index = prev.ship.crew.indexOf(prevCrew)

            // Don't bother with crew on the enemy ship.
            if (index == -1)
                continue

            selectedCrew += ship.crew[index]
        }

        // Reloading should keep the store open, if it's run manually.
        val oldStore = prev.currentWindow as? StoreWindow
        if (oldStore != null) {
            showStoreWindow()
            val store = currentWindow as StoreWindow
            store.sellTab = oldStore.sellTab
            store.secondBuyTab = oldStore.secondBuyTab
        }
    }

    private fun updateHoveredCrew(mouseX: Int, mouseY: Int, playerShipPosition: IPoint) {
        hoveredCrew.clear()
        skillsHoveredCrew = null

        // Allow for smart-casting
        val csr = crewSelectionRectangle

        // If a rectangle is visible on screen (ie, the player isn't clicking a point) then build a rectangle
        val rect = if (csr != null && !isCrewSelectionPoint) {
            val pos = csr.first.min(csr.second)
            val size = (csr.second - csr.first).abs()
            Rectangle(pos.x.f, pos.y.f, size.x.f, size.y.f)
        } else null

        // Any intruders on the enemy ship should also be controllable,
        // including mind controlled enemies (once that's implemented).
        // Mind-controlled crew are then filtered out in the for loop.
        val controllableCrew = ArrayList(ship.friendlyCrew)
        game.enemy?.let { controllableCrew.addAll(it.intruders) }

        for (crew in controllableCrew) {
            // Skip drones, mind controlled crew and the like which the
            // player isn't allowed to control.
            if (!crew.playerControllable)
                continue

            val crewPos = crewScreenPos(crew, playerShipPosition)

            @Suppress("IfThenToElvis")
            val hovered = if (rect != null) {
                // If we're in rectangle mode, check that it intersects the centre of the player's body
                // TODO also check if the player is selecting the crew name box
                rect.contains(crewPos.x.f, crewPos.y.f)
            } else {
                // Otherwise check the point overlaps the player
                isCrewHovered(crew, mouseX, mouseY, crewPos)
            }

            if (hovered) {
                hoveredCrew += crew
            }
        }
    }

    fun saveToXML(elem: Element, refs: ObjectRefs) {
        val window = currentWindow

        // If the player is currently playing through an event, save that.
        if (window is DialogueWindow) {
            val dialogueElem = Element("dialogue")
            window.saveToXML(dialogueElem, refs)
            elem.addContent(dialogueElem)
        }
    }

    fun loadFromXML(elem: Element, refs: RefLoader) {
        val dialogueElem = elem.getChild("dialogue")
        if (dialogueElem != null) {
            currentWindow = DialogueWindow(game, ship, dialogueElem, refs, this::eventDialogueClosed)
        }
    }

    fun showGameOverScreen(outcome: GameOverWindow.Outcome) {
        currentWindow = GameOverWindow(game, outcome)
    }

    private abstract inner class WeaponDroneButton(pos: IPoint, slotNumber: Int, size: ConstPoint) :
        Button(game, pos, size) {

        abstract val empty: Boolean
        abstract val name: String
        abstract val chargeTime: Float
        abstract val chargeProgress: Float
        abstract val requiredPower: Int
        abstract val zoltanPower: Int
        abstract val isPowered: Boolean
        abstract val isCharged: Boolean
        abstract val isTargeted: Boolean
        abstract val isSelectingTarget: Boolean
        abstract val hasChargeBar: Boolean
        abstract val isBeingHacked: Boolean

        override val disabled: Boolean get() = empty

        protected val mainColour: Color
            get() = when {
                empty -> WEAPONS_ITEM_DESELECTED
                isBeingHacked -> SYSTEM_HACKED
                !isPowered -> WEAPONS_ITEM_DESELECTED
                isSelectingTarget -> WEAPONS_ITEM_TARGETING
                isCharged -> WEAPONS_ITEM_CHARGED
                else -> WEAPONS_ITEM_SELECTED
            }

        private var hackingSparks: FTLAnimation? = null
        private var hackingOffsetX: Int = 0
        private var hackingMaskY: Int = 0
        private var hackingMirror: Boolean = false
        private var hackingLastNS: Long = 0

        // Move these out to save a few allocations
        // (it's probably well into paranoid territory, but why not)
        private val nameLines by lazy { weaponNameText.wrapString(name, 58) }
        private val weaponNumberString = (slotNumber + 1).toString()

        override fun draw(g: Graphics) {
            g.colour = mainColour

            // Draw the outline box
            g.drawRect(pos.x.f, pos.y.f, (size.x - 1).f, (size.y - 1).f)
            g.drawRect((pos.x + 1).f, (pos.y + 1).f, (size.x - 3).f, (size.y - 3).f)

            if (empty)
                return

            if (hasChargeBar) {
                val maxBarSize = 35 - 2
                val barSize = (maxBarSize * chargeTime / maxWeaponChargeTime).toInt()

                // The Y position of the inside of the charge bar, relative to the main weapons box
                val top = maxBarSize - barSize

                // The top point of the triangle
                val triangleTop = top - 7

                for (j in 8 downTo 1) {
                    val triangleY = j + triangleTop
                    if (triangleY < 0)
                        continue
                    val y = pos.y + triangleY
                    g.drawLine((pos.x - j).f, y.f, pos.x.f, y.f)
                }

                if (isTargeted)
                    g.colour = WEAPONS_ITEM_TARGETING

                val chargePx = (barSize * chargeProgress).toInt()
                g.fillRect((pos.x - 5).f, (pos.y + 36 - chargePx).f, 4f, chargePx.f)

                g.colour = mainColour

                g.lineWidth = 2f
                g.drawLine(pos.x - 7.5f, pos.y.f + top.f + 1.5f, pos.x - 7.5f, pos.y + 39 - 1.5f)
                g.drawLine(pos.x - 7.5f, pos.y + 39 - 1.5f, pos.x - 0.5f, pos.y + 39 - 1.5f)
                g.lineWidth = 1f
            }

            // Draw the weapon number box
            val numBoxX = pos.x + size.x - 12
            val numBoxY = pos.y + size.y - 15
            g.lineWidth = 2f
            g.drawLine(numBoxX + 0.5f, numBoxY + 0.5f, numBoxX + 0.5f, numBoxY + 12f + 0.5f)
            g.drawLine(numBoxX + 0.5f, numBoxY + 0.5f, numBoxX + 10f + 0.5f, numBoxY + 0.5f)
            g.lineWidth = 1f

            // Draw the weapon/drone number itself
            val weaponNumberWidth = weaponNumberFont.getWidth(weaponNumberString)
            weaponNumberFont.drawString(
                (numBoxX + 2 + 1 + (8 - weaponNumberWidth) / 2).f,
                pos.y + size.y - 4f,
                weaponNumberString,
                mainColour
            )

            // Draw the item name, which is split across
            // multiple lines to fit in the box.
            var lineY = pos.y + 15
            for (line in nameLines) {
                weaponNameText.drawString(pos.x + 26f, lineY.f, line, mainColour)
                lineY += 15
            }

            for (bar in 0 until requiredPower) {
                val y = pos.y + size.y - 11 - bar * 8

                if (zoltanPower > bar) {
                    g.colour = WEAPONS_ITEM_ENERGY_ZOLTAN
                } else if (!isPowered) {
                    g.colour = WEAPONS_ITEM_ENERGY_UNPOWERED
                    g.drawRect((pos.x + 4).f, y.f, (16 - 1).f, (7 - 1).f)
                    continue
                } else if (isBeingHacked) {
                    g.colour = SYSTEM_HACKED
                } else if (isSelectingTarget) {
                    g.colour = WEAPONS_ITEM_TARGETING
                } else if (isCharged) {
                    g.colour = WEAPONS_ITEM_ENERGY_CHARGED
                } else {
                    g.colour = WEAPONS_ITEM_ENERGY_POWERED
                }
                g.fillRect((pos.x + 4).f, y.f, 16f, 7f)
            }

            drawHackingSparks()
        }

        private fun drawHackingSparks() {
            if (!isBeingHacked) {
                hackingSparks = null
                return
            }

            val currentNS = System.nanoTime()
            val dt = (currentNS - hackingLastNS) / 1_000_000_000f
            hackingLastNS = currentNS

            if (hackingSparks == null || hackingSparks?.isStopped == true) {
                hackingSparks = ship.sys.animations["stun_spark_big"].startSingle(game)

                hackingOffsetX = Random.nextInt(25)
                hackingMaskY = Random.nextInt(31)
                hackingMirror = Random.nextBoolean()
            }

            // Update the animation regardless of whether we're paused.
            hackingSparks!!.update(dt)

            val frame = hackingSparks!!.currentFrame
            val cutoutWidth = 69
            val cutoutHeight = 39
            frame.draw(
                // Screen points
                pos.x.f + hackingOffsetX, pos.y.f,
                pos.x.f + hackingOffsetX + cutoutWidth, pos.y.f + cutoutHeight,

                // Image points
                if (hackingMirror) cutoutWidth.f else 0f, hackingMaskY.f,
                if (hackingMirror) 0f else cutoutWidth.f, hackingMaskY.f + cutoutHeight
            )
        }
    }

    /**
     * This is the button that adjusts a system's power.
     */
    private inner class SystemPowerButton(pos: IPoint, val system: AbstractSystem) :
        Button(game, pos, ConstPoint(26, 26)) {

        // Subsystems can't be clicked, so don't play their hover noise.
        // This will also keep hovered set to false, since we don't want
        // to activate any hovering-related stuff for something you can't click.
        override val disabled: Boolean get() = system !is MainSystem

        override fun draw(g: Graphics) {
            system.drawIconAndPower(game, g, pos.x, pos.y)
        }

        override fun click(button: Int) {
            // You can't adjust the power of subsystems
            if (system !is MainSystem)
                return

            if (button == MOUSE_LEFT_BUTTON) {
                changeSystemPower(system, true)
            } else if (button == MOUSE_RIGHT_BUTTON) {
                changeSystemPower(system, false)
            }
        }
    }

    private inner class TeleportRoomListener(val send: Boolean) : RoomClickListener {
        override fun roomClicked(room: Room, gc: GameContainer) {
            // Can't teleport to/from our own ship
            if (room.ship == ship)
                return

            ship.teleporter!!.selectTeleportAction(send, room)
        }
    }

    private inner class HackingRoomListener : RoomClickListener {
        override fun roomClicked(room: Room, gc: GameContainer) {
            // Can't hack our own ship
            if (room.ship == ship)
                return

            ship.hacking!!.selectTarget(room)
        }
    }

    private inner class MindControlRoomListener : RoomClickListener {
        override fun roomClicked(room: Room, gc: GameContainer) {
            ship.mindControl!!.selectRoom(room)
        }
    }

    companion object {
        /**
         * The diagonal distance (squared) that the crew selection box has to be before it appears. If the
         * player drags out an area smaller than this, it'll be treated as a click and select whoever is
         * standing underneath.
         */
        val SELECTION_BOX_SIZE = 6f.pow(2f).toInt()

        private const val INSUFFICIENT_SCRAP_FLASH_TIME = 0.35f
        private const val WEAPON_BOX_GLOW = 12

        private const val MIN_BEAM_LENGTH = 10f

        private const val CREW_BOX_WIDTH = 86
        private const val CREW_BOX_HEIGHT = 27
        private const val CREW_BOX_SPACING = 30
    }
}
