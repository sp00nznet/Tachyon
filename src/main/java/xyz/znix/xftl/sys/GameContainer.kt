package xyz.znix.xftl.sys

import org.newdawn.slick.GameContainer as SlickGameContainer

/**
 * A wrapper for Slick's GameContainer, for transitioning over to LWJGL.
 */
class GameContainer(private val slick: SlickGameContainer) {
    val input: Input = Input(slick.input)

    val width: Int get() = slick.width
    val height: Int get() = slick.height

    fun exit() {
        slick.exit()
    }
}
