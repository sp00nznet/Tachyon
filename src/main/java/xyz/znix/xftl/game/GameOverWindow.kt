package xyz.znix.xftl.game

import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import xyz.znix.xftl.Constants
import xyz.znix.xftl.f
import xyz.znix.xftl.math.ConstPoint

/**
 * This is the window that is displayed when the game ends, either
 * through success or failure.
 */
class GameOverWindow(private val game: InGameState, val outcome: Outcome) : Window() {
    override lateinit var size: ConstPoint
        private set

    private val buttonFont = game.getFont("HL2", 2f)
    private val titleFont = game.getFont("HL2", 4f)
    private val bodyFont = game.getFont("c&cnew", 2f)
    private val scoreFont = game.getFont("c&cnew", 3f)

    private val image = game.getImg("img/scoreUI/gameover_main.png")

    private val btnRestart: Buttons.BasicButton
    private val btnHangar: Buttons.BasicButton
    private val btnMainMenu: Buttons.BasicButton
    private val btnQuit: Buttons.BasicButton
    private val btnStats: Buttons.BasicButton

    private val Button.rightX get() = basePos.x + size.x

    // The number of pixels on either side of a button to draw as part of the side blocks.
    private val buttonMargin = 10

    // These are the sizes of the left and right half of the top of the image
    private val leftWidth = 197 + buttonMargin
    private val rightWidth = 198 + buttonMargin

    private val wrappedMessage: List<String>

    init {
        // Make the bottom row of buttons, which define the width of the window.
        // These are all based on the width of their localised text.
        btnRestart = makeButton(null, 9, 276, "gameover_button_restart", this::restartClicked)
        btnHangar = makeButton(btnRestart, 24, 276, "gameover_button_hangar", this::hangarClicked)
        btnMainMenu = makeButton(btnHangar, 24, 276, "gameover_button_mainmenu", this::mainMenuClicked)
        btnQuit = makeButton(btnMainMenu, 23, 276, "gameover_button_quit", this::quitClicked)

        val width = btnQuit.rightX + 9

        // TODO support languages like German where the stats (STATISTIK) button
        //  is wide enough to change the window width
        size = ConstPoint(width, 309)

        // The stats button grows to fit the available space.
        // Note we go two pixel on either side in past the button margin
        // to make the button fit properly.
        val statsWidth = size.x - leftWidth - rightWidth + buttonMargin * 2 + 2 * 2
        btnStats = Buttons.BasicButton(
            game,
            ConstPoint(leftWidth - buttonMargin - 2, 240), ConstPoint(statsWidth, 24),
            game.translator["gameover_button_stats"],
            4,
            buttonFont, 18, this::statsClicked
        )
        buttons += btnStats

        // Build and wrap the message once
        val bodyKey = when (outcome) {
            Outcome.WIN -> "gameover_win"
            Outcome.LOOSE_HULL -> "gameover_blowup"
            Outcome.LOOSE_CREW -> "gameover_crew"
            Outcome.LOOSE_BASE_DESTROYED -> "gameover_fedbase"
        }
        val bodyStr = game.translator[bodyKey]
        wrappedMessage = bodyFont.wrapString(bodyStr, size.x - 80)
    }

    override fun draw(g: Graphics) {
        // g.color = Color.red
        // g.fillRect(position.x.f, position.y.f, size.x.f, size.y.f)

        // The image is cut up to fit all the localised buttons.

        // Draw the top half
        drawSection(
            -GLOW, -GLOW,
            0, 0,
            GLOW + leftWidth, 279
        )

        drawSection(
            size.x - rightWidth, -GLOW,
            image.width - rightWidth - GLOW, 0,
            rightWidth + GLOW, 279
        )

        val middleWidth = size.x - leftWidth - rightWidth
        // TODO tile this
        drawStretch(
            leftWidth, -GLOW,
            204, 0,
            middleWidth, 279
        )

        // Draw the bottom half
        drawSection(-GLOW, 272, 0, 279, 12, 44)
        drawButtonBox(btnRestart, 12, 279, 109)
        drawStretch(btnRestart.rightX + 4, 272, 121, 272 + GLOW, 16, 44)
        drawButtonBox(btnHangar, 135, 279, 101)
        drawStretch(btnHangar.rightX + 4, 272, 121, 272 + GLOW, 16, 44)
        drawButtonBox(btnMainMenu, 250, 279, 136)
        drawStretch(btnMainMenu.rightX + 4, 272, 121, 272 + GLOW, 15, 44)
        drawButtonBox(btnQuit, 400, 279, 69)
        drawSection(btnQuit.rightX + 4, 272, 469, 279, 12, 44)

        for (button in buttons) {
            button.draw(g)
        }

        // Draw the title
        val titleKey = when (outcome) {
            Outcome.WIN -> "gameover_title_victory"
            else -> "gameover_title_gameover"
        }
        val titleStr = game.translator[titleKey]
        val centreX = position.x + size.x / 2

        titleFont.drawStringCentred(
            centreX.f,
            position.y + 40f,
            0f,
            titleStr, Constants.JUMP_DISABLED_TEXT
        )

        // Draw and wrap the main message

        // Drawing wrapped text is duplicated with the dialogue window,
        // but it's probably not a big problem as I can't see where else
        // this would be required.
        var textY = 92
        for (line in wrappedMessage) {
            bodyFont.drawStringCentred(centreX.f, position.y + textY.f, 0f, line, Color.white)
            textY += 34
        }

        val scoreText = "SCORE: 123"
        scoreFont.drawStringCentred(centreX.f, position.y + 208f, 0f, scoreText, Color.white)
    }

    private fun restartClicked() {
        game.mainGame.startNewGame(game.player.name, game.difficulty)
    }

    private fun statsClicked() {
        TODO("Not yet implemented")
    }

    private fun quitClicked() {
        game.mainGame.quitGame()
    }

    private fun mainMenuClicked() {
        // TODO implement a main menu
        game.mainGame.switchToShipSelect()
    }

    private fun hangarClicked() {
        game.mainGame.switchToShipSelect()
    }

    private fun makeButton(prev: Button?, x: Int, y: Int, key: String, handler: () -> Unit): Buttons.BasicButton {
        val offset = prev?.let { it.pos.x + it.size.x } ?: 0

        val text = game.translator[key]
        val width = 3 + buttonFont.getWidth(text) + 3

        val button = Buttons.BasicButton(
            game,
            ConstPoint(x + offset, y), ConstPoint(width, 24),
            text,
            4,
            buttonFont, 18, handler
        )
        buttons += button

        // Return the right-hand side X of the button
        return button
    }

    // Draw part of the background image.
    private fun drawSection(outX: Int, outY: Int, imgX: Int, imgY: Int, width: Int, height: Int) {
        image.draw(
            position.x.f + outX, position.y.f + outY,
            position.x.f + outX + width, position.y.f + outY + height,

            imgX.f, imgY.f,
            imgX.f + width, imgY.f + height
        )
    }

    private fun drawStretch(outX: Int, outY: Int, imgX: Int, imgY: Int, width: Int, height: Int) {
        // Offset the position 7,7 pixels to account for the glow
        image.draw(
            position.x.f + outX, position.y.f + outY,
            position.x.f + outX + width, position.y.f + outY + height,

            imgX.f, imgY.f,
            imgX.f + 10, imgY.f + height
        )
    }

    private fun drawButtonBox(button: Button, imgX: Int, imgY: Int, imgWidth: Int) {
        val topPadding = 4 // The margin between the button top and the frame top
        val bottomPadding = 4 + 5 + GLOW
        val height = topPadding + button.size.y + bottomPadding

        val outY = button.basePos.y - topPadding

        val sideMargin = 4
        val sideCover = 20 // How far into the button the sides go
        val sideWidth = sideMargin + sideCover

        // Draw the left-hand section
        drawSection(
            button.basePos.x - sideMargin, outY,
            imgX, imgY, sideWidth, height
        )

        // Draw the centre section
        drawStretch(
            button.basePos.x + sideCover, outY,
            imgX + sideWidth, imgY, button.size.x - 2 * sideCover, height
        )

        // Draw the right-hand section
        drawSection(
            button.rightX - sideCover, outY,
            imgX + imgWidth - sideWidth, imgY, sideWidth, height
        )
    }

    enum class Outcome {
        WIN,
        LOOSE_HULL,
        LOOSE_CREW,
        LOOSE_BASE_DESTROYED,
    }

    companion object {
        private const val GLOW: Int = 7
    }
}
