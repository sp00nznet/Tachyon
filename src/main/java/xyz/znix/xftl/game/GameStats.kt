package xyz.znix.xftl.game

import org.jdom2.Element

/**
 * Per-run statistics, accumulated over a playthrough and shown on the
 * game-over score screen.
 */
class GameStats {
    /** Total scrap the player has collected (gains only - spending doesn't count). */
    var scrapCollected: Int = 0

    /** Number of hostile ships the player has destroyed. */
    var shipsDestroyed: Int = 0

    /** Number of beacons the player has jumped to. */
    var beaconsExplored: Int = 0

    fun recordShipDestroyed() {
        shipsDestroyed++
    }

    fun recordBeaconExplored() {
        beaconsExplored++
    }

    /**
     * Compute the final score from the run's stats. Winning doubles it.
     */
    fun computeScore(sectorsVisited: Int, won: Boolean): Int {
        var score = scrapCollected
        score += shipsDestroyed * SHIP_POINTS
        score += beaconsExplored * BEACON_POINTS
        score += sectorsVisited * SECTOR_POINTS
        if (won)
            score *= WIN_MULTIPLIER
        return score
    }

    fun saveToXML(): Element {
        val elem = Element("gameStats")
        elem.setAttribute("scrapCollected", scrapCollected.toString())
        elem.setAttribute("shipsDestroyed", shipsDestroyed.toString())
        elem.setAttribute("beaconsExplored", beaconsExplored.toString())
        return elem
    }

    fun loadFromXML(elem: Element?) {
        if (elem == null)
            return
        scrapCollected = elem.getAttributeValue("scrapCollected")?.toIntOrNull() ?: 0
        shipsDestroyed = elem.getAttributeValue("shipsDestroyed")?.toIntOrNull() ?: 0
        beaconsExplored = elem.getAttributeValue("beaconsExplored")?.toIntOrNull() ?: 0
    }

    companion object {
        private const val SHIP_POINTS = 15
        private const val BEACON_POINTS = 4
        private const val SECTOR_POINTS = 25
        private const val WIN_MULTIPLIER = 2
    }
}
