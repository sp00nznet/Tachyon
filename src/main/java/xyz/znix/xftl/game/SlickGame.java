package xyz.znix.xftl.game;

import kotlin.random.Random;
import org.jdom2.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.newdawn.slick.*;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.*;
import xyz.znix.xftl.ai.ShipAI;
import xyz.znix.xftl.crew.AbstractCrew;
import xyz.znix.xftl.crew.CrewNameManager;
import xyz.znix.xftl.crew.LivingCrew;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.math.IPoint;
import xyz.znix.xftl.math.Point;
import xyz.znix.xftl.math.RoomPoint;
import xyz.znix.xftl.sector.*;
import xyz.znix.xftl.shipgen.EnemyShipSpec;
import xyz.znix.xftl.shipgen.ShipGenerator;
import xyz.znix.xftl.systems.*;

import java.util.*;

public class SlickGame extends BasicGame {
    private static ConstPoint PLAYER_SHIP_POSITION = new ConstPoint(50, 150);

    private BlueprintManager blueprintManager;
    private EventManager eventManager;
    private CrewNameManager nameManager;
    private GameMap gameMap;
    private Ship player;
    private Ship enemy;
    private ShipAI enemyAI;
    private final Datafile df;
    private Image floorPlan;
    private Image outside;

    private final Map<String, Image> images = new HashMap<>();
    private final Map<String, SILFontLoader> fonts = new HashMap<>();
    private Animations animations;
    private SoundManager sounds;
    private ShipGenerator generator;
    private LootPool lootPool;

    private Translator translator;

    private Image missingImage;

    private Point tempPoint = new Point(0, 0);

    private Room hoveredRoom;
    private RoomClickListener clickEvent;
    private boolean mouseDownPrev[] = new boolean[3];

    private PlayerShipUI shipUI;
    private HostileShipUI hostileShipUI;

    private boolean enemyIsHostile;
    private boolean enemyInteriorVisible;

    private Beacon currentBeacon;
    private boolean paused;

    // The list of all the sectors the ship has visited, including
    // the current one.
    private final ArrayList<GameMap.SectorInfo> visitedSectors = new ArrayList<>();

    private Image background;
    private Image planet;

    private DebugConsole debugConsole;
    private boolean debugConsoleVisible;

    private float asteroidAnimationTimer;

    public SlickGame(Datafile df) {
        super("Subluminal");
        this.df = df;
    }

    @Override
    public void init(GameContainer container) throws SlickException {
        blueprintManager = new BlueprintManager(df);
        animations = new Animations(df);
        sounds = new SoundManager(df);
        generator = new ShipGenerator(df, blueprintManager);

        translator = new Translator(df, "en");
        eventManager = new EventManager(df, translator, blueprintManager);
        nameManager = new CrewNameManager(df);
        gameMap = new GameMap(df, eventManager);

        blueprintManager.initialiseGame(this);

        loadPlayerShip();
        shipUI = new PlayerShipUI(df, translator, player, this);

        // Start at the first beacon of the first sector
        // Be sure we do this after creating the player ship, it's used by the enemy AI
        // Note that we don't store the current sector anywhere - it's determined via
        // the current beacon.
        Sector firstSector = gameMap.generateSector(gameMap.getSectors().get(0).get(0));
        setCurrentBeacon(firstSector.getStartBeacon());

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

        // Feed inputs to the debug console.
        container.getInput().addKeyListener(new InputAdapter() {
            @Override
            public void keyPressed(int key, char c) {
                if (!debugConsoleVisible)
                    return;

                debugConsole.keyPressed(key, c);
            }
        });
    }

    private void loadPlayerShip() {
        Element playerXml = ((MiscBlueprint) blueprintManager.get("PLAYER_SHIP_HARD")).loadElem(df);
        player = new Ship(df, playerXml, this, null); // Kestral

        for (Element elem : playerXml.getChildren("crewCount")) {
            int count = Integer.parseInt(elem.getAttributeValue("amount").strip());
            String race = elem.getAttributeValue("class");
            for (int i = 0; i < count; i++) {
                player.addCrewMember(race, true, false);
            }
        }
    }

    @Override
    public void update(GameContainer container, int delta) throws SlickException {
        if (!isPaused())
            updateGameState(delta / 1000f);

        shipUI.updateAlways(delta / 1000f);

        asteroidAnimationTimer += delta / 1000f;

        hoveredRoom = null;

        Input in = container.getInput();

        if (in.isKeyPressed(Input.KEY_GRAVE)) {
            if (debugConsole == null) {
                debugConsole = new DebugConsole(this, player);
            }
            debugConsoleVisible = !debugConsoleVisible;
        }

        // Block all key inputs if the debug console is open
        if (!debugConsoleVisible) {
            readKeyboardInput(in);
        } else {
            debugConsole.update(container, delta / 1000f);
        }

        boolean rightClicked = false;

        for (int i = 0; i < 3; i++) {
            boolean prev = mouseDownPrev[i];
            boolean now = in.isMouseButtonDown(i);
            if (now && !prev) {
                if (i == Input.MOUSE_RIGHT_BUTTON) {
                    clickEvent = null;
                    rightClicked = true;
                }

                shipUI.mouseClick(i, in.getMouseX(), in.getMouseY(), PLAYER_SHIP_POSITION);
            } else if (prev && !now) {
                shipUI.mouseUp(i, in.getMouseX(), in.getMouseY(), PLAYER_SHIP_POSITION);
            }
            mouseDownPrev[i] = now;
        }

        shipUI.updateUI(in.getMouseX(), in.getMouseY());

        // Figure out when the player right-clicks
        // an enemy room - this is used for controlling boarders.
        if (rightClicked && enemy != null) {
            tempPoint.setX(container.getInput().getMouseX());
            tempPoint.setY(container.getInput().getMouseY());
            tempPoint.minusAssign(hostileShipUI.getShipPos());
            enemy.screenPosToShipPos(tempPoint);

            RoomPoint rp = enemy.shipToRoomPos(tempPoint);
            if (rp != null) {
                shipUI.enemyRoomRightClicked(rp.getRoom(), enemy);
            }
        }

        // Handle stuff like weapon targeting where the player
        // highlights a room on the enemy (or their) ship.
        if (clickEvent == null)
            return;

        // Hovering over the enemy
        if (enemy != null && enemyInteriorVisible) {
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
            prev.roomClicked(hoveredRoom, container);
        }
    }

    private void readKeyboardInput(Input in) {
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

        if (in.isKeyPressed(Input.KEY_ESCAPE))
            shipUI.escapePressed();

        boolean powerUp = !in.isKeyDown(Input.KEY_LSHIFT);
        if (in.isKeyPressed(Input.KEY_A))
            shipUI.systemPowerHotkeyPressed(Shields.class, powerUp);
        if (in.isKeyPressed(Input.KEY_S))
            shipUI.systemPowerHotkeyPressed(Engines.class, powerUp);
        if (in.isKeyPressed(Input.KEY_F))
            shipUI.systemPowerHotkeyPressed(Oxygen.class, powerUp);
        if (in.isKeyPressed(Input.KEY_D))
            shipUI.systemPowerHotkeyPressed(Medbay.class, powerUp);
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
            // If the enemy ship is neutral but we still have crew inside,
            // allow the user to control and recover them.
            boolean anyLivingBoarders = enemy.getIntruders().stream().anyMatch(crew -> crew instanceof LivingCrew);
            enemyInteriorVisible = enemyIsHostile || anyLivingBoarders;

            hostileShipUI.render(container, g, hoveredRoom, enemyInteriorVisible);
        }

        // Draw the paused text before the UI, so the UI goes on top.
        if (paused) {
            Image pauseImg = getImg("img/Text_pause2.png");
            int imgY = container.getHeight() - 193;
            int imgX = container.getWidth() / 2 - pauseImg.getWidth() / 2;
            pauseImg.draw(imgX, imgY);
        }

        shipUI.renderMenus(container, g);

        if (debugConsoleVisible) {
            debugConsole.render(container, g);
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
        int offset = (int) (asteroidAnimationTimer * speed) % img.getWidth();
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

            // Since we try and limit direct communications between
            // the ships, pass information about the cloak status back
            // and forth here.
            //noinspection ConstantConditions
            player.getWeapons().setOpponentCloakActive(enemy.isCloakActive());
            //noinspection ConstantConditions
            enemy.getWeapons().setOpponentCloakActive(player.isCloakActive());

            if (enemyIsHostile) {
                enemyAI.update(dt);
            }

            if (enemy.isGone()) {
                // Be sure to check if the enemy is still hostile, if we've
                // already killed their crew we can't get a second lot of
                // rewards if they later blow up.
                if (enemy.getSpec() != null && enemyIsHostile) {
                    IEvent event = enemy.getSpec().getDestroyed();
                    if (event != null)
                        shipUI.showEventDialogue(event.resolve());
                }

                setEnemy(null);
                currentBeacon.setShip(null);

                // At this point enemy may be null if they
                // were destroyed, so we can't use that any more.
                // Just returning here keeps things simple.
                return;
            }

            Float escapeTimer = enemy.getEscapeTimer();
            if (escapeTimer != null && escapeTimer <= 0f) {
                // TODO play the jumping away animation
                setEnemy(null);
                currentBeacon.setShip(null);
                return;
            }

            // Check if the enemy crew is dead (including any aboard the player ship).
            boolean anyCrewLeft = enemy.getFriendlyCrew().stream().anyMatch(crew -> crew instanceof LivingCrew);
            boolean anyBoardersLeft = player.getIntruders().stream().anyMatch(crew -> crew instanceof LivingCrew);
            if (!anyCrewLeft && !anyBoardersLeft && !enemy.isAutoScout() && enemyIsHostile) {

                if (enemy.getSpec() != null) {
                    IEvent event = enemy.getSpec().getDeadCrew();
                    if (event != null)
                        shipUI.showEventDialogue(event.resolve());
                }

                enemyIsHostile = false;

                // If we jump back, they shouldn't re-appear.
                // This also means there won't be a danger mark
                // on the map.
                currentBeacon.setShip(null);
            }
        } else {
            //noinspection ConstantConditions
            player.getWeapons().setOpponentCloakActive(false);
        }

        if (!isInDanger()) {
            // If the player isn't fighting a ship, there are no borders (not yet implemented), and they're not
            // in a dangerous environment (eg, an asteroid field) then let them jump instantly.
            player.setFtlChargeProgress(1);
        }
    }

    public void setCurrentBeacon(Beacon currentBeacon) {
        if (this.currentBeacon == null || this.currentBeacon.getSector() != currentBeacon.getSector()) {
            lootPool = new LootPool(blueprintManager, currentBeacon.getSector().getType());
        }

        this.currentBeacon = currentBeacon;

        // Keep track of which sectors we visit, to show on the sector map UI.
        GameMap.SectorInfo sectorInfo = currentBeacon.getSector().getInfo();
        if (!visitedSectors.contains(sectorInfo)) {
            visitedSectors.add(sectorInfo);
        }

        enemyIsHostile = true;
        setEnemy(currentBeacon.getShip());

        // Make the store button appear and disappear.
        shipUI.updateButtons();

        player.resetAfterJump();
        if (currentBeacon.getState() == Beacon.State.UNVISITED) {
            shipUI.showEventDialogue(currentBeacon.getEvent());
        }

        // Spawn in a new rebel elite every jump for overtaken beacons
        if (currentBeacon.getState() == Beacon.State.OVERTAKEN) {
            // See doc/sector-map for information on these events

            Difficulty difficulty = Difficulty.NORMAL; // TODO set this properly
            boolean isAE = true; // TODO set this properly

            String eventName;

            if (currentBeacon.isExit()) {
                eventName = "FLEET_EASY_BEACON";
                if (difficulty != Difficulty.EASY) {
                    // This is correct - use _DLC for non-easy difficulties!
                    eventName += "_DLC";
                }
            } else {
                eventName = "FLEET_EASY";

                if (currentBeacon.getEnvironmentType() == Beacon.EnvironmentType.NEBULA) {
                    eventName += "_NEBULA";
                } else if (isAE) {
                    eventName += "_DLC";
                }
            }

            Event event = eventManager.get(eventName).resolve();
            shipUI.showEventDialogue(event);
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

        // TODO show the rebel fleet in the background if we're at an overtaken beacon
        // TODO show the flagship rebel/fed mixed fight backgrounds

        // TODO load image settings from text tags

        // Note: the new ship is loaded by loadShipEvent, which is called by the event dialogue window.
    }

    public void loadEventShip(Event event) {
        if (event.getLoadShipName() != null) {
            EnemyShipSpec spec = eventManager.getShip(event.getLoadShipName());
            // TODO use the proper difficulty
            int sector = currentBeacon.getSector().getSectorNumber();
            setEnemy(generator.buildShip(this, spec, sector, Difficulty.NORMAL, null));

            // Ships aren't hostile by default
            this.enemyIsHostile = false;
        }
        Boolean hostileState = event.getLoadShipHostile();
        if (hostileState != null) {
            this.currentBeacon.setShip(hostileState ? enemy : null);
            this.enemyIsHostile = hostileState;
        }
    }

    // For use by the debug console
    public void debugSpawnShip(EnemyShipSpec spec, Difficulty difficulty, int sector, int seed) {
        setEnemy(generator.buildShip(this, spec, sector, difficulty, seed));
        currentBeacon.setShip(enemy);
        enemyIsHostile = true;
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

        // Update the drones system, as drones may need to disappear
        // when the enemy ship is changed.
        Drones playerDrones = player.getDrones();
        if (playerDrones != null) {
            playerDrones.enemyShipUpdated();
        }
    }

    public boolean isInDanger() {
        if (enemy != null && enemyIsHostile)
            return true;

        return currentBeacon.getEnvironmentType().isDangerous();
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

    public SoundManager getSounds() {
        return sounds;
    }

    public RoomClickListener getClickEvent() {
        return clickEvent;
    }

    public void setClickEvent(RoomClickListener clickEvent) {
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
     * <p>
     * It's generally better to go through SlickGame or something else to
     * keep the various moving parts separate.
     */
    public PlayerShipUI getShipUI() {
        return shipUI;
    }

    /**
     * Try to avoid using, this is generally indicative of little hacks.
     */
    public Ship getPlayer() {
        return player;
    }

    /**
     * Try to avoid using, this is generally indicative of little hacks.
     */
    public Ship getEnemy() {
        return enemy;
    }

    /**
     * Find the ship that's the enemy of the specified one.
     * <p>
     * This should be used for things like drones and teleporters
     * which need to access the enemy ship if installed on the player,
     * or the player ship if installed on the enemey.
     */
    @Nullable
    public Ship getEnemyOf(Ship source) {
        if (source == player) {
            return enemy;
        } else {
            return player;
        }
    }

    /**
     * Check if the given ship is either the player or enemy.
     * <p>
     * This is intended for drones, which need to self-destruct
     * when the ship powering them is gone (regardless of which
     * ship had the system installed, or who jumped away).
     */
    public boolean isShipPresent(@NotNull Ship ship) {
        Objects.requireNonNull(ship);
        return ship == enemy || ship == player;
    }

    /**
     * Try to avoid using, this is generally indicative of little hacks.
     */
    public IPoint getEnemyPosition() {
        return hostileShipUI.getShipPos();
    }

    public LootPool getLootPool() {
        return lootPool;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public List<GameMap.SectorInfo> getVisitedSectors() {
        return Collections.unmodifiableList(visitedSectors);
    }

    public CrewNameManager getNameManager() {
        return nameManager;
    }

    @NotNull
    public Image getMissingImage() {
        if (missingImage != null)
            return missingImage;

        // Unfortunately we can't use nullResource, since it uses indexed
        // colour and Slick doesn't support that.

        ImageBuffer buffer = new ImageBuffer(64, 64);
        for (int y = 0; y < buffer.getHeight(); y++) {
            for (int x = 0; x < buffer.getWidth(); x++) {
                // Produce a stripe pattern
                boolean secondColour = ((x + y) / 4) % 2 == 0;
                int rg = secondColour ? 255 : 0;

                buffer.setRGBA(x, y, rg, rg, 0, 255);
            }
        }
        missingImage = new Image(buffer);
        return missingImage;
    }

    public void givePlayerResources(@NotNull ResourceSet resources) {
        player.setFuelCount(player.getFuelCount() + resources.getFuel());
        player.setDronesCount(player.getDronesCount() + resources.getDroneParts());
        player.setMissilesCount(player.getMissilesCount() + resources.getMissiles());
        player.setScrap(player.getScrap() + resources.getScrap());

        for (Blueprint item : resources.getItems()) {
            player.addBlueprint(item, true);
        }

        for (AddCrewEval crewSpec : resources.getCrew()) {
            LivingCrew crew = player.addCrewMember(crewSpec.getRace().getName(), false, false);
            crew.setSelectedName(crewSpec.getName());
        }

        for (RemoveCrewEval removed : resources.getLostCrew()) {
            LivingCrew crew = removed.getCrew();
            // TODO clone bay support
            if (removed.getInfo().getTurnHostile()) {
                crew.setMode(crew.getMode().getOther());
            } else {
                crew.removeFromShip();
            }
        }

        // Put all the intruders in the same room, as far as possible.
        int intruderRoomId = Random.Default.nextInt(player.getRooms().size());
        Room intruderRoom = player.getRooms().get(intruderRoomId);

        for (AddCrewEval spec : resources.getIntruders()) {
            LivingCrew intruder = player.addCrewMember(spec.getRace().getName(), false, true);
            intruder.setSelectedName(spec.getName());

            RoomPoint slot = player.findSpaceForCrew(intruderRoom, AbstractCrew.SlotType.INTRUDER);
            intruder.jumpTo(slot.getRoom(), slot);
        }
    }

    /**
     * Advance the fleet pursuit by the amount determined by the current beacon.
     */
    public void advanceFleet() {
        Sector sector = currentBeacon.getSector();
        Point dangerZone = sector.getDangerZoneCentre();

        int advance = sector.getFleetAdvanceFor(currentBeacon);

        dangerZone.setX(dangerZone.getX() + advance);

        for (Beacon beacon : sector.getBeacons()) {
            int distSq = beacon.getPos().distToSq(dangerZone);
            if (distSq > Sector.DANGER_ZONE_RADIUS_SQUARED)
                continue;

            beacon.setOvertaken(true);
        }
    }

    public interface RoomClickListener {
        void roomClicked(Room room, GameContainer gc);
    }
}
