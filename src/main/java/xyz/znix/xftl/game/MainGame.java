package xyz.znix.xftl.game;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.Datafile;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.hangar.EditableShip;
import xyz.znix.xftl.hangar.SelectShipState;
import xyz.znix.xftl.rendering.Cursor;
import xyz.znix.xftl.rendering.Graphics;
import xyz.znix.xftl.rendering.ShaderProgramme;
import xyz.znix.xftl.sys.Game;
import xyz.znix.xftl.sys.GameContainer;
import xyz.znix.xftl.sys.PlatformSpecific;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MainGame implements Game {
    private final Datafile vanillaDatafile;
    private final CommandLineArgs commandLineArgs;

    private GameContainer gameContainer;

    private GameState currentState;

    private InGameState.GameContent content;

    private SaveProfile profile;

    private Cursor currentCursor;

    public MainGame(Datafile datafile, CommandLineArgs args) {
        this.vanillaDatafile = datafile;
        this.commandLineArgs = args;
    }

    @Override
    public void init(@NotNull GameContainer gc) throws SlickException {
        gameContainer = gc;

        profile = loadProfile();

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

        // If the profile needs saving, do that now. This means that if there's
        // a bunch of changes in the same update, we won't save multiple times.
        if (profile.getDirty()) {
            saveProfile();
        }
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

        // Update the mouse cursor, if required.
        Cursor newCursor = currentState.getCurrentCursor();
        if (newCursor != currentCursor) {
            gc.setCursor(newCursor);
            currentCursor = newCursor;
        }
    }

    @Override
    public String getTitle() {
        return "Project Wormhole";
    }

    public SaveProfile getProfile() {
        return profile;
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

    private SaveProfile loadProfile() {
        Path profilePath = PlatformSpecific.INSTANCE.getSaveProfilePath();

        if (!Files.exists(profilePath)) {
            return SaveProfile.createBlank();
        }

        // If the profile file does exist, either load it or fail.
        // We *really* don't want to save over the top of it.
        SaveProfile profile = SaveProfile.load(profilePath);

        if (profile == null) {
            // TODO some better behaviour for if/when users encounter this
            JOptionPane.showMessageDialog(
                    null,
                    "Failed to load profile at: " + profilePath,
                    "Failed to load profile",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(0);
        }

        return profile;
    }

    private void saveProfile() {
        // First, save to a temporary file
        Path tempFile = PlatformSpecific.INSTANCE.getSaveGamePath().resolve("profile-save-temp.xml");
        Document doc = profile.save();

        // If we fail to save, don't try again every frame.
        profile.markSaveComplete();

        // Make sure the directory exists
        if (!Files.exists(tempFile.getParent())) {
            try {
                Files.createDirectories(tempFile.getParent());
            } catch (IOException e) {
                // Fine to crash, should happen on startup when we try
                // and save the new, blank profile.
                // If we don't crash, the player might try and play without
                // being able to save their game.
                throw new RuntimeException("Failed to create savegame directory", e);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            XMLOutputter xmlOutput = new XMLOutputter(Format.getPrettyFormat());
            xmlOutput.output(doc, writer);

            // Overwrite the main save file with the newly-written one
            // On most OSes this should be atomic, so we shouldn't be able
            // to get a corrupted profile with this.
            Files.move(tempFile, PlatformSpecific.INSTANCE.getSaveProfilePath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // Hopefully the profile will be saved again, and succeed.
            System.err.println("Failed to save profile:");
            ex.printStackTrace(System.err);
        }
    }

    public static abstract class GameState extends InputAdapter {
        public void init(@NotNull GameContainer container) {
        }

        public abstract void shutdown();

        public abstract void update(@NotNull GameContainer container, float delta) throws SlickException;

        public abstract void render(@NotNull GameContainer container, @NotNull Graphics g) throws SlickException;

        @Nullable
        public Cursor getCurrentCursor() {
            return null;
        }
    }

    public static class CommandLineArgs {
        // If a new game should be started, this is the name of the ship to use.
        public String newGameShip;

        // If a save created with the debug 'save' command should be loaded, this is it's name.
        public String debugLoad;
    }
}
