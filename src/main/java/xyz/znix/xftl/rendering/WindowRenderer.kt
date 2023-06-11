package xyz.znix.xftl.rendering

import org.newdawn.slick.Color
import xyz.znix.xftl.Utils
import xyz.znix.xftl.f

/**
 * This class can efficiently render dynamically-sized windows by tiling
 * the window_base texture.
 */
class WindowRenderer(
    backgroundImage: Image,
    private val outlineImage: Image,
    private val maskImage: Image
) {
    private val baseImage = BulkImageRenderer(backgroundImage)

    fun render(x: Int, y: Int, width: Int, height: Int) {
        // Subtract out the size of the glow, to avoid having to do that later on.
        val xBase = x - GLOW
        val yBase = y - GLOW
        val outlineWidth = width + GLOW * 2
        val outlineHeight = height + GLOW * 2

        // Tile in the background area. We have to mask it, since the tiles
        // will clip the corners of the image.
        Utils.drawStenciled(Utils.StencilMode.MASKING, {
            drawOutlineOrMask(maskImage, xBase, yBase, outlineWidth, outlineHeight)
        }, {
            drawTiled(x, y, width, height)
        })

        // Draw the outline image
        drawOutlineOrMask(outlineImage, xBase, yBase, outlineWidth, outlineHeight)

        // For testing, this will draw an outline around the specified area.
        // g.color = Color(1f, 0f, 0f, 0.2f)
        // g.drawRect(x.f, y.f, width - 1f, height - 1f)
    }

    // This draws more than the specified width/height, so it must be masked.
    private fun drawTiled(x: Int, y: Int, width: Int, height: Int) {
        var tileX = 0
        while (tileX <= width) {
            var tileY = 0
            while (tileY <= height) {
                baseImage.pushImage(
                    x.f + tileX,
                    y.f + tileY,

                    x.f + tileX + TILE_X,
                    y.f + tileY + TILE_Y,

                    28f, 29f,
                    28f + TILE_X, 29f + TILE_Y,

                    Color.white
                )

                tileY += TILE_Y
            }
            tileX += TILE_X
        }
        baseImage.flush()
    }

    // Note the coordinates for this include the glow!
    private fun drawOutlineOrMask(image: Image, x: Int, y: Int, width: Int, height: Int) {
        // Define some lines that run CORNER_SIZE away from the edge, since
        // we'll need these repeatedly when drawing.
        val leftLineX = x.f + CORNER_SIZE
        val rightLineX = x.f + width - CORNER_SIZE
        val topLineY = y.f + CORNER_SIZE
        val bottomLineY = y.f + height - CORNER_SIZE

        val rightX = x.f + width
        val bottomY = y.f + height

        // And the same in image-space
        val srcRightLine = image.width - CORNER_SIZE.f
        val srcBottomLine = image.height - CORNER_SIZE.f
        val srcRight = image.width.f
        val srcBottom = image.height.f

        // Top-left
        image.draw(
            x.f, y.f,
            leftLineX, topLineY,
            0f, 0f, CORNER_SIZE.f, CORNER_SIZE.f
        )

        // Top-right
        image.draw(
            rightLineX, y.f,
            rightX, topLineY,
            srcRightLine, 0f, srcRight, CORNER_SIZE.f
        )

        // Bottom-left
        image.draw(
            x.f, bottomLineY,
            leftLineX, bottomY,
            0f, srcBottomLine, CORNER_SIZE.f, srcBottom
        )

        // Bottom-right
        image.draw(
            rightLineX, bottomLineY,
            rightX, bottomY,
            srcRightLine, srcBottomLine, srcRight, srcBottom
        )

        // Draw the lines connecting the corners

        // Left side
        image.draw(
            x.f, topLineY,
            leftLineX, bottomLineY,
            0f, CORNER_SIZE.f, CORNER_SIZE.f, srcBottomLine
        )

        // Right side
        image.draw(
            rightLineX, topLineY,
            rightX, bottomLineY,
            srcRightLine, CORNER_SIZE.f, srcRight, srcBottomLine
        )

        // Top side
        image.draw(
            leftLineX, y.f,
            rightLineX, topLineY,
            CORNER_SIZE.f, 0f, srcRightLine, CORNER_SIZE.f
        )

        // Bottom side
        image.draw(
            leftLineX, bottomLineY,
            rightLineX, bottomY,
            CORNER_SIZE.f, srcBottomLine, srcRightLine, srcBottom
        )

        // In case we're drawing the mask, fill in the centre too.
        image.draw(
            leftLineX, topLineY,
            rightLineX, bottomLineY,
            CORNER_SIZE.f, CORNER_SIZE.f, srcRightLine, srcBottomLine
        )
    }

    companion object {
        private const val GLOW = 7

        private const val TILE_X = 10
        private const val TILE_Y = 17

        // This is a little larger than needed, but it doesn't hurt in case
        // there's a subtle change in the glow closer.
        private const val CORNER_SIZE = 28
    }
}
