package xyz.znix.xftl.crew

import xyz.znix.xftl.Datafile
import kotlin.random.Random

/**
 * Stores all the available crew names, from names.xml.
 */
class CrewNameManager(df: Datafile) {
    val names: List<CrewNameList>

    init {
        names = ArrayList()

        val root = df.parseXML(df["data/names.xml"]).rootElement

        for (child in root.children) {
            require(child.name == "nameList") { "Found non-nameList element in names.xml root" }

            val listNames = ArrayList<String>()

            for (nameElem in child.children) {
                require(nameElem.name == "name") { "Found non-name element in nameList (in names.xml)" }
                listNames.add(nameElem.textTrim)
            }

            val isMale = child.getAttributeValue("sex") == "male"
            val language = child.getAttributeValue("language") ?: "en"

            names.add(CrewNameList(listNames, isMale, language))
        }
    }

    fun getForGender(male: Boolean?, language: String, rand: Random): String {
        val lists = names.filter {
            // Filter for the correct language
            if (it.language != language)
                return@filter false

            // If a gender is specified, filter on that
            if (male != null) {
                if (male != it.isMale)
                    return@filter false
            }

            return@filter true
        }

        return lists.random(rand).names.random(rand)
    }
}

class CrewNameList(val names: List<String>, val isMale: Boolean, val language: String)
