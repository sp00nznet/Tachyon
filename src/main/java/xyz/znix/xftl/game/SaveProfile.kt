package xyz.znix.xftl.game

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents the user's profile: achievements/ships/high scores/settings/etc.
 *
 * Unlike the game state saves, this is supposed to be durable, as it'd be
 * much, much worse to lose it than a savegame.
 */
class SaveProfile private constructor() {
    /**
     * Store the unlocked achievements by ID, rather than using [Achievement]
     * objects, so that profiles don't get broken if you add or remove a mod.
     */
    private val unlockedAchievements = HashMap<String, AchievementUnlockInfo>()

    /**
     * Stores the ships (indexed by their unlockShip id) which the player
     * has completed the unlock quest for.
     *
     * This re-uses the AchievementUnlockInfo class, since we need to track
     * the difficulty the quests were completed on to show in the UI.
     */
    private val unlockedShips = HashMap<String, AchievementUnlockInfo>()

    /**
     * Set to true if this profile needs saving.
     */
    var dirty = false
        private set

    fun getAchievement(ach: Achievement): AchievementUnlockInfo? {
        return unlockedAchievements[ach.id]
    }

    /**
     * Remove an achievement, if it's unlocked. This is intended for debugging.
     */
    fun deleteAchievement(ach: Achievement) {
        unlockedAchievements.remove(ach.id)
        dirty = true
    }

    fun unlockAchievement(ach: Achievement, difficulty: Difficulty) {
        val current = unlockedAchievements[ach.id]

        // If we've already unlocked the achievement on a harder difficulty,
        // don't overwrite it with the easier unlock.
        if (current != null && current.difficulty.ordinal <= difficulty.ordinal) {
            return
        }

        unlockedAchievements[ach.id] = AchievementUnlockInfo(difficulty)
        dirty = true
    }

    fun getShipUnlock(shipFamily: ShipFamily): AchievementUnlockInfo? {
        requireNotNull(shipFamily.unlockId)
        return unlockedShips[shipFamily.unlockId]
    }

    fun deleteShipUnlock(shipFamily: ShipFamily) {
        requireNotNull(shipFamily.unlockId)
        unlockedShips.remove(shipFamily.unlockId)
        dirty = true
    }

    fun unlockShip(shipFamily: ShipFamily, difficulty: Difficulty) {
        requireNotNull(shipFamily.unlockId)

        val current = unlockedShips[shipFamily.unlockId]

        // If we've already unlocked the achievement on a harder difficulty,
        // don't overwrite it with the easier unlock.
        if (current != null && current.difficulty.ordinal <= difficulty.ordinal) {
            return
        }

        unlockedShips[shipFamily.unlockId] = AchievementUnlockInfo(difficulty)
        dirty = true
    }

    fun save(): Document {
        val doc = Document(Element("xftl-profile"))
        val root = doc.rootElement

        for ((id, info) in unlockedAchievements) {
            val elem = Element("ach")
            root.addContent(elem)

            elem.setAttribute("id", id)
            elem.setAttribute("diff", info.difficulty.name)
        }

        for ((id, info) in unlockedShips) {
            val elem = Element("ship")
            root.addContent(elem)

            elem.setAttribute("unlockId", id)
            elem.setAttribute("diff", info.difficulty.name)
        }

        return doc
    }

    fun markSaveComplete() {
        dirty = false
    }

    private fun load(doc: Document) {
        val root = doc.rootElement
        check(root.name == "xftl-profile")

        for (elem in root.getChildren("ach")) {
            val id = elem.getAttributeValue("id")
            val difficultyName = elem.getAttributeValue("diff")

            unlockedAchievements[id] = AchievementUnlockInfo(
                Difficulty.valueOf(difficultyName)
            )
        }

        for (elem in root.getChildren("ship")) {
            val id = elem.getAttributeValue("unlockId")
            val difficultyName = elem.getAttributeValue("diff")

            unlockedShips[id] = AchievementUnlockInfo(
                Difficulty.valueOf(difficultyName)
            )
        }
    }

    class AchievementUnlockInfo(val difficulty: Difficulty)

    companion object {
        @JvmField
        val PROFILE_NAME = "save-profile.xml"

        // Make it a bit more explicit what this is doing
        @JvmStatic
        fun createBlank(): SaveProfile {
            val profile = SaveProfile()

            // Immediately save the empty profile, so there's at least something there.
            profile.dirty = true

            return profile
        }

        @JvmStatic
        fun load(path: Path): SaveProfile? {
            val profile = SaveProfile()

            val doc: Document = try {
                Files.newBufferedReader(path).use { reader ->
                    val builder = SAXBuilder()
                    builder.setExpandEntities(true)
                    builder.build(reader)
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
                return null
            } catch (ex: JDOMException) {
                ex.printStackTrace()
                return null
            }

            profile.load(doc)
            return profile
        }
    }
}
