package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Animation
import org.newdawn.slick.Image
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.math.ConstPoint

class Animations(df: Datafile) {
    private val sheets: Map<String, SpriteSheet>
    val animations: Map<String, AnimationSpec>
    val weaponAnimations: Map<String, WeaponAnimationSpec>

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

        weaponAnimations = HashMap()

        for (xml in doc.rootElement.getChildren("weaponAnim")) {
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

            val chargedFrame = xml.getChild("chargedFrame").textTrim.toInt()
            val fireFrame = xml.getChild("fireFrame").textTrim.toInt()

            val mountPoint = parsePosElem(xml.getChild("mountPoint"))
            val firePoint = parsePosElem(xml.getChild("firePoint"))

            val chargeImage = xml.getChild("chargeImage")?.textTrim?.let { i -> df.readImage(df["img/$i"]) }

            weaponAnimations[name] = WeaponAnimationSpec(sheet, x, y, length, chargedFrame, fireFrame, mountPoint, firePoint, chargeImage)
            System.out.println(name)
        }
    }

    private fun parsePosElem(elem: Element): ConstPoint {
        val x = elem.getAttributeValue("x").toInt()
        val y = elem.getAttributeValue("y").toInt()
        return ConstPoint(x, y)
    }

    operator fun get(name: String): AnimationSpec {
        return animations[name] ?: throw IllegalArgumentException("Cannot find animation $name")
    }

    class WeaponAnimationSpec(val sheet: SpriteSheet, val x: Int, val y: Int, val length: Int, val chargedFrame: Int,
                              val fireFrame: Int, val mountPoint: ConstPoint, val firePoint: ConstPoint,
                              val chargeImage: Image?) {
        // TODO find out the time properly
        fun start() = Animation(sheet, x, y, x + length - 1, y, true, (1000 * 0.25f).toInt(), true)
    }
}
