package xyz.znix.xftl.game

import org.newdawn.slick.Image
import xyz.znix.xftl.SILFontLoader
import xyz.znix.xftl.f

object UIUtils {
    /**
     * Draws a text tab. In the images these are thin tabs that get expanded to correctly fit the localised
     * string at runtime. The image consists of what you might call three regions:
     *
     * - The start region, drawn before the text
     * - The text region, which is stretched to match the width of the text (which is drawn onto it)
     * - The end region, which is drawn after the text region
     *
     * @return The end position of the tab
     */
    fun drawTab(font: SILFontLoader, text: String, img: Image,
                x: Float, y: Float, startWidth: Float, endWidth: Float): Float {
        val textWidth = font.getWidth(text).f
        val scrBase = y + img.height

        // Screen X coordinates
        val sx1 = x + startWidth // Between the start and text areas
        val sx2 = sx1 + textWidth // Between the text and end areas
        val sx3 = sx2 + endWidth // The end X position

        img.draw(x, y, sx1, scrBase, 0f, 0f, startWidth, img.height.f)
        img.draw(sx1, y, sx2, scrBase, startWidth, 0f, img.width.f - endWidth, img.height.f)
        img.draw(sx2, y, sx3, scrBase, img.width.f - endWidth, 0f, img.width.f, img.height.f)

        return startWidth + textWidth + endWidth
    }
}
