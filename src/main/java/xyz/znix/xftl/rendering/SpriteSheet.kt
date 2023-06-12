package xyz.znix.xftl.rendering

class SpriteSheet(val sheetImage: Image, val frameWidth: Int, val frameHeight: Int) {
    val verticalCount: Int get() = sheetImage.height / frameHeight
    val horizontalCount: Int get() = sheetImage.width / frameWidth

    fun getSprite(x: Int, y: Int): Image {
        require(x in 0 until horizontalCount)
        require(y in 0 until verticalCount)

        return sheetImage.getSubImage(
            x * frameWidth, y * frameHeight,
            frameWidth, frameHeight
        )
    }
}
