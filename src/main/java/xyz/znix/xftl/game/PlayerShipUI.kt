package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input.MOUSE_LEFT_BUTTON
import org.newdawn.slick.Input.MOUSE_RIGHT_BUTTON
import org.newdawn.slick.geom.Rectangle
import xyz.znix.xftl.*
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.game.SlickGame.RoomClickListener
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.systems.*
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import java.util.*
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.round

class PlayerShipUI(df: Datafile, val translator: Translator, val ship: Ship, private val game: SlickGame) {
    private val font = game.getFont("HL2", 2f)
    private val weaponNameText = SILFontLoader(df, df["fonts/JustinFont8.font"])
    private val weaponNumberFont = SILFontLoader(df, df["fonts/c&c.font"])
    private val numberFont = SILFontLoader(df, df["fonts/num_font.font"])
    private val oxygenEvadeFont = SILFontLoader(df, df["fonts/JustinFont10.font"])

    private val powerUpSound = game.sounds.getSample("powerUpSystem")
    private val powerUpFailSound = game.sounds.getSample("powerUpFail")
    private val powerDownSound = game.sounds.getSample("powerDownSystem")

    private var selectWeaponClickEvent: RoomClickListener? = null
    private var targetingSelectedWeapon: Int? = null

    private val selectedCrew: MutableList<AbstractCrew> = ArrayList()

    private val buttons = ArrayList<Button>()

    /**
     * The currently shown window. When a window is shown, the main UI is darkened, buttons are locked
     * and all input is sent to the window.
     */
    private var currentWindow: Window? = null

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

    /**
     * If the user is selecting a room to teleport to/from, this is non-null.
     * True if sending crew to an enemy ship, false if receiving.
     */
    val teleportMode: Boolean? get() = (game.clickEvent as? TeleportRoomListener)?.send

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

        crewSelectionRectangle?.let { csr ->
            if (button != MOUSE_LEFT_BUTTON) return@let

            selectedCrew.clear()

            // If a rectangle is visible on screen (ie, the player isn't clicking a point) then build a rectangle
            val rect = if (!isCrewSelectionPoint) {
                val pos = csr.first.min(csr.second)
                val size = (csr.second - csr.first).abs()
                Rectangle(pos.x.f, pos.y.f, size.x.f, size.y.f)
            } else null

            // Any intruders on the enemy ship should also be controllable,
            // including mind controlled enemies (once that's implemented).
            val controllableCrew = ArrayList(ship.friendlyCrew)
            game.enemy?.let { controllableCrew.addAll(it.intruders) }

            for (crew in controllableCrew) {
                // Skip drones and the like which the player isn't allowed to control
                if (!crew.playerControllable)
                    continue

                val crewPos = crewScreenPos(crew, playerShipPosition)

                // If we're in rectangle mode, check that it intersects the centre of the player's body
                val hovered = rect?.contains(crewPos.x.f, crewPos.y.f)
                // Otherwise check the point overlaps the player
                    ?: isCrewHovered(crew, csr.first, crewPos)

                if (hovered) {
                    selectedCrew += crew
                }
            }

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

    fun isCrewHovered(crew: AbstractCrew, mousePos: IPoint, crewPos: IPoint): Boolean {
        return mousePos.x in crewPos.x..crewPos.x + crew.icon.width &&
                mousePos.y in crewPos.y..crewPos.y + crew.icon.height
    }

    // Called by SlickGame, used for controlling boarders.
    fun enemyRoomRightClicked(room: Room, enemyShip: Ship) {
        for (crew in selectedCrew) {
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

        // If the user was previously targeting a beam, cancel that.
        beamTargeting = null

        val weapon = ship.hardpoints[id].weapon ?: return

        if (!weapon.isPowered) {
            val weapons = ship.weapons!!
            if (weapon.type.power > weapons.powerUnused) {
                // TODO warn the player there is not enough energy
                powerUpFailSound.play()
                return
            }
            if (!weapon.hasEnoughMissiles) {
                // TODO warn the player there isn't enough missiles.
                powerUpFailSound.play()
                return
            }
            powerUpSound.play()
            weapon.isPowered = true
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
                beamTargeting = SelectedTarget.BeamAim(weapon, id, game.enemy, mousePos, shipPos)
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
        beam.hitRooms.clear()

        val point = Point(beam.startMousePoint)

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
    fun update(dt: Float) {
        var fired: MutableList<SelectedTarget>? = null

        // TODO move this to Weapons.update
        val targets = ship.weapons?.selectedTargets ?: return

        for (tgt in targets) {
            if (!tgt.weapon.asWeaponInstance().isCharged)
                continue

            if (fired == null)
                fired = ArrayList()

            fired.add(tgt)

            when (tgt) {
                is SelectedTarget.BeamAim -> tgt.beamWeapon.fire(tgt)
                is SelectedTarget.RoomAim -> tgt.roomTargetingWeapon.fire(ship.weapons!!, tgt.room)
            }
        }

        fired?.forEach { tgt ->
            targets.unTarget(tgt.weaponNumber)
        }
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

        val powerTreeX = 86 - 53
        val powerTreeY = gc.height - 21 - 302
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
                g.color = SYS_ENERGY_ACTIVE
                g.fillRect(x.f, y.f, 28f, 7f)
            } else {
                g.color = SYS_ENERGY_DEPOWERED
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

            powerX += when (system) {
                is Weapons -> 48 + ship.weaponSlots!! * 97
                is Drones -> 48 + ship.droneSlots!! * 97
                is Cloaking, is Teleporter -> 54
                else -> 36
            }

            // Don't draw the bar under the last item - since each item draws the
            // wire to the next item, this would result in a wire sticking out.
            if (i == ship.mainSystems.size - 1)
                continue

            val lastSystem = i == ship.mainSystems.size - 2

            val wireWidth = when (system) {
                // Remember that we'll only run this if this isn't the last system,
                // so we won't be drawing a wire under weapons if drones isn't installed.
                is Weapons -> {
                    // Draw the wire that goes under the weapons to the drone system
                    when (ship.weaponSlots) {
                        4 -> "456"
                        else -> "456_${ship.weaponSlots!!}weapon"
                    }
                }

                is Cloaking, is Teleporter -> "54"
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

        g.font = weaponNameText

        // Find the longest charge time of all equipped weapons
        maxWeaponChargeTime = ship.hardpoints.stream()
            .map { it.weapon }
            .filter { Objects.nonNull(it) }
            .map { it!!.type.chargeTime }
            .reduce { a, b -> Math.max(a, b) }
            .orElse(1f)

        // Draw the weapons box
        val weaponBoxX = weaponPowerX!! + 1
        drawWeaponBox(weaponBoxX, "weapons_label", ship.weaponSlots!!)

        for (i in 0 until ship.weaponSlots) {
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
                override val chargeTime: Float get() = weapon!!.type.chargeTime
                override val chargeProgress: Float get() = weapon!!.chargeProgress
                override val zoltanPower: Int get() = 0 // TODO
                override val isPowered: Boolean get() = weapon!!.isPowered
                override val isCharged: Boolean get() = weapon!!.isCharged
                override val isTargeted: Boolean get() = ship.weapons!!.selectedTargets.getTarget(i) != null
                override val isSelectingTarget: Boolean get() = targetingSelectedWeapon == i
                override val hasChargeBar: Boolean get() = true

                override fun click(button: Int) {
                    val weapon = ship.hardpoints[i].weapon
                    if (button == MOUSE_LEFT_BUTTON) {
                        weaponHotkeyPressed(i)
                    }
                    if (weapon != null && weapon.isPowered && button == MOUSE_RIGHT_BUTTON) {
                        weapon.isPowered = false
                        powerDownSound.play()
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
            g.color = Color.white
            val size = csr.second - csr.first
            g.drawRect(csr.first.x.f, csr.first.y.f, size.x.f, size.y.f)
            g.drawRect(csr.first.x + 1f, csr.first.y + 1f, size.x - 2f, size.y - 2f)
        }

        for (button in buttons) {
            button.draw(g)
        }

        updatingButtons = false
    }

    fun renderMenus(container: GameContainer, g: Graphics) {
        val window = currentWindow ?: return

        // Add the tint over all the regular game stuff to make the window clearer.
        g.color = Color(0f, 0f, 0f, 0.65f)
        g.fillRect(0f, 0f, container.width.f, container.height.f)

        // Centre the window.
        window.position = ConstPoint(
            (container.width - window.size.x) / 2,
            (container.height - window.size.y) / 2
        )

        window.draw(g)
    }

    private fun drawTopBar(g: Graphics) {
        game.getImg("img/statusUI/top_hull.png").draw(0f, 0f)

        val labelImg = game.getImg("img/statusUI/top_hull_label.png")
        val txt = "HULL"
        UIUtils.drawTab(font, txt, labelImg, 0f, 0f, 10f, 30f)

        font.drawStringLegacy(9f, 21f, "HULL", UI_TEXT_COLOUR_1)

        // Draw the hull bar

        // TODO support ships with more or less health
        check(ship.maxHealth == 30)

        // TODO use the correct colours
        val hpW = 12f * ship.health
        val healthColour = when (ship.health.f / ship.maxHealth) {
            in 0f..0.33f -> Color.red
            in 0.33f..0.66f -> Color.yellow
            else -> Color.green
        }
        val mask = game.getImg("img/statusUI/top_hull_bar_mask.png")
        val hpH = mask.height.f
        mask.draw(11f, 0f, 11f + hpW, hpH, 0f, 0f, hpW, hpH, healthColour)

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
            game.getImg("img/statusUI/top_shields4_on.png").draw(0, shieldY)
            // TODO shield bubble indicators

            val shieldImg = game.getImg("img/statusUI/top_shieldsquare1_on.png")
            for (i in 0 until shields.activeShields) {
                shieldImg.draw(30 + 23 * i, shieldY + 4)
            }
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

            numberFont.drawStringLegacy(areaStart + textInternalX, shieldY + 23f, count.toString(), Color.white)
        }

        val shieldsEndX = 122

        // Fuel shows as red when there's three or less left.
        drawSmallCounter("fuel", shieldsEndX, 0, ship.fuelCount, 3)

        // Missiles only show as red when there's none left.
        drawSmallCounter("missiles", shieldsEndX + 66, 1, ship.missilesCount)

        // TODO drone red threshold.
        drawSmallCounter("drones", shieldsEndX + 66 + 70, -2, ship.dronesCount)

        // Oxygen and evasion indicator
        val oxyY = 96f;
        game.getImg("img/statusUI/top_evade_oxygen.png").draw(1f, oxyY - 7)

        val evadeBoxLeft = 92f

        // Evade
        oxygenEvadeFont.drawStringLeftAlignedLegacy(evadeBoxLeft, oxyY + 8, "${ship.evasion}%", Color.white)

        // Oxygen
        val avgOxygen = round(ship.averageOxygen * 100).toInt()
        oxygenEvadeFont.drawStringLeftAlignedLegacy(evadeBoxLeft, oxyY + 8 + 22, "$avgOxygen%", Color.white)
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

    private fun drawWeaponString(g: Graphics, str: String, x: Int, y: Int) {
        var y = y
        for (line in str.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val font = g.font as SILFontLoader
            font.drawStringLegacy(x.f, y.f, line, g.color)
            y += 15
        }
    }

    fun updateUI(x: Int, y: Int) {
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
    }

    fun showEventDialogue(event: Event) {
        var hasImmediatelyClosed = false

        currentWindow = DialogueWindow(game, ship, event) {
            currentWindow = null
            hasImmediatelyClosed = true

            // If a store was made available by the dialogue, open it
            if (game.currentBeacon.hasStore && !storeAlreadyOpened) {
                updateButtons() // Make the store button show up
                showStoreWindow()
            }
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
    fun isCrewSelected(crew: AbstractCrew): Boolean {
        return selectedCrew.contains(crew)
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

    private abstract inner class WeaponDroneButton(pos: IPoint, val slotNumber: Int, size: ConstPoint) :
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

        override val disabled: Boolean get() = empty

        override fun draw(g: Graphics) {
            val mainColour = when {
                empty -> WEAPONS_ITEM_DESELECTED
                !isPowered -> WEAPONS_ITEM_DESELECTED
                isSelectingTarget -> WEAPONS_ITEM_TARGETING
                isCharged -> WEAPONS_ITEM_CHARGED
                else -> WEAPONS_ITEM_SELECTED
            }
            g.color = mainColour

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
                    g.color = WEAPONS_ITEM_TARGETING

                val chargePx = (barSize * chargeProgress).toInt()
                g.fillRect((pos.x - 5).f, (pos.y + 36 - chargePx).f, 4f, chargePx.f)

                g.color = mainColour

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
            val weaponNumber = (slotNumber + 1).toString()
            val weaponNumberWidth = weaponNumberFont.getWidth(weaponNumber)
            weaponNumberFont.drawString(
                (numBoxX + 2 + 1 + (8 - weaponNumberWidth) / 2).f,
                pos.y + size.y - 4f,
                weaponNumber,
                g.color
            )

            val shortName = name.replaceFirst(" ".toRegex(), "\n")
            drawWeaponString(g, shortName, pos.x + 26, pos.y + 8)

            for (bar in 0 until requiredPower) {
                val y = pos.y + size.y - 11 - bar * 8

                if (zoltanPower > bar) {
                    g.color = WEAPONS_ITEM_ENERGY_ZOLTAN
                } else if (!isPowered) {
                    g.color = WEAPONS_ITEM_ENERGY_UNPOWERED
                    g.drawRect((pos.x + 4).f, y.f, (16 - 1).f, (7 - 1).f)
                    continue
                } else if (isSelectingTarget) {
                    g.color = WEAPONS_ITEM_TARGETING
                } else if (isCharged) {
                    g.color = WEAPONS_ITEM_ENERGY_CHARGED
                } else {
                    g.color = WEAPONS_ITEM_ENERGY_POWERED
                }
                g.fillRect((pos.x + 4).f, y.f, 16f, 7f)
            }
        }
    }

    /**
     * This is the button that adjusts a system's power.
     */
    private inner class SystemPowerButton(pos: IPoint, val system: AbstractSystem) :
        Button(game, pos, ConstPoint(26, 26)) {

        override fun draw(g: Graphics) {
            system.drawIconAndPower(game, g, pos.x - 19, pos.y - 19)
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
    }
}
