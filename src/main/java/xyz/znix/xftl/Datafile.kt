package xyz.znix.xftl

import org.jdom2.Document
import org.jdom2.input.SAXBuilder
import org.newdawn.slick.Image
import java.io.*

const val HEADER_SIZE = 16
const val ENTRY_SIZE = 20

class Datafile @Throws(FileNotFoundException::class)
constructor(private val data_file: File) {

    private val files: MutableMap<Int, FTLFile> = HashMap()
    private val fi: RandomAccessFile = RandomAccessFile(data_file, "r")

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
            val hash = fi.readInt()

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

            val file = FTLFile(name, offset, decompressedSize)
            files[hash] = file
            check(hash == file.hash)

            check(fi.filePointer == entriesStart + i * ENTRY_SIZE)
        }

        // for(pair in files)
        //     System.out.println(pair.value.name)

        // val mantis = this["data/mantis_scout.xml"]
        // val mantis = this["data/kestral.txt"]
        // System.out.println(String(read(mantis)))
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

    operator fun get(name: String): FTLFile =
            files[Hash.hash(name)] ?: throw IllegalArgumentException("No such file '$name'")

    fun read(file: FTLFile): ByteArray {
        fi.seek(file.offset.toLong())
        val bytes = ByteArray(file.length)
        fi.read(bytes)
        return bytes
    }

    fun readString(file: FTLFile) = String(read(file), Charsets.UTF_8)

    fun parseXML(file: FTLFile): Document {
        val builder = SAXBuilder()
        return builder.build(open(file))
    }

    fun open(file: FTLFile): InputStream {
        return ByteArrayInputStream(read(file))
    }

    fun readImage(file: FTLFile): Image {
        return Image(open(file), file.name, false)
    }
}
