package xyz.znix.xftl

import org.newdawn.slick.Color
import org.newdawn.slick.Font
import org.newdawn.slick.Image
import org.newdawn.slick.opengl.ImageData
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.roundToInt

class SILFontLoader(df: Datafile, file: FTLFile) : Font {
    private val chars: Map<Char, Charinfo>
    private val picture: Image

    private val height: Int
    private val baseline: Int

    var scale: Float = 1f

    init {
        val seeking = SeekingInputStream(df.read(file))
        val bytes = DataInputStream(seeking)

        check(Arrays.equals(bytes.readNBytes(4), "FONT".toByteArray()))

        val version = bytes.read()
        height = bytes.read()
        baseline = bytes.read()

        // Pad
        bytes.read()

        val charinfo_offset = bytes.readInt()

        val charinfo_count = bytes.readUnsignedShort()
        val charinfo_size = bytes.readUnsignedShort()

        chars = HashMap(charinfo_count)

        val texture_offset = bytes.readInt()
        val texture_size = bytes.readInt()

        check(version == 1)

        seeking.position = charinfo_offset

        for (i in 1..charinfo_count) {
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

        check(seeking.position == charinfo_offset + charinfo_size * charinfo_count)

        // Read the texture

        seeking.position = texture_offset
        val magic = bytes.readNBytes(4)
        check(Arrays.equals(magic, "TEX\u000a".toByteArray()))

        val tex_version = bytes.read()
        check(tex_version == 2)

        val format = bytes.read()
        check(format == 64)

        val mipmaps = bytes.read()
        check(mipmaps == 0)

        val opaque_bitmap = bytes.read()
        check(opaque_bitmap == 0)

        val tex_width = bytes.readUnsignedShort()
        val tex_height = bytes.readUnsignedShort()

        // Throw away four bytes
        bytes.readInt()

        val pixel_offsets = bytes.readInt()
        val pixel_size = bytes.readInt()

        val bitmap_offset = bytes.readInt()
        val bitmap_size = bytes.readInt()

        seeking.position = pixel_offsets + texture_offset

        val data = ByteArray(pixel_size)
        bytes.read(data)

        val img = MonochromeImage(tex_width, tex_height, data)

        picture = Image(img, Image.FILTER_NEAREST)
    }

    override fun getHeight(str: String?): Int {
        TODO("not implemented")
    }

    override fun drawString(x: Float, y: Float, text: String?) {
        TODO("not implemented")
    }

    override fun drawString(x: Float, y: Float, text: String, col: Color) {
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
                    (info.x + info.w).f, (info.y + info.h).f, col)
            next += ((info.w + info.postkern) * scale).roundToInt()
        }
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

    private class Charinfo(val ch: Char, val x: Int, val y: Int, val w: Int, val h: Int, val ascent: Int,
                           val prekern: Float, val postkern: Float)

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
            for (i in 0 until load.size) {
                val b = load[i]

                // Java uses two's complement, so -1 is 0xff
                for (j in 1..3)
                    data.put(-1)

                // Copy over the alpha
                data.put(b)
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
