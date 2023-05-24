package xyz.znix.xftl.game;

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

    public MainGame(Datafile datafile, CommandLineArgs args) {
        this.vanillaDatafile = datafile;
        this.commandLineArgs = args;
    }

    @Override
    public void init(GameContainer gc) throws SlickException {
        this.gameContainer = gc;

        if (commandLineArgs.newGameShip != null) {
            // Switch right into a new game
            startNewGame(commandLineArgs.newGameShip);
        } else {
            SelectShipState state = new SelectShipState(vanillaDatafile, this);
            state.init(gc);
            setCurrentState(state);
        }
    }

    public void startNewGame(@NotNull String shipName) throws SlickException {
        InGameState inGameState = new InGameState(vanillaDatafile, shipName);
        inGameState.init(gameContainer);
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

    public GameState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(GameState currentState) {
        this.currentState = currentState;

        gameContainer.getInput().removeAllListeners();
        gameContainer.getInput().addListener(currentState);
    }

    public static abstract class GameState extends InputAdapter {
        public abstract void init(@NotNull GameContainer container) throws SlickException;

        public abstract void update(@NotNull GameContainer container, float delta) throws SlickException;

        public abstract void render(@NotNull GameContainer container, @NotNull Graphics g) throws SlickException;
    }

    public static class CommandLineArgs {
        // If a new game should be started, this is the name of the ship to use.
        public String newGameShip;
    }
}
