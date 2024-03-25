package xyz.znix.xftl.game

import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import xyz.znix.xftl.GameText
import xyz.znix.xftl.requireAttributeValue
import xyz.znix.xftl.sys.Input

class HotkeyManager {
    val groups: List<HotkeyGroup>

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
}

class HotkeyGroup(val id: String, val name: GameText, val columns: Int, val hotkeys: List<Hotkey>)

class Hotkey(val id: String, val name: GameText, val default: HotkeyButton?)

class HotkeyButton(val id: String, val keyID: Int) {
    val locKey: String = "xftl_key_$id"

    companion object {
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

        val BY_NAME: Map<String, HotkeyButton> = ALL.associateBy { it.id }
        val BY_KEY_ID: Map<Int, HotkeyButton> = ALL.associateBy { it.keyID }
    }
}
