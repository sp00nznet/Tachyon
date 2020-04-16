package xyz.znix.xftl

import org.jdom2.Element
import org.newdawn.slick.Animation
import org.newdawn.slick.Image
import org.newdawn.slick.SpriteSheet
import xyz.znix.xftl.math.ConstPoint
import java.util.regex.Pattern

class Animations(df: Datafile) {
    private val sheets: Map<String, SpriteSheetSpec>
    val animations: Map<String, AnimationSpec>
    val weaponAnimations: Map<String, WeaponAnimationSpec>

    init {
        sheets = HashMap()
        animations = HashMap()
        weaponAnimations = HashMap()

        load(df, "data/animations.xml")
        load(df, "data/dlcAnimations.xml")
    }

    private fun load(df: Datafile, name: String) {
        sheets as MutableMap
        animations as MutableMap
        weaponAnimations as MutableMap

        val doc = df.parseXML(df[name])

        // Parse the sheets
        for (elem in doc.rootElement.getChildren("animSheet")) {
            val name = elem.getAttributeValue("name")

            if (this.sheets.containsKey(name)) {
                System.out.println("Warning: duplicate spritesheet $name, using first one")
                continue
            }

            // These have image sizes which don't match the XML, skip verification of them for now
            if (TMP_BROKEN_IMAGES.contains(name))
                continue

            // The filename is the text inside the element
            val path = "img/${elem.textTrim}"
            val img = df.readImage(path)

            run {
                // Don't verify the small bomb, since it's image is two pixels short
                if (name == "bomb_1" || name == "bomb_stun")
                    return@run

                // Make sure the texture size is correct
                check(elem.getAttributeValue("w").toInt() == img.width)
                check(elem.getAttributeValue("h").toInt() == img.height)
            }

            // Some images, most notably some bombs, have frame heights in the XML set that are greater than
            // the image height. This was causing all sorts of trouble, most notably that bombs were shifted
            // down a bit and the bottom part of their images were cut off.
            val frameWidth = elem.getAttributeValue("fw").toInt()
            val frameHeight = elem.getAttributeValue("fh").toInt().coerceAtMost(img.height)

            val sheet = SpriteSheet(img, frameWidth, frameHeight)

            // The background images for crewmembers. I couldn't find any references to them in the XML
            // though so I suspect they're hard-coded.
            val match = PLAYER_BASE_REGEX.matcher(path)
            val alt: SpriteSheet? = if (match.matches()) {
                val backName = match.group(1) + "_color.png"
                val backImg = df.getOrNull(backName)?.let(df::readImage)
                backImg?.let { SpriteSheet(it, frameWidth, frameHeight) }
            } else {
                null
            }

            sheets[name] = SpriteSheetSpec(sheet, alt)
        }

        for (xml in doc.rootElement.getChildren("anim")) {
            val name = xml.getAttributeValue("name")
            animations[name] = buildAnimation(xml) ?: continue
        }

        for (xml in doc.rootElement.getChildren("weaponAnim")) {
            val name = xml.getAttributeValue("name")

            // Add this here since weapon animations don't have time elements
            xml.addContent(Element("time").apply { text = "-1" })
            val anim = buildAnimation(xml) ?: continue

            val chargedFrame = xml.getChild("chargedFrame").textTrim.toInt()
            val fireFrame = xml.getChild("fireFrame").textTrim.toInt()

            val mountPoint = parsePosElem(xml.getChild("mountPoint"))
            val firePoint = parsePosElem(xml.getChild("firePoint"))

            val chargeImage = xml.getChild("chargeImage")?.textTrim?.let { i -> df.readImage("img/$i") }

            weaponAnimations[name] = WeaponAnimationSpec(anim.sheet.sheet, anim.x, anim.y, anim.length, chargedFrame, fireFrame, mountPoint, firePoint, chargeImage)
        }
    }

    private fun buildAnimation(xml: Element): AnimationSpec? {
        val sheetName = xml.getChild("sheet").textTrim

        // Skip the broken animation files
        if (TMP_BROKEN_IMAGES.contains(sheetName))
            return null

        val sheet = this.sheets[sheetName] ?: error("Unknown sheet $sheetName")

        val desc = xml.getChild("desc")

        val length = desc.getAttributeValue("length").toInt()
        val x = desc.getAttributeValue("x").toInt()
        val y = sheet.sheet.verticalCount - desc.getAttributeValue("y").toInt() - 1

        // Convert from per-cycle to per-frame
        val time = xml.getChild("time").textTrim.toFloat() / length

        return AnimationSpec(sheet, x, y, length, time)
    }

    private fun parsePosElem(elem: Element): ConstPoint {
        val x = elem.getAttributeValue("x").toInt()
        val y = elem.getAttributeValue("y").toInt()
        return ConstPoint(x, y)
    }

    operator fun get(name: String): AnimationSpec {
        if (name == "explosion_random") {
            val test = arrayOf("explosion_big2", "explosion_big3", "explosion_big4")
            return this[test.random()]
        }

        return animations[name] ?: throw IllegalArgumentException("Cannot find animation $name")
    }

    class WeaponAnimationSpec(val sheet: SpriteSheet, val x: Int, val y: Int, val length: Int, val chargedFrame: Int,
                              val fireFrame: Int, val mountPoint: ConstPoint, val firePoint: ConstPoint,
                              val chargeImage: Image?) {

        val chargedImage: Image get() = spriteAt(chargedFrame)

        // It *appears* that FTL plays the shoot animation at around 30fps
        fun shoot() = Animation(sheet, x + chargedFrame, y, x + length - 1, y, true, 1000 / 30, false)

        fun spriteAt(i: Int): Image {
            if (i >= length) throw IndexOutOfBoundsException(i)
            return sheet.getSprite(x + i, y)
        }
    }

    class SpriteSheetSpec(val sheet: SpriteSheet, val secondary: SpriteSheet?)

    companion object {
        val PLAYER_BASE_REGEX = Pattern.compile("(img/people/.*)_base.png")
        val TMP_BROKEN_IMAGES = setOf("artillery_fed", "explosion_big1", "room_touch2x2", "room_touch2x1")
    }
}
