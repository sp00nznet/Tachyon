package xyz.znix.xftl.game;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.Datafile;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.hangar.EditableShip;
import xyz.znix.xftl.hangar.SelectShipState;
import xyz.znix.xftl.rendering.Graphics;
import xyz.znix.xftl.rendering.ShaderProgramme;
import xyz.znix.xftl.sys.Game;
import xyz.znix.xftl.sys.GameContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public void init(@NotNull GameContainer gc) throws SlickException {
        gameContainer = gc;

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

    @Override
    public void shutdown() {
        currentState.shutdown();

        content.freeResources();
    }

    public void switchToShipSelect() {
        SelectShipState state = new SelectShipState(vanillaDatafile, this);
        setCurrentState(state);
    }

    public void startNewGame(@NotNull String shipName, Difficulty difficulty, EditableShip customised) {
        InGameState inGameState = new InGameState(this, content, gameContainer, shipName, difficulty, customised);
        setCurrentState(inGameState);
    }

    public void restartGame() {
        InGameState state = (InGameState) currentState;
        startNewGame(state.getPlayer().getName(), state.getDifficulty(), state.getPlayer().getCustomised());
    }

    public void loadSavedGame(Document savedGame) {
        InGameState inGameState = new InGameState(this, content, gameContainer, savedGame);
        setCurrentState(inGameState);
    }

    @Override
    public void update(@NotNull GameContainer gc, float dt) throws SlickException {
        currentState.update(gc, dt);
    }

    @Override
    public void render(@NotNull GameContainer gc, Graphics g) throws SlickException {
        // When we use shaders, we have to transform from pixels to NDC
        // If this is set wrong, all the text etc will be transformed wrong.
        ShaderProgramme.getSHADER_SCREEN_SIZE().set(gameContainer.getWidth(), gameContainer.getHeight());

        // Reset the transform from last frame, in case there was a transform
        // call that wasn't inside a pushTransform block.
        g.loadIdentityMatrix();

        currentState.render(gc, g);

        // Check there aren't any mismatched pushTransform/popTransform calls.
        g.checkNoPushedTransforms();
    }

    @Override
    public String getTitle() {
        return "Project Wormhole";
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
        if (this.currentState != null) {
            this.currentState.shutdown();
        }

        this.currentState = currentState;

        gameContainer.getInput().removeAllListeners();
        gameContainer.getInput().addListener(currentState);

        currentState.init(gameContainer);
    }

    public void quitGame() {
        // Do we need to do anything else?
        gameContainer.exit();
    }

    public static abstract class GameState extends InputAdapter {
        public void init(@NotNull GameContainer container) {
        }

        public abstract void shutdown();

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
