package xyz.znix.xftl.game

import org.newdawn.slick.Image
import xyz.znix.xftl.Blueprint
import xyz.znix.xftl.crew.CrewBlueprint
import xyz.znix.xftl.crew.LivingCrew
import xyz.znix.xftl.sector.AddCrew
import xyz.znix.xftl.sector.EventHullDamage
import xyz.znix.xftl.sector.EventSystemUpgrade
import xyz.znix.xftl.sector.RemoveCrew

enum class Resource {
    FUEL,
    MISSILES,
    DRONES,
    SCRAP;

    private var image: Image? = null

    fun getIcon(game: SlickGame): Image {
        image?.let { return it }

        val img = game.getImg("img/ui_icons/icon_${name.toLowerCase()}.png")
        image = img
        return img
    }

    companion object {
        fun byName(name: String): Resource = when (name.toLowerCase()) {
            "fuel" -> FUEL
            "missiles" -> MISSILES
            "drones" -> DRONES
            "scrap" -> SCRAP

            // ... and then 'missile' is used once by ROCK_STARSHIP_MINE
            "missile" -> MISSILES

            else -> throw IllegalArgumentException("Invalid resource name $name")
        }
    }
}

class ResourceSet() : Map<Resource, Int> {
    var fuel: Int = 0
    var missiles: Int = 0
    var droneParts: Int = 0
    var scrap: Int = 0
    val items = ArrayList<Blueprint>()
    val crew = ArrayList<AddCrewEval>()
    val lostCrew = ArrayList<RemoveCrewEval>()
    val intruders = ArrayList<AddCrewEval>()
    val damage = ArrayList<EventHullDamage>()
    val upgrades = ArrayList<EventSystemUpgrade>()

    // Should this really be here? It's convenient for the dialogue stuff.
    var modifyPursuit: Int = 0

    val hasAnything: Boolean
        get() = isNotEmpty() || items.isNotEmpty() || crew.isNotEmpty() || lostCrew.isNotEmpty()
                || intruders.isNotEmpty() || damage.isNotEmpty() || upgrades.isNotEmpty() || modifyPursuit != 0

    constructor(basicResources: Map<Resource, Int>) : this() {
        for ((k, v) in basicResources) {
            this[k] = v
        }
    }

    override val entries: Set<Map.Entry<Resource, Int>>
        get() = keys.map { res ->
            object : Map.Entry<Resource, Int> {
                override val key: Resource get() = res
                override val value: Int get() = this@ResourceSet[res]!!
            }
        }.toSet()

    override val keys: Set<Resource>
        get() {
            val hs = HashSet<Resource>(size)
            if (fuel != 0) hs.add(Resource.FUEL)
            if (missiles != 0) hs.add(Resource.MISSILES)
            if (droneParts != 0) hs.add(Resource.DRONES)
            if (scrap != 0) hs.add(Resource.SCRAP)
            return hs
        }

    override val size: Int
        get() {
            var count = 0
            if (fuel != 0) count++
            if (missiles != 0) count++
            if (droneParts != 0) count++
            if (scrap != 0) count++
            return count
        }

    override fun containsKey(key: Resource): Boolean = this[key] != null

    override operator fun get(key: Resource): Int? {
        val value = when (key) {
            Resource.FUEL -> fuel
            Resource.MISSILES -> missiles
            Resource.DRONES -> droneParts
            Resource.SCRAP -> scrap
        }

        return if (value == 0) null else value
    }

    operator fun set(key: Resource, value: Int) {
        when (key) {
            Resource.FUEL -> fuel = value
            Resource.MISSILES -> missiles = value
            Resource.DRONES -> droneParts = value
            Resource.SCRAP -> scrap = value
        }
    }

    fun remove(key: Resource) = set(key, 0)

    override fun isEmpty(): Boolean = size == 0

    // Neither of these make sense for this class
    override val values: Collection<Int> get() = error("Not supported, not very useful")
    override fun containsValue(value: Int): Boolean = error("Unimplemented")

    operator fun plusAssign(other: ResourceSet) {
        this.fuel += other.fuel
        this.scrap += other.scrap
        this.droneParts += other.droneParts
        this.missiles += other.missiles
        this.items += other.items
        this.crew += other.crew
        this.lostCrew += other.lostCrew
        this.intruders += other.intruders
        this.damage += other.damage
        this.modifyPursuit += other.modifyPursuit
    }

    companion object {
        fun of(type: Resource, count: Int) = ResourceSet().apply { this[type] = count }
    }
}

/**
 * Evaluated version of [AddCrew].
 */
class AddCrewEval(val race: CrewBlueprint, val name: String)

class RemoveCrewEval(val crew: LivingCrew, val info: RemoveCrew)
