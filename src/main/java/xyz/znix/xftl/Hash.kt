package xyz.znix.xftl

object Hash {
    fun hash(name: String): Int {
        var acc: Int = 0

        for (c: Char in name.toLowerCase()) {
            acc = (acc shl 27) or (acc ushr 5)
            acc = acc xor c.toInt()
        }

        return acc
    }
}