package xyz.znix.xftl

import org.jdom2.Document
import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.modding.SlipstreamMod
import xyz.znix.xftl.modding.SlipstreamPatcher
import xyz.znix.xftl.modding.SlipstreamZipMod
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.TextureLoader
import xyz.znix.xftl.sys.PlatformSpecific
import xyz.znix.xftl.sys.ResourceContext
import java.io.*
import java.nio.file.Files
import java.util.*

const val HEADER_SIZE = 16
const val ENTRY_SIZE = 20

/**
 * Represents a vanilla FTL .dat file, without any mods applied.
 *
 * [Datafile] acts as a layer over the top of this, applying changes from
 * [SlipstreamPatcher] and any other mod sources we might later have.
 */
class VanillaDatafile(val underlyingFile: File) {
    private val files: MutableMap<String, Entry> = HashMap()
    private val fi: RandomAccessFile = RandomAccessFile(underlyingFile, "r")

    init {
        // Skip the 'PKG\n' header
        fi.seek(4)

        // Header size
        check(fi.readUnsignedShort() == HEADER_SIZE)

        // Entry size
        check(fi.readUnsignedShort() == ENTRY_SIZE)

        val fileCount = fi.readInt()

        @Suppress("UNUSED_VARIABLE") val nameSizeTODO = fi.readInt()

        val entriesStart = fi.filePointer
        check(entriesStart == HEADER_SIZE.toLong())
        val namesStart = entriesStart + fileCount * ENTRY_SIZE

        for (i in 1..fileCount) {
            fi.readInt() // The file name hash

            val flags = readByte(fi)

            check(flags == 0) { "Datafile does not yet support compression" }

            val nameOffset = read3byte(fi)

            val pos = fi.filePointer
            fi.seek(nameOffset.toLong() + namesStart)
            val name = readStringNulTerm(fi)
            fi.seek(pos)

            val offset = fi.readInt()
            val compressedSize = fi.readInt()
            val decompressedSize = fi.readInt()

            check(compressedSize == decompressedSize) { "Compressed and uncompressed file sizes do not match" }

            files[name] = Entry(name, offset, decompressedSize)

            check(fi.filePointer == entriesStart + i * ENTRY_SIZE)
        }
    }

    private fun readStringNulTerm(fi: RandomAccessFile): String {
        val bytes = ByteArray(1024)
        var count = 0

        while (true) {
            val ch = readByte(fi)
            if (ch == 0)
                break

            bytes[count++] = ch.toByte()
        }

        return String(bytes, 0, count, Charsets.UTF_8)
    }

    private fun readByte(fi: RandomAccessFile): Int {
        val n = fi.read()
        if (n < 0)
            throw EOFException()
        return n
    }

    private fun read3byte(fi: RandomAccessFile): Int {
        val ch1 = fi.read()
        val ch2 = fi.read()
        val ch3 = fi.read()
        if ((ch1 or ch2 or ch3) < 0) {
            throw EOFException()
        } else {
            return (ch1 shl 16) + (ch2 shl 8) + ch3
        }
    }

    operator fun get(name: String): Entry =
        files[name] ?: throw IllegalArgumentException("No such file '$name'")

    fun read(file: Entry): ByteArray {
        fi.seek(file.offset.toLong())
        val bytes = ByteArray(file.length)
        fi.read(bytes)
        return bytes
    }

    fun getAllFiles(): List<Entry> {
        return files.values.toList()
    }

    class Entry(val name: String, val offset: Int, val length: Int)

    companion object {
        @JvmStatic
        fun createWithDefaultPath(): VanillaDatafile {
            val path = MainGame.findFtlDat() ?: error("Datafile path not set!")
            return VanillaDatafile(path.toFile())
        }
    }
}

class Datafile(val vanilla: VanillaDatafile, slipstreamMods: List<SlipstreamMod>) {
    // TODO handle closing this, to close the zip files
    private val patcher = SlipstreamPatcher(vanilla)
    private val files: Map<String, FTLFile>

    init {
        patcher.patch(slipstreamMods)
        files = patcher.files.keys.map { FTLFile(it) }.associateBy { it.name }
    }

    operator fun get(name: String): FTLFile =
        files[name] ?: throw IllegalArgumentException("No such file '$name'")

    fun getOrNull(name: String): FTLFile? = files[name]

    fun read(file: FTLFile): ByteArray {
        return patcher.files[file.name]!!.open().use { it.readAllBytes() }
    }

    fun readString(file: FTLFile): String {
        // The vanilla files use LF endings, but mods might introduce CRLF endings too.
        return String(read(file), Charsets.UTF_8).replace("\r\n", "\n")
    }

    fun parseXML(file: FTLFile): Document {
        val builder = SAXBuilder()
        builder.expandEntities = false
        return builder.build(open(file))
    }

    fun open(file: FTLFile): InputStream {
        return ByteArrayInputStream(read(file))
    }

    fun readImage(context: ResourceContext, file: FTLFile): Image {
        return TextureLoader.loadImage(context, open(file), file.name)
    }

    fun getAllFiles(): List<FTLFile> {
        return files.values.toList()
    }

    companion object {
        fun loadWithMods(vanilla: VanillaDatafile): Datafile {
            // Load slipstream mods from a 'mods' folder. The mod order is specified
            // with the order.txt file, which has the names of each of the mods
            // on separate lines.
            // TODO move into a GUI.
            val mods: List<SlipstreamMod>
            val modDir = PlatformSpecific.INSTANCE.modsDirectory
            val modOrderFile = modDir.resolve("order.txt")
            if (Files.isRegularFile(modOrderFile)) {
                println("Using mod order file: '$modOrderFile'")
                mods = Files.readAllLines(modOrderFile)
                    .filter { it.isNotBlank() }
                    .filter { !it.startsWith("//") } // Comments
                    .map { SlipstreamZipMod(modDir.resolve(it).toFile()) }
            } else {
                println("Missing mod order file, mods disabled: '$modOrderFile'")
                mods = Collections.emptyList()
            }

            return Datafile(vanilla, mods)
        }
    }
}
