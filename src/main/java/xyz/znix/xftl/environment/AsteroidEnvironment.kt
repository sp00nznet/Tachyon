package xyz.znix.xftl.environment

import org.newdawn.slick.GameContainer
import xyz.znix.xftl.game.InGameState
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.sector.Beacon

class AsteroidEnvironment(game: InGameState, beacon: Beacon) : AbstractEnvironment(game, beacon) {
    override val serialiseImageIndexes: Boolean get() = false
    override val type: Beacon.EnvironmentType get() = Beacon.EnvironmentType.ASTEROID

    private var asteroidAnimationTimer = 0f

    // The actual background
    private val realBackground = game.getImg("img/stars/bg_dullstars.png")

    private val layer1 = game.getImg("img/asteroids/asteroid_back1.png")
    private val layer2 = game.getImg("img/asteroids/asteroid_back2.png")
    private val layer3 = game.getImg("img/asteroids/asteroid_back3.png")

    override fun renderBackground(gc: GameContainer, g: Graphics) {
        realBackground.draw()

        // Rough speeds measured from FTL
        // back img = ~13sec to traverse weapons bar (~400px)
        // middle img = ~10sec
        // foreground img = ~8sec

        // Rough speeds measured from FTL
        // back img = ~13sec to traverse weapons bar (~400px)
        // middle img = ~10sec
        // foreground img = ~8sec
        renderAsteroid(gc, layer1, 400f / 13)
        renderAsteroid(gc, layer2, 400f / 10)
        renderAsteroid(gc, layer3, 400f / 8)
    }

    override fun update(dt: Float) {
        super.update(dt)
        asteroidAnimationTimer += dt
    }

    private fun renderAsteroid(gc: GameContainer, img: Image, speed: Float) {
        val offset = (asteroidAnimationTimer * speed).toInt() % img.width
        for (x in -offset until gc.width step img.width) {
            for (y in 0 until gc.height step img.height) {
                img.draw(x, y)
            }
        }
    }
}
