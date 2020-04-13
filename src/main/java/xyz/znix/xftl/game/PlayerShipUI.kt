package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input.MOUSE_LEFT_BUTTON
import org.newdawn.slick.Input.MOUSE_RIGHT_BUTTON
import xyz.znix.xftl.*
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.systems.MainSystem
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.ceil

class PlayerShipUI(df: Datafile, val translator: Translator, val ship: Ship, private val game: SlickGame) {
    private val font = game.getFont("HL2", 2f)
    private val weaponNameText = SILFontLoader(df, df["fonts/JustinFont8.font"])
    private val weaponNumberFont = SILFontLoader(df, df["fonts/c&c.font"])
    private val numberFont = SILFontLoader(df, df["fonts/num_font.font"])
    private val oxygenEvadeFont = SILFontLoader(df, df["fonts/JustinFont10.font"])

    private var selectWeaponClickEvent: Consumer<Room>? = null
    private var targetingSelectedWeapon: Int? = null

    private val selectedTargets: MutableMap<AbstractWeaponInstance, SelectedTarget> = HashMap()
    private val selectedCrew: MutableList<AbstractCrew> = ArrayList()

    private val buttons = ArrayList<Button>()

    /**
     * The currently shown window. When a window is shown, the main UI is darkened, buttons are locked
     * and all input is sent to the window.
     *
     * TODO pause the game when a window is present
     */
    private var currentWindow: Window? = null

    val isWindowOpen: Boolean get() = currentWindow != null

    // Set by render
    private var height: Int = 500

    // The position of the weapons box
    val boxX get() = 234
    val boxY get() = height - 113

    fun sysImgX(i: Int): Int = 58 + i * 36
    fun sysImgY(i: Int): Int = height - 69

    // The position of a given weapon's selector
    fun weaponBoxX(i: Int): Int = boxX + 12 + 12 + 97 * i

    fun weaponBoxY(i: Int): Int = boxY + 12 + 4

    init {
        buttons += Buttons.JumpButton(ConstPoint(531, 29), ship, game) {
            currentWindow = JumpWindow(game) { beacon ->
                currentWindow = null
            }
        }
    }

    fun mouseClick(button: Int, x: Int, y: Int, playerShipPosition: IPoint) {
        currentWindow?.let { win ->
            win.mouseClick(button, x, y)
            return
        }

        for (i in 0 until ship.weaponSlots!!) {
            val wx = weaponBoxX(i)
            val wy = weaponBoxY(i)

            if (x >= wx && y >= wy && x < wx + 87 && y < wy + 39) {
                if (button == MOUSE_LEFT_BUTTON) {
                    weaponHotkeyPressed(i)
                }
                if (button == MOUSE_RIGHT_BUTTON) {
                    ship.hardpoints[i].weapon?.isPowered = false
                }

                return
            }
        }

        var i = 0
        for (sys in sortedMainSystems()) {
            val imgX = sysImgX(i) + 19
            val imgY = sysImgY(i) + 19

            if (x >= imgX && y >= imgY && x < imgX + 26 && y < imgY + 26) {
                if (button == MOUSE_LEFT_BUTTON && sys.powerUnused > 0) {
                    sys.increasePower()
                } else if (button == MOUSE_RIGHT_BUTTON && sys.powerSelected > 0) {
                    sys.decreasePower()
                }
                return
            }

            i++
        }

        // Select players
        val shipMousePos = Point(x, y)
        shipMousePos -= playerShipPosition
        for (crew in ship.crew) {
            if (shipMousePos.x < crew.screenX || shipMousePos.y < crew.screenY)
                continue
            if (shipMousePos.x >= crew.screenX + crew.icon.width || shipMousePos.y >= crew.screenY + crew.icon.height)
                continue

            if (button == MOUSE_LEFT_BUTTON) {
                selectedCrew.clear()
                selectedCrew += crew
                return
            }
        }

        val roomPoint = Point(shipMousePos)
        roomPoint += ship.hullOffset
        roomPoint.divideFloor(ROOM_SIZE)
        roomPoint -= ship.offset
        for (room in ship.rooms) {
            if (!room.containsAbsolute(roomPoint))
                continue

            if (button == MOUSE_RIGHT_BUTTON) {
                for (crew in selectedCrew)
                    crew.setTargetRoom(room)

                return
            }
        }

        for (btn in buttons) {
            btn.mouseDown(button, x, y)
        }
    }

    fun weaponHotkeyPressed(id: Int) {
        // Temporary hack to make the option hotkeys work
        (currentWindow as? DialogueWindow)?.selectOption(id)

        if (currentWindow != null) return

        val weapon = ship.hardpoints[id].weapon ?: return

        // TODO support beams
        check(weapon is IRoomTargetingWeapon)

        if (!weapon.isPowered) {
            val weapons = ship.weapons!!
            if (weapon.type.power > weapons.powerUnused) {
                // TODO warn the player there is not enough energy
                return
            }
            weapon.isPowered = true
            return
        }

        selectedTargets.remove(weapon)

        targetingSelectedWeapon = id
        selectWeaponClickEvent = Consumer { r: Room ->
            selectedTargets[weapon] = SelectedTarget(r, weapon)
        }
        game.clickEvent = selectWeaponClickEvent
    }

    fun systemPowerHotkeyPressed(type: Class<*>, powerUp: Boolean) {
        for (room in ship.rooms) {
            val sys = room.system as? MainSystem ?: continue
            if (sys.javaClass != type) continue

            if (powerUp) {
                sys.increasePower()
            } else {
                sys.decreasePower()
            }
        }
    }

    // Dispatch actions from the UI - this is only called when the game is not paused, so dispatch
    // everything from here so a player can cancel their actions if they remain paused.
    fun update(dt: Float) {
        var fired: MutableList<SelectedTarget>? = null

        for (tgt in selectedTargets.values) {
            val weapon = tgt.weapon
            if (!weapon.asWeaponInstance().isCharged)
                continue

            if (fired == null)
                fired = ArrayList()

            fired.add(tgt)

            weapon.fire(ship.weapons!!, tgt.room)
        }

        fired?.let { selectedTargets.values.removeAll(it) }

        // Untarget all unpowered weapons
        selectedTargets.keys.removeIf { !it.isPowered }
    }

    fun render(gc: GameContainer, g: Graphics) {
        height = gc.height

        drawTopBar(g)

        g.font = font

        game.getImg("img/box_weapons_bottom" + ship.weaponSlots + ".png").draw(boxX.f, boxY.f)
        val tx = boxX + 18
        val ty = boxY + 61

        g.color = UI_BACKGROUND_GLOW_COLOUR
        val tw = font.getWidth("WEAPONS")
        g.fillRect(tx.f, ty.f, (tw - 13).f, 20f)
        game.getImg("img/box_weapons_bottom_label.png").draw((tx + tw - 13).f, ty.f)

        font.drawStringLegacy((tx + 1).f, (ty + 11).f, "WEAPONS", UI_TEXT_COLOUR_1)

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

        // Draw the systems
        var systemCount = 0

        for (sys in sortedMainSystems()) {
            sys.drawIconAndPower(game, g, sysImgX(systemCount), sysImgY(systemCount))
            systemCount++
        }

        // Draw the wires between the systems
        for (i in 0 until systemCount - 1) {
            // Draw the power bar to the next item
            val powerX = sysImgX(i) + 31
            val powerY = sysImgY(i) + 45 + 12 - 26

            if (i == systemCount - 2)
                game.getImg("img/wireUI/wire_36_cap.png").draw(powerX.f, powerY.f)
            else
                game.getImg("img/wireUI/wire_36.png").draw(powerX.f, powerY.f)
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
        val maxWeaponChargeTime = ship.hardpoints.stream()
                .map { it.weapon }
                .filter { Objects.nonNull(it) }
                .map { it!!.type.chargeTime }
                .reduce { a, b -> Math.max(a, b) }
                .orElse(1f)

        for (i in 0 until ship.weaponSlots!!) {
            val wx = weaponBoxX(i)
            val wy = weaponBoxY(i)

            val hp = ship.hardpoints[i]
            val weapon = hp.weapon

            val mainColour = when {
                weapon == null -> WEAPONS_ITEM_DESELECTED
                !weapon.isPowered -> WEAPONS_ITEM_DESELECTED
                targetingSelectedWeapon == i -> WEAPONS_ITEM_TARGETING
                weapon.isCharged -> WEAPONS_ITEM_CHARGED
                else -> WEAPONS_ITEM_SELECTED
            }
            g.color = mainColour

            // Draw the outline box
            g.drawRect(wx.f, wy.f, (87 - 1).f, (39 - 1).f)
            g.drawRect((wx + 1).f, (wy + 1).f, (87 - 3).f, (39 - 3).f)

            if (weapon == null)
                continue

            val maxBarSize = 35 - 2
            val barSize = (maxBarSize * weapon.type.chargeTime / maxWeaponChargeTime).toInt()

            // The Y position of the inside of the charge bar, relative to the main weapons box
            val top = maxBarSize - barSize

            // The top point of the triangle
            val triangleTop = top - 7

            for (j in 8 downTo 1) {
                val pos = j + triangleTop
                if (pos < 0)
                    continue
                val y = wy + pos
                g.drawLine((wx - j).f, y.f, wx.f, y.f)
            }

            if (selectedTargets.containsKey(weapon))
                g.color = WEAPONS_ITEM_TARGETING

            val chargePx = (barSize * weapon.chargeProgress).toInt()
            g.fillRect((wx - 5).f, (wy + 36 - chargePx).f, 4f, chargePx.f)

            g.color = mainColour

            g.lineWidth = 2f
            g.drawLine(wx - 7.5f, wy.f + top.f + 1.5f, wx - 7.5f, wy + 39 - 1.5f)
            g.drawLine(wx - 7.5f, wy + 39 - 1.5f, wx - 0.5f, wy + 39 - 1.5f)
            g.lineWidth = 1f

            // Draw the weapon number box
            g.lineWidth = 2f
            g.drawLine(wx.f + 75f + 0.5f, wy.f + 24f + 0.5f, wx.f + 75f + 0.5f, wy.f + 36f + 0.5f)
            g.drawLine(wx.f + 75f + 0.5f, wy.f + 24f + 0.5f, wx.f + 85f + 0.5f, wy.f + 24f + 0.5f)
            g.lineWidth = 1f

            // Draw the weapon number itself
            val weaponNumber = Integer.toString(i + 1)
            val weaponNumberWidth = weaponNumberFont.getWidth(weaponNumber)
            weaponNumberFont.drawStringLegacy((wx + 77 + 1 + (8 - weaponNumberWidth) / 2).f, (wy + 30).f, weaponNumber, g.color)

            val shortName = translator[weapon.type.shortKey].replaceFirst(" ".toRegex(), "\n")
            drawWeaponString(g, shortName, wx + 26, wy + 8)

            // TODO make these correct
            val zoltanPower = 1

            for (bar in 0 until weapon.type.power) {
                val y = wy + 28 - bar * 8

                if (zoltanPower > bar) {
                    g.color = WEAPONS_ITEM_ENERGY_ZOLTAN
                } else if (!weapon.isPowered) {
                    g.color = WEAPONS_ITEM_ENERGY_UNPOWERED
                    g.drawRect((wx + 4).f, y.f, (16 - 1).f, (7 - 1).f)
                    continue
                } else if (weapon.isCharged) {
                    g.color = WEAPONS_ITEM_ENERGY_CHARGED
                } else {
                    g.color = WEAPONS_ITEM_ENERGY_POWERED
                }
                g.fillRect((wx + 4).f, y.f, 16f, 7f)
            }
        }

        for (button in buttons) {
            button.draw(g)
        }
    }

    fun renderMenus(container: GameContainer, g: Graphics) {
        currentWindow?.draw(g)
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
        game.getImg("img/statusUI/top_scrap.png").draw(374f + 8 - 5, 0f)
        // TODO scrap number

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

        // TODO use correct fuel quantity
        // TODO correctly set the red threshold?
        drawSmallCounter("fuel", shieldsEndX, 0, 0)

        // TODO missiles number
        drawSmallCounter("missiles", shieldsEndX + 66, 1, 16)

        // TODO missiles number
        drawSmallCounter("drones", shieldsEndX + 66 + 70, -2, 0)

        // Oxygen and evasion indicator
        val oxyY = 96f;
        game.getImg("img/statusUI/top_evade_oxygen.png").draw(1f, oxyY - 7)

        val evadeBoxLeft = 92f

        // Evade
        oxygenEvadeFont.drawStringLeftAlignedLegacy(evadeBoxLeft, oxyY + 8, "${ship.evasion}%", Color.white)

        // Oxygen
        // TODO use correct numbers once oxygen is implemented
        oxygenEvadeFont.drawStringLeftAlignedLegacy(evadeBoxLeft, oxyY + 8 + 22, "100%", Color.white)
    }

    private fun sortedMainSystems(): Stream<MainSystem> = ship.rooms.stream()
            .map { it.system }
            .filter { MainSystem::class.java.isInstance(it) }
            .map { MainSystem::class.java.cast(it) }
            .sorted(Comparator.comparing<MainSystem, MainSystem.SortingType> { it.sortingType })

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
    }

    fun showEventDialogue(event: Event) {
        currentWindow = DialogueWindow(game, event) {
            currentWindow = null
        }
    }

    private class SelectedTarget(val room: Room, val weapon: IRoomTargetingWeapon)
}
