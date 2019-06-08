package xyz.znix.xftl.game

import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants.*
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.Ship
import xyz.znix.xftl.Translator
import xyz.znix.xftl.systems.MainSystem
import java.util.*

class PlayerShipUI(df: Datafile, val translator: Translator, val ship: Ship, private val game: SlickGame) {

    private val font = SILFontLoader(df, df["fonts/HL2.font"])
    private val weaponNameText = SILFontLoader(df, df["fonts/JustinFont8.font"])
    private val weaponNumberFont = SILFontLoader(df, df["fonts/c&c.font"])

    fun render(gc: GameContainer, g: Graphics) {
        font.scale = 2f
        g.font = font

        val bx = 234
        val by = gc.height - 113
        game.getImg("img/box_weapons_bottom" + ship.weaponSlots + ".png").draw(bx.toFloat(), by.toFloat())
        val tx = bx + 18
        val ty = by + 61

        g.color = UI_BACKGROUND_GLOW_COLOUR
        val tw = font.getWidth("WEAPONS")
        g.fillRect(tx.toFloat(), ty.toFloat(), (tw - 13).toFloat(), 20f)
        game.getImg("img/box_weapons_bottom_label.png").draw((tx + tw - 13).toFloat(), ty.toFloat())

        g.color = UI_TEXT_COLOUR_1
        g.drawString("WEAPONS", (tx + 1).toFloat(), (ty + 11).toFloat())
        // getImg("img/icons/s_weapons_grey1.png").draw(202, container.getHeight() - 69);

        // Draw the systems
        var x = 58
        val systems = ship.rooms.stream()
                .map { it.system }
                .filter { MainSystem::class.java.isInstance(it) }
                .map { MainSystem::class.java.cast(it) }
                .sorted(Comparator.comparing<MainSystem, MainSystem.SortingType> { it.sortingType })

        for (sys in systems) {
            val colour = when {
                sys.damagedEnergyLevels == sys.energyLevels -> "red"
                sys.damagedEnergyLevels > 0 -> "orange"
                sys.selectedEnergyLevel == 0 -> "grey"
                else -> "green"
            }

            var y = gc.height - 69
            game.getImg("img/icons/s_" + sys.codename + "_" + colour + "1.png").draw(x.toFloat(), y.toFloat())

            y += 8

            for (i in 0 until sys.energyLevels) {
                when {
                    i >= sys.energyLevels - sys.damagedEnergyLevels -> {
                        // System damaged/broken
                        g.color = SYS_ENERGY_BROKEN
                        g.drawRect((x + 24).toFloat(), y.toFloat(), (16 - 1).toFloat(), (6 - 1).toFloat())
                        g.drawLine((x + 24).toFloat(), (y + 6).toFloat(), (x + 24 + 16).toFloat(), y.toFloat())
                    }
                    i < sys.selectedEnergyLevel -> {
                        // System powered
                        g.color = SYS_ENERGY_ACTIVE
                        g.fillRect((x + 24).toFloat(), y.toFloat(), 16f, 6f)
                    }
                    else -> {
                        // System depowered
                        g.color = SYS_ENERGY_DEPOWERED
                        g.drawRect((x + 24).toFloat(), y.toFloat(), (16 - 1).toFloat(), (6 - 1).toFloat())
                    }
                }
                y -= 8
            }

            x += 36
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
            val wx = bx + 12 + 12 + 97 * i
            val wy = by + 12 + 4

            val hp = ship.hardpoints.get(i)
            val weapon = hp.weapon

            if (weapon == null || !weapon.isPowered)
                g.color = WEAPONS_ITEM_DESELECTED
            else if (weapon.isCharged)
                g.color = WEAPONS_ITEM_CHARGED
            else
                g.color = WEAPONS_ITEM_SELECTED

            // Draw the outline box
            g.drawRect(wx.toFloat(), wy.toFloat(), (87 - 1).toFloat(), (39 - 1).toFloat())
            g.drawRect((wx + 1).toFloat(), (wy + 1).toFloat(), (87 - 3).toFloat(), (39 - 3).toFloat())

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
                g.drawLine((wx - j).toFloat(), y.toFloat(), wx.toFloat(), y.toFloat())
            }

            val chargePx = (barSize * weapon.chargeProgress).toInt()
            g.fillRect((wx - 5).toFloat(), (wy + 36 - chargePx).toFloat(), 4f, chargePx.toFloat())

            g.lineWidth = 2f
            g.drawLine(wx - 7.5f, wy.toFloat() + top.toFloat() + 1.5f, wx - 7.5f, wy + 39 - 1.5f)
            g.drawLine(wx - 7.5f, wy + 39 - 1.5f, wx - 0.5f, wy + 39 - 1.5f)
            g.lineWidth = 1f

            // Draw the weapon number box
            g.lineWidth = 2f
            g.drawLine(wx.toFloat() + 75f + 0.5f, wy.toFloat() + 24f + 0.5f, wx.toFloat() + 75f + 0.5f, wy.toFloat() + 36f + 0.5f)
            g.drawLine(wx.toFloat() + 75f + 0.5f, wy.toFloat() + 24f + 0.5f, wx.toFloat() + 85f + 0.5f, wy.toFloat() + 24f + 0.5f)
            g.lineWidth = 1f

            // Draw the weapon number itself
            g.font = weaponNumberFont
            val weaponNumber = Integer.toString(i + 1)
            val weaponNumberWidth = weaponNumberFont.getWidth(weaponNumber)
            g.drawString(weaponNumber, (wx + 77 + 1 + (8 - weaponNumberWidth) / 2).toFloat(), (wy + 30).toFloat())

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
                    g.drawRect((wx + 4).toFloat(), y.toFloat(), (16 - 1).toFloat(), (7 - 1).toFloat())
                    continue
                } else if (weapon.isCharged) {
                    g.color = WEAPONS_ITEM_ENERGY_CHARGED
                } else {
                    g.color = WEAPONS_ITEM_ENERGY_POWERED
                }
                g.fillRect((wx + 4).toFloat(), y.toFloat(), 16f, 7f)
            }
        }
    }

    private fun drawWeaponString(g: Graphics, str: String, x: Int, y: Int) {
        var y = y
        for (line in str.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            g.drawString(line, x.toFloat(), y.toFloat())
            y += 15
        }
    }
}
