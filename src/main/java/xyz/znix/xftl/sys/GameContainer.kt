package xyz.znix.xftl.sys

interface GameContainer {
    val input: Input
    val width: Int
    val height: Int

    fun exit()
}
