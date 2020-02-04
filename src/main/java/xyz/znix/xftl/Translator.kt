package xyz.znix.xftl

class Translator(df: Datafile, lang: String) {
    val translations: Map<String, String>

    init {
        translations = HashMap()
        fun load(name: String) = parseFile(name, df, translations)

        when (lang) {
            "en" -> {
                load("data/text_achievements.xml")
                load("data/text_blueprints.xml")
                load("data/text_events.xml")
                load("data/text_misc.xml")
                load("data/text_sectorname.xml")
                load("data/text_tooltips.xml")
                load("data/text_tutorial.xml")
            }
            "zh" -> load("data/text-zh-Hans.xml")
            else -> load("data/text-$lang.xml")
        }
    }

    private fun parseFile(filename: String, df: Datafile, tl: HashMap<String, String>) {
        val doc = df.parseXML(df[filename])

        for (elem in doc.rootElement.children) {
            check(elem.name == "text")
            tl[elem.getAttributeValue("name")] = elem.textTrim
        }
    }

    operator fun get(key: String): String {
        return translations[key] ?: throw IllegalArgumentException("Missing translation key $key")
    }
}
