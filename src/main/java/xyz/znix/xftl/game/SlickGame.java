package xyz.znix.xftl.game;

import org.newdawn.slick.*;
import xyz.znix.xftl.AbstractSystem;
import xyz.znix.xftl.Animations;
import xyz.znix.xftl.Datafile;
import xyz.znix.xftl.Ship;
import xyz.znix.xftl.crew.HumanCrew;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.shipgen.ShipGenerator;
import xyz.znix.xftl.weapons.WeaponDict;

import java.util.HashMap;
import java.util.Map;

public class SlickGame extends BasicGame {
    private Ship player;
    private Ship enemy; // temporary, TODO handle this properly
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
    }

    @Override
    public void render(GameContainer container, Graphics g) throws SlickException {
        player.render(g);

        g.translate(container.getWidth() - enemy.getHullImage().getWidth(), 0);
        enemy.render(g);
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
