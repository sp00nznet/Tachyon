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
import xyz.znix.xftl.weapons.AbstractWeaponInstance;
import xyz.znix.xftl.weapons.WeaponDict;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static xyz.znix.xftl.Constants.*;

public class SlickGame extends BasicGame {
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
    private SILFontLoader font;
    private SILFontLoader weaponNameText;
    private SILFontLoader weaponNumberFont;

    private Point tempPoint = new Point(0, 0);

    private Room hoveredRoom;
    private Consumer<Room> clickEvent;

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
        font = new SILFontLoader(df, df.get("fonts/HL2.font"));
        weaponNameText = new SILFontLoader(df, df.get("fonts/JustinFont8.font"));
        weaponNumberFont = new SILFontLoader(df, df.get("fonts/c&c.font"));

        for (Room room : player.getRooms()) {
            AbstractSystem system = room.getSystem();
            if (system == null)
                continue;

            getImg(system.getIcon());

            String img = system.getImg();
            if (img != null)
                getImg(img);
        }

        HumanCrew crew = new HumanCrew(animations, player.getRooms().get(0));
        player.getCrew().add(crew);
        crew.setTargetRoom(player.shipToRoomPos(new ConstPoint(1, 1)).getRoom());

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
        float dt = delta / 1000f;
        player.update(dt);
        enemy.update(dt);
        enemyAI.update(dt);

        hoveredRoom = null;

        if (clickEvent == null)
            return;

        // Hovering over the enemy
        int enemyX = container.getWidth() - enemy.getHullImage().getWidth();
        tempPoint.setX(container.getInput().getMouseX());
        tempPoint.setY(container.getInput().getMouseY());
        tempPoint.add(-enemyX, 0);
        enemy.screenPosToShipPos(tempPoint);

        RoomPoint rp = enemy.shipToRoomPos(tempPoint);
        if (rp != null)
            hoveredRoom = rp.getRoom();

        // Hovering over the player
        tempPoint.setX(container.getInput().getMouseX());
        tempPoint.setY(container.getInput().getMouseY());
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
        player.render(g, hoveredRoom);

        g.translate(container.getWidth() - enemy.getHullImage().getWidth(), 0);
        enemy.render(g, hoveredRoom);

        g.resetTransform();

        font.setScale(2);
        g.setFont(font);

        int bx = 234;
        int by = container.getHeight() - 113;
        getImg("img/box_weapons_bottom" + player.getWeaponSlots() + ".png").draw(bx, by);
        int tx = bx + 18;
        int ty = by + 61;

        g.setColor(UI_BACKGROUND_GLOW_COLOUR);
        int tw = font.getWidth("WEAPONS");
        g.fillRect(tx, ty, tw - 13, 20);
        getImg("img/box_weapons_bottom_label.png").draw(tx + tw - 13, ty);

        g.setColor(UI_TEXT_COLOUR_1);
        g.drawString("WEAPONS", tx + 1, ty + 11);
        // getImg("img/icons/s_weapons_grey1.png").draw(202, container.getHeight() - 69);

        // Draw the systems
        int x = 58;
        Stream<MainSystem> systems = player.getRooms().stream()
                .map(Room::getSystem)
                .filter(MainSystem.class::isInstance)
                .map(MainSystem.class::cast)
                .sorted(Comparator.comparing(MainSystem::getSortingType));
        for (MainSystem sys : systems.toArray(MainSystem[]::new)) {
            String mode;
            if (sys.getDamagedEnergyLevels() == sys.getEnergyLevels())
                mode = "red";
            else if (sys.getDamagedEnergyLevels() > 0)
                mode = "orange";
            else if (sys.getSelectedEnergyLevel() == 0)
                mode = "grey";
            else
                mode = "green";

            int y = container.getHeight() - 69;
            getImg("img/icons/s_" + sys.getCodename() + "_" + mode + "1.png").draw(x, y);

            y += 8;

            for (int i = 0; i < sys.getEnergyLevels(); i++) {
                if (i >= sys.getEnergyLevels() - sys.getDamagedEnergyLevels()) {
                    // System damaged/broken
                    g.setColor(SYS_ENERGY_BROKEN);
                    g.drawRect(x + 24, y, 16 - 1, 6 - 1);
                    g.drawLine(x + 24, y + 6, x + 24 + 16, y);
                } else if (i < sys.getSelectedEnergyLevel()) {
                    // System powered
                    g.setColor(SYS_ENERGY_ACTIVE);
                    g.fillRect(x + 24, y, 16, 6);
                } else {
                    // System depowered
                    g.setColor(SYS_ENERGY_DEPOWERED);
                    g.drawRect(x + 24, y, 16 - 1, 6 - 1);
                }
                y -= 8;
            }

            x += 36;
        }

        g.setFont(weaponNameText);

        // Find the longest charge time of all equipped weapons
        float max_weapon_charge_time = player.getHardpoints().stream()
                .map(Ship.Hardpoint::getWeapon)
                .filter(Objects::nonNull)
                .map(hp -> hp.getType().getChargeTime())
                .reduce(Math::max)
                .orElse(1f);

        for (int i = 0; i < player.getWeaponSlots(); i++) {
            int wx = bx + 12 + 12 + 97 * i;
            int wy = by + 12 + 4;

            Ship.Hardpoint hp = player.getHardpoints().get(i);
            AbstractWeaponInstance weapon = hp.getWeapon();

            if (weapon == null || !weapon.isPowered())
                g.setColor(WEAPONS_ITEM_DESELECTED);
            else if (weapon.isCharged())
                g.setColor(WEAPONS_ITEM_CHARGED);
            else
                g.setColor(WEAPONS_ITEM_SELECTED);

            // Draw the outline box
            g.drawRect(wx, wy, 87 - 1, 39 - 1);
            g.drawRect(wx + 1, wy + 1, 87 - 3, 39 - 3);

            if (weapon == null)
                continue;

            int max_bar_size = 35 - 2;
            int bar_size = (int) (max_bar_size * weapon.getType().getChargeTime() / max_weapon_charge_time);

            // The Y position of the inside of the charge bar, relative to the main weapons box
            int top = max_bar_size - bar_size;

            // The top point of the triangle
            int triangle_top = top - 7;

            for (int j = 8; j > 0; j--) {
                int pos = j + triangle_top;
                if (pos < 0)
                    continue;
                int y = wy + pos;
                g.drawLine(wx - j, y, wx, y);
            }

            int charge_px = (int) (bar_size * weapon.getChargeProgress());
            g.fillRect(wx - 5, wy + 36 - charge_px, 4, charge_px);

            g.setLineWidth(2);
            g.drawLine(wx - 7.5f, wy + top + 1.5f, wx - 7.5f, wy + 39 - 1.5f);
            g.drawLine(wx - 7.5f, wy + 39 - 1.5f, wx - 0.5f, wy + 39 - 1.5f);
            g.setLineWidth(1);

            // Draw the weapon number box
            g.setLineWidth(2);
            g.drawLine(wx + 75 + 0.5f, wy + 24 + 0.5f, wx + 75 + 0.5f, wy + 36 + 0.5f);
            g.drawLine(wx + 75 + 0.5f, wy + 24 + 0.5f, wx + 85 + 0.5f, wy + 24 + 0.5f);
            g.setLineWidth(1);

            // Draw the weapon number itself
            g.setFont(weaponNumberFont);
            String weaponNumber = Integer.toString(i + 1);
            int weaponNumberWidth = weaponNumberFont.getWidth(weaponNumber);
            g.drawString(weaponNumber, wx + 77 + 1 + (8 - weaponNumberWidth) / 2, wy + 30);

            String shortName = translator.get(weapon.getType().getShortKey()).replaceFirst(" ", "\n");
            drawWeaponString(g, shortName, wx + 26, wy + 8);

            // TODO make these correct
            int zoltanPower = 1;

            for (int bar = 0; bar < weapon.getType().getPower(); bar++) {
                int y = wy + 28 - bar * 8;

                if (zoltanPower > bar) {
                    g.setColor(WEAPONS_ITEM_ENERGY_ZOLTAN);
                } else if (!weapon.isPowered()) {
                    g.setColor(WEAPONS_ITEM_ENERGY_UNPOWERED);
                    g.drawRect(wx + 4, y, 16 - 1, 7 - 1);
                    continue;
                } else if (weapon.isCharged()) {
                    g.setColor(WEAPONS_ITEM_ENERGY_CHARGED);
                } else {
                    g.setColor(WEAPONS_ITEM_ENERGY_POWERED);
                }
                g.fillRect(wx + 4, y, 16, 7);
            }
        }
    }

    private void drawWeaponString(Graphics g, String str, int x, int y) {
        for (String line : str.split("\n")) {
            g.drawString(line, x, y);
            y += 15;
        }
    }

    public Image getImg(String name) {
        Image img = images.get(name);
        if (img != null)
            return img;

        img = df.readImage(df.get(name));
        images.put(name, img);
        return img;
    }

    public Animations getAnimations() {
        return animations;
    }

    public WeaponDict getWeapons() {
        return weapons;
    }
}
