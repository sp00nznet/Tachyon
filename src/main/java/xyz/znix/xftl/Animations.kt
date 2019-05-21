package xyz.znix.xftl

import org.newdawn.slick.SpriteSheet

class Animations(df: Datafile) {
    private val sheets: Map<String, SpriteSheet>
    val animations: Map<String, AnimationSpec>

    init {
        val mutableSheets: MutableMap<String, SpriteSheet> = HashMap()
        sheets = mutableSheets

        val doc = df.parseXML(df["data/animations.xml"])

        // Parse the sheets
        for (elem in doc.rootElement.getChildren("animSheet")) {
            val name = elem.getAttributeValue("name")

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
            val img = df.readImage(df["img/${elem.textTrim}"])

            // Make sure the texture size is correct
            check(elem.getAttributeValue("w").toInt() == img.width)
            check(elem.getAttributeValue("h").toInt() == img.height)

            val frame_width = elem.getAttributeValue("fw").toInt()
            val frame_height = elem.getAttributeValue("fh").toInt()

            val sheet = SpriteSheet(img, frame_width, frame_height)

            mutableSheets[name] = sheet
        }

        val mutableAnimations: MutableMap<String, AnimationSpec> = HashMap()
        animations = mutableAnimations

        for (xml in doc.rootElement.getChildren("anim")) {
            val name = xml.getAttributeValue("name")

            val sheetName = xml.getChild("sheet").textTrim

            // Skip the broken animation files
            if (sheetName == "bomb_1")
                continue
            if (sheetName == "artillery_fed")
                continue
            if (sheetName == "explosion_big1")
                continue

            val sheet = this.sheets[sheetName] ?: throw IllegalStateException("Unknown sheet $sheetName")

            val desc = xml.getChild("desc")

            val length = desc.getAttributeValue("length").toInt()
            val x = desc.getAttributeValue("x").toInt()
            val y = sheet.verticalCount - desc.getAttributeValue("y").toInt() - 1

            // Convert from per-cycle to per-frame
            val time = xml.getChild("time").textTrim.toFloat() / length

            animations[name] = AnimationSpec(sheet, x, y, length, time)
        }
    }

    operator fun get(name: String): AnimationSpec {
        return animations[name] ?: throw IllegalArgumentException("Cannot find animation $name")
    }
}