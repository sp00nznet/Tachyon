package xyz.znix.xftl;

import org.newdawn.slick.Color;

public class Constants {
    public static final int ROOM_SIZE = 35;

    public static final Color FLOOR_COLOUR = new Color(230, 226, 219);
    public static final Color FLOOR_GRID_COLOUR = new Color(172, 169, 164);
    public static final Color FLOOR_GRID_CROSS = new Color(129, 127, 123);
    public static final Color ROOM_BORDER_COLOUR = Color.black;
    public static final Color ROOM_BORDER_COLOUR_SELECTED = Color.yellow;
    public static final Color ROOM_BORDER_COLOUR_SELECTED_INNER = new Color(255, 188, 0);

    public static final int ROOM_BORDER_SIZE = 2;
    public static final Color DOOR_COLOUR_1 = new Color(255, 150, 48);

    public static final Color SYSTEM_NORMAL = new Color(125, 125, 125);
    public static final Color SYSTEM_IONISED = new Color(133, 231, 237);
    public static final Color SYSTEM_DAMAGED = new Color(255, 153, 76);
    public static final Color SYSTEM_BROKEN = new Color(255, 0, 0);

    public static final Color UI_TEXT_COLOUR_1 = new Color(40, 78, 82);
    public static final Color UI_BACKGROUND_GLOW_COLOUR = new Color(243, 255, 238);

    public static final Color SYS_ENERGY_ACTIVE = new Color(96, 255, 64);
    public static final Color SYS_ENERGY_DEPOWERED = new Color(251, 251, 251);
    public static final Color SYS_ENERGY_BROKEN = new Color(255, 50, 50);

    public static final Color WEAPONS_ITEM_DESELECTED = new Color(150, 150, 150);
    public static final Color WEAPONS_ITEM_SELECTED = UI_BACKGROUND_GLOW_COLOUR;
    public static final Color WEAPONS_ITEM_CHARGED = new Color(120, 255, 120);

    private Constants() {
    }
}
