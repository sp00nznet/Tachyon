package xyz.znix.xftl

import org.newdawn.slick.SpriteSheet
import org.w3c.dom.Element

class Animations(df: Datafile) {
    private val sheets: Map<String, SpriteSheet>
    val animations: Map<String, AnimationSpec>

    init {
        val mutableSheets: MutableMap<String, SpriteSheet> = HashMap()
        sheets = mutableSheets

        val doc = df.parseXML(df["data/animations.xml"])

        // Parse the sheets
        val sheets = doc.getElementsByTagName("animSheet")
        for (i in 0..sheets.length) {
            val elem = sheets.item(i) as? Element ?: continue

            val name = elem.getAttribute("name")

            if (this.sheets.containsKey(name)) {
                System.out.println("Warning: duplicate spritesheet $name, using first one")
                continue
            }

            // These have image sizes which don't match the XML, skip them for now
            if (name == "bomb_1")
                continue
            if (name == "artillery_fed")
                continue
            if (name == "explosion_big1")
                continue

            // The filename is the text inside the element
            val img = df.readImage(df["img/${elem.textContent}"])

            // Make sure the texture size is correct
            check(elem.getAttribute("w").toInt() == img.width)
            check(elem.getAttribute("h").toInt() == img.height)

            val frame_width = elem.getAttribute("fw").toInt()
            val frame_height = elem.getAttribute("fh").toInt()

            val sheet = SpriteSheet(img, frame_width, frame_height)

            mutableSheets[name] = sheet
        }

        val mutableAnimations: MutableMap<String, AnimationSpec> = HashMap()
        animations = mutableAnimations

        val animsXMl = doc.getElementsByTagName("anim")
        for (i in 0..animsXMl.length) {
            val xml = animsXMl.item(i) as? Element ?: continue

            val name = xml.getAttribute("name")

            val sheetName = xml.getElementsByTagName("sheet").item(0).textContent

            // Skip the broken animation files
            if (sheetName == "bomb_1")
                continue
            if (sheetName == "artillery_fed")
                continue
            if (sheetName == "explosion_big1")
                continue

            val sheet = this.sheets[sheetName] ?: throw IllegalStateException("Unknown sheet $sheetName")

            val desc = xml.getElementsByTagName("desc").item(0) as Element

            val length = desc.getAttribute("length").toInt()
            val x = desc.getAttribute("x").toInt()
            val y = sheet.verticalCount - desc.getAttribute("y").toInt() - 1

            // Convert from per-cycle to per-frame
            val time = xml.getElementsByTagName("time").item(0).textContent.toFloat() / length

            animations[name] = AnimationSpec(sheet, x, y, length, time)
        }
    }

    operator fun get(name: String): AnimationSpec {
        return animations[name] ?: throw IllegalArgumentException("Cannot find animation $name")
    }
}