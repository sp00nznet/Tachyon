package xyz.znix.xftl.weapons

import xyz.znix.xftl.Datafile

class WeaponDict(df: Datafile) {
    val blueprints: Map<String, AbstractWeaponBlueprint>

    init {
        val doc = df.parseXML(df["data/blueprints.xml"])
        val weaponElems = doc.rootElement.getChildren("weaponBlueprint")

        blueprints = HashMap()

        for (elem in weaponElems) {
            val type = elem.getChildren("type")[0].textTrim

            // Anything without a cooldown is a drone weapon, skip it
            if (elem.getChild("cooldown") == null)
                continue

            val weapon: AbstractWeaponBlueprint = when (type) {
                "LASER" -> LaserBlueprint(elem)
                "MISSILES" -> MissileBlueprint(elem)
                "BEAM" -> BeamBlueprint(elem)
                "BOMB" -> BombBlueprint(elem)
                else -> null
            } ?: continue

            blueprints[weapon.name] = weapon
        }
    }
}
