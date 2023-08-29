package xyz.znix.xftl.game;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.newdawn.slick.Game;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.Datafile;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.hangar.EditableShip;
import xyz.znix.xftl.hangar.SelectShipState;
import xyz.znix.xftl.rendering.Graphics;
import xyz.znix.xftl.rendering.ShaderProgramme;
import xyz.znix.xftl.sys.GameContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainGame implements Game {
    private final Datafile vanillaDatafile;
    private final CommandLineArgs commandLineArgs;

    private Graphics graphics;

    private GameContainer gameContainer;

    private GameState currentState;

    private InGameState.GameContent content;

    public MainGame(Datafile datafile, CommandLineArgs args) {
        this.vanillaDatafile = datafile;
        this.commandLineArgs = args;
    }

    @Override
    public void init(org.newdawn.slick.GameContainer gc) throws SlickException {
        gameContainer = new GameContainer(gc);

        graphics = new Graphics();
        graphics.markCurrentImageTransformSource();

        // Load the game content immediately - this will work until
        // we support mods or turning Advanced Edition on or off.
        content = new InGameState.GameContent(vanillaDatafile, true);

        if (commandLineArgs.newGameShip != null) {
            // Switch right into a new game
            startNewGame(commandLineArgs.newGameShip, Difficulty.NORMAL, null);
        } else if (commandLineArgs.debugLoad != null) {
            Path path = DebugConsole.DEBUG_SAVE_DIR.resolve(commandLineArgs.debugLoad + ".xml");
            Document doc;
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                @SuppressWarnings("VulnerableCodeUsages") // we set expandEntities
                SAXBuilder builder = new SAXBuilder();
                builder.setExpandEntities(true);
                doc = builder.build(reader);
            } catch (IOException | JDOMException ex) {
                throw new RuntimeException("Failed to load save", ex);
            }

            loadSavedGame(doc);
        } else {
            switchToShipSelect();
        }
    }

    public void switchToShipSelect() {
        SelectShipState state = new SelectShipState(vanillaDatafile, this);
        setCurrentState(state);
    }

    public void startNewGame(@NotNull String shipName, Difficulty difficulty, EditableShip customised) {
        InGameState inGameState = new InGameState(this, content, gameContainer, shipName, difficulty, customised);
        setCurrentState(inGameState);
    }

    public void loadSavedGame(Document savedGame) {
        InGameState inGameState = new InGameState(this, content, gameContainer, savedGame);
        setCurrentState(inGameState);
    }

    @Override
    public void update(org.newdawn.slick.GameContainer gc, int deltaMS) throws SlickException {
        // Convert the delta-time to seconds, from milliseconds.
        float dt = deltaMS / 1000f;

        currentState.update(gameContainer, dt);
    }

    @Override
    public void render(org.newdawn.slick.GameContainer gc, org.newdawn.slick.Graphics slickG) throws SlickException {
        // When we use shaders, we have to transform from pixels to NDC
        // If this is set wrong, all the text etc will be transformed wrong.
        ShaderProgramme.getSHADER_SCREEN_SIZE().set(gameContainer.getWidth(), gameContainer.getHeight());

        // Reset the transform from last frame, in case there was a transform
        // call that wasn't inside a pushTransform block.
        graphics.loadIdentityMatrix();

        currentState.render(gameContainer, graphics);

        // Check there aren't any mismatched pushTransform/popTransform calls.
        graphics.checkNoPushedTransforms();
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

        // If a save created with the debug 'save' command should be loaded, this is it's name.
        public String debugLoad;
    }
}
