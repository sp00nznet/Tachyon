package xyz.znix.xftl.game;

import kotlin.random.Random;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.ImageBuffer;
import org.newdawn.slick.Input;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.util.InputAdapter;
import xyz.znix.xftl.*;
import xyz.znix.xftl.ai.ShipAI;
import xyz.znix.xftl.crew.*;
import xyz.znix.xftl.devutil.DebugConsole;
import xyz.znix.xftl.devutil.DebugFlagManager;
import xyz.znix.xftl.hangar.EditableShip;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.math.IPoint;
import xyz.znix.xftl.math.Point;
import xyz.znix.xftl.math.RoomPoint;
import xyz.znix.xftl.rendering.Graphics;
import xyz.znix.xftl.rendering.Image;
import xyz.znix.xftl.rendering.TextureLoader;
import xyz.znix.xftl.rendering.WindowRenderer;
import xyz.znix.xftl.savegame.ObjectRefs;
import xyz.znix.xftl.savegame.RefLoader;
import xyz.znix.xftl.savegame.SaveUtil;
import xyz.znix.xftl.sector.*;
import xyz.znix.xftl.shipgen.EnemyShipSpec;
import xyz.znix.xftl.shipgen.ShipGenerator;
import xyz.znix.xftl.systems.*;

import java.util.*;
import java.util.stream.Collectors;

public class InGameState extends MainGame.GameState {
    private static final ConstPoint PLAYER_SHIP_POSITION = new ConstPoint(100, 150);

    private final MainGame mainGame;

    private final GameContent content;

    private Difficulty difficulty = Difficulty.NORMAL;

    // These are copied from the GameContent object for convenience
    private final Datafile df;
    private final boolean enableAdvancedEdition;
    private final BlueprintManager blueprintManager;
    private final EventManager eventManager;

    private GameMap gameMap;
    private Ship player;
    private Ship enemy;
    private ShipAI enemyAI;

    private LootPool lootPool;

    private Image missingImage;

    private final Point tempPoint = new Point(0, 0);

    private Room hoveredRoom;
    private RoomClickListener clickEvent;
    private final boolean[] mouseDownPrev = new boolean[3];

    private PlayerShipUI shipUI;
    private HostileShipUI hostileShipUI;

    private boolean enemyIsHostile;
    private boolean enemyInteriorVisible;

    private Beacon currentBeacon;
    private boolean paused;

    // The list of all the sectors the ship has visited, including
    // the current one.
    private final ArrayList<GameMap.SectorInfo> visitedSectors = new ArrayList<>();

    private DebugConsole debugConsole;
    private boolean debugConsoleVisible;

    private DebugFlagManager debugFlags = new DebugFlagManager();

    private boolean isCurrentlyLoadingSave;

    /**
     * The quest events that couldn't be fit in the current sector,
     * and were delayed to the next one.
     */
    private final ArrayList<Event> delayedQuests = new ArrayList<>();

    public InGameState(MainGame mainGame, GameContent content, GameContainer container, String playerShipName, Difficulty difficulty, EditableShip customised) {
        this(mainGame, content, container);
        this.difficulty = difficulty;

        gameMap = new GameMap(df, eventManager, enableAdvancedEdition, Random.Default);

        createNewPlayerShip(playerShipName, customised);
        shipUI = new PlayerShipUI(player, this);

        // Start at the first beacon of the first sector
        // Be sure we do this after creating the player ship, it's used by the enemy AI
        // Note that we don't store the current sector anywhere - it's determined via
        // the current beacon.
        Sector firstSector = gameMap.generateSector(gameMap.getSectors().get(0).get(0));
        setCurrentBeacon(firstSector.getStartBeacon());
    }

    /**
     * Load a previously-saved game.
     */
    public InGameState(MainGame mainGame, GameContent content, GameContainer container, Document saveGame) {
        this(mainGame, content, container);

        Element root = saveGame.getRootElement();
        if (!root.getName().equals("xftlSaveGame")) {
            throw new IllegalArgumentException("Bad save-game root element name: " + root.getName());
        }

        // If AE was enabled on the save, make sure our content also has it.
        boolean saveEnabledAE = Boolean.parseBoolean(saveGame.getRootElement().getAttributeValue("enableAE"));
        if (saveEnabledAE != content.enableAdvancedEdition) {
            throw new IllegalArgumentException("Cannot load a game with content that doesn't match it's AE state!");
        }

        difficulty = Difficulty.valueOf(root.getAttributeValue("difficulty"));

        isCurrentlyLoadingSave = true;
        loadGameState(root);
        isCurrentlyLoadingSave = false;

        // This just loads stuff from XML, we don't need to serialise it
        lootPool = new LootPool(blueprintManager, currentBeacon.getSector().getType());

        // Give all the ships an update, for stuff like the
        // doors automatically opening. This must only be done once all
        // the ships are loaded, otherwise it'd break hacking since the
        // hacked system would think it wasn't being hacked any more.
        for (Beacon beacon : currentBeacon.getSector().getBeacons()) {
            if (beacon.getShip() != null)
                beacon.getShip().update(0f);
        }
        player.update(0f);
    }

    private InGameState(MainGame mainGame, GameContent content, GameContainer container) {
        this.mainGame = mainGame;
        this.content = content;

        // Load up the convenience variables
        df = content.datafile;
        enableAdvancedEdition = content.enableAdvancedEdition;
        blueprintManager = content.blueprintManager;
        eventManager = content.eventManager;

        // Load a bunch of images that we'll need, to make them available for later.
        List<SystemBlueprint> systems = blueprintManager.getBlueprints().values().stream()
                .filter(bp -> bp instanceof SystemBlueprint)
                .map(bp -> (SystemBlueprint) bp)
                .collect(Collectors.toList());
        for (SystemBlueprint system : systems) {
            getImg(system.getRoomIconPath());
        }

        for (SystemBlueprint system : systems) {
            getImg("img/icons/s_" + system.getType() + "_red1.png");
            getImg("img/icons/s_" + system.getType() + "_orange1.png");
            getImg("img/icons/s_" + system.getType() + "_grey1.png");
            getImg("img/icons/s_" + system.getType() + "_green1.png");
        }

        // Feed inputs to the debug console.
        container.getInput().addListener(new InputAdapter() {
            @Override
            public void keyPressed(int key, char c) {
                if (!debugConsoleVisible)
                    return;

                getDebugConsole().keyPressed(key, c);
            }

            @Override
            public void mouseWheelMoved(int change) {
                if (!debugConsoleVisible)
                    return;

                getDebugConsole().mouseWheelMoved(change);
            }

            @Override
            public void mousePressed(int button, int x, int y) {
                if (debugConsoleVisible)
                    debugConsole.mousePressed(button, x, y);
            }

            @Override
            public void mouseReleased(int button, int x, int y) {
                if (debugConsoleVisible)
                    debugConsole.mouseReleased(button, x, y);
            }

            @Override
            public void mouseDragged(int oldX, int oldY, int newX, int newY) {
                if (debugConsoleVisible)
                    debugConsole.mouseDragged(oldX, oldY, newX, newY);
            }
        });
    }

    private void createNewPlayerShip(String shipName, EditableShip customised) {
        ShipBlueprint blueprint = (ShipBlueprint) blueprintManager.get(shipName);
        Element playerXml = blueprint.loadElem(df);
        player = new Ship(blueprint, this, customised, null);
        player.loadDefaultContents();

        for (Element elem : playerXml.getChildren("crewCount")) {
            int count = Integer.parseInt(elem.getAttributeValue("amount").strip());
            String raceName = elem.getAttributeValue("class");
            CrewBlueprint race = (CrewBlueprint) blueprintManager.get(raceName);
            for (int i = 0; i < count; i++) {
                LivingCrewInfo info = LivingCrewInfo.generateRandom(race, this);
                player.addCrewMember(info, true, false);
            }
        }
    }

    @Override
    public void update(@NotNull GameContainer container, float delta) throws SlickException {
        if (!isPaused())
            updateGameState(delta);

        Input in = container.getInput();

        // For debugging, this lets you either step or fast-forward through time.
        if (container.getInput().isKeyDown(Input.KEY_TAB))
            updateGameState(delta * 4);
        if (container.getInput().isKeyPressed(Input.KEY_PERIOD))
            updateGameState(0.01f);

        shipUI.updateAlways(delta);

        hoveredRoom = null;

        content.sounds.updateLoopedSounds(isPaused());

        if (in.isKeyPressed(Input.KEY_GRAVE)) {
            debugConsoleVisible = !debugConsoleVisible;

            // Clear out any pending key presses, so keys pressed while
            // the console is open don't have an effect once it's closed.
            in.clearControlPressedRecord();
            in.clearKeyPressedRecord();
            in.clearMousePressedRecord();
        }

        // Block all key inputs if the debug console is open
        if (!debugConsoleVisible) {
            readKeyboardInput(in);
        } else {
            getDebugConsole().update(container, delta);
        }

        boolean rightClicked = false;

        for (int i = 0; i < 3; i++) {
            boolean prev = mouseDownPrev[i];
            boolean now = in.isMouseButtonDown(i);

            // Block mouse input while the debug console is open, to avoid
            // accidentally clicking on something under the console.
            if (debugConsoleVisible) {
                now = false;
            }

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

        shipUI.updateUI(in.getMouseX(), in.getMouseY(), PLAYER_SHIP_POSITION);

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

        updateClickEvent(container);

        // Hammer away at the serialisation logic, if that flag is set.
        // Only do it while un-paused, since we can't properly use
        // the console if we're constantly reloading.
        if (debugFlags.getContinuousSaveLoad().getSet() && !isPaused()) {
            mainGame.doSaveLoadGame();
        }
    }

    private void updateClickEvent(GameContainer container) {
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

        if (hoveredRoom != null && clickEvent != null && mouseDownPrev[Input.MOUSE_LEFT_BUTTON]) {
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

        if (in.isKeyPressed(Input.KEY_Z))
            shipUI.openAllDoors();
        if (in.isKeyPressed(Input.KEY_X))
            shipUI.closeAllDoors();
    }

    @Override
    public void render(@NotNull GameContainer container, @NotNull Graphics g) throws SlickException {
        currentBeacon.getEnvironment(this).renderBackground(container, g);

        // Get the player's ship away from the top UI
        g.pushTransform();
        g.translate(PLAYER_SHIP_POSITION.getX(), PLAYER_SHIP_POSITION.getY());
        player.render(g, true, hoveredRoom);
        player.renderTargeting(g, Objects.requireNonNull(player.getWeapons()).getSelectedTargets());
        g.popTransform();

        shipUI.render(container, g);

        if (enemy != null) {
            // If the enemy ship is neutral but we still have crew inside,
            // allow the user to control and recover them.
            boolean anyLivingBoarders = enemy.hasCrewOwnedByShip(player);
            enemyInteriorVisible = enemyIsHostile || anyLivingBoarders;

            hostileShipUI.render(container, g, hoveredRoom, enemyInteriorVisible, enemyIsHostile);
        }

        // Draw the paused text before the UI, so the UI goes on top.
        if (paused) {
            Image pauseImg = getImg("img/Text_pause2.png");
            int imgY = container.getHeight() - 193;
            int imgX = container.getWidth() / 2 - pauseImg.getWidth() / 2;
            pauseImg.draw(imgX, imgY);
        }

        // Draw solar flares and pulsar pulses
        currentBeacon.getEnvironment(this).renderOverlay(container, g);

        shipUI.renderMenus(container, g);

        if (debugConsoleVisible) {
            getDebugConsole().render(container, g);
        }
    }

    private void updateGameState(float dt) {
        currentBeacon.getEnvironment(this).update(dt);

        shipUI.update();
        player.update(dt);

        if (enemy != null) {
            enemy.update(dt);

            // Since we try and limit direct communications between
            // the ships, pass information about the cloak status back
            // and forth here.
            player.setOpponentCloakActive(enemy.isCloakActive());
            enemy.setOpponentCloakActive(player.isCloakActive());

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

                if (enemy.isFlagship()) {
                    onFlagshipKilled();
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
                // TODO show the gotaway event
                return;
            }

            // Check if the enemy crew is dead (including any aboard the player ship).
            // Note we check the crew owners, so that mind-controlling the last
            // crew doesn't break it.
            boolean anyCrewLeft = enemy.hasCrewOwnedByShip(enemy);
            boolean anyBoardersLeft = player.hasCrewOwnedByShip(enemy);
            if (!anyCrewLeft && !anyBoardersLeft && !enemy.isAutoScout() && enemyIsHostile) {
                if (enemy.getSpec() != null) {
                    IEvent event = enemy.getSpec().getDeadCrew();
                    if (event != null)
                        shipUI.showEventDialogue(event.resolve());
                } else if (enemy.isFlagship()) {
                    // TODO turn the enemy into an autoscout
                    // Event event = eventManager.get("BOSS_AUTOMATED").resolve();
                    // shipUI.showEventDialogue(event);
                }

                enemyIsHostile = false;

                // If we jump back, they shouldn't re-appear.
                // This also means there won't be a danger mark
                // on the map.
                currentBeacon.setShip(null);
            }
        } else {
            player.setOpponentCloakActive(false);
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

            // Add any quests we didn't have time for last time
            for (Event quest : delayedQuests) {
                currentBeacon.getSector().addQuest(currentBeacon, quest, true);
            }
            delayedQuests.clear();
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
                } else if (enableAdvancedEdition) {
                    eventName += "_DLC";
                }
            }

            Event event = eventManager.get(eventName).resolve();
            shipUI.showEventDialogue(event);
        }

        // If the flagship is here, spawn it in. Doing this last overwrites
        // the rebel elite that's already been spawned.
        if (currentBeacon == currentBeacon.getSector().getFlagshipBeacon()) {
            spawnFlagship();
        }

        currentBeacon.setVisited(true);

        // Note: the new ship is loaded by loadShipEvent, which is called by the event dialogue window.
    }

    private void spawnFlagship() {
        int stage = currentBeacon.getSector().getFlagshipStage();

        // If there's a ship at the current beacon, get rid of it. This avoids
        // it sticking around after the flagship leaves, and saves space
        // in the serialised game.
        // Note the flagship isn't marked as belonging to a beacon, since it's
        // generated whenever the player jumps there.
        currentBeacon.setShip(null);

        // The ship name is in the format of BOSS_1_NORMAL_DLC
        String shipName = String.format("BOSS_%d_%s", stage, difficulty);
        if (content.enableAdvancedEdition) {
            shipName += "_DLC";
        }

        ShipBlueprint blueprint = (ShipBlueprint) blueprintManager.get(shipName);
        Ship flagship = new Ship(blueprint, this, null, null);
        flagship.loadDefaultContents();

        // TODO handle crew being killed across fights
        for (int i = 0; i < 11; i++) {
            CrewBlueprint race = (CrewBlueprint) blueprintManager.get("human");
            LivingCrewInfo info = LivingCrewInfo.generateRandom(race, this);
            LivingCrew crew = flagship.addCrewMember(info, true, false);

            // Put some crew in the artillery rooms
            for (Artillery artillery : flagship.getArtillery()) {
                Room room = Objects.requireNonNull(artillery.getRoom());

                boolean anyInRoom = flagship.getCrew().stream().anyMatch(c -> c.getRoom() == room);
                if (anyInRoom)
                    continue;

                crew.jumpTo(room, ConstPoint.ZERO);
            }
        }

        setEnemy(flagship);
        enemyIsHostile = true;

        Event event = eventManager.get("BOSS_TEXT_" + stage).resolve();
        shipUI.showEventDialogue(event);
    }

    private void onFlagshipKilled() {
        Sector sector = currentBeacon.getSector();
        sector.updateFlagshipNextBeacon();

        // If we're not at the base, continue on our path there.
        // We have to set the is-jumping flag, otherwise the flagship
        // could wait at the same beacon as the player.
        if (sector.getFlagshipNextBeacon() != null) {
            sector.setFlagshipJumping(true);
        } else {
            // The flagship is at the base, force it to jump away.
            List<Beacon> adjacent = currentBeacon.getNeighbours();
            Beacon next = adjacent.get(Random.Default.nextInt(adjacent.size()));

            sector.setFlagshipRunningAway(true);
            sector.setFlagshipJumping(true);
            sector.setFlagshipNextBeacon(next);
        }

        int stageKilled = sector.getFlagshipStage();
        if (stageKilled == 3) {
            shipUI.showGameOverScreen(GameOverWindow.Outcome.WIN);
        } else {
            sector.setFlagshipStage(stageKilled + 1);
        }
    }

    public void loadEventShip(Event event) {
        if (event.getLoadShipName() != null) {
            EnemyShipSpec spec = eventManager.getShip(event.getLoadShipName());
            int sector = currentBeacon.getSector().getSectorNumber();
            setEnemy(content.generator.buildShip(this, spec, sector, difficulty, null));

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
        setEnemy(content.generator.buildShip(this, spec, sector, difficulty, seed));
        currentBeacon.setShip(enemy);
        enemyIsHostile = true;
    }

    private void setEnemy(Ship enemy) {
        this.enemy = enemy;

        if (enemy != null) {
            enemyAI = new ShipAI(enemy, player);
            hostileShipUI = new HostileShipUI(this, enemy);
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

    // This is, somewhat ironically, only used by the debug console itself.
    public void reloadDebugConsole() {
        debugConsole = null;
        debugConsoleVisible = false;
    }

    public void reloadDebugFlags() {
        debugFlags = new DebugFlagManager();
    }

    public boolean isInDanger() {
        if (enemy != null && enemyIsHostile)
            return true;

        return currentBeacon.getEnvironmentType().isDangerous();
    }

    /**
     * Add a quest event marker, as required by an event.
     */
    @NotNull
    public QuestAddResult addQuest(Event questEvent) {
        Sector sector = currentBeacon.getSector();
        boolean wasAdded = sector.addQuest(currentBeacon, questEvent, false);

        if (wasAdded) {
            return QuestAddResult.CURRENT_SECTOR;
        } else if (sector.getSectorNumber() >= 6) {
            // If we're on sector 7 (6 when zero-indexed) or later,
            // the quest is skipped as we shouldn't put quests into
            // the last stand.
            return QuestAddResult.TOO_LATE;
        } else {
            delayedQuests.add(questEvent);
            return QuestAddResult.NEXT_SECTOR;
        }
    }

    /**
     * Save the entire game state to a new XML document.
     * <p>
     * This can then be trivially serialised, and loaded back into
     * a new {@link InGameState} instance.
     */
    public Document saveGameState() {
        Element root = new Element("xftlSaveGame");
        root.setAttribute("enableAE", Boolean.toString(enableAdvancedEdition));
        root.setAttribute("difficulty", difficulty.name());

        ObjectRefs refs = new ObjectRefs();
        Sector sector = currentBeacon.getSector();

        // Generate IDs for all known ships
        refs.register(player, "player");
        for (Beacon beacon : sector.getBeacons()) {
            refs.register(beacon.getShip(), "ship");
        }
        if (enemy != null && enemy.isFlagship()) {
            refs.register(enemy, "flagship");
        }

        // If the player ship has a custom layout, save that.
        if (player.getCustomised() != null) {
            Element customLayout = new Element("customLayout");
            player.getCustomised().saveToXML(customLayout);
            root.addContent(customLayout);
        }

        // Serialise the player ship
        Element playerShip = new Element("playerShip");
        player.saveToXML(playerShip, refs);
        root.addContent(playerShip);

        // Serialise the flagship, as it's not stored at a beacon like normal ships.
        if (enemy != null && enemy.isFlagship()) {
            Element flagshipElem = new Element("flagship");
            enemy.saveToXML(flagshipElem, refs);
            root.addContent(flagshipElem);
        }

        // Serialise the sector map (the one you access via exit beacons)
        Element mapXML = new Element("gameMap");
        gameMap.saveToXML(mapXML, refs);
        root.addContent(mapXML);

        // Serialise the sector
        Element sectorXML = new Element("currentSector");
        ObjectRefs sectorRefs = sector.saveToXML(sectorXML, refs);
        root.addContent(sectorXML);

        // Save the beacon the player is currently at - this is how
        // we'll know what enemy ship to load, we'll just use whatever
        // is at the same beacon.
        SaveUtil.INSTANCE.addRef(root, "currentBeacon", sectorRefs, currentBeacon);

        // Save the list of visited sectors, which is used by the sector map.
        Element visitedSectorsXML = new Element("visitedSectors");
        for (GameMap.SectorInfo visited : visitedSectors) {
            String ref = sectorRefs.get(visited);
            Element visitedElem = new Element("sectorInfo");
            visitedElem.setAttribute("idr", ref);
            visitedSectorsXML.addContent(visitedElem);
        }
        root.addContent(visitedSectorsXML);

        // If the player saves while they're currently in the event UI, save that.
        Element shipUiXML = new Element("shipUI");
        shipUI.saveToXML(shipUiXML, refs);
        root.addContent(shipUiXML);

        return new Document(root);
    }

    private void loadGameState(Element root) {
        RefLoader refs = new RefLoader();

        // If the player ship has a custom layout, load that.
        Element customLayout = root.getChild("customLayout");
        EditableShip customised = null;
        if (customLayout != null) {
            customised = EditableShip.loadFromXML(customLayout);
        }

        // Load the player ship
        Element playerShip = root.getChild("playerShip");
        player = deserialiseSingleShip(playerShip, refs, customised);

        // Load the flagship, if we're fighting it.
        Element flagshipElem = root.getChild("flagship");
        Ship flagship = null;
        if (flagshipElem != null) {
            flagship = deserialiseSingleShip(flagshipElem, refs, null);
        }

        // Load the game map. The sector needs to reference it's SectorInfo
        // objects, so we need a separate reference loader we can switch
        // to resolve mode before loading the sector.
        RefLoader mapRefLoader = new RefLoader();
        Element mapXML = root.getChild("gameMap");
        gameMap = new GameMap(content, mapXML, mapRefLoader);
        mapRefLoader.switchToResolveMode();

        // Load the current sector - it looks odd to have a floating constructor,
        // but we only need it to create the current beacon and register it with
        // the reference loader. We then use our normal trick of only referring
        // to the current beacon, and letting that imply the current sector.
        Element sectorXML = root.getChild("currentSector");
        new Sector(sectorXML, refs, this, mapRefLoader);

        Element shipUiXML = root.getChild("shipUI");
        shipUI = new PlayerShipUI(player, this);
        shipUI.loadFromXML(shipUiXML, refs);

        // This resolves any async-resolved object references.
        refs.switchToResolveMode();

        // Load the list of visited sectors, which is used by the sector map.
        for (Element visitedElem : root.getChild("visitedSectors").getChildren("sectorInfo")) {
            String ref = visitedElem.getAttributeValue("idr");
            GameMap.SectorInfo visited = mapRefLoader.resolve(GameMap.SectorInfo.class, ref);
            visitedSectors.add(visited);
        }

        // We can finally load out our current beacon.
        currentBeacon = SaveUtil.INSTANCE.getRefImmediate(root, "currentBeacon", refs, Beacon.class);

        // Load in the previous enemy
        // FIXME keep crew-killed enemy ships around, currently they disappear
        enemyIsHostile = true;
        if (flagship != null) {
            setEnemy(flagship);
        } else {
            setEnemy(currentBeacon.getShip());
        }

        // We might have loaded a beacon with a store
        shipUI.updateButtons();
    }

    /**
     * Deserialise a player or enemy ship.
     * <p>
     * This should only be used by deserialisation code, specifically
     * the player ship deserialisation in this class and the enemy ship
     * deserialisation in beacons.
     */
    public Ship deserialiseSingleShip(Element shipElement, RefLoader refs, EditableShip customised) {
        // Load the appropriate ship definition XML element from the blueprints
        String shipId = shipElement.getAttributeValue("shipId");

        String specId = shipElement.getAttributeValue("specId");
        EnemyShipSpec spec;
        if (specId.equals("null")) {
            spec = null;
        } else {
            spec = content.eventManager.getShip(specId);
        }

        ShipBlueprint blueprint = (ShipBlueprint) blueprintManager.get(shipId);

        // And use that to build and deserialise the ship
        Ship ship = new Ship(blueprint, this, customised, spec);
        ship.loadFromXml(shipElement, refs);

        return ship;
    }

    @NotNull
    public Image getImg(String name) {
        Image img = content.images.get(name);
        if (img != null)
            return img;

        // This is just a way to access the image by a path, since we need
        // that for the missing animation.
        if (name.equals(Constants.MISSING_FILE_PATH))
            return getMissingImage();

        img = df.readImage(name);
        content.images.put(name, img);
        return img;
    }

    /**
     * Loads an image if it's file exists, or returns null.
     * <p>
     * Try to avoid this if you can - normally image paths are specified in
     * some predictable way, so you shouldn't be guessing their names.
     */
    @Nullable
    public Image getImgIfExists(String name) {
        Image img = content.images.get(name);
        if (img != null)
            return img;

        FTLFile file = df.getOrNull(name);
        if (file == null)
            return null;

        img = df.readImage(file);
        content.images.put(name, img);
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
        SILFontLoader font = content.fonts.get(name);
        if (font != null) {
            // Make a new instance sharing the font data
            return new SILFontLoader(font);
        }

        font = new SILFontLoader(df, df.get("fonts/" + name + ".font"));
        content.fonts.put(name, font);
        return new SILFontLoader(font);
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
        return content.animations;
    }

    public SoundManager getSounds() {
        return content.sounds;
    }

    public RoomClickListener getClickEvent() {
        return clickEvent;
    }

    public void setClickEvent(RoomClickListener clickEvent) {
        this.clickEvent = clickEvent;
    }

    public Translator getTranslator() {
        return content.translator;
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
        // If there isn't a fight going on, no-one should be shooting at anyone else.
        if (!enemyIsHostile) {
            return null;
        }

        if (source == player) {
            return enemy;
        } else if (source == enemy) {
            return player;
        } else {
            // Some ship that isn't present
            return null;
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
        return content.nameManager;
    }

    public GameContent getContent() {
        return content;
    }

    public DebugFlagManager getDebugFlags() {
        return debugFlags;
    }

    public DebugConsole getDebugConsole() {
        // Create the debug console if it doesn't already exist
        if (debugConsole == null) {
            debugConsole = new DebugConsole(this);
        }

        return debugConsole;
    }

    /**
     * Stuff that happens in-game should be isolated to this specific game.
     * Think carefully about why you need to use this!
     */
    public MainGame getMainGame() {
        return mainGame;
    }

    /**
     * True if stuff is currently being deserialised.
     * <p>
     * It's generally a bit ugly to use this, but it's an easy
     * way to stop stuff from happening while the game is being loaded.
     */
    public boolean isCurrentlyLoadingSave() {
        return isCurrentlyLoadingSave;
    }

    public Difficulty getDifficulty() {
        return difficulty;
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
        missingImage = TextureLoader.INSTANCE.loadImage(buffer);
        return missingImage;
    }

    public WindowRenderer getWindowRenderer() {
        if (content.windowRenderer != null)
            return content.windowRenderer;

        Image background = getImg("img/window_base.png");
        Image outline = getImg("img/window_outline.png");
        Image mask = getImg("img/window_mask.png");
        content.windowRenderer = new WindowRenderer(background, outline, mask);

        return content.windowRenderer;
    }

    public void givePlayerResources(@NotNull ResourceSet resources) {
        player.setFuelCount(player.getFuelCount() + resources.getFuel());
        player.setDronesCount(player.getDronesCount() + resources.getDroneParts());
        player.setMissilesCount(player.getMissilesCount() + resources.getMissiles());
        player.setScrap(player.getScrap() + resources.getScrap());

        for (Blueprint item : resources.getItems()) {
            player.addBlueprint(item, true);
        }

        for (LivingCrewInfo info : resources.getCrew()) {
            player.addCrewMember(info, false, false);
        }

        for (RemoveCrewEval removed : resources.getLostCrew()) {
            LivingCrew crew = removed.getCrew();
            RemoveCrew info = removed.getInfo();

            if (player.getClonebay() != null) {
                String cloneMessage = info.getCloneText().resolve();
                if (!cloneMessage.isBlank()) {
                    shipUI.showSyntheticDialogue(new DialogueWindow.SyntheticEvent(cloneMessage));
                }

                // Never actually remove the crewmember if they're cloned
                if (info.getClone()) {
                    // TODO deduct skills
                    continue;
                }
            }

            if (info.getTurnHostile()) {
                crew.setOwnerShip(null);
            } else {
                crew.removeFromShip();
            }
        }

        // Put all the intruders in the same room, as far as possible.
        int intruderRoomId = Random.Default.nextInt(player.getRooms().size());
        Room intruderRoom = player.getRooms().get(intruderRoomId);

        for (LivingCrewInfo info : resources.getIntruders()) {
            LivingCrew intruder = player.addCrewMember(info, false, true);

            RoomPoint slot = player.findSpaceForCrew(intruderRoom, AbstractCrew.SlotType.INTRUDER);
            intruder.jumpTo(slot);
        }

        // Apply the hull damage
        // TODO play the damage sound effect
        for (EventHullDamage damage : resources.getDamage()) {
            String system = damage.getSystem();
            if (system == null) {
                // A null system means hull damage only
                player.setHealth(player.getHealth() - damage.getAmount());
                continue;
            }

            Room targetRoom;

            if (system.equals("random")) {
                AbstractSystem randomSystem = player.getSystems().get(Random.Default.nextInt(player.getSystems().size()));
                targetRoom = randomSystem.getRoom();
            } else if (system.equals("room")) {
                targetRoom = player.getRooms().get(Random.Default.nextInt(player.getRooms().size()));
            } else {
                Optional<AbstractSystem> foundSystem = player.getSystems().stream()
                        .filter(s -> s.getCodename().equals(system))
                        .findFirst();
                if (foundSystem.isPresent()) {
                    targetRoom = foundSystem.get().getRoom();
                } else {
                    // Just pick a random room if this system isn't installed
                    // TODO check how FTL does it
                    targetRoom = player.getRooms().get(Random.Default.nextInt(player.getRooms().size()));
                }
            }

            Objects.requireNonNull(targetRoom);

            player.damage(targetRoom, damage.getAmount(), damage.getAmount(), 0);

            if (damage.getEffectFire()) {
                targetRoom.spawnFire();
                targetRoom.spawnFire();
            }
            if (damage.getEffectBreach()) {
                targetRoom.spawnBreach();
            }

            // TEST_EVENT is a good way to test this, as it spawns both of them.
        }

        // Apply system/reactor upgrades
        for (EventSystemUpgrade upgrade : resources.getUpgrades()) {
            if (upgrade.getSystem().equals("reactor")) {
                int newPower = player.getPurchasedReactorPower() + upgrade.getAmount();
                player.setPurchasedReactorPower(Math.min(newPower, player.getMaxReactorPower()));
                continue;
            }

            AbstractSystem system = player.getSystems().stream()
                    .filter(s -> s.getCodename().equals(upgrade.getSystem()))
                    .findFirst().orElse(null);

            if (system == null)
                continue;

            int newPower = system.getEnergyLevels() + upgrade.getAmount();
            system.setEnergyLevels(Math.min(system.getBlueprint().getMaxPower(), newPower));
        }

        // Apply the fleet pursuit modifier
        Sector currentSector = currentBeacon.getSector();
        currentSector.setFleetAdvanceModifier(currentSector.getFleetAdvanceModifier() + resources.getModifyPursuit());
    }

    /**
     * Advance the fleet pursuit by the amount determined by the current beacon.
     */
    public void advanceFleet() {
        Sector sector = currentBeacon.getSector();

        // The last stand works differently, capturing two beacons per jump.
        if (sector.isLastStand()) {
            advanceFleetLastStand();
            return;
        }

        Point dangerZone = sector.getDangerZoneCentre();

        int advance = sector.getFleetAdvanceFor(currentBeacon);

        dangerZone.setX(dangerZone.getX() + advance);

        for (Beacon beacon : sector.getBeacons()) {
            int distSq = beacon.getPos().distToSq(dangerZone);
            if (distSq > Sector.DANGER_ZONE_RADIUS_SQUARED)
                continue;

            beacon.setOvertaken(true);
        }

        // The modifier is a counter of how long we apply no or double pursuit,
        // so we have to bring it back towards zero.
        int modifier = sector.getFleetAdvanceModifier();
        if (modifier < 0)
            modifier++;
        if (modifier > 0)
            modifier--;
        sector.setFleetAdvanceModifier(modifier);
    }

    private void advanceFleetLastStand() {
        Sector sector = currentBeacon.getSector();

        // TODO implement fleet advance, capturing two beacons

        // Make the flagship jump

        if (!sector.getFlagshipJumping()) {
            // Jump every second turn, this turn wasn't a jump.
            // (except if the flagship doesn't want to jump, when
            //  it's at the base - this is when the next beacon is null.)
            sector.updateFlagshipNextBeacon();
            sector.setFlagshipJumping(sector.getFlagshipNextBeacon() != null);
        } else {
            sector.setFlagshipBeacon(sector.getFlagshipNextBeacon());
            sector.updateFlagshipNextBeacon();

            // Don't jump again next turn
            sector.setFlagshipJumping(false);

            // If we were running away, we're allowed to go back to the base again.
            sector.setFlagshipRunningAway(false);
        }
    }

    /**
     * FOR DEBUG USE ONLY!
     * <p>
     * When reloading the game every frame, some UI stuff (like the selection
     * rectangle) breaks. It'd be a bit silly to serialise this, but it's
     * also very hard to meaningfully play with it being constantly cleared.
     * <p>
     * Thus, this copies over basic UI stuff that's very unlikely to cover
     * up any serialisation bugs.
     */
    void debugContinuousSaveRestore(InGameState prev) {
        // Swap over the entire debug console, since it has no state.
        // Without this, the debug console would close across a reload.
        // Note we copy it whether or not it's open, so we don't loose
        // it's command history when you close it.
        if (prev.debugConsole != null) {
            debugConsole = prev.debugConsole;
            debugConsole.setGame(this);
        }
        debugConsoleVisible = prev.debugConsoleVisible;

        shipUI.debugContinuousSaveRestore(prev.shipUI);
        System.arraycopy(prev.mouseDownPrev, 0, mouseDownPrev, 0, mouseDownPrev.length);

        paused = prev.paused;

        // Copy over the debug flags.
        // It'd be annoying to lose those, since they're not supposed
        // to be saved.
        List<DebugFlagManager.DebugFlag> oldFlags = prev.debugFlags.getAll();
        List<DebugFlagManager.DebugFlag> newFlags = debugFlags.getAll();
        for (int i = 0; i < newFlags.size(); i++) {
            newFlags.get(i).setSet(oldFlags.get(i).getSet());
        }
    }

    /**
     * FOR DEBUGGING USE ONLY!
     * <p>
     * This is called by {@link MainGame} when we're in continuous save-load mode,
     * and there's an exception during the save-load process.
     */
    public void debugFailedSaveRestore() {
        // Pause the game so we don't get an ongoing stream of these errors.
        paused = true;

        // Print a message in the debug console, so the player knows what happened.
        debugConsoleVisible = true;
        getDebugConsole().onFailedSaveRestore();
    }

    public interface RoomClickListener {
        void roomClicked(Room room, GameContainer gc);
    }

    public enum QuestAddResult {
        CURRENT_SECTOR,
        NEXT_SECTOR,
        TOO_LATE,
    }

    /**
     * This represents all the parsed resources that can be pulled from a {@link Datafile}.
     * <p>
     * It's kept separate so that stuff like restarting the game (or starting a new game
     * where the mods and Advanced Edition mode are the same) is fast.
     */
    public static class GameContent {
        public final Datafile datafile;
        public final boolean enableAdvancedEdition;

        public final BlueprintManager blueprintManager;
        public final EventManager eventManager;
        public final CrewNameManager nameManager;
        public final Animations animations;
        public final SoundManager sounds;
        public final Translator translator;
        public final ShipGenerator generator;

        // These are loaded on-demand
        private final Map<String, Image> images = new HashMap<>();
        private final Map<String, SILFontLoader> fonts = new HashMap<>();

        // This is only here to avoid wasting heaps of resources in cont-save-load mode.
        private WindowRenderer windowRenderer;

        public GameContent(Datafile datafile, boolean enableAdvancedEdition) {
            this.datafile = datafile;
            this.enableAdvancedEdition = enableAdvancedEdition;

            blueprintManager = new BlueprintManager(datafile, enableAdvancedEdition);
            animations = new Animations(datafile);
            sounds = new SoundManager(datafile);
            generator = new ShipGenerator(datafile, blueprintManager);
            translator = new Translator(datafile, "en");
            eventManager = new EventManager(datafile, translator, blueprintManager);
            nameManager = new CrewNameManager(datafile);

            blueprintManager.finishLoading(this);
        }
    }
}
