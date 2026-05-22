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
import xyz.znix.xftl.VanillaDatafile;
import xyz.znix.xftl.devmenu.DevMenu;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.net.Command;
import xyz.znix.xftl.net.Multiplayer;
import xyz.znix.xftl.hangar.EditableShip;
import xyz.znix.xftl.hangar.SelectShipState;
import xyz.znix.xftl.rendering.Colour;
import xyz.znix.xftl.rendering.Cursor;
import xyz.znix.xftl.rendering.Graphics;
import xyz.znix.xftl.rendering.ShaderProgramme;
import xyz.znix.xftl.sys.DatafileSelectState;
import xyz.znix.xftl.sys.Game;
import xyz.znix.xftl.sys.GameContainer;
import xyz.znix.xftl.sys.PlatformSpecific;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MainGame implements Game {
    private VanillaDatafile vanillaDatafile;
    private Datafile datafile;
    private final CommandLineArgs commandLineArgs;

    private GameContainer gameContainer;

    private GameState currentState;

    private InGameState.GameContent content;

    private SaveProfile profile;

    private Cursor currentCursor;

    /** The always-on developer menu overlay. */
    private DevMenu devMenu;

    // Multiplayer: how often the host streams a snapshot, and client-side
    // tracking of which snapshot has been displayed.
    private static final float SNAPSHOT_INTERVAL = 0.2f;
    private float snapshotTimer;
    private int lastAppliedSnapshot;
    private boolean wasSpectating;

    // --mp-test: drives the client to fire a door-toggle command periodically,
    // so the co-op command round-trip can be verified without clicking.
    private float mpTestTimer;
    private int mpTestCounter;

    // Log the snapshot size once, to keep an eye on co-op bandwidth.
    private boolean snapshotSizeLogged;

    public MainGame(CommandLineArgs args) {
        this.commandLineArgs = args;
    }

    @Override
    public void init(@NotNull GameContainer gc) throws SlickException {
        gameContainer = gc;

        profile = loadProfile();

        // Create the developer menu and register it as the input overlay, so
        // it's available on every screen (including the datafile selector).
        devMenu = new DevMenu(this);
        gc.setInputOverlay(devMenu);

        // If we don't have a valid ftl.dat file to use, open the selection screen
        if (!loadDatafile()) {
            switchToDatafileSelect();
            return;
        }

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

        // Start multiplayer immediately if asked to on the command line.
        if (commandLineArgs.mpHost) {
            Multiplayer.INSTANCE.host();
        } else if (commandLineArgs.mpJoin != null) {
            Multiplayer.INSTANCE.join(commandLineArgs.mpJoin);
        }
    }

    @Override
    public void shutdown() {
        // The state and content might be null if there was an exception during
        // initialisation, as this will be run in a finally block.
        // If we throw an exception here, it'll hide the original one that
        // caused the crash in the first place.
        if (currentState != null) {
            currentState.shutdown();
        }

        if (content != null) {
            content.freeResources();
        }
    }

    public void switchToShipSelect() {
        // This may have been called by DatafileSelectState
        if (vanillaDatafile == null) {
            loadDatafile();
        }

        SelectShipState state = new SelectShipState(datafile, this);
        setCurrentState(state);
    }

    public void switchToDatafileSelect() {
        setCurrentState(new DatafileSelectState(this));
    }

    public void startNewGame(@NotNull String shipName, Difficulty difficulty, EditableShip customised) {
        InGameState inGameState = new InGameState(this, content, shipName, difficulty, customised);
        setCurrentState(inGameState);
    }

    public void restartGame() {
        InGameState state = (InGameState) currentState;
        startNewGame(state.getPlayer().getName(), state.getDifficulty(), state.getPlayer().getCustomised());
    }

    public void loadSavedGame(Document savedGame) {
        InGameState inGameState = new InGameState(this, content, savedGame);
        setCurrentState(inGameState);
    }

    @Override
    public void update(@NotNull GameContainer gc, float dt) throws SlickException {
        boolean spectating = Multiplayer.INSTANCE.isConnected() && !Multiplayer.INSTANCE.getHosting();

        if (spectating) {
            // Client: refresh the displayed game from the host's latest snapshot.
            applyHostSnapshot();
        } else if (wasSpectating) {
            // We were spectating and the connection ended - leave that game.
            switchToShipSelect();
        }
        wasSpectating = spectating;

        // Always update the current state. A co-op client's InGameState has
        // simulate=false, so this only processes its local input - turning
        // clicks into commands - without running the simulation.
        currentState.update(gc, dt);

        // Test harness: drive the client to fire each kind of command in turn.
        if (spectating && commandLineArgs.mpTest && currentState instanceof InGameState) {
            mpTestTimer += dt;
            if (mpTestTimer >= 2f) {
                mpTestTimer = 0f;
                InGameState state = (InGameState) currentState;
                var player = state.getPlayer();
                switch (mpTestCounter % 5) {
                    case 0 -> state.submitCommand(new Command.ToggleDoor(mpTestCounter));
                    case 1 -> {
                        if (player != null && !player.getCrew().isEmpty()
                                && !player.getRooms().isEmpty()) {
                            int roomId = player.getRooms()
                                    .get((mpTestCounter / 5) % player.getRooms().size()).getId();
                            state.submitCommand(new Command.MoveCrew(java.util.List.of(0), roomId));
                        }
                    }
                    case 2 -> {
                        if (player != null && !player.getMainSystems().isEmpty())
                            state.submitCommand(new Command.SetSystemPower(0, mpTestCounter % 10 == 2));
                    }
                    case 3 -> {
                        if (player != null && !player.getHardpoints().isEmpty())
                            state.submitCommand(new Command.SetWeaponArmed(0, mpTestCounter % 10 == 3));
                    }
                    case 4 -> state.submitCommand(new Command.SelectDialogueOption(0));
                }
                mpTestCounter++;
            }
        }

        // Host: apply any co-op commands the connected client has sent.
        if (Multiplayer.INSTANCE.isConnected() && Multiplayer.INSTANCE.getHosting()
                && currentState instanceof InGameState) {
            byte[] data;
            while ((data = Multiplayer.INSTANCE.pollCommand()) != null) {
                Command command = Command.decode(data);
                if (command != null) {
                    System.out.println("Co-op: applying command from client: " + command);
                    command.apply((InGameState) currentState);
                }
            }
        }

        devMenu.update(gc, dt);

        // If the profile needs saving, do that now. This means that if there's
        // a bunch of changes in the same update, we won't save multiple times.
        if (profile.getDirty()) {
            saveProfile();
        }

        // Host: stream the game state to the connected client.
        if (Multiplayer.INSTANCE.isConnected() && Multiplayer.INSTANCE.getHosting()) {
            snapshotTimer += dt;
            if (snapshotTimer >= SNAPSHOT_INTERVAL) {
                snapshotTimer = 0f;
                sendHostSnapshot();
            }
        }

        // Co-op: share this player's cursor with the peer, as a fraction of
        // the window so it maps across differently sized windows.
        if (Multiplayer.INSTANCE.isConnected()) {
            int w = gc.getWidth();
            int h = gc.getHeight();
            if (w > 0 && h > 0) {
                Multiplayer.INSTANCE.sendCursor(
                        gc.getInput().getMouseX() / (float) w,
                        gc.getInput().getMouseY() / (float) h);
            }
        }
    }

    /** Host: serialise the current game and stream it to the client. */
    private void sendHostSnapshot() {
        if (!(currentState instanceof InGameState))
            return;
        try {
            Document doc = ((InGameState) currentState).saveGameState();
            // The snapshot is XML, which compresses heavily - gzip it so the
            // stream stays light enough for internet play.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                new XMLOutputter(Format.getRawFormat()).output(doc, gzip);
            }
            if (!snapshotSizeLogged) {
                snapshotSizeLogged = true;
                System.out.println("Co-op: snapshot " + bytes.size() + " bytes (gzipped)");
            }
            Multiplayer.INSTANCE.sendSnapshot(bytes.toByteArray());
        } catch (Exception ex) {
            System.err.println("Failed to serialise a multiplayer snapshot");
            ex.printStackTrace();
        }
    }

    /** Client: rebuild and display the latest snapshot the host sent. */
    private void applyHostSnapshot() {
        int version = Multiplayer.INSTANCE.getSnapshotVersion();
        if (version == lastAppliedSnapshot)
            return;
        byte[] data = Multiplayer.INSTANCE.getLatestSnapshot();
        if (data == null)
            return;
        lastAppliedSnapshot = version;

        // Rebuilding the game discards the old UI, so remember the local
        // player's selection and carry it onto the new one.
        PlayerShipUI previousUI = (currentState instanceof InGameState)
                ? ((InGameState) currentState).getShipUI() : null;

        try {
            SAXBuilder builder = new SAXBuilder();
            builder.setExpandEntities(false);
            Document doc = builder.build(
                    new GZIPInputStream(new ByteArrayInputStream(data)));
            loadSavedGame(doc);
            if (currentState instanceof InGameState) {
                InGameState newState = (InGameState) currentState;
                // The client renders this snapshot; it must not simulate it.
                newState.setSimulate(false);
                // Carry the local UI state (selection, resource counters)
                // onto the rebuilt UI. previousUI is null on the first
                // snapshot, which carryOverFrom handles.
                if (newState.getShipUI() != null)
                    newState.getShipUI().carryOverFrom(previousUI);
            }
        } catch (Exception ex) {
            System.err.println("Failed to load a multiplayer snapshot");
            ex.printStackTrace();
        }
    }

    @Override
    public void render(@NotNull GameContainer gc, Graphics g) throws SlickException {
        // Pass 1: the game, drawn into the window area below the menu bar.
        gc.setGameViewport();

        // When we use shaders, we have to transform from pixels to NDC
        // If this is set wrong, all the text etc will be transformed wrong.
        ShaderProgramme.getSHADER_SCREEN_SIZE().set(gameContainer.getWidth(), gameContainer.getHeight());

        // Reset the transform from last frame, in case there was a transform
        // call that wasn't inside a pushTransform block.
        g.loadIdentityMatrix();

        currentState.render(gc, g);

        // Co-op: draw the connected player's cursor over the shared game,
        // while it is within their window.
        if (Multiplayer.INSTANCE.isConnected() && Multiplayer.INSTANCE.getHasRemoteCursor()) {
            float rx = Multiplayer.INSTANCE.getRemoteCursorX();
            float ry = Multiplayer.INSTANCE.getRemoteCursorY();
            if (rx >= 0f && rx <= 1f && ry >= 0f && ry <= 1f) {
                g.loadIdentityMatrix();
                drawRemoteCursor(g, rx * gameContainer.getWidth(),
                        ry * gameContainer.getHeight());
            }
        }

        // Check there aren't any mismatched pushTransform/popTransform calls.
        g.checkNoPushedTransforms();

        // Pass 2: the developer menu, drawn over the whole window so its bar
        // sits above the game and its dropdowns can extend down over it.
        gc.setMenuViewport();
        ShaderProgramme.getSHADER_SCREEN_SIZE().set(gameContainer.getWidth(), DevMenu.CANVAS_HEIGHT);
        g.loadIdentityMatrix();
        devMenu.render(gc, g);

        // Update the mouse cursor, if required.
        Cursor newCursor = currentState.getCurrentCursor();
        if (newCursor != currentCursor) {
            gc.setCursor(newCursor);
            currentCursor = newCursor;
        }
    }

    /** Draw the co-op partner's mouse cursor as a small amber crosshair. */
    private void drawRemoteCursor(Graphics g, float fx, float fy) {
        int x = Math.round(fx);
        int y = Math.round(fy);
        g.setColour(new Colour(1f, 0.75f, 0.2f, 0.9f));

        int reach = 10;
        int gap = 3;
        g.drawLine(x - reach, y, x - gap, y);
        g.drawLine(x + gap, y, x + reach, y);
        g.drawLine(x, y - reach, x, y - gap);
        g.drawLine(x, y + gap, x, y + reach);
        g.fillRect(x - 1, y - 1, 2, 2);
    }

    @Override
    public String getTitle() {
        return "Tachyon";
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
            newGame = new InGameState(this, content, savedGame);
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

    private boolean loadDatafile() {
        Path ftlDat = findFtlDat();
        if (ftlDat == null) {
            switchToDatafileSelect();
            return false;
        }
        vanillaDatafile = new VanillaDatafile(ftlDat.toFile());

        // TODO make mods adjustable in the ship select screen
        datafile = Datafile.Companion.loadWithMods(vanillaDatafile);

        // Load the game content immediately - this will work until
        // we support mods or turning Advanced Edition on or off.
        content = new InGameState.GameContent(datafile, true);

        return true;
    }

    public static Path findFtlDat() {
        // Let the user override the path with system properties
        String override = System.getProperty("xftl.datafile-path");
        if (override != null) {
            return Path.of(override);
        }
        override = System.getenv("XFTL_DATAFILE");
        if (override != null) {
            return Path.of(override);
        }

        // There's a text file which contains the path to the ftl.dat file
        Path ftlPathFile = PlatformSpecific.INSTANCE.getFtlDatPathFile();
        if (!Files.isRegularFile(ftlPathFile)) {
            // Create the file, so the user can find and edit it
            try {
                Files.writeString(ftlPathFile, "Put the path to ftl.dat here", StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("Failed to write placeholder to: " + ftlPathFile);
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }

            return null;
        }

        String rawPath;
        try {
            rawPath = Files.readString(ftlPathFile).strip();
        } catch (IOException e) {
            // Abort in this case, since there's probably something valid there.
            throw new RuntimeException(e);
        }

        Path path;
        try {
            path = Path.of(rawPath);
        } catch (InvalidPathException ignored) {
            System.err.printf("Invalid ftl.dat path (could not parse): '%s'%n", rawPath);
            return null;
        }

        if (!Files.isRegularFile(path)) {
            System.err.printf("Invalid ftl.dat path: '%s'%n", rawPath);
            return null;
        }

        return path;
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

        // Start hosting a multiplayer game immediately.
        public boolean mpHost;

        // Join a multiplayer game at this address immediately.
        public String mpJoin;

        // Co-op test harness: auto-fire door-toggle commands while spectating.
        public boolean mpTest;
    }
}
