package xyz.znix.xftl.systems

import org.jdom2.Element

class Engines(blueprint: SystemBlueprint, elem: Element) : MainSystem(blueprint, elem) {
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

        when {
            enginesOn && !lastEnginesOn -> enginesOnSound.play()
            !enginesOn && lastEnginesOn -> enginesOffSound.play()
        }

        lastEnginesOn = enginesOn
    }

    companion object {
        // https://ftl.fandom.com/wiki/Engines
        private val evasions: Array<Int> = arrayOf(0, 5, 10, 15, 20, 25, 28, 31, 35)
        private val chargeRates = arrayOf(0f, 1f, 1.26f, 1.58f, 1.84f, 2.11f, 2.4f, 2.68f, 2.96f)

        const val NAME = "engines"
    }
}
