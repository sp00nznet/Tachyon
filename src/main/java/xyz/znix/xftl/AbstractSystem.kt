package xyz.znix.xftl

import org.w3c.dom.Element

abstract class AbstractSystem(val codename: String, elem: Element) {
    fun update(dt: Float) {
    }

    open val icon: String = "img/icons/s_${codename}_overlay.png"

    val img: String = "img/ship/interior/${elem.getAttribute("img")}.png"

}