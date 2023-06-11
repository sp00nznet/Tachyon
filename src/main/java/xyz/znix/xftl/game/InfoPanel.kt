package xyz.znix.xftl.game

import org.newdawn.slick.Color
import xyz.znix.xftl.*
import xyz.znix.xftl.augments.AugmentBlueprint
import xyz.znix.xftl.crew.LivingCrewInfo
import xyz.znix.xftl.crew.Skill
import xyz.znix.xftl.crew.SkillLevel
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.systems.SystemBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.DroneBlueprint
import xyz.znix.xftl.weapons.LaserBlueprint
import kotlin.math.max

// TODO vertically offset all the non-system boxes correctly

class InfoPanel(private val game: InGameState) {
    private val systemLevelFont = game.getFont("JustinFont10")
    private val numberFont = game.getFont("num_font")
    private val titleFont = game.getFont("c&cnew", 2f)
    private val descriptionFont = game.getFont("JustinFont10")
    private val tipFont = game.getFont("JustinFont8")

    var position: IPoint = ConstPoint.ZERO

    fun drawAugment(blueprint: AugmentBlueprint) {
        drawDescriptionBox(blueprint.title, blueprint.desc, null, emptyList(), INFO_HEIGHT_AUGMENT)
    }

    fun drawItem(blueprint: ItemBlueprint) {
        drawDescriptionBox(blueprint.title, blueprint.desc, null, emptyList(), INFO_HEIGHT_ITEM)
    }

    fun drawDrone(blueprint: DroneBlueprint) {
        val lines = ArrayList<String>()
        lines += game.translator["required_power"].replace("\\1", blueprint.power.toString())
        lines += game.translator["drone_required"]
        drawDescriptionBox(blueprint.title, blueprint.desc, blueprint.tip, lines, INFO_HEIGHT_DRONE)
    }

    fun drawWeapon(blueprint: AbstractWeaponBlueprint) {
        val lines = ArrayList<String>()

        // Build all the weapon attributes that appear when hovering over it
        // TODO support hull missiles

        lines += game.translator["required_power"].replace("\\1", blueprint.power.toString())
        lines += game.translator["charge_time"].replace("\\1", UIUtils.formatFloat(blueprint.chargeTime))
        if (blueprint.boost?.type == AbstractWeaponBlueprint.BoostType.COOLDOWN) {
            val maxBoost = blueprint.boost.maxCount * blueprint.boost.perShot
            lines += game.translator["boost_power_speed"]
            lines += game.translator["speed_cap"].replace("\\1", maxBoost.toString())
        }
        if (blueprint.boost?.type == AbstractWeaponBlueprint.BoostType.DAMAGE) {
            // We have to include the initial damage in the max value shown.
            // How does vanilla FTL do this?
            val baseDamage = max(blueprint.damage, blueprint.ionDamage)
            val maxDamage = blueprint.boost.maxCount * blueprint.boost.perShot + baseDamage
            lines += game.translator["boost_power_damage"]
            lines += game.translator["damage_cap"].replace("\\1", maxDamage.toString())
        }
        if (blueprint.missilesUsed > 0) {
            lines += game.translator["requires_missiles"]
        }
        if (blueprint.shots != 1 || blueprint is LaserBlueprint) {
            lines += game.translator["shots"].replace("\\1", blueprint.shots.toString())
        }
        if (blueprint.chargeLevels != null) {
            lines += game.translator["charge"].replace("\\1", blueprint.chargeLevels.toString())
        }
        if (blueprint is BeamBlueprint) {
            lines += game.translator["damage_room"].replace("\\1", blueprint.damage.toString())
        } else {
            lines += game.translator["damage_shot"].replace("\\1", blueprint.damage.toString())
        }
        if (blueprint.shieldPiercing != 0) {
            lines += game.translator["shield_piercing"].replace("\\1", blueprint.shieldPiercing.toString())
        }
        addChanceString(lines, "fire_chance", blueprint.fireChance)
        addChanceString(lines, "breach_chance", blueprint.breachChance)
        addChanceString(lines, "stun_chance", blueprint.stunChance)
        if (blueprint.ionDamage != 0) {
            lines += game.translator["ion_damage"].replace("\\1", blueprint.ionDamage.toString())
        }
        if (blueprint.personnelDamage != null) {
            // Damage is specified in multiples of 15
            val hpDamage = blueprint.personnelDamage * 15
            lines += game.translator["personnel_damage"].replace("\\1", hpDamage.toString())
        }
        if (blueprint.sysDamage != blueprint.damage) {
            lines += game.translator["system_damage"].replace("\\1", blueprint.sysDamage.toString())
        }

        drawDescriptionBox(blueprint.title, blueprint.desc, blueprint.tip, lines, INFO_HEIGHT_WEAPON)
    }

    fun drawCrew(g: Graphics, info: LivingCrewInfo) {
        val raceName = game.translator[info.race.title!!]
        val title = GameText.literal("${info.name} ($raceName)")
        val powers = info.race.powerStringIds.map { "-" + game.translator[it] }
        drawDescriptionBox(title, info.race.desc, null, powers, INFO_HEIGHT_CREW)

        // Draw the skills box
        val skillsY = position.y + INFO_HEIGHT_CREW + 24
        game.windowRenderer.render(position.x, skillsY, 323, 242)

        val leftIconX = position.x + 9
        val rightIconX = position.x + 164

        titleFont.drawString(position.x + 28f, skillsY + 32f, game.translator["crew_skills"], Color.white)

        for ((index, skill) in Skill.values().withIndex()) {
            val row = index / 2
            val iconX = if (index % 2 == 0) leftIconX else rightIconX
            val iconY = skillsY + 46 + row * 66

            val iconColour = when (info.getSkillLevel(skill)) {
                SkillLevel.MAX -> Constants.SYS_ENERGY_REPAIR
                SkillLevel.PARTIAL -> Constants.SYS_ENERGY_ACTIVE
                else -> Constants.UI_BACKGROUND_GLOW_COLOUR
            }
            val icon = game.getImg(skill.iconPath)
            icon.draw(iconX, iconY, iconColour)

            // Draw the skill bar
            info.drawSkillProgressBar(g, iconX + 30, iconY + 8, 110, 9, skill)

            // Draw the description text
            val baseDescription = info.getSkillDescription(game, skill)
            val skillLocName = when (skill) {
                Skill.PILOTING -> "pilot"
                Skill.ENGINES -> "engines"
                Skill.SHIELDS -> "shields"
                Skill.WEAPONS -> "weapons"
                Skill.REPAIRS -> "repair"
                Skill.COMBAT -> "combat"
            }
            val description = game.translator[skillLocName + "_skill_infobox"].replaceArg(baseDescription)
            val lines = tipFont.wrapString(description, 140)
            val textCentreX = iconX + 85

            for ((i, line) in lines.withIndex()) {
                val lineY = iconY + 32 + i * 15
                tipFont.drawStringCentred(textCentreX.f, lineY.f, 0f, line, Color.white)
            }
        }
    }

    /**
     * Note: this does not render the system power bars!
     */
    fun drawDescriptionBoxSystem(blueprint: SystemBlueprint) {
        drawDescriptionBox(blueprint.title, blueprint.desc, null, emptyList(), INFO_HEIGHT_SYSTEM)
    }

    fun drawDescriptionBox(
        title: GameText?,
        description: GameText?,
        tip: GameText?,
        extraLines: List<String>,
        height: Int
    ) {
        game.windowRenderer.render(position.x, position.y, 333, height)

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

            // Draw the extra lines, which are for stuff like weapon attributes.
            y += 17
            for (line in extraLines) {
                descriptionFont.drawString(position.x + 11f, y.f, line, Color.white)
                y += 17
            }
        }

        if (tip != null) {
            val tipStr = game.translator[tip]
            val tipLines = tipFont.wrapString(tipStr, 303)

            val tipY = position.y + height + 17
            val tipHeight = 31 + tipLines.size * 15

            game.windowRenderer.render(position.x, tipY, 333, tipHeight)

            var lineY = tipY + 27
            for (line in tipLines) {
                tipFont.drawString(position.x + 13f, lineY.f, line, Color.white)
                lineY += 15
            }
        }
    }

    fun drawSystemPowerBox(g: Graphics, system: SystemBlueprint, energyLevels: Int, undoablePower: Int) {
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

    private fun addChanceString(lines: ArrayList<String>, baseKey: String, chance: Int) {
        // Always look this up to quickly catch invalid keys
        val baseStr = game.translator[baseKey]

        if (chance == 0) {
            return
        }

        val chanceKey = when (chance) {
            in 1..3 -> "chance_low"
            in 4..6 -> "chance_medium"
            else -> "chance_high"
        }

        val chanceStr = game.translator[chanceKey]
        lines += baseStr.replace("\\1", chanceStr)
    }

    companion object {
        const val INFO_HEIGHT_SYSTEM = 121
        const val INFO_HEIGHT_AUGMENT = 136
        const val INFO_HEIGHT_CREW = 168
        const val INFO_HEIGHT_ITEM = 121
        const val INFO_HEIGHT_DRONE = 162
        const val INFO_HEIGHT_WEAPON = 252
    }
}
