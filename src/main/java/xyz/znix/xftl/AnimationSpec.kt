package xyz.znix.xftl

import org.newdawn.slick.Animation
import org.newdawn.slick.SpriteSheet

class AnimationSpec(val sheet: SpriteSheet, val x: Int, val y: Int, val length: Int, val time: Float) {
    fun start() = Animation(sheet, x, y, x + length - 1, y, true, (1000 * time).toInt(), true)
}