package xyz.znix.xftl.systems

import org.jdom2.Element
import xyz.znix.xftl.AbstractSystem

abstract class MainSystem(codename: String, elem: Element) : AbstractSystem(codename, elem) {
    var selectedEnergyLevel: Int = 1
}
