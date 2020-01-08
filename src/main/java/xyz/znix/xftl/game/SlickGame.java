package xyz.znix.xftl.game;

import org.newdawn.slick.*;
import xyz.znix.xftl.*;
import xyz.znix.xftl.ai.ShipAI;
import xyz.znix.xftl.crew.HumanCrew;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.math.Point;
import xyz.znix.xftl.math.RoomPoint;
import xyz.znix.xftl.shipgen.ShipGenerator;
import xyz.znix.xftl.systems.MainSystem;
import xyz.znix.xftl.weapons.WeaponDict;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SlickGame extends BasicGame {
    private static ConstPoint PLAYER_SHIP_POSITION = new ConstPoint(50, 150);

    private Ship player;
    private Ship enemy; // temporary, TODO handle this properly
    private ShipAI enemyAI;
    private final Datafile df;
    private Image floorPlan;
    private Image outside;

    private Map<String, Image> images = new HashMap<>();
    private Animations animations;
    private WeaponDict weapons;
    private ShipGenerator generator;

    private Translator translator;

    private Point tempPoint = new Point(0, 0);

    private Room hoveredRoom;
    private Consumer<Room> clickEvent;

    private PlayerShipUI shipUI;
    private HostileShipUI hostileShipUI;

    private boolean paused;

    public SlickGame(Datafile df) throws SlickException {
        super("Subluminal");
        this.df = df;
    }

    @Override
    public void init(GameContainer container) throws SlickException {
        animations = new Animations(df);
        weapons = new WeaponDict(df);
        generator = new ShipGenerator(df);

        player = new Ship(df, "PLAYER_SHIP_HARD", this); // Kestral
        enemy = generator.buildShip(this, "REBEL_FAT");
        enemyAI = new ShipAI(enemy, player);

        translator = new Translator(df, "en");

        shipUI = new PlayerShipUI(df, translator, player, this);
        hostileShipUI = new HostileShipUI(this, df, enemy);

        for (Room room : player.getRooms()) {
            AbstractSystem system = room.getSystem();
            if (system == null)
                continue;

            getImg(system.getIcon());

            String img = system.getImg();
            if (img != null)
                getImg(img);
        }

        HumanCrew crew = new HumanCrew(animations, player.getRooms().get(0), HumanCrew.SlotType.CREW);
        player.getCrew().add(crew);
        crew.setTargetRoom(player.shipToRoomPos(new ConstPoint(1, 1)).getRoom());

        HumanCrew enemyCrew = new HumanCrew(animations, enemy.getRooms().get(0), HumanCrew.SlotType.CREW);
        enemy.getCrew().add(enemyCrew);

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

    @Override
    public void update(GameContainer container, int delta) throws SlickException {
        if (!paused)
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

        for (int i = 0; i < 3; i++) {
            if (in.isMousePressed(i)) {
                if (i == Input.MOUSE_RIGHT_BUTTON) {
                    clickEvent = null;
                }

                shipUI.mouseClick(i, in.getMouseX(), in.getMouseY(), PLAYER_SHIP_POSITION);
            }
        }

        if (clickEvent == null)
            return;

        // Hovering over the enemy
        tempPoint.setX(container.getInput().getMouseX());
        tempPoint.setY(container.getInput().getMouseY());
        tempPoint.minusAssign(hostileShipUI.getShipPos());
        enemy.screenPosToShipPos(tempPoint);

        RoomPoint rp = enemy.shipToRoomPos(tempPoint);
        if (rp != null)
            hoveredRoom = rp.getRoom();

        // Hovering over the player
        tempPoint.setX(container.getInput().getMouseX());
        tempPoint.setY(container.getInput().getMouseY());
        tempPoint.minusAssign(PLAYER_SHIP_POSITION);
        player.screenPosToShipPos(tempPoint);

        rp = player.shipToRoomPos(tempPoint);
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
        // Get the player's ship away from the top UI
        g.translate(PLAYER_SHIP_POSITION.getX(), PLAYER_SHIP_POSITION.getY());
        player.render(g, hoveredRoom);
        g.resetTransform();

        shipUI.render(container, g);
        hostileShipUI.render(container, g, hoveredRoom);

        if (paused) {
            Image pauseImg = getImg("img/Text_pause2.png");
            int imgY = shipUI.getBoxY() - 80;
            int imgX = container.getWidth() / 2 - pauseImg.getWidth() / 2;
            pauseImg.draw(imgX, imgY);
        }
    }

    private void updateGameState(float dt) {
        shipUI.update(dt);
        player.update(dt);
        enemy.update(dt);
        enemyAI.update(dt);
    }

    public Image getImg(String name) {
        Image img = images.get(name);
        if (img != null)
            return img;

        img = df.readImage(name);
        images.put(name, img);
        return img;
    }

    public Animations getAnimations() {
        return animations;
    }

    public WeaponDict getWeapons() {
        return weapons;
    }

    public Consumer<Room> getClickEvent() {
        return clickEvent;
    }

    public void setClickEvent(Consumer<Room> clickEvent) {
        this.clickEvent = clickEvent;
    }
}
