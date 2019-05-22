package xyz.znix.xftl.weapons

import xyz.znix.xftl.Datafile

class WeaponDict(df: Datafile) {
    val blueprints: Map<String, AbstractWeaponBlueprint>

    init {
        val doc = df.parseXML(df["data/blueprints.xml"])
        val weaponElems = doc.rootElement.getChildren("weaponBlueprint")

        val mutableWeapons = HashMap<String, AbstractWeaponBlueprint>()
        blueprints = mutableWeapons

        for (elem in weaponElems) {
            val type = elem.getChildren("type")[0].textTrim

            val weapon: AbstractWeaponBlueprint = when (type) {
                "LASER" -> LaserBlueprint(elem)
                "MISSILES" -> MissileBlueprint(elem)
                else -> null
            } ?: continue

            mutableWeapons[weapon.name] = weapon
        }
    }
}
