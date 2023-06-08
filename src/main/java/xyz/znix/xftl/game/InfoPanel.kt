package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.*
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.systems.SystemBlueprint

class InfoPanel(private val game: InGameState) {
    private val systemLevelFont = game.getFont("JustinFont10")
    private val numberFont = game.getFont("num_font")
    private val titleFont = game.getFont("c&cnew", 2f)
    private val descriptionFont = game.getFont("JustinFont10")

    var position: IPoint = ConstPoint.ZERO

    fun drawDescriptionBox(blueprint: Blueprint) {
        drawDescriptionBox(blueprint.title, blueprint.desc)
    }

    fun drawDescriptionBox(title: GameText?, description: GameText?) {
        game.windowRenderer.render(position.x, position.y, 333, 121)

        val titleStr = title?.get(game.translator)
        if (titleStr != null) {
            titleFont.drawString(
                position.x + 11f, position.y + 30f,
                titleStr,
                Color.white
            )
        }

        val descriptionStr = description?.get(game.translator)
        if (descriptionStr != null) {
            val lines = descriptionFont.wrapString(descriptionStr, 310)

            var y = position.y + 53
            for (line in lines) {
                descriptionFont.drawString(position.x + 11f, y.f, line, Color.white)
                y += 17
            }
        }
    }

    fun drawPowerBox(g: Graphics, system: SystemBlueprint, energyLevels: Int, undoablePower: Int) {
        val x = position.x
        val y = position.y + 133
        val totalWidth = 333 // Maybe this comes from localisation?

        val scrapIcon = game.getImg("img/upgradeUI/details_scrap.png")

        val maxNonUndoable = energyLevels - undoablePower

        // Draw the level text
        for (i in 0 until 8) {
            val canHaveLevel = i < system.maxPower
            val hasLevel = i < energyLevels
            val undoable = hasLevel && i >= maxNonUndoable
            val boxY = y + 192 - i * 26

            // The details images are drawn on top, so we have to paint
            // the background that the text sits on here.
            g.color = when (canHaveLevel) {
                true -> Constants.UPGRADE_DETAILS_BG_ON
                false -> Constants.UPGRADE_DETAILS_BG_OFF
            }
            g.fillRect(x + 68f, boxY.f, 261f, 24f)

            if (!canHaveLevel) {
                continue
            }

            // Only draw the prices for levels the player can buy or refund.
            if (!hasLevel || undoable) {
                scrapIcon.draw(x + 68, boxY)

                val price = system.upgradeCost.getOrElse(i - 1) { -1 }
                numberFont.drawString(x + 98f, boxY + 19f, price.toString(), Color.white)
            }

            val description = system.info!!.getLevelName(i, game.translator)
            systemLevelFont.drawString(x + 142f, boxY + 17f, description, Color.white)
        }

        val offsetY = y + 6 // Due to the ledge at the top of the left side

        // The images are drawn on top of the text
        val leftSideImg = game.getImg("img/upgradeUI/details_base_A.png")
        leftSideImg.draw(x - ShipWindow.GLOW_WIDTH, y - ShipWindow.GLOW_WIDTH)

        val rightSideImg = game.getImg("img/upgradeUI/details_base_C.png")
        val rightX = x + totalWidth + ShipWindow.GLOW_WIDTH - rightSideImg.width
        rightSideImg.draw(rightX, offsetY - ShipWindow.GLOW_WIDTH)

        val middleImg = game.getImg("img/upgradeUI/details_base_B.png")
        val rightSideOfLeftImg = x - ShipWindow.GLOW_WIDTH + leftSideImg.width
        middleImg.draw(
            rightSideOfLeftImg.f, offsetY.f - ShipWindow.GLOW_WIDTH,
            rightX.f, offsetY.f - ShipWindow.GLOW_WIDTH + middleImg.height,
            0f, 0f, middleImg.width.f, middleImg.height.f
        )

        // Finally, draw on the energy bars
        for (i in 0 until system.maxPower) {
            val hasLevel = i < energyLevels
            val undoable = hasLevel && i >= maxNonUndoable

            g.color = when {
                undoable -> Constants.SYS_ENERGY_PURCHASE_UNDOABLE
                hasLevel -> Constants.SYS_ENERGY_ACTIVE
                else -> Constants.UPGRADE_DETAILS_POWER_OFF
            }
            g.fillRect(x + 20f, y.f + 195 - i * 26, 28f, 18f)
        }
    }
}
