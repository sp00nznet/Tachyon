package xyz.znix.xftl.sys

import org.newdawn.slick.util.InputAdapter
import org.newdawn.slick.Input as SlickInput

/**
 * A representation of all user input.
 *
 * This is based around Slick's Input class.
 */
class Input(private val input: SlickInput) {
    val mouseX: Int get() = input.mouseX
    val mouseY: Int get() = input.mouseY

    fun isMouseButtonDown(button: Int): Boolean = input.isMouseButtonDown(button)

    fun isKeyPressed(key: Int): Boolean = input.isKeyPressed(key)

    fun isKeyDown(key: Int): Boolean = input.isKeyDown(key)

    fun addListener(listener: InputAdapter) = input.addListener(listener)

    fun removeAllListeners() = input.removeAllListeners()

    fun clearInputPressedRecord() {
        input.clearControlPressedRecord()
        input.clearKeyPressedRecord()
        input.clearMousePressedRecord()
    }

    @Suppress("unused", "SpellCheckingInspection")
    companion object {
        const val MOUSE_LEFT_BUTTON: Int = SlickInput.MOUSE_LEFT_BUTTON
        const val MOUSE_RIGHT_BUTTON: Int = SlickInput.MOUSE_RIGHT_BUTTON

        const val KEY_F1: Int = SlickInput.KEY_F1
        const val KEY_F2: Int = SlickInput.KEY_F2
        const val KEY_F3: Int = SlickInput.KEY_F3
        const val KEY_F4: Int = SlickInput.KEY_F4
        const val KEY_F5: Int = SlickInput.KEY_F5

        const val KEY_UP: Int = SlickInput.KEY_UP
        const val KEY_DOWN: Int = SlickInput.KEY_DOWN
        const val KEY_LEFT: Int = SlickInput.KEY_LEFT
        const val KEY_RIGHT: Int = SlickInput.KEY_RIGHT

        const val KEY_ENTER: Int = SlickInput.KEY_ENTER
        const val KEY_ESCAPE: Int = SlickInput.KEY_ESCAPE
        const val KEY_SPACE: Int = SlickInput.KEY_SPACE
        const val KEY_TAB: Int = SlickInput.KEY_TAB
        const val KEY_FULL_STOP: Int = SlickInput.KEY_PERIOD

        const val KEY_LSHIFT: Int = SlickInput.KEY_LSHIFT

        const val KEY_BACK: Int = SlickInput.KEY_BACK
        const val KEY_DELETE: Int = SlickInput.KEY_DELETE
        const val KEY_GRAVE: Int = SlickInput.KEY_GRAVE

        const val KEY_A: Int = SlickInput.KEY_A
        const val KEY_B: Int = SlickInput.KEY_B
        const val KEY_C: Int = SlickInput.KEY_C
        const val KEY_D: Int = SlickInput.KEY_D
        const val KEY_E: Int = SlickInput.KEY_E
        const val KEY_F: Int = SlickInput.KEY_F
        const val KEY_G: Int = SlickInput.KEY_G
        const val KEY_H: Int = SlickInput.KEY_H
        const val KEY_I: Int = SlickInput.KEY_I
        const val KEY_J: Int = SlickInput.KEY_J
        const val KEY_K: Int = SlickInput.KEY_K
        const val KEY_L: Int = SlickInput.KEY_L
        const val KEY_M: Int = SlickInput.KEY_M
        const val KEY_N: Int = SlickInput.KEY_N
        const val KEY_O: Int = SlickInput.KEY_O
        const val KEY_P: Int = SlickInput.KEY_P
        const val KEY_Q: Int = SlickInput.KEY_Q
        const val KEY_R: Int = SlickInput.KEY_R
        const val KEY_S: Int = SlickInput.KEY_S
        const val KEY_T: Int = SlickInput.KEY_T
        const val KEY_U: Int = SlickInput.KEY_U
        const val KEY_V: Int = SlickInput.KEY_V
        const val KEY_W: Int = SlickInput.KEY_W
        const val KEY_X: Int = SlickInput.KEY_X
        const val KEY_Y: Int = SlickInput.KEY_Y
        const val KEY_Z: Int = SlickInput.KEY_Z

        const val KEY_0 = SlickInput.KEY_0
        const val KEY_1 = SlickInput.KEY_1
        const val KEY_2 = SlickInput.KEY_2
        const val KEY_3 = SlickInput.KEY_3
        const val KEY_4 = SlickInput.KEY_4
        const val KEY_5 = SlickInput.KEY_5
        const val KEY_6 = SlickInput.KEY_6
        const val KEY_7 = SlickInput.KEY_7
        const val KEY_8 = SlickInput.KEY_8
        const val KEY_9 = SlickInput.KEY_9
    }
}
