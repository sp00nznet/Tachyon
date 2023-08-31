package xyz.znix.xftl.hangar

import xyz.znix.xftl.*
import xyz.znix.xftl.game.Difficulty
import xyz.znix.xftl.game.MainGame
import xyz.znix.xftl.game.ShipBlueprint
import xyz.znix.xftl.math.Point
import xyz.znix.xftl.rendering.Color
import xyz.znix.xftl.rendering.Graphics
import xyz.znix.xftl.rendering.Image
import xyz.znix.xftl.rendering.WindowRenderer
import xyz.znix.xftl.sys.GameContainer
import xyz.znix.xftl.sys.ResourceContext

class SelectShipState(private val vanillaDF: Datafile, private val main: MainGame) : MainGame.GameState() {
    private val resourceContext = ResourceContext()

    val font = SILFontLoader(resourceContext, vanillaDF, vanillaDF["fonts/c&c.font"])
    val fontHL2 = SILFontLoader(resourceContext, vanillaDF, vanillaDF["fonts/HL2.font"])

    val mousePos = Point(0, 0)
    val shipOffset = Point(0, 0)

    // This is the size of the game window, not the full screen - but 'window'
    // also refers to in-game windows.
    val screenSize = Point(0, 0)

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
    val translator: Translator = Translator(vanillaDF, "en")
    val animations: Animations = Animations(vanillaDF)

    val roomImageMeta: RoomImageMeta = RoomImageMeta.loadFromResource()

    val windowRenderer: WindowRenderer

    private val ships: List<ShipBlueprint> = blueprints.blueprints.values
        .filterIsInstance(ShipBlueprint::class.java)
        .filter { it.isPlayerShip }
        .sortedBy { it.name }
        .sortedBy { ship -> baseShipNames.indexOfFirst { ship.name.startsWith(it) } }

    private var hovered: ShipBlueprint? = null

    private val current: EditableShip get() = editor.ship
    private val currentBlueprint: ShipBlueprint get() = blueprints[current.baseBlueprint] as ShipBlueprint

    var isShipEdited = false
        private set

    private val images = HashMap<String, Image>()

    private val editFileControls: EditFileControls

    private var editor: ShipEditor

    private val startGameButton: StartGameButton

    init {
        editor = ShipEditor(this, EditableShip.fromBlueprint(ships.first()))
        startGameButton = StartGameButton(this)
        editFileControls = EditFileControls(this)

        val background = getImg("img/window_base.png")
        val outline = getImg("img/window_outline.png")
        val mask = getImg("img/window_mask.png")
        windowRenderer = WindowRenderer(background, outline, mask)
    }

    override fun shutdown() {
        resourceContext.freeAll()
    }

    override fun render(container: GameContainer, g: Graphics) {
        g.clear(Color.darkGray)

        screenSize.x = container.width
        screenSize.y = container.height

        hovered = null

        val x = 30
        var y = 40
        val width = 175
        val height = 17

        for (ship in ships) {
            val highlighted = mousePos.x in x..x + width && mousePos.y in y..y + height

            if (highlighted) {
                g.colour = Color(50, 50, 150)
                g.fillRect(x.f, y.f, width.f, height.f)
                hovered = ship
            }

            font.drawString(x + 5f, y + 10f, ship.name, Color.white)
            y += height + 3
        }

        startGameButton.draw(g)

        editFileControls.draw(g)

        g.pushTransform()
        shipOffset.x = x + width + 50 - currentBlueprint.hullOffset.x
        shipOffset.y = 100 - currentBlueprint.hullOffset.y
        g.translate(shipOffset.x.f, shipOffset.y.f)
        current.draw(g, this, false)
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
            editor = ShipEditor(this, EditableShip.fromBlueprint(hovered!!))
            return
        }

        // Block editor interactions if we're only inspecting a ship
        if (isShipEdited) {
            editor.mouseClicked(button, x - shipOffset.x, y - shipOffset.y, clickCount)
        }

        startGameButton.mouseClicked(button)

        editFileControls.mouseClicked(button)
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

    override fun mouseWheelMoved(change: Int) {
        editor.mouseWheelMoved(change)
    }

    override fun keyReleased(key: Int, c: Char) {
        editor.keyReleased(key, c)
    }

    fun getImg(path: String): Image {
        return images.getOrPut(path) { vanillaDF.readImage(resourceContext, vanillaDF[path]) }
    }

    fun startGame(selected: Difficulty) {
        val editedShip = if (isShipEdited) editor.ship else null
        main.startNewGame(currentBlueprint.name, selected, editedShip)
    }

    fun startEditingShip() {
        isShipEdited = true
    }

    fun stopEditingShip() {
        isShipEdited = false

        // Re-create the editor to throw away the changes
        editor = ShipEditor(this, EditableShip.fromBlueprint(currentBlueprint))
    }
}
