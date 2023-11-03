/**
 * Utility functions for implementing BXML.
 */
package xyz.znix.xftl.bxml

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

object BXMLUtils {
    const val ID_TEXT = 0
    const val ID_ELEM = 1
    const val ID_EOF = 2
}

fun DataOutputStream.writeVarInt(value: Int) {
    require(value >= 0)

    var remaining = value
    do {
        val segment = remaining and 0x7f
        remaining = remaining ushr 7
        val hasMore = remaining != 0

        val byte = segment or (if (hasMore) 0x80 else 0)
        write(byte)
    } while (hasMore)
}

fun DataInputStream.readVarInt(): Int {
    var value = 0
    var shift = 0
    do {
        val byte = read()
        if (byte == -1)
            throw EOFException()

        val segment = byte and 0x7f
        val moreMarker = byte and 0x80

        value += segment shl shift
        shift += 7
    } while (moreMarker != 0)

    return value
}
