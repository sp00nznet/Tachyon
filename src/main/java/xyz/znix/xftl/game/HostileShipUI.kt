package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import xyz.znix.xftl.*
import xyz.znix.xftl.layout.Room
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.math.Point

class HostileShipUI(private val game: SlickGame, df: Datafile, private val enemy: Ship) {
    private val mutableShipPos = Point(0, 0)
    val shipPos: IPoint get() = mutableShipPos

    private val font = SILFontLoader(df, df["fonts/HL2.font"])

    private val shieldIconStandard = df.readImage("img/combatUI/box_hostiles_shield1.png")
    private val shieldIconBroken = df.readImage("img/combatUI/box_hostiles_shield2.png")
    private val shieldIconStandardHacked = df.readImage("img/combatUI/box_hostiles_shield2_hacked_charged.png")
    private val shieldIconBrokenHacked = df.readImage("img/combatUI/box_hostiles_shield2_hacked.png")

    private val superShieldBar = game.getImg("img/combatUI/box_hostiles_shield_super5.png") // TODO flagship
    private val shieldChargeBar = game.getImg("img/combatUI/box_hostiles_shield_charge.png")

    fun render(gc: GameContainer, g: Graphics, hoveredRoom: Room?, isHostile: Boolean) {
        val box = game.getImg("img/combatUI/box_hostiles2.png")

        val boxX = gc.width - (box.width - 20) - 18
        val boxY = 54 - 9

        // It's not quite the same as FTL, but works well enough for now
        mutableShipPos.x = boxX + (box.width - enemy.hullImage.width) / 2
        mutableShipPos.y = boxY + (box.height - enemy.hullImage.height) / 2
        mutableShipPos -= enemy.hullOffset

        box.draw(boxX, boxY)

        Utils.drawStenciled(Utils.StencilMode.MASKING, {
            game.getImg("img/combatUI/box_hostiles_mask.png").draw(boxX, boxY)
        }) {
            g.translate(shipPos.x.f, shipPos.y.f)
            enemy.render(g, isHostile, hoveredRoom)

            // FIXME this is pretty horrible accessing the player ship like this
            enemy.renderTargeting(g, game.shipUI.ship.weapons!!.selectedTargets)

            g.resetTransform()
        }

        val textX = boxX + 12

        // Draw the hull level
        val hullY = boxY + 29 + 4
        renderSmallbar(textX, hullY, "HULL")
        val hpWidth = 11 * enemy.health
        val hpX = textX + 5
        val hpY = hullY + 12
        val hull = game.getImg("img/combatUI/box_hostiles_hull2.png")
        // TODO colour
        hull.draw(
            hpX.f, hpY.f, hpX.f + hpWidth, hpY.f + hull.height,
            0f, 0f, hpWidth.f, hull.height.f
        )

        enemy.shields?.let { shields ->
            // Draw the shield bubbles
            val shieldsY = hullY + 27
            renderSmallbar(textX, shieldsY, "SHIELDS")

            var bubbleX = textX + 7

            for (i in 0 until shields.selectedShieldBars) {
                val intact = i < shields.activeShields
                val hacked = shields.isHackActive
                val img = when {
                    hacked && intact -> shieldIconStandardHacked
                    hacked && !intact -> shieldIconBrokenHacked
                    intact -> shieldIconStandard
                    else -> shieldIconBroken
                }
                img.draw(bubbleX, shieldsY + 15)
                bubbleX += 23
            }

            if (enemy.superShield != 0) {
                // TODO support ships with a super-shield but no shields system
                bubbleX += 10
                val superShieldY = shieldsY + 15 + 5

                superShieldBar.draw(bubbleX, superShieldY)

                val width = 50f * enemy.superShield / enemy.maxSuperShield
                g.color = Constants.SYS_ENERGY_ACTIVE
                g.fillRect(bubbleX + 3f, superShieldY + 3f, width, 7f)
            } else if (shields.rechargeTimer != 0f) {
                // Draw the charge bar
                shieldChargeBar.draw(textX + 5, shieldsY + 39)

                val progress = shields.rechargeTimer / shields.rechargeDelay
                val colour = when {
                    shields.isHackActive -> Constants.SHIELD_BAR_HACKED
                    else -> Constants.SHIELD_BAR_NORMAL
                }

                val width = (56 * progress)
                g.color = colour
                g.fillRect(textX.f + 5 + 3, shieldsY.f + 39 + 3, width, 6f)
            }
        }

        // TODO draw the 'FTL charging' warning if they're trying to escape

        // Draw the enemy's systems
        // TODO sorting
        for ((i, sys) in enemy.systems.withIndex()) {
            val y = boxY + box.height - 85
            val x = boxX + i * 30 + 40

            sys.drawIconAndPower(game, g, x, y)
        }
    }

    private fun renderSmallbar(x: Int, y: Int, text: String) {
        // TODO colours

        val textX = 2
        val textWidth = font.getWidth(text)

        val left = game.getImg("img/combatUI/box_hostiles_smallbar_left.png")
        left.draw(x, y)
        val middle = game.getImg("img/combatUI/box_hostiles_smallbar_middle.png")
        middle.filter = Image.FILTER_NEAREST
        val midWidth = textWidth + textX - left.width
        middle.draw(x.f + left.width, y.f, midWidth.f, middle.height.f)

        game.getImg("img/combatUI/box_hostiles_smallbar_right.png").draw(x + left.width + midWidth, y)

        font.drawStringLegacy(x + 2f, y + 7f, text, Color.black)
    }
}
