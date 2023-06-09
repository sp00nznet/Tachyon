package xyz.znix.xftl

import org.newdawn.slick.Color
import org.newdawn.slick.Font
import org.newdawn.slick.Image
import org.newdawn.slick.opengl.ImageData
import xyz.znix.xftl.rendering.BulkImageRenderer
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

class SILFontLoader : Font {
    private val chars: Map<Char, Charinfo>
    private val picture: Image
    private val renderer: BulkImageRenderer

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
        renderer = BulkImageRenderer(picture)
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
        renderer = other.renderer
    }

    override fun getHeight(str: String?): Int {
        throw UnsupportedOperationException("not implemented")
    }

    override fun drawString(x: Float, y: Float, text: String?) {
        throw UnsupportedOperationException("not implemented")
    }

    override fun drawString(x: Float, y: Float, text: String, col: Color) {
        drawStringTruncated(x, y, Float.MAX_VALUE, text, col)
    }

    fun drawStringTruncated(x: Float, y: Float, width: Float, text: String, col: Color) {
        var next = x
        for (ch in text) {
            // Replace unknown characters with question marks
            val info = chars[ch] ?: chars['?']!!

            val cy = y + (scale * -info.ascent).roundToInt()
            next += (info.prekern * scale).roundToInt()
            val cx = next.roundToInt()

            // Check if this character is going to overflow the allowed area
            val charWidth = min((width + x - cx) / scale, info.w.f)

            renderer.pushImage(
                cx.f, cy,
                cx.f + charWidth * scale, cy + info.h * scale,
                info.x.f, info.y.f,
                info.x + charWidth, (info.y + info.h).f, col
            )
            next += ((info.w + info.postkern) * scale).roundToInt()

            if (next - x > width)
                return
        }

        renderer.flush()
    }

    fun drawStringCentred(x: Float, y: Float, width: Float, text: String, col: Color) {
        val textX = x + (width - getWidth(text)) / 2
        drawString(textX, y, text, col)
    }

    fun drawStringLeftAligned(x: Float, y: Float, text: String, colour: Color) {
        drawString(x - getWidth(text), y, text, colour)
    }

    override fun drawString(x: Float, y: Float, text: String?, col: Color?, startIndex: Int, endIndex: Int) {
        throw UnsupportedOperationException("not implemented")
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
        throw UnsupportedOperationException("not implemented")
    }

    fun supportsCharacter(c: Char): Boolean {
        return chars.containsKey(c)
    }

    fun wrapString(string: String, maxWidth: Int): List<String> {
        val lines = ArrayList<String>()

        // We have to round the prekern and width+postkern separately, since that's how we render it.
        val spaceInfo = chars.getValue(' ')
        val spaceWidth = (spaceInfo.prekern * scale).roundToInt() +
                ((spaceInfo.w + spaceInfo.postkern) * scale).roundToInt()

        var currentWidth = 0
        val line = StringBuilder()

        for (word in string.split(' ', '\t')) {
            if (line.isNotEmpty()) {
                line.append(' ')
                currentWidth += spaceWidth
            }

            var nextWidth = 0
            for (ch in word) {
                val info = chars[ch] ?: error("Unknown char $ch")
                nextWidth += (info.prekern * scale).roundToInt()

                // Always include the postkern, since we'll be writing
                // another word after this. And if we wrap at slightly
                // the wrong time by the distance of a postkern, who cares.
                val postKern = info.postkern
                nextWidth += ((info.w + postKern) * scale).roundToInt()
            }

            if (currentWidth + nextWidth > maxWidth) {
                lines.add(line.toString())
                line.clear()
                currentWidth = 0
            }

            line.append(word)
            currentWidth += nextWidth
        }

        lines.add(line.toString())

        return lines
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
