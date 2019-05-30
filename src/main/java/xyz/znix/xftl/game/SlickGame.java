package xyz.znix.xftl.game;

import org.newdawn.slick.*;
import xyz.znix.xftl.AbstractSystem;
import xyz.znix.xftl.Animations;
import xyz.znix.xftl.Datafile;
import xyz.znix.xftl.Ship;
import xyz.znix.xftl.ai.ShipAI;
import xyz.znix.xftl.crew.HumanCrew;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.shipgen.ShipGenerator;
import xyz.znix.xftl.systems.MainSystem;
import xyz.znix.xftl.weapons.WeaponDict;

import java.util.HashMap;
import java.util.Map;

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
    }

    @Override
    public void update(GameContainer container, int delta) throws SlickException {
        float dt = delta / 1000f;
        player.update(dt);
        enemy.update(dt);
        enemyAI.update(dt);
    }

    @Override
    public void render(GameContainer container, Graphics g) throws SlickException {
        player.render(g);

        g.translate(container.getWidth() - enemy.getHullImage().getWidth(), 0);
        enemy.render(g);

        g.resetTransform();

        // Draw the systems
        int x = 58;
        for (Room r : player.getRooms()) {
            AbstractSystem as = r.getSystem();
            if (!(as instanceof MainSystem))
                continue;

            MainSystem sys = (MainSystem) as;

            String mode;
            if (sys.getDamagedEnergyLevels() == sys.getEnergyLevels())
                mode = "red";
            else if (sys.getDamagedEnergyLevels() > 0)
                mode = "orange";
            else if (sys.getSelectedEnergyLevel() == 0)
                mode = "gray";
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
