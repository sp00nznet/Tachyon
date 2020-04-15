package xyz.znix.xftl.game;

import org.jdom2.Element;
import org.jetbrains.annotations.NotNull;
import org.newdawn.slick.*;
import xyz.znix.xftl.*;
import xyz.znix.xftl.ai.ShipAI;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.math.Point;
import xyz.znix.xftl.math.RoomPoint;
import xyz.znix.xftl.sector.*;
import xyz.znix.xftl.shipgen.EnemyShipSpec;
import xyz.znix.xftl.shipgen.ShipGenerator;
import xyz.znix.xftl.systems.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SlickGame extends BasicGame {
    private static ConstPoint PLAYER_SHIP_POSITION = new ConstPoint(50, 150);

    private BlueprintManager blueprintManager;
    private EventManager eventManager;
    private GameMap gameMap;
    private Ship player;
    private Ship enemy;
    private ShipAI enemyAI;
    private final Datafile df;
    private Image floorPlan;
    private Image outside;

    private Map<String, Image> images = new HashMap<>();
    private Map<String, SILFontLoader> fonts = new HashMap<>();
    private Animations animations;
    private ShipGenerator generator;

    private Translator translator;

    private Point tempPoint = new Point(0, 0);

    private Room hoveredRoom;
    private Consumer<Room> clickEvent;

    private PlayerShipUI shipUI;
    private HostileShipUI hostileShipUI;

    private boolean enemyIsHostile;

    private Beacon currentBeacon;
    private boolean paused;

    private Image background;
    private Image planet;

    public SlickGame(Datafile df) throws SlickException {
        super("Subluminal");
        this.df = df;
    }

    @Override
    public void init(GameContainer container) throws SlickException {
        blueprintManager = new BlueprintManager(df);
        animations = new Animations(df);
        generator = new ShipGenerator(df, blueprintManager);

        translator = new Translator(df, "en");
        eventManager = new EventManager(df, translator, blueprintManager);
        gameMap = new GameMap(df, eventManager);

        loadPlayerShip();
        shipUI = new PlayerShipUI(df, translator, player, this);

        // Start at the first beacon of the first sector
        // Be sure we do this after creating the player ship, it's used by the enemy AI
        setCurrentBeacon(gameMap.getSectors()[0].getBeacons().get(0));

        for (Room room : player.getRooms()) {
            AbstractSystem system = room.getSystem();
            if (system == null)
                continue;

            getImg(system.getIcon());

            String img = system.getImg();
            if (img != null)
                getImg(img);
        }

        for (Room r : player.getRooms()) {
            AbstractSystem system = r.getSystem();
            if (system == null)
                continue;

            if (!(system instanceof MainSystem))
                continue;

            getImg("img/icons/s_" + system.getCodename() + "_red1.png");
            getImg("img/icons/s_" + system.getCodename() + "_orange1.png");
            getImg("img/icons/s_" + system.getCodename() + "_grey1.png");
            getImg("img/icons/s_" + system.getCodename() + "_green1.png");
        }
    }

    private void loadPlayerShip() {
        Element playerXml = ((MiscBlueprint) blueprintManager.get("PLAYER_SHIP_HARD")).loadElem(df);
        player = new Ship(df, playerXml, this, null); // Kestral

        for (Element elem : playerXml.getChildren("crewCount")) {
            int count = Integer.parseInt(elem.getAttributeValue("amount").strip());
            String race = elem.getAttributeValue("class");
            for (int i = 0; i < count; i++) {
                player.addCrewMember(race);
            }
        }
    }

    @Override
    public void update(GameContainer container, int delta) throws SlickException {
        if (!isPaused())
            updateGameState(delta / 1000f);

        hoveredRoom = null;

        Input in = container.getInput();

        if (in.isKeyPressed(Input.KEY_SPACE))
            paused = !paused;

        if (in.isKeyPressed(Input.KEY_1))
            shipUI.weaponHotkeyPressed(0);
        if (in.isKeyPressed(Input.KEY_2))
            shipUI.weaponHotkeyPressed(1);
        if (in.isKeyPressed(Input.KEY_3))
            shipUI.weaponHotkeyPressed(2);
        if (in.isKeyPressed(Input.KEY_4))
            shipUI.weaponHotkeyPressed(3);

        boolean powerUp = !in.isKeyDown(Input.KEY_LSHIFT);
        if (in.isKeyPressed(Input.KEY_A))
            shipUI.systemPowerHotkeyPressed(Shields.class, powerUp);
        if (in.isKeyPressed(Input.KEY_S))
            shipUI.systemPowerHotkeyPressed(Engines.class, powerUp);
        if (in.isKeyPressed(Input.KEY_F))
            shipUI.systemPowerHotkeyPressed(Oxygen.class, powerUp);
        if (in.isKeyPressed(Input.KEY_D))
            shipUI.systemPowerHotkeyPressed(Medbay.class, powerUp);

        for (int i = 0; i < 3; i++) {
            if (in.isMousePressed(i)) {
                if (i == Input.MOUSE_RIGHT_BUTTON) {
                    clickEvent = null;
                }

                shipUI.mouseClick(i, in.getMouseX(), in.getMouseY(), PLAYER_SHIP_POSITION);
            }
        }

        shipUI.updateUI(in.getMouseX(), in.getMouseY());

        if (clickEvent == null)
            return;

        // Hovering over the enemy
        if (enemy != null && enemyIsHostile) {
            tempPoint.setX(container.getInput().getMouseX());
            tempPoint.setY(container.getInput().getMouseY());
            tempPoint.minusAssign(hostileShipUI.getShipPos());
            enemy.screenPosToShipPos(tempPoint);

            RoomPoint rp = enemy.shipToRoomPos(tempPoint);
            if (rp != null)
                hoveredRoom = rp.getRoom();
        }

        // Hovering over the player
        tempPoint.setX(container.getInput().getMouseX());
        tempPoint.setY(container.getInput().getMouseY());
        tempPoint.minusAssign(PLAYER_SHIP_POSITION);
        player.screenPosToShipPos(tempPoint);

        RoomPoint rp = player.shipToRoomPos(tempPoint);
        if (rp != null)
            hoveredRoom = rp.getRoom();

        if (hoveredRoom != null && clickEvent != null
                && container.getInput().isMouseButtonDown(Input.MOUSE_LEFT_BUTTON)) {
            var prev = clickEvent;
            clickEvent = null;
            prev.accept(hoveredRoom);
        }
    }

    @Override
    public void render(GameContainer container, Graphics g) throws SlickException {
        renderBackground(container, g);

        // Get the player's ship away from the top UI
        g.translate(PLAYER_SHIP_POSITION.getX(), PLAYER_SHIP_POSITION.getY());
        player.render(g, true, hoveredRoom);
        g.resetTransform();

        shipUI.render(container, g);

        if (enemy != null) {
            hostileShipUI.render(container, g, hoveredRoom, enemyIsHostile);
        }

        shipUI.renderMenus(container, g);

        if (paused) {
            Image pauseImg = getImg("img/Text_pause2.png");
            int imgY = shipUI.getBoxY() - 80;
            int imgX = container.getWidth() / 2 - pauseImg.getWidth() / 2;
            pauseImg.draw(imgX, imgY);
        }
    }

    private void renderBackground(GameContainer gc, Graphics g) throws SlickException {
        if (currentBeacon.getEnvironmentType() == Beacon.EnvironmentType.ASTEROID) {
            // The actual background
            getImg("img/stars/bg_dullstars.png").draw();

            // Rough speeds measured from FTL
            // back img = ~13sec to traverse weapons bar (~400px)
            // middle img = ~10sec
            // foreground img = ~8sec
            renderAsteroid(gc, getImg("img/asteroids/asteroid_back1.png"), 400f / 13);
            renderAsteroid(gc, getImg("img/asteroids/asteroid_back2.png"), 400f / 10);
            renderAsteroid(gc, getImg("img/asteroids/asteroid_back3.png"), 400f / 8);

            return;
        }

        if (background != null) {
            background.draw();
        }
        if (planet != null) {
            planet.draw();
        }
    }

    private void renderAsteroid(GameContainer gc, Image img, float speed) {
        int offset = (int) (System.nanoTime() / 1_000_000_000f * speed) % img.getWidth();
        for (int x = -offset; x < gc.getWidth(); x += img.getWidth()) {
            for (int y = 0; y < gc.getHeight(); y += img.getHeight()) {
                img.draw(x, y);
            }
        }
    }

    private void updateGameState(float dt) {
        shipUI.update(dt);
        player.update(dt);

        if (enemy != null) {
            enemy.update(dt);

            if (enemyIsHostile) {
                enemyAI.update(dt);
            }

            if (enemy.isGone()) {
                if (enemy.getSpec() != null) {
                    IEvent event = enemy.getSpec().getDestroyed();
                    if (event != null)
                        shipUI.showEventDialogue(event.resolve());
                }

                setEnemy(null);
                currentBeacon.setShip(null);
            }
        }

        if (!currentBeacon.getEnvironmentType().isDangerous() && (enemy == null || !enemyIsHostile)) {
            // If the player isn't fighting a ship, there are no borders (not yet implemented), and they're not
            // in a dangerous environment (eg, an asteroid field) then let them jump instantly.
            player.setFtlChargeProgress(1);
        }
    }

    public void setCurrentBeacon(Beacon currentBeacon) {
        this.currentBeacon = currentBeacon;

        enemyIsHostile = true;
        setEnemy(currentBeacon.getShip());

        player.resetAfterJump();
        if (currentBeacon.getState() == Beacon.State.UNVISITED) {
            shipUI.showEventDialogue(currentBeacon.getEvent());
        }

        currentBeacon.setVisited(true);

        background = eventManager.getImageList("BACKGROUND").get(this);
        planet = eventManager.getImageList("PLANET").get(this);

        String environmentBg = currentBeacon.getEnvironmentType().getBackgroundName();
        if (environmentBg != null) {
            background = getImg("img/stars/" + environmentBg + ".png");
            planet = null;
        }

        ImageList planetList = currentBeacon.getEvent().getPlanetImg();
        if (planetList != null)
            planet = planetList.get(this);

        ImageList backList = currentBeacon.getEvent().getBackImg();
        if (backList != null)
            background = backList.get(this);

        // TODO load image settings from text tags

        // Note: the new ship is loaded by loadShipEvent, which is called by the event dialogue window.
    }

    public void loadEventShip(Event event) {
        if (event.getLoadShipName() != null) {
            EnemyShipSpec spec = eventManager.getShip(event.getLoadShipName());
            // TODO use the proper sector number
            setEnemy(generator.buildShip(this, spec, 0));
        }
        Boolean hostileState = event.getLoadShipHostile();
        if (hostileState != null) {
            this.currentBeacon.setShip(hostileState ? enemy : null);
            this.enemyIsHostile = hostileState;
        }
    }

    private void setEnemy(Ship enemy) {
        this.enemy = enemy;

        if (enemy != null) {
            enemyAI = new ShipAI(enemy, player);
            hostileShipUI = new HostileShipUI(this, df, enemy);
        } else {
            enemyAI = null;
            hostileShipUI = null;
        }
    }

    @NotNull
    public Image getImg(String name) {
        Image img = images.get(name);
        if (img != null)
            return img;

        img = df.readImage(name);
        images.put(name, img);
        return img;
    }

    /**
     * Creates a new {@link SILFontLoader} for a given font. This caches the bitmap and character
     * data, but the scale of each returned fontloader is completely independent.
     *
     * @param name The name of the font - the font at 'fonts/$name.font' is loaded
     * @return The new {@link SILFontLoader} instance
     */
    @NotNull
    public SILFontLoader getFont(String name) {
        SILFontLoader font = fonts.get(name);
        if (font != null) {
            // Make a new instance sharing the font data
            return new SILFontLoader(font);
        }

        font = new SILFontLoader(df, df.get("fonts/" + name + ".font"));
        fonts.put(name, font);
        return font;
    }

    /**
     * Create a new {@link SILFontLoader} (see {@link #getFont(String)}), and also set the font's scale
     */
    @NotNull
    public SILFontLoader getFont(String name, float scale) {
        SILFontLoader font = getFont(name);
        font.setScale(scale);
        return font;
    }

    public Animations getAnimations() {
        return animations;
    }

    public Consumer<Room> getClickEvent() {
        return clickEvent;
    }

    public void setClickEvent(Consumer<Room> clickEvent) {
        this.clickEvent = clickEvent;
    }

    public Translator getTranslator() {
        return translator;
    }

    public GameMap getGameMap() {
        return gameMap;
    }

    public Beacon getCurrentBeacon() {
        return currentBeacon;
    }

    public BlueprintManager getBlueprintManager() {
        return blueprintManager;
    }

    public boolean isPaused() {
        return paused || shipUI.isWindowOpen();
    }

    /**
     * Try to avoid using, this is generally indicative of little hacks.
     */
    public PlayerShipUI getShipUI() {
        return shipUI;
    }
}
