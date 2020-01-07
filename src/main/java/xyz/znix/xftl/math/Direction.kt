package xyz.znix.xftl.math

enum class Direction(override val x: Int, override val y: Int, val angle: Int) : IPoint {
    UP(0, -1, 0),
    UP_RIGHT(1, -1, 45),
    RIGHT(1, 0, 90),
    RIGHT_DOWN(1, 1, 135),
    DOWN(0, 1, 180),
    DOWN_LEFT(-1, 1, 225),
    LEFT(-1, 0, 270),
    LEFT_UP(-1, -1, 315);

    companion object {
        fun fromPoint(point: IPoint): Direction? {
            if (point is Direction)
                return point

            for (p in values()) {
                if (p posEq point)
                    return p
            }

            return null
        }
    }
}
