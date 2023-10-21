package xyz.znix.xftl

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder

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

        // Put a when block here if/when our strings are translated.
        parseEmbedded("assets/lang/en.xml", translations)
    }

    private fun parseFile(filename: String, df: Datafile, tl: HashMap<String, String>) {
        val doc = df.parseXML(df[filename])
        parseDoc(filename, doc, tl)
    }

    private fun parseEmbedded(path: String, tl: HashMap<String, String>) {
        val builder = SAXBuilder()
        builder.expandEntities = false
        val doc = builder.build(javaClass.classLoader.getResourceAsStream(path)!!)

        parseDoc(path, doc, tl)
    }

    private fun parseDoc(fileName: String, doc: Document, tl: HashMap<String, String>) {
        for (elem in doc.rootElement.children) {
            if (elem.name != "text") {
                println("[WARN] Found invalid element '${elem.name}' in translation file '$fileName'")
                continue
            }

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

        fun literal(string: String): GameText {
            return GameText(string, null)
        }
    }
}

fun Element.getGameTextChild(name: String): GameText? {
    val elem = this.getChild(name) ?: return null
    return GameText.parse(elem)
}
