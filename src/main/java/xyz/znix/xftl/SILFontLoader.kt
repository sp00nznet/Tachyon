package xyz.znix.xftl

import org.newdawn.slick.Color
import org.newdawn.slick.Font
import org.newdawn.slick.Image
import org.newdawn.slick.opengl.ImageData
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.roundToInt

class SILFontLoader : Font {
    private val chars: Map<Char, Charinfo>
    private val picture: Image

    private val height: Int
    private val baseline: Int

    var scale: Float = 1f

    constructor(df: Datafile, file: FTLFile) {
        val seeking = SeekingInputStream(df.read(file))
        val bytes = DataInputStream(seeking)

        check(Arrays.equals(bytes.readNBytes(4), "FONT".toByteArray()))

        val version = bytes.read()
        height = bytes.read()
        baseline = bytes.read()

        // Pad
        bytes.read()

        val charinfoOffset = bytes.readInt()

        val charinfoCount = bytes.readUnsignedShort()
        val charinfoSize = bytes.readUnsignedShort()

        chars = HashMap(charinfoCount)

        val textureOffset = bytes.readInt()
        @Suppress("UNUSED_VARIABLE") val textureSize = bytes.readInt()

        check(version == 1)

        seeking.position = charinfoOffset

        for (i in 1..charinfoCount) {
            val ch = bytes.readInt()

            val x = bytes.readShort().toInt()
            val y = bytes.readShort().toInt()

            val w = bytes.readUnsignedByte()
            val h = bytes.readUnsignedByte()

            val accent = bytes.readByte().toInt()

            // Pad
            bytes.read()

            val prekern = bytes.readShort().toInt()
            val postkern = bytes.readShort().toInt()

            chars[ch.toChar()] = Charinfo(ch.toChar(), x, y, w, h, accent, prekern / 256f, postkern / 256f)
        }

        check(seeking.position == charinfoOffset + charinfoSize * charinfoCount)

        // Read the texture

        seeking.position = textureOffset
        val magic = bytes.readNBytes(4)
        check(Arrays.equals(magic, "TEX\u000a".toByteArray()))

        val texVersion = bytes.read()
        check(texVersion == 2)

        val format = bytes.read()
        check(format == 64)

        val mipmaps = bytes.read()
        check(mipmaps == 0)

        val opaqueBitmap = bytes.read()
        check(opaqueBitmap == 0)

        val texWidth = bytes.readUnsignedShort()
        val texHeight = bytes.readUnsignedShort()

        // Throw away four bytes
        bytes.readInt()

        val pixelOffsets = bytes.readInt()
        val pixelSize = bytes.readInt()

        @Suppress("UNUSED_VARIABLE") val bitmapOffset = bytes.readInt()
        @Suppress("UNUSED_VARIABLE") val bitmapSize = bytes.readInt()

        seeking.position = pixelOffsets + textureOffset

        val data = ByteArray(pixelSize)
        bytes.read(data)

        val img = MonochromeImage(texWidth, texHeight, data)

        picture = Image(img, Image.FILTER_NEAREST)
    }

    /**
     * Create a new [SILFontLoader] representing the same font as the supplied instance. This shares
     * the image and character data (thus making clones like this is cheap), but the scale (and any other
     * later adjustable properties) are kept separate.
     */
    constructor(other: SILFontLoader) {
        chars = other.chars
        picture = other.picture
        height = other.height
        baseline = other.baseline
    }

    override fun getHeight(str: String?): Int {
        TODO("not implemented")
    }

    override fun drawString(x: Float, y: Float, text: String?) {
        TODO("not implemented")
    }

    /**
     * Draw a string using the legacy (incorrect) positioning, which made 'P' elevated well above
     * where they should be.
     *
     * The new [drawString] function always draws at a font's baseline, which should make working
     * with it much nicer too.
     */
    @Deprecated(message = "Use the regular drawString instead")
    fun drawStringLegacy(x: Float, y: Float, text: String, col: Color) {
        var next = x
        for (ch in text) {
            val info = chars[ch] ?: error("Unknown char $ch")
            val cy = y + (scale * (height - info.h - baseline)).roundToInt()
            next += (info.prekern * scale).roundToInt()
            val cx = next.roundToInt()
            picture.draw(
                cx.f, cy,
                cx.f + info.w * scale, cy + info.h * scale,
                info.x.f, info.y.f,
                (info.x + info.w).f, (info.y + info.h).f, col
            )
            next += ((info.w + info.postkern) * scale).roundToInt()
        }
    }

    override fun drawString(x: Float, y: Float, text: String, col: Color) {
        var next = x
        for (ch in text) {
            val info = chars[ch] ?: error("Unknown char $ch")
            val cy = y + (scale * -info.ascent).roundToInt()
            next += (info.prekern * scale).roundToInt()
            val cx = next.roundToInt()
            picture.draw(
                cx.f, cy,
                cx.f + info.w * scale, cy + info.h * scale,
                info.x.f, info.y.f,
                (info.x + info.w).f, (info.y + info.h).f, col
            )
            next += ((info.w + info.postkern) * scale).roundToInt()
        }
    }

    fun drawStringCentred(x: Float, y: Float, width: Float, text: String, col: Color) {
        val textX = x + (width - getWidth(text)) / 2
        drawString(textX, y, text, col)
    }

    @Deprecated(message = "Uses the legacy baseline drawing function")
    fun drawStringLeftAlignedLegacy(x: Float, y: Float, text: String, colour: Color) {
        drawStringLegacy(x - getWidth(text), y, text, colour)
    }

    fun drawStringLeftAligned(x: Float, y: Float, text: String, colour: Color) {
        drawString(x - getWidth(text), y, text, colour)
    }

    override fun drawString(x: Float, y: Float, text: String?, col: Color?, startIndex: Int, endIndex: Int) {
        TODO("not implemented")
    }

    override fun getWidth(str: String): Int {
        var next = 0
        for ((i, ch) in str.withIndex()) {
            val info = chars[ch] ?: error("Unknown char $ch")
            next += (info.prekern * scale).roundToInt()

            val last = i == str.length - 1
            val postKern = if (last) 0f else info.postkern
            next += ((info.w + postKern) * scale).roundToInt()
        }
        return next
    }

    override fun getLineHeight(): Int {
        TODO("not implemented")
    }

    @Suppress("unused")
    private class Charinfo(
        val ch: Char, val x: Int, val y: Int, val w: Int, val h: Int, val ascent: Int,
        val prekern: Float, val postkern: Float
    )

    private class SeekingInputStream(ba: ByteArray) : ByteArrayInputStream(ba) {
        var position: Int
            get() = pos
            set(value) {
                pos = value
            }
    }

    private class MonochromeImage(val w: Int, val h: Int, load: ByteArray) : ImageData {
        val data: ByteBuffer = ByteBuffer.allocateDirect(load.size * 4)

        init {
            // Image is stored as RGBA, and our font should be 0xfff with the alpha loaded
            // from the file. This way we can easily control the colour of the rendered
            // text by applying a filter.
            for (element in load) {
                // Java uses two's complement, so -1 is 0xff
                for (j in 1..3)
                    data.put(-1)

                // Copy over the alpha
                data.put(element)
            }

            data.position(0)
        }

        override fun getHeight(): Int = h

        override fun getTexWidth(): Int = w

        override fun getDepth(): Int = 8 * 4

        override fun getImageBufferData(): ByteBuffer = data

        override fun getWidth(): Int = w

        override fun getTexHeight(): Int = h
    }
}
