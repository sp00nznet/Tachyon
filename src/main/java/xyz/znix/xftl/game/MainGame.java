package xyz.znix.xftl.game;

import org.jdom2.Document;
import org.jetbrains.annotations.NotNull;
import org.newdawn.slick.Game;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.Datafile;
import xyz.znix.xftl.rendering.ShaderProgramme;

public class MainGame implements Game {
    private final Datafile vanillaDatafile;
    private final CommandLineArgs commandLineArgs;
    private GameContainer gameContainer;

    private GameState currentState;

    private InGameState.GameContent content;

    public MainGame(Datafile datafile, CommandLineArgs args) {
        this.vanillaDatafile = datafile;
        this.commandLineArgs = args;
    }

    @Override
    public void init(GameContainer gc) throws SlickException {
        this.gameContainer = gc;

        // Load the game content immediately - this will work until
        // we support mods or turning Advanced Edition on or off.
        content = new InGameState.GameContent(vanillaDatafile, true);

        if (commandLineArgs.newGameShip != null) {
            // Switch right into a new game
            startNewGame(commandLineArgs.newGameShip);
        } else {
            switchToShipSelect();
        }
    }

    public void switchToShipSelect() {
        SelectShipState state = new SelectShipState(vanillaDatafile, this);
        setCurrentState(state);
    }

    public void startNewGame(@NotNull String shipName) {
        InGameState inGameState = new InGameState(this, content, gameContainer, shipName);
        setCurrentState(inGameState);
    }

    public void loadSavedGame(Document savedGame) {
        InGameState inGameState = new InGameState(this, content, gameContainer, savedGame);
        setCurrentState(inGameState);
    }

    @Override
    public void update(GameContainer gc, int deltaMS) throws SlickException {
        // Convert the delta-time to seconds, from milliseconds.
        float dt = deltaMS / 1000f;

        currentState.update(gc, dt);
    }

    @Override
    public void render(GameContainer gc, Graphics g) throws SlickException {
        // When we use shaders, we have to transform from pixels to NDC
        // If this is set wrong, all the text etc will be transformed wrong.
        ShaderProgramme.getSHADER_SCREEN_SIZE().set(gc.getWidth(), gc.getHeight());

        currentState.render(gc, g);
    }

    @Override
    public boolean closeRequested() {
        // If we're asked to close, do so.
        return true;
    }

    @Override
    public String getTitle() {
        // TODO come up with a proper name
        return "Codename XFTL";
    }

    /**
     * Save the current game to XML, and re-load it.
     * <p>
     * This is only for development use, to find bugs in the serialisation logic!
     */
    public boolean doSaveLoadGame() {
        InGameState oldGame = (InGameState) currentState;
        InGameState newGame;

        Document savedGame;
        try {
            savedGame = oldGame.saveGameState();
            newGame = new InGameState(this, content, gameContainer, savedGame);
        } catch (Exception ex) {
            oldGame.debugFailedSaveRestore();
            ex.printStackTrace();
            return false;
        }

        // Only change the state once we've done the save-load, so if
        // there's an exception while loading the saved state we stay
        // on the old state for ease of debugging.
        setCurrentState(newGame);

        // Copy over some basic UI stuff
        newGame.debugContinuousSaveRestore(oldGame);

        return true;
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(GameState currentState) {
        this.currentState = currentState;

        gameContainer.getInput().removeAllListeners();
        gameContainer.getInput().addListener(currentState);
    }

    public void quitGame() {
        // Do we need to do anything else?
        gameContainer.exit();
    }

    public static abstract class GameState extends InputAdapter {
        public abstract void update(@NotNull GameContainer container, float delta) throws SlickException;

        public abstract void render(@NotNull GameContainer container, @NotNull Graphics g) throws SlickException;
    }

    public static class CommandLineArgs {
        // If a new game should be started, this is the name of the ship to use.
        public String newGameShip;
    }
}
