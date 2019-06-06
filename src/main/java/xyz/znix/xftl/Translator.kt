package xyz.znix.xftl

class Translator(df: Datafile, lang: String) {
    val translations: Map<String, String>

    init {
        val filename = when (lang) {
            "en" -> "data/text_blueprints.xml"
            "zh" -> "data/text-zh-Hans.xml"
            else -> "data/text-$lang.xml"
        }
        val doc = df.parseXML(df[filename])

        translations = HashMap(doc.rootElement.children.size)

        for (elem in doc.rootElement.children) {
            check(elem.name == "text")
            translations[elem.getAttributeValue("name")] = elem.textTrim
        }
    }

    operator fun get(key: String): String {
        return translations[key] ?: throw IllegalArgumentException("Missing translation key $key")
    }
}
