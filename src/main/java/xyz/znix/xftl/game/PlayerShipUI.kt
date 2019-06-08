package xyz.znix.xftl.game

import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Input.MOUSE_LEFT_BUTTON
import org.newdawn.slick.Input.MOUSE_RIGHT_BUTTON
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.Ship
import xyz.znix.xftl.Translator
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.systems.MainSystem
import xyz.znix.xftl.weapons.AbstractProjectileWeaponInstance
import xyz.znix.xftl.weapons.AbstractWeaponInstance
import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PlayerShipUI(df: Datafile, val translator: Translator, val ship: Ship, private val game: SlickGame) {
    // Make <int>.f a shorthand for <int>.toFloat(), cleaning things up a lot
    private val Int.f: Float get() = this.toFloat()

    private val font = SILFontLoader(df, df["fonts/HL2.font"])
    private val weaponNameText = SILFontLoader(df, df["fonts/JustinFont8.font"])
    private val weaponNumberFont = SILFontLoader(df, df["fonts/c&c.font"])

    private var selectWeaponClickEvent: Consumer<Room>? = null
    private var targetingSelectedWeapon: Int? = null

    private val selectedTargets: MutableMap<AbstractWeaponInstance, SelectedTarget> = HashMap()

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

    fun mouseClick(button: Int, x: Int, y: Int) {
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
                if (button == MOUSE_LEFT_BUTTON && sys.powerSelected < sys.powerAvailable) {
                    sys.increasePower()
                } else if (button == MOUSE_RIGHT_BUTTON && sys.powerSelected > 0) {
                    sys.decreasePower()
                }
                return
            }

            i++
        }
    }

    fun weaponHotkeyPressed(id: Int) {
        val weapon = ship.hardpoints[id].weapon ?: return

        if (!weapon.isPowered) {
            val weapons = ship.weapons!!
            if (weapon.type.power + weapons.powerSelected > weapons.powerAvailable) {
                // TODO warn the player there is not enough energy
                return
            }
            if (weapon.type.power > ship.powerAvailable) {
                // TODO warn the player there is not enough reactor power
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

    // Dispatch actions from the UI - this is only called when the game is not paused, so dispatch
    // everything from here so a player can cancel their actions if they remain paused.
    fun update(dt: Float) {
        var fired: MutableList<SelectedTarget>? = null

        for (tgt in selectedTargets.values) {
            val weapon = tgt.weapon
            if (!weapon.isCharged)
                continue

            if (fired == null)
                fired = ArrayList()

            fired.add(tgt)

            val apwi = weapon as AbstractProjectileWeaponInstance
            apwi.fire(ship.weapons!!, tgt.room)
        }

        fired?.let { selectedTargets.values.removeAll(it) }

        // Untarget all unpowered weapons
        selectedTargets.keys.removeIf { !it.isPowered }
    }

    fun render(gc: GameContainer, g: Graphics) {
        height = gc.height

        font.scale = 2f
        g.font = font

        game.getImg("img/box_weapons_bottom" + ship.weaponSlots + ".png").draw(boxX.f, boxY.f)
        val tx = boxX + 18
        val ty = boxY + 61

        g.color = UI_BACKGROUND_GLOW_COLOUR
        val tw = font.getWidth("WEAPONS")
        g.fillRect(tx.f, ty.f, (tw - 13).f, 20f)
        game.getImg("img/box_weapons_bottom_label.png").draw((tx + tw - 13).f, ty.f)

        g.color = UI_TEXT_COLOUR_1
        g.drawString("WEAPONS", (tx + 1).f, (ty + 11).f)

        // Draw the systems
        var systemCount = 0

        for (sys in sortedMainSystems()) {
            val colour = when {
                sys.damagedEnergyLevels == sys.energyLevels -> "red"
                sys.damagedEnergyLevels > 0 -> "orange"
                sys.powerSelected == 0 -> "grey"
                else -> "green"
            }

            val x = sysImgX(systemCount)

            game.getImg("img/icons/s_" + sys.codename + "_" + colour + "1.png").draw(x.f, sysImgY(systemCount).f)

            for (i in 0 until sys.energyLevels) {
                val y = sysImgY(systemCount) + 8 - i * 8

                when {
                    i >= sys.energyLevels - sys.damagedEnergyLevels -> {
                        // System damaged/broken
                        g.color = SYS_ENERGY_BROKEN
                        g.drawRect((x + 24).f, y.f, (16 - 1).f, (6 - 1).f)
                        g.drawLine((x + 24).f, (y + 6).f, (x + 24 + 16).f, y.f)
                    }
                    i < sys.powerSelected -> {
                        // System powered
                        g.color = SYS_ENERGY_ACTIVE
                        g.fillRect((x + 24).f, y.f, 16f, 6f)
                    }
                    else -> {
                        // System depowered
                        g.color = SYS_ENERGY_DEPOWERED
                        g.drawRect((x + 24).f, y.f, (16 - 1).f, (6 - 1).f)
                    }
                }
            }

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
            g.font = weaponNumberFont
            val weaponNumber = Integer.toString(i + 1)
            val weaponNumberWidth = weaponNumberFont.getWidth(weaponNumber)
            g.drawString(weaponNumber, (wx + 77 + 1 + (8 - weaponNumberWidth) / 2).f, (wy + 30).f)

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
    }

    private fun sortedMainSystems(): Stream<MainSystem> = ship.rooms.stream()
            .map { it.system }
            .filter { MainSystem::class.java.isInstance(it) }
            .map { MainSystem::class.java.cast(it) }
            .sorted(Comparator.comparing<MainSystem, MainSystem.SortingType> { it.sortingType })

    private fun drawWeaponString(g: Graphics, str: String, x: Int, y: Int) {
        var y = y
        for (line in str.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            g.drawString(line, x.f, y.f)
            y += 15
        }
    }

    private class SelectedTarget(val room: Room, val weapon: AbstractWeaponInstance)
}
