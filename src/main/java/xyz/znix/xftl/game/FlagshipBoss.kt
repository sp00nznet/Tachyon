package xyz.znix.xftl.game

import org.jdom2.Element
import xyz.znix.xftl.*
import xyz.znix.xftl.crew.AbstractCrew
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrewInfo.Companion.generateRandom
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.math.IPoint
import xyz.znix.xftl.rendering.Colour
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader
import xyz.znix.xftl.savegame.SaveUtil
import xyz.znix.xftl.sector.Beacon
import xyz.znix.xftl.sector.Event
import xyz.znix.xftl.sector.Sector
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random.Default.nextInt

class FlagshipBoss private constructor(val sector: Sector, val game: InGameState) : BossManager {
    override val serialisationType: String get() = SERIALISATION_TYPE

    override val useBossShipUI: Boolean get() = true
    override val beacon: Beacon get() = realBeacon
    override var nextBeacon: Beacon? = null
        private set
    override var jumping: Boolean = false
        private set

    // Can't make beacon lateinit since the parent is nullable
    private lateinit var realBeacon: Beacon

    private var runningAway: Boolean = false
    private var stage: Int = 1

    private var mapIconRotation: Float = (0f..10f).random(VisualRandom)
    private val flagshipIcon = game.getImg("img/map/map_icon_boss.png")

    constructor(sector: Sector, game: InGameState, start: Beacon) : this(sector, game) {
        realBeacon = start

        updateNextBeacon()
    }

    override fun createShip(): Ship {
        // The ship name is in the format of BOSS_1_NORMAL_DLC
        var shipName = String.format("BOSS_%d_%s", stage, game.difficulty)
        if (game.content.enableAdvancedEdition) {
            shipName += "_DLC"
        }

        val blueprint: ShipBlueprint = game.blueprintManager.getShip(shipName)
        val flagship = Ship(blueprint, game, null, null, this)
        flagship.loadDefaultContents()

        // TODO handle crew being killed across fights
        for (i in 0..10) {
            val info = generateRandom(game.blueprintManager["human"] as CrewBlueprint, game)
            val crew = flagship.addCrewMember(info, true, false)

            // Put some crew in the artillery rooms
            for (artillery in flagship.artillery) {
                val room = Objects.requireNonNull(artillery.room)

                val anyInRoom = flagship.crew.stream().anyMatch { c: AbstractCrew -> c.room === room }
                if (anyInRoom) continue

                crew.jumpTo(room!!, ConstPoint.ZERO)
            }
        }

        // Copied from ShipGenerator, required for stage 1
        if (flagship.dronesCount == 0 && flagship.hacking != null) {
            flagship.dronesCount = 5
        }

        val event: Event = game.eventManager["BOSS_TEXT_$stage"].resolve()
        game.shipUI.showEventDialogue(event, nextInt())

        return flagship
    }

    override fun drawMapIcon(g: Graphics, centre: IPoint) {
        val rotationSpeed = TWO_PI / 20f // Go around every 20 seconds
        mapIconRotation += game.renderingDeltaTime * rotationSpeed

        // These offsets are approximate.
        g.pushTransform()
        g.rotate(centre.x.f, centre.y.f, -mapIconRotation)
        flagshipIcon.draw(centre.x - 8, centre.y - 32)
        g.popTransform()
    }

    override fun advanceFleet() {
        if (!jumping) {
            // Jump every second turn, this turn wasn't a jump.
            // (except if the flagship doesn't want to jump, when
            //  it's at the base - this is when the next beacon is null.)
            updateNextBeacon()
            jumping = nextBeacon != null
        } else {
            realBeacon = nextBeacon!!
            updateNextBeacon()

            // Don't jump again next turn
            jumping = false

            // If we were running away, we're allowed to go back to the base again.
            runningAway = false
        }
    }

    override fun drawJumpArc(g: Graphics, from: IPoint, to: IPoint) {
        val width = 10f
        val angle = atan2(from.y.f - to.y, from.x.f - to.x)
        val tangentX = cos(angle + PIf / 2f) * width / 2
        val tangentY = sin(angle + PIf / 2f) * width / 2

        g.drawCustomQuads { renderer ->
            renderer.pushVert(from.x + tangentX, from.y + tangentY, Constants.BEACON_LINE_FLAGSHIP)
            renderer.pushVert(from.x - tangentX, from.y - tangentY, Constants.BEACON_LINE_FLAGSHIP)
            renderer.pushVert(to.x - tangentX, to.y - tangentY, Constants.BEACON_LINE_FLAGSHIP)
            renderer.pushVert(to.x + tangentX, to.y + tangentY, Constants.BEACON_LINE_FLAGSHIP)
        }

        // Draw the animated flagship on top of it.
        // Note these numbers are approximate.
        val period = 1_500_000_000
        val progress: Float = (System.nanoTime() % period).toFloat() / period
        val movement = 20f + progress * 20f
        val alpha = min(1f, 2f - progress * 2f)

        g.pushTransform()
        g.rotate(from.x.f, from.y.f, Math.toDegrees(angle.toDouble()).toFloat() - 90f)
        flagshipIcon.draw(from.x - 32f, from.y - 32f - movement, Colour(1f, 1f, 1f, alpha))
        g.popTransform()
    }

    override fun bossShipKilled(enemy: Ship) {
        updateNextBeacon()

        // If we're not at the base, continue on our path there.
        // We have to set the is-jumping flag, otherwise the flagship
        // could wait at the same beacon as the player.
        if (nextBeacon != null) {
            jumping = true
        } else {
            // The flagship is at the base, force it to jump away.
            val adjacent: List<Beacon> = beacon.neighbours
            nextBeacon = adjacent[nextInt(adjacent.size)]
            runningAway = true
            jumping = true
        }

        if (stage == 3) {
            game.shipUI.showGameOverScreen(GameOverWindow.Outcome.WIN)
        } else {
            stage++

            // The event when you defeat a stage
            val event: Event = game.eventManager["BOSS_ESCAPED"].resolve()
            game.shipUI.showEventDialogue(event, nextInt())
        }
    }

    private fun updateNextBeacon() {
        // If the flagship is running away, don't touch its next beacon.
        // That'll have been set when it was marked as running away.
        if (runningAway) {
            return
        }

        // If the flagship is at the rebel base, it'll stay there.
        if (beacon == sector.finishBeacon) {
            nextBeacon = null
            return
        }

        val path = sector.findShortestPath(beacon, sector.finishBeacon)
        require(path != null) { "Flagship has no path to the federation base!" }

        nextBeacon = path.first()
    }

    override fun saveToXML(elem: Element, refs: ObjectRefs) {
        SaveUtil.addAttrRef(elem, "beacon", refs, beacon)
        SaveUtil.addAttrRef(elem, "next", refs, nextBeacon)
        SaveUtil.addAttrBool(elem, "jumping", jumping)
        SaveUtil.addAttrBool(elem, "runningAway", runningAway)
        SaveUtil.addAttrInt(elem, "stage", stage)
    }

    override fun loadFromXML(elem: Element, refs: RefLoader) {
        SaveUtil.getAttrRef(elem, "beacon", refs, Beacon::class.java) { realBeacon = it!! }
        SaveUtil.getAttrRef(elem, "next", refs, Beacon::class.java) { nextBeacon = it }
        jumping = SaveUtil.getAttrBool(elem, "jumping")
        runningAway = SaveUtil.getAttrBool(elem, "runningAway")
        stage = SaveUtil.getAttrInt(elem, "stage")
    }

    companion object {
        const val SERIALISATION_TYPE = "wormhole.flagship"

        // Force the deserialiser to use this function rather than making the constructor public,
        // as it makes it very clear that this function isn't supposed to be used for anything ele.
        fun createForDeserialisation(sector: Sector, game: InGameState): FlagshipBoss {
            return FlagshipBoss(sector, game)
        }
    }
}
