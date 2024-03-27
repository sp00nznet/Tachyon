package xyz.znix.xftl.game

import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.GameText
import xyz.znix.xftl.requireAttributeValue
import xyz.znix.xftl.sys.Input

class HotkeyManager {
    val groups: List<HotkeyGroup>
    val byID: Map<String, Hotkey>

    // These are special-cased, and thus we have to fish them out here
    val keyDevConsole: Hotkey
    val keyFrameStep: Hotkey
    val keyFastForward: Hotkey

    init {
        groups = ArrayList()

        // TODO support modding these files
        val builder = SAXBuilder()
        builder.setExpandEntities(false)
        val doc = builder.build(javaClass.getResourceAsStream("/assets/data/xftl_hotkeys.xml"))

        require(doc.rootElement.name == "hotkeys")

        for (groupElem in doc.rootElement.children) {
            require(groupElem.name == "group")

            groups += parseGroup(groupElem)
        }

        byID = groups.flatMap { it.hotkeys }.associateBy { it.id }
        keyDevConsole = byID.getValue(VanillaHotkeys.DEV_CONSOLE)
        keyFrameStep = byID.getValue(VanillaHotkeys.DEV_FRAME_STEP)
        keyFastForward = byID.getValue(VanillaHotkeys.DEV_FAST_FORWARD)
    }

    private fun parseGroup(elem: Element): HotkeyGroup {
        val id = elem.requireAttributeValue("id")
        val uiName = elem.requireAttributeValue("ui_name")
        val columnCount = elem.getAttributeValue("columns")?.toInt() ?: 1

        val hotkeys = ArrayList<Hotkey>()
        for (child in elem.children) {
            require(child.name == "hotkey")
            hotkeys.add(parseHotkey(child))
        }

        return HotkeyGroup(id, GameText.localised(uiName), columnCount, hotkeys)
    }

    private fun parseHotkey(elem: Element): Hotkey {
        val id = elem.requireAttributeValue("id")
        val uiName = elem.requireAttributeValue("ui_name")
        val defaultId = elem.getAttributeValue("default_bind")

        val default = defaultId?.let { HotkeyButton.BY_NAME[it] ?: error("Invalid default keybind $it") }

        return Hotkey(id, GameText.localised(uiName), default)
    }

    /**
     * Updates the [forward] and [reverse] map with the hotkey-to-key bindings, including defaults.
     */
    fun calculateBindings(
        profile: SaveProfile,
        forward: HashMap<HotkeyButton, Hotkey>,
        reverse: HashMap<Hotkey, HotkeyButton>
    ) {
        forward.clear()
        reverse.clear()

        val byId: Map<String, Hotkey> = groups
            .flatMap { it.hotkeys }
            .associateBy { it.id }

        // Add the default mappings
        for (hotkey in byId.values) {
            reverse[hotkey] = hotkey.default ?: continue
        }

        // Add our custom mappings
        for ((actionId, keyId) in profile.getKeybinds()) {
            val action = byId[actionId] ?: continue

            if (keyId == null) {
                reverse.remove(action)
                continue
            }

            val key = HotkeyButton.BY_NAME[keyId] ?: continue
            reverse[action] = key
        }

        // Build the forward bindings map.
        // Note some actions might overlap each other.
        for ((action, key) in reverse) {
            forward[key] = action
        }
    }
}

class HotkeyGroup(val id: String, val name: GameText, val columns: Int, val hotkeys: List<Hotkey>)

class Hotkey(val id: String, val name: GameText, val default: HotkeyButton?)

class HotkeyButton(val id: String, val keyID: Int) {
    val locKey: String = "xftl_key_$id"

    companion object {
        @JvmField
        val ALL = listOf(
            HotkeyButton("f1", Input.KEY_F1),
            HotkeyButton("f2", Input.KEY_F2),
            HotkeyButton("f3", Input.KEY_F3),
            HotkeyButton("f4", Input.KEY_F4),
            HotkeyButton("f5", Input.KEY_F5),
            HotkeyButton("f6", Input.KEY_F6),
            HotkeyButton("f7", Input.KEY_F7),
            HotkeyButton("f8", Input.KEY_F8),
            HotkeyButton("f9", Input.KEY_F9),

            HotkeyButton("up", Input.KEY_UP),
            HotkeyButton("down", Input.KEY_DOWN),
            HotkeyButton("left", Input.KEY_LEFT),
            HotkeyButton("right", Input.KEY_RIGHT),

            HotkeyButton("enter", Input.KEY_ENTER),
            HotkeyButton("escape", Input.KEY_ESCAPE),
            HotkeyButton("space", Input.KEY_SPACE),
            HotkeyButton("tab", Input.KEY_TAB),
            HotkeyButton("full_stop", Input.KEY_FULL_STOP),
            HotkeyButton("stroke", Input.KEY_STROKE),
            HotkeyButton("lshift", Input.KEY_LSHIFT),
            HotkeyButton("rshift", Input.KEY_RSHIFT),
            HotkeyButton("lctrl", Input.KEY_LCTRL),
            HotkeyButton("rctrl", Input.KEY_RCTRL),
            HotkeyButton("back", Input.KEY_BACK),
            HotkeyButton("delete", Input.KEY_DELETE),
            HotkeyButton("backtick", Input.KEY_GRAVE),

            HotkeyButton("a", Input.KEY_A),
            HotkeyButton("b", Input.KEY_B),
            HotkeyButton("c", Input.KEY_C),
            HotkeyButton("d", Input.KEY_D),
            HotkeyButton("e", Input.KEY_E),
            HotkeyButton("f", Input.KEY_F),
            HotkeyButton("g", Input.KEY_G),
            HotkeyButton("h", Input.KEY_H),
            HotkeyButton("i", Input.KEY_I),
            HotkeyButton("j", Input.KEY_J),
            HotkeyButton("k", Input.KEY_K),
            HotkeyButton("l", Input.KEY_L),
            HotkeyButton("m", Input.KEY_M),
            HotkeyButton("n", Input.KEY_N),
            HotkeyButton("o", Input.KEY_O),
            HotkeyButton("p", Input.KEY_P),
            HotkeyButton("q", Input.KEY_Q),
            HotkeyButton("r", Input.KEY_R),
            HotkeyButton("s", Input.KEY_S),
            HotkeyButton("t", Input.KEY_T),
            HotkeyButton("u", Input.KEY_U),
            HotkeyButton("v", Input.KEY_V),
            HotkeyButton("w", Input.KEY_W),
            HotkeyButton("x", Input.KEY_X),
            HotkeyButton("y", Input.KEY_Y),
            HotkeyButton("z", Input.KEY_Z),

            HotkeyButton("0", Input.KEY_0),
            HotkeyButton("1", Input.KEY_1),
            HotkeyButton("2", Input.KEY_2),
            HotkeyButton("3", Input.KEY_3),
            HotkeyButton("4", Input.KEY_4),
            HotkeyButton("5", Input.KEY_5),
            HotkeyButton("6", Input.KEY_6),
            HotkeyButton("7", Input.KEY_7),
            HotkeyButton("8", Input.KEY_8),
            HotkeyButton("9", Input.KEY_9),
        )

        @JvmField
        val BY_NAME: Map<String, HotkeyButton> = ALL.associateBy { it.id }

        @JvmField
        val BY_KEY_ID: Map<Int, HotkeyButton> = ALL.associateBy { it.keyID }
    }
}

/**
 * Contains the string IDs for the vanilla hotkeys, to make stuff like searching
 * and refactoring easier.
 *
 * It also makes it easy to see what hotkeys aren't yet implemented - anything
 * that's shown as unused in your IDE is obviously unimplemented.
 */
object VanillaHotkeys {
    const val PAUSE: String = "pause"
    const val FTL_JUMP: String = "ftl_jump"
    const val SHIP_INVENTORY: String = "ship_inventory"
    const val SHIP_UPGRADES: String = "ship_upgrades"
    const val SHIP_CREW: String = "ship_crew"
    const val OPEN_STORE: String = "open_store"
    const val OPEN_OPTIONS: String = "open_options"

    @JvmField
    val WEAPON_SLOTS: List<String> = listOf("weapon_slot_1", "weapon_slot_2", "weapon_slot_3", "weapon_slot_4")

    @JvmField
    val DRONE_SLOTS: List<String> = listOf("drone_slot_1", "drone_slot_2", "drone_slot_3")

    const val WEAPON_AUTOFIRE_TOGGLE: String = "weapon_autofire_toggle"
    const val WEAPON_AUTOFIRE_TARGET: String = "weapon_autofire_target"

    // We don't have constants for the systems, as their ID is built from the blueprint name
    // The exception is the medbay/clonebay which share a hotkey
    const val SYS_POWER_MEDICAL: String = "sys_power_medical"
    const val SYS_POWER_MEDICAL_OFF: String = "sys_power_medical_off"

    const val SYS_ACTION_DOOR_OPEN: String = "sys_action_door_open"
    const val SYS_ACTION_DOOR_CLOSE: String = "sys_action_door_close"
    const val SYS_ACTION_CLOAKING: String = "sys_action_cloaking"
    const val SYS_ACTION_TELEPORT_SEND: String = "sys_action_teleport_send"
    const val SYS_ACTION_TELEPORT_RECV: String = "sys_action_teleport_recv"
    const val SYS_ACTION_HACKING: String = "sys_action_hacking"
    const val SYS_ACTION_MIND: String = "sys_action_mind"
    const val SYS_ACTION_BATTERY: String = "sys_action_battery"

    @JvmField
    val SELECT_CREW: List<String> = listOf(
        "select_crew_1", "select_crew_2", "select_crew_3", "select_crew_4",
        "select_crew_5", "select_crew_6", "select_crew_7", "select_crew_8",
    )

    const val SELECT_CREW_ALL: String = "select_crew_all"
    const val LOAD_CREW_POS: String = "load_crew_pos"
    const val SAVE_CREW_POS: String = "save_crew_pos"
    const val LOCKDOWN_ACTIVATE: String = "lockdown_activate"

    const val DEV_CONSOLE: String = "dev_console"
    const val DEV_FRAME_STEP: String = "dev_frame_step"
    const val DEV_FAST_FORWARD: String = "dev_fast_forward"
}
