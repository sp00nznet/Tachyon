package xyz.znix.xftl.hangar

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.opengl.TextureImpl
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.f
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.WindowRenderer

class SelectShipState(private val vanillaDF: Datafile, private val main: MainGame) : MainGame.GameState() {

    val font = SILFontLoader(vanillaDF, vanillaDF["fonts/c&c.font"])

    private val baseShipNames = listOf(
        "PLAYER_SHIP_HARD",
        "PLAYER_SHIP_CIRCLE",
        "PLAYER_SHIP_FED",
        "PLAYER_SHIP_ENERGY",

        "PLAYER_SHIP_MANTIS",
        "PLAYER_SHIP_JELLY",
        "PLAYER_SHIP_ROCK",
        "PLAYER_SHIP_STEALTH",

        "PLAYER_SHIP_ANAEROBIC",
        "PLAYER_SHIP_CRYSTAL"
    )

    val blueprints: BlueprintManager = BlueprintManager(vanillaDF, true)

    val windowRenderer: WindowRenderer

    private val ships: List<ShipBlueprint> = blueprints.blueprints.values
        .filterIsInstance(ShipBlueprint::class.java)
        .filter { it.isPlayerShip }
        .sortedBy { it.name }
        .sortedBy { ship -> baseShipNames.indexOfFirst { ship.name.startsWith(it) } }

    private var hovered: ShipBlueprint? = null

    private var current: EditableShip

    private val mousePos = Point(0, 0)

    private val images = HashMap<String, Image>()

    private var editor: ShipEditor

    private val shipOffset = Point(0, 0)

    init {
        current = EditableShip.fromBlueprint(this, ships.first())
        editor = ShipEditor(this, current)

        val background = getImg("img/window_base.png")
        val outline = getImg("img/window_outline.png")
        val mask = getImg("img/window_mask.png")
        windowRenderer = WindowRenderer(background, outline, mask)
    }

    override fun render(container: GameContainer, g: Graphics) {
        g.background = Color.darkGray
        g.clear()

        TextureImpl.unbind()

        hovered = null

        val x = 30
        var y = 40
        val width = 350
        val height = 17

        for (ship in ships) {
            val highlighted = mousePos.x in x..x + width && mousePos.y in y..y + height

            if (highlighted) {
                g.color = Color(50, 50, 150)
                g.fillRect(x.f, y.f, width.f, height.f)
                hovered = ship
            }

            font.drawString(x + 5f, y + 10f, ship.name, Color.white)
            y += height + 3
        }

        g.pushTransform()
        shipOffset.x = x + width + 50 - current.hullOffset.x
        shipOffset.y = 100 - current.hullOffset.y
        g.translate(shipOffset.x.f, shipOffset.y.f)
        current.draw(g, false)
        editor.editorWidth = container.width - shipOffset.x
        editor.editorHeight = container.height - shipOffset.y
        editor.draw(g)
        g.popTransform()
    }

    override fun update(container: GameContainer, delta: Float) {
        val input = container.input
        mousePos.x = input.mouseX
        mousePos.y = input.mouseY
    }

    override fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        if (hovered != null) {
            current = EditableShip.fromBlueprint(this, hovered!!)
            editor = ShipEditor(this, current)
            // TODO main.startNewGame(hovered!!.name, Difficulty.NORMAL)
            return
        }

        editor.mouseClicked(button, x - shipOffset.x, y - shipOffset.y, clickCount)
    }

    override fun mousePressed(button: Int, x: Int, y: Int) {
        editor.mousePressed(button, x - shipOffset.x, y - shipOffset.y)
    }

    override fun mouseReleased(button: Int, x: Int, y: Int) {
        editor.mouseReleased(button, x - shipOffset.x, y - shipOffset.y)
    }

    override fun mouseDragged(oldX: Int, oldY: Int, newX: Int, newY: Int) {
        editor.mouseDragged(oldX - shipOffset.x, oldY - shipOffset.y, newX - shipOffset.x, newY - shipOffset.y)
    }

    override fun mouseMoved(oldX: Int, oldY: Int, newX: Int, newY: Int) {
        editor.mouseMoved(newX - shipOffset.x, newY - shipOffset.y)
    }

    override fun keyReleased(key: Int, c: Char) {
        editor.keyReleased(key, c)
    }

    fun getImg(path: String): Image {
        return images.getOrPut(path) { vanillaDF.readImage(path) }
    }
}
