package xyz.znix.xftl.devutil

import org.newdawn.slick.BasicGame
import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Utils

object AnimationViewer {

    @JvmStatic
    fun main(args: Array<String>) {
        Utils.startSlick { GameImpl(it) }
    }

    private class GameImpl(val df: Datafile) : BasicGame("Animation Viewer") {
        val anims by lazy { Animations(df) }
        val bomb by lazy { anims.weaponAnimations.getValue("bomb_1") }
        val bombShoot by lazy { bomb.shoot() }

        override fun init(container: GameContainer) {
        }

        override fun update(container: GameContainer, dt: Int) {
            bombShoot.update(dt.toLong() / 3)
        }

        override fun render(container: GameContainer, g: Graphics) {
            g.background = Color(0f, 1f, 1f)
            g.clear()

            bomb.sheet.draw(100f, 0f)

            bombShoot.draw()
            bombShoot.getImage(0).draw(50f, 0f)
        }
    }
}
