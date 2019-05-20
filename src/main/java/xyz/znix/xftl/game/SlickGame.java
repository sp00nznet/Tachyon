package xyz.znix.xftl.game;

import org.newdawn.slick.*;
import xyz.znix.xftl.*;
import xyz.znix.xftl.crew.AbstractCrew;
import xyz.znix.xftl.crew.HumanCrew;
import xyz.znix.xftl.layout.Door;
import xyz.znix.xftl.layout.Room;
import xyz.znix.xftl.math.ConstPoint;

import java.util.HashMap;
import java.util.Map;

import static xyz.znix.xftl.Constants.*;

public class SlickGame extends BasicGame {
    private final Ship player;
    private final Datafile df;
    private Image floorPlan;
    private Image outside;

    private Map<String, Image> images = new HashMap<>();
    private Animations animations;

    public SlickGame(Datafile df, Ship player) throws SlickException {
        super("Subluminal");
        this.player = player;
        this.df = df;
    }

    @Override
    public void init(GameContainer container) throws SlickException {
        String filename = "img/ship/" + player.getImageName() + "_floor.png";
        floorPlan = new Image(df.open(df.get(filename)), filename, false);

        filename = "img/ship/" + player.getImageName() + "_base.png";
        outside = new Image(df.open(df.get(filename)), filename, false);

        for (Room room : player.getRooms()) {
            AbstractSystem system = room.getSystem();
            if (system == null)
                continue;

            getImg(system.getIcon());
            getImg(system.getImg());
        }

        animations = new Animations(df);

        HumanCrew crew = new HumanCrew(animations, player.getRooms().get(0));
        player.getCrew().add(crew);
        crew.setTargetRoom(player.shipToRoomPos(new ConstPoint(1, 1)).getRoom());
    }

    @Override
    public void update(GameContainer container, int delta) throws SlickException {
        for (Room room : player.getRooms())
            room.update(delta / 1000f);

        for (AbstractCrew crew : player.getCrew())
            crew.update(delta / 1000f);
    }

    @Override
    public void render(GameContainer container, Graphics g) throws SlickException {
        g.drawImage(outside, 0, 0);
        g.drawImage(floorPlan, player.getFloorOffset().getX(), player.getFloorOffset().getY());

        // Draw the rooms
        for (Room room : player.getRooms()) {
            int x = room.getOffsetX();
            int y = room.getOffsetY();

            g.setColor(FLOOR_COLOUR);
            g.fillRect(
                    x,
                    y,
                    ROOM_SIZE * room.getWidth(),
                    ROOM_SIZE * room.getHeight());

            g.setColor(FLOOR_GRID_COLOUR);
            for (int i = 1; i < room.getWidth(); i++) {
                g.drawLine(
                        x + i * ROOM_SIZE,
                        y,
                        x + i * ROOM_SIZE,
                        y + ROOM_SIZE * room.getHeight() - 1);
            }

            for (int i = 1; i < room.getHeight(); i++) {
                g.drawLine(
                        x,
                        y + ROOM_SIZE * i,
                        x + ROOM_SIZE * room.getWidth() - 1,
                        y + ROOM_SIZE * i);
            }

            AbstractSystem system = room.getSystem();
            if (system != null) {
                Image bg = images.get(system.getImg());
                g.drawImage(bg, x, y);
            }

            g.setColor(ROOM_BORDER_COLOUR);
            // Draw two one-pixel lines around the room, as it's too much of a hassle to
            // change the line width, as it seems to be rather implementation-specific
            g.drawRect(x, y,
                    ROOM_SIZE * room.getWidth() - 1,
                    ROOM_SIZE * room.getHeight() - 1);
            g.drawRect(x + 1, y + 1,
                    ROOM_SIZE * room.getWidth() - 3,
                    ROOM_SIZE * room.getHeight() - 3);

            if (system == null)
                continue;

            Image img = images.get(system.getIcon());
            g.drawImage(img,
                    x + (int) (room.getWidth() * ROOM_SIZE / 2f - img.getWidth() / 2f),
                    y + (int) (room.getHeight() * ROOM_SIZE / 2f - img.getHeight() / 2f)
            );
        }

        // Draw the doors
        for (Door door : player.getDoors()) {
            g.setColor(Color.blue);

            if (door.isVertical()) {
                int x = door.getOffsetX() - 3;
                int y = door.getOffsetY() + 8;

                g.setColor(Color.black);
                g.fillRect(x, y, 6, 21);

                g.setColor(Constants.DOOR_COLOUR_1);
                g.fillRect(x + 1, y + 1, 4, 21 - 2);

                g.setColor(Color.black);
                g.drawLine(x + 1, y + 10, x + 5, y + 10);
            } else {
                int x = door.getOffsetX() + 8;
                int y = door.getOffsetY() - 3;

                g.setColor(Color.black);
                g.fillRect(x, y, 21, 6);

                g.setColor(Constants.DOOR_COLOUR_1);
                g.fillRect(x + 1, y + 1, 21 - 2, 4);

                g.setColor(Color.black);
                g.drawLine(x + 10, y + 1, x + 10, y + 5);
            }
        }

        // Draw the crew
        for (AbstractCrew crew : player.getCrew()) {
            Room room = crew.getRoom();
            crew.getIcon().draw(crew.getScreenX(), crew.getScreenY());
        }
    }

    private Image getImg(String name) {
        Image img = images.get(name);
        if (img != null)
            return img;

        img = df.readImage(df.get(name));
        images.put(name, img);
        return img;
    }
}
