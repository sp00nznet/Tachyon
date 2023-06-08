package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.SystemInfo
import xyz.znix.xftl.savegame.ObjectRefs
import xyz.znix.xftl.savegame.RefLoader

class Engines(blueprint: SystemBlueprint) : MainSystem(blueprint) {
    override val sortingType: SortingType get() = SortingType.ENGINES

    private val enginesOnSound by onInit { it.sounds.getSample("enginesOn") }
    private val enginesOffSound by onInit { it.sounds.getSample("enginesOff") }

    // TODO add crew evasion and charge bonus
    val evasion: Int get() = evasions[powerSelected]
    val chargeRate: Float get() = chargeRates[powerSelected]

    val evasionMultiplier: Float
        get() {
            // If engines is off or hacked, you get no evasion
            if (powerSelected == 0 || isHackActive)
                return 0f

            // TODO skills

            return 1f
        }

    private var lastEnginesOn = false

    override fun powerStateChanged() {
        super.powerStateChanged()

        // Play the sound effect when the engines are turned on and off
        val enginesOn = powerSelected > 0

        // Don't play sounds while we're being deserialised
        if (ship.sys.isCurrentlyLoadingSave) {
            lastEnginesOn = enginesOn
            return
        }

        when {
            enginesOn && !lastEnginesOn -> enginesOnSound.play()
            !enginesOn && lastEnginesOn -> enginesOffSound.play()
        }

        lastEnginesOn = enginesOn
    }

    // Nothing to serialise
    override fun saveSystem(elem: Element, refs: ObjectRefs) = Unit
    override fun loadSystem(elem: Element, refs: RefLoader) = Unit

    companion object {
        // https://ftl.fandom.com/wiki/Engines
        private val evasions: Array<Int> = arrayOf(0, 5, 10, 15, 20, 25, 28, 31, 35)
        private val chargeRates = arrayOf(0f, 1f, 1.26f, 1.58f, 1.84f, 2.11f, 2.4f, 2.68f, 2.96f)

        val INFO: SystemInfo = EngineInfo
    }
}

private object EngineInfo : SystemInfo("engines") {
    override val canBeManned: Boolean get() = true

    override fun create(blueprint: SystemBlueprint) = Engines(blueprint)
}
