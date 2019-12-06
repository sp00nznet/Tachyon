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

    public static final Color ROOM_SELECTION_COLOUR = new Color(106, 169, 102);
    public static final Color ROOM_SELECTION_COLOURS[];

    public static final int ROOM_BORDER_SIZE = 2;
    public static final Color DOOR_COLOUR_1 = new Color(255, 150, 48);

    public static final Color SYSTEM_NORMAL = new Color(125, 125, 125);
    public static final Color SYSTEM_IONISED = new Color(133, 231, 237);
    public static final Color SYSTEM_DAMAGED = new Color(255, 153, 76);
    public static final Color SYSTEM_BROKEN = new Color(255, 0, 0);

    public static final Color UI_TEXT_COLOUR_1 = new Color(40, 78, 82);
    public static final Color UI_BACKGROUND_GLOW_COLOUR = new Color(243, 255, 238);

    public static final Color SYS_ENERGY_ACTIVE = new Color(100, 255, 100);
    public static final Color SYS_ENERGY_DEPOWERED = new Color(251, 251, 251);
    public static final Color SYS_ENERGY_BROKEN = new Color(255, 50, 50);
    public static final Color SYS_ENERGY_REPAIR = new Color(255, 255, 50);

    public static final Color WEAPONS_ITEM_DESELECTED = new Color(150, 150, 150);
    public static final Color WEAPONS_ITEM_SELECTED = UI_BACKGROUND_GLOW_COLOUR;
    public static final Color WEAPONS_ITEM_CHARGED = new Color(120, 255, 120);
    public static final Color WEAPONS_ITEM_TARGETING = new Color(255, 120, 120);

    public static final Color WEAPONS_ITEM_ENERGY_UNPOWERED = WEAPONS_ITEM_DESELECTED;
    public static final Color WEAPONS_ITEM_ENERGY_POWERED = WEAPONS_ITEM_SELECTED;
    public static final Color WEAPONS_ITEM_ENERGY_CHARGED = WEAPONS_ITEM_CHARGED;
    public static final Color WEAPONS_ITEM_ENERGY_ZOLTAN = new Color(255, 250, 90);

    // These numbers took quite some work to obtain
    // They're specifically from the Kestral's shields, but I assume/hope they're the same for everything
    // The actual numbers was 0.571429 for level one shields, 0.742857 for level two and 0.914286 for level
    // three (I didn't have the patience to get Level 4 shields, or to install a savefile editor)
    // These ones are close enough and add up
    public static final float SHIELD_OPACITY_BASE = 0.4f;
    public static final float SHIELD_OPACITY_LEVEL = (1 - SHIELD_OPACITY_BASE) / 4;

    // Time in seconds it takes a single human to repair one bar of a system
    // TODO measure more accurately
    public static final float BASE_REPAIR_TIME = 13.0f;

    private Constants() {
    }

    static {
        float opacities[] = new float[]{
                1,
                0.8f, 0.8f, 0.8f,
                0.6f, 0.6f,
                0.5f, 0.5f,
                0.35f
        };
        ROOM_SELECTION_COLOURS = new Color[opacities.length];

        for (int i = 0; i < opacities.length; i++) {
            Color c = new Color(ROOM_SELECTION_COLOUR);
            c.a = opacities[i];
            ROOM_SELECTION_COLOURS[i] = c;
        }
    }
}
