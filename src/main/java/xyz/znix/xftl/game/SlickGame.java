package xyz.znix.xftl.game;

import org.newdawn.slick.*;
import xyz.znix.xftl.*;
import xyz.znix.xftl.crew.AbstractCrew;
import xyz.znix.xftl.crew.HumanCrew;
import xyz.znix.xftl.layout.Door;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;
import xyz.znix.xftl.math.IPoint;
import xyz.znix.xftl.weapons.AbstractProjectile;
import xyz.znix.xftl.weapons.WeaponDict;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlickGame extends BasicGame {
    private Ship player;
    private final Datafile df;
    private Image floorPlan;
    private Image outside;

    private Map<String, Image> images = new HashMap<>();
    private Animations animations;
    private WeaponDict weapons;

    public SlickGame(Datafile df) throws SlickException {
        super("Subluminal");
        this.df = df;
    }

    @Override
    public void init(GameContainer container) throws SlickException {
        animations = new Animations(df);
        weapons = new WeaponDict(df);

        player = new Ship(df, "PLAYER_SHIP_HARD", this); // Kestral

        for (Room room : player.getRooms()) {
            AbstractSystem system = room.getSystem();
            if (system == null)
                continue;

            getImg(system.getIcon());
            getImg(system.getImg());
        }

        HumanCrew crew = new HumanCrew(animations, player.getRooms().get(0));
        player.getCrew().add(crew);
        crew.setTargetRoom(player.shipToRoomPos(new ConstPoint(1, 1)).getRoom());
    }

    @Override
    public void update(GameContainer container, int delta) throws SlickException {
        float dt = delta / 1000f;
        for (Room room : player.getRooms())
            room.update(dt);

        for (AbstractCrew crew : player.getCrew())
            crew.update(dt);

        List<AbstractProjectile> ib = player.getInboundProjectiles();

        // Walk backwards, since missiles remove themselves when they hit
        for (int i = ib.size() - 1; i >= 0; i--) {
            AbstractProjectile projectile = ib.get(i);
            projectile.update(dt);
        }
    }

    @Override
    public void render(GameContainer container, Graphics g) throws SlickException {
        player.render(g);
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
