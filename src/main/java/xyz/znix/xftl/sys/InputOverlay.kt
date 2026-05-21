package xyz.znix.xftl.sys

/**
 * An overlay - such as the developer menu - that gets first chance at mouse
 * input, before it reaches the active [xyz.znix.xftl.game.MainGame.GameState].
 *
 * Only the mouse is intercepted; keyboard input is left untouched so the
 * overlay never interferes with the game's hotkeys or the debug console.
 *
 * All coordinates are in the game's logical coordinate space, the same space
 * the game states render and receive input in.
 */
interface InputOverlay {
    /**
     * @return true if the overlay currently occupies the given point, and the
     *         game underneath should not react to the mouse being there.
     */
    fun isCapturingMouse(x: Int, y: Int): Boolean

    /**
     * @return true if the overlay consumed this press. When true, the matching
     *         release is also routed to the overlay and withheld from the game.
     */
    fun overlayMousePressed(button: Int, x: Int, y: Int): Boolean

    /**
     * Called for the release of a button whose press the overlay consumed.
     */
    fun overlayMouseReleased(button: Int, x: Int, y: Int)

    /**
     * @return true if the overlay consumed this scroll event.
     */
    fun overlayMouseWheel(change: Int): Boolean
}
