package xyz.znix.xftl;

import org.newdawn.slick.Color;

public class Constants {
    public static final int ROOM_SIZE = 35;

    public static final Color FLOOR_COLOUR = new Color(230, 226, 219);
    public static final Color FLOOR_COLOUR_NO_OXYGEN = new Color(254, 178, 171);
    public static final Color FLOOR_GRID_COLOUR = new Color(172, 169, 164);
    public static final Color FLOOR_GRID_CROSS = new Color(129, 127, 123);
    public static final Color ROOM_BORDER_COLOUR = Color.black;
    public static final Color ROOM_BORDER_COLOUR_SELECTED = Color.yellow;
    public static final Color ROOM_BORDER_COLOUR_SELECTED_INNER = new Color(255, 188, 0);

    // An image tint applied to broken doors
    public static final Color DOOR_BROKEN_FILTER = new Color(1f, 0.6f, 0.6f);

    public static final Color SYSTEM_NORMAL = new Color(125, 125, 125);
    public static final Color SYSTEM_IONISED = new Color(133, 231, 237);
    public static final Color SYSTEM_HACKED = new Color(207, 70, 253);
    public static final Color SYSTEM_DAMAGED = new Color(255, 153, 76);
    public static final Color SYSTEM_BROKEN = new Color(255, 0, 0);

    public static final Color UI_TEXT_COLOUR_1 = new Color(40, 78, 82);
    public static final Color UI_BUTTON_HOVER = new Color(255, 230, 94);
    public static final Color UI_BACKGROUND_GLOW_COLOUR = new Color(243, 255, 238);
    public static final Color UI_SCRAP_TEXT_COLOUR = new Color(243, 255, 230);

    public static final Color SYS_ENERGY_ACTIVE = new Color(100, 255, 100);
    public static final Color SYS_ENERGY_DEPOWERED = new Color(251, 251, 251);
    public static final Color SYS_ENERGY_BROKEN = new Color(255, 50, 50);
    public static final Color SYS_ENERGY_SABOTAGE = new Color(252, 53, 49);
    public static final Color SYS_ENERGY_REPAIR = new Color(255, 255, 50);
    public static final Color SYS_ENERGY_PURCHASE = new Color(104, 98, 59);
    public static final Color SYS_ENERGY_PURCHASE_HOVER = new Color(164, 146, 108);
    public static final Color SYS_ENERGY_PURCHASE_UNDOABLE = new Color(255, 255, 100);
    public static final Color SYS_ENERGY_EVENT_LOCKED = new Color(44, 206, 225);

    public static final Color WEAPONS_ITEM_DESELECTED = new Color(150, 150, 150);
    public static final Color WEAPONS_ITEM_SELECTED = UI_BACKGROUND_GLOW_COLOUR;
    public static final Color WEAPONS_ITEM_CHARGED = new Color(120, 255, 120);
    public static final Color WEAPONS_ITEM_TARGETING = new Color(255, 120, 120);

    public static final Color WEAPONS_ITEM_ENERGY_UNPOWERED = WEAPONS_ITEM_DESELECTED;
    public static final Color WEAPONS_ITEM_ENERGY_POWERED = WEAPONS_ITEM_SELECTED;
    public static final Color WEAPONS_ITEM_ENERGY_CHARGED = WEAPONS_ITEM_CHARGED;
    public static final Color WEAPONS_ITEM_ENERGY_ZOLTAN = new Color(255, 250, 90);

    public static final Color JUMP_READY = new Color(235, 245, 0);
    public static final Color JUMP_READY_TEXT = new Color(37, 74, 77);
    public static final Color JUMP_READY_TEXT_HOVER = new Color(62, 125, 131);
    public static final Color JUMP_DISABLED = new Color(164, 171, 160);
    public static final Color JUMP_DISABLED_TEXT = new Color(25, 49, 51);

    public static final Color SECTOR_CUTOUT = new Color(53, 75, 89);
    public static final Color SECTOR_CUTOUT_TEXT = new Color(235, 245, 229);

    public static final Color STORE_BUY_HOVER = new Color(245, 238, 163);
    public static final Color STORE_SELL_TITLE = new Color(28, 21, 21);

    public static final Color TEXT_OPTION_BLUE = new Color(0, 195, 255);
    public static final Color TEXT_OPTION_HOVER = new Color(243, 255, 80);
    public static final Color TEXT_OPTION_DISABLED = WEAPONS_ITEM_DESELECTED;

    public static final Color REWARDS_ICONS = SYS_ENERGY_ACTIVE;
    public static final Color REWARDS_NEGATIVE_ICONS = SYS_ENERGY_BROKEN;
    public static final Color REWARDS_BACKGROUND = new Color(0, 0, 0, 128);

    public static final Color CREW_DESELECTED_BG = SYS_ENERGY_REPAIR;
    public static final Color CREW_SELECTED_BG = SYS_ENERGY_ACTIVE;
    public static final Color CREW_HOSTILE_BG = SYS_ENERGY_BROKEN;

    // Note: divide the colours by 4 if they're greyed-out because another
    // sector is hovered.
    public static final Color SECTOR_CIVILIAN = new Color(135, 199, 74);
    public static final Color SECTOR_HOSTILE = new Color(214, 50, 50);
    public static final Color SECTOR_NEBULA = new Color(128, 51, 210);

    // The colours of the branch lines that link up the sectors
    public static final Color SECTOR_BRANCH = new Color(255, 255, 255);
    public static final Color SECTOR_BRANCH_GREYED = new Color(125, 125, 125);
    public static final Color SECTOR_BRANCH_HOVER = SYS_ENERGY_ACTIVE;
    public static final Color SECTOR_BRANCH_PATH = SYS_ENERGY_REPAIR;

    // The colours of the background and text in the new sector window, which
    // describes the different sector colours.
    public static final Color SECTOR_TYPE_CUTOUT = new Color(31, 19, 19);
    public static final Color SECTOR_TYPE_CUTOUT_TEXT = SECTOR_CUTOUT_TEXT;

    // These describe the name label box that shows the name of the next two sectors
    public static final Color SECTOR_NAME_BACKGROUND = new Color(0f, 0f, 0f, 0.8f);
    public static final Color SECTOR_NAME_TEXT = SECTOR_BRANCH;
    public static final Color SECTOR_NAME_TEXT_GREYED = SECTOR_BRANCH_GREYED;
    public static final Color SECTOR_NAME_TEXT_HOVER = SYS_ENERGY_REPAIR;

    // These are the colours of the augment box
    public static final Color AUGMENT_BOX_OUTLINE = SECTOR_CUTOUT_TEXT;
    public static final Color AUGMENT_BOX_OUTLINE_HOVER = UI_BUTTON_HOVER;
    public static final Color AUGMENT_BOX_INSIDE = new Color(146, 117, 113, 216);
    public static final Color AUGMENT_NAME_TEXT = Color.white;
    public static final Color AUGMENT_EMPTY_OUTLINE = JUMP_DISABLED;
    public static final Color AUGMENT_EMPTY_INSIDE = new Color(34, 27, 26);

    public static final Color SHIELD_BAR_NORMAL = new Color(27, 132, 255);
    public static final Color SHIELD_BAR_HACKED = SYSTEM_HACKED;

    // SHIELD_OPACITY_BASE is the base amount of opacity drawn on level-0 shields
    // (if they were to be drawn at all, which they won't), with SHIELD_OPACITY_SCALING
    // then multiplied by the current shield power divided by the maximum number
    // of shield layers (which is 4 for enemy ships and 3.5 for the player for some reason?).
    public static final float SHIELD_OPACITY_BASE = 0.4f;
    public static final float SHIELD_OPACITY_SCALING = (1 - SHIELD_OPACITY_BASE);

    // Time in seconds it takes a single human to repair one bar of a system
    // TODO measure more accurately
    public static final float BASE_REPAIR_TIME = 13.0f;

    public static final Color CLONE_DYING_FILTER = new Color(1f, 0.3f, 0.3f);

    public static final Color CREW_BOX_NORMAL = new Color(100, 100, 100);
    public static final Color CREW_BOX_HOVER = new Color(235, 235, 235);
    public static final Color CREW_BOX_SELECT = new Color(120, 255, 120);
    public static final Color CREW_BOX_LOW_HEALTH = new Color(255, 120, 120);
    public static final Color CREW_BOX_STUNNED = new Color(255, 255, 100);
    public static final Color CREW_BOX_MIND_CONTROLLED = new Color(255, 0, 255);
    public static final float CREW_BOX_BG_ALPHA = 0.25f;
    public static final Color CREW_BOX_NAME_COLOUR = new Color(200, 200, 200);

    private Constants() {
    }
}
