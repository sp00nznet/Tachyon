package xyz.znix.xftl

class FTLFile(val name: String, val offset: Int, val length: Int) {
    val hash: Int = Hash.hash(name)
}