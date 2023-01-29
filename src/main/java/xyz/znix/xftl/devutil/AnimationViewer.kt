package xyz.znix.xftl.devutil

import org.newdawn.slick.*
import xyz.znix.xftl.Animations
import xyz.znix.xftl.Datafile
import java.io.File

object AnimationViewer {

    @JvmStatic
    fun main(args: Array<String>) {
        val df = Datafile(File("/home/znix/Static/Games/FTL-linux/data/ftl.dat"))

        // TODO automatic extraction
        if (System.getProperty("org.lwjgl.librarypath") == null) System.setProperty(
            "org.lwjgl.librarypath",
            File("natives").absolutePath
        )

        // Enable stenciling support
        GameContainer.enableStencil()

        val gc = AppGameContainer(GameImpl(df))
        gc.setTargetFrameRate(120)
        gc.setDisplayMode(1024, 768, false)
        gc.setShowFPS(false)
        gc.start()
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
