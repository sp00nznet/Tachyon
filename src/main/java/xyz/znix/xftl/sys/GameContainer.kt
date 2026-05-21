package xyz.znix.xftl.sys

import xyz.znix.xftl.rendering.Cursor

interface GameContainer {
    val input: Input
    val width: Int
    val height: Int

    /**
     * An overlay (such as the developer menu) that intercepts mouse input
     * before it reaches the active game state. Null when there is no overlay.
     */
    var inputOverlay: InputOverlay?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    fun exit()

    /**
     * Set the OpenGL viewport to the game area: the window below the menu bar.
     */
    fun setGameViewport() {}

    /**
     * Set the OpenGL viewport to the whole window, used to draw the dev menu.
     */
    fun setMenuViewport() {}

    /**
     * Switch to the given cursor image, or the default image if [cursor]
     * is null.
     */
    fun setCursor(cursor: Cursor?)
}
