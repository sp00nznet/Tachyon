package xyz.znix.xftl

import org.jdom2.Element

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
            tl[elem.getAttributeValue("name")] = elem.textTrim.replace("\\n", "\n")
        }
    }

    operator fun get(key: String): String {
        return translations[key] ?: throw IllegalArgumentException("Missing translation key $key")
    }

    /**
     * Convenience helper for [GameText.get].
     */
    operator fun get(text: GameText): String {
        return text.get(this)
    }
}

/**
 * Represents a string parsed from the XML files that may or may not be localised.
 */
class GameText private constructor(private val literal: String?, private val key: String?) {

    fun get(translator: Translator): String {
        if (literal != null) {
            return literal
        }

        return translator[key!!]
    }

    companion object {
        fun parse(elem: Element): GameText {
            val id: String? = elem.getAttributeValue("id")
            if (id != null) {
                return GameText(null, id)
            }

            return GameText(elem.textTrim, null)
        }

        fun localised(key: String): GameText {
            return GameText(null, key)
        }
    }
}

fun Element.getGameTextChild(name: String): GameText? {
    val elem = this.getChild(name) ?: return null
    return GameText.parse(elem)
}
