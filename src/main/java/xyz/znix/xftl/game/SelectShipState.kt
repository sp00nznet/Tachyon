package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import org.newdawn.slick.opengl.TextureImpl
import xyz.znix.xftl.*
import xyz.znix.xftl.game.MainGame.GameState
import xyz.znix.xftl.math.Point

class SelectShipState(private val vanillaDF: Datafile, private val main: MainGame) : GameState() {

    private val font = SILFontLoader(vanillaDF, vanillaDF["fonts/c&c.font"])

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

    private val ships = ArrayList<ShipBlueprint>()
    private val hullImages = HashMap<ShipBlueprint, Image>()

    private var hovered: ShipBlueprint? = null

    private val mousePos = Point(0, 0)

    init {
        // TODO do this properly
        scanForShips(vanillaDF["data/blueprints.xml"])
        scanForShips(vanillaDF["data/dlcBlueprintsOverwrite.xml"])
        scanForShips(vanillaDF["data/dlcBlueprints.xml"])

        ships.sortBy { ship ->
            baseShipNames.indexOfFirst { ship.name.startsWith(it) }
        }
    }

    private fun scanForShips(file: FTLFile) {
        val root = vanillaDF.parseXML(file).rootElement
        for (node in root.getChildren("shipBlueprint")) {
            val name = node.getAttributeValue("name")
            var foundMatch = false
            for (prefix in baseShipNames) {
                if (name.startsWith(prefix))
                    foundMatch = true
            }
            if (!foundMatch)
                continue

            val ship = ShipBlueprint(node, file)
            ships.add(ship)

            hullImages[ship] = vanillaDF.readImage("img/ship/${ship.img}_base.png")
        }
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

        if (hovered != null) {
            val img = hullImages.getValue(hovered!!)
            img.draw(x + width + 50f, 100f)
        }
    }

    override fun update(container: GameContainer, delta: Float) {
        val input = container.input
        mousePos.x = input.mouseX
        mousePos.y = input.mouseY
    }

    override fun mouseClicked(button: Int, x: Int, y: Int, clickCount: Int) {
        if (hovered != null) {
            main.startNewGame(hovered!!.name)
        }
    }
}
