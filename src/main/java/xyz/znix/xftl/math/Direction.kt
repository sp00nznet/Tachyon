package xyz.znix.xftl.math

enum class Direction(override val x: Int, override val y: Int) : IPoint {
    UP(0, -1),
    UP_RIGHT(1, -1),
    RIGHT(1, 0),
    RIGHT_DOWN(1, 1),
    DOWN(0, 1),
    DOWN_LEFT(-1, 1),
    LEFT(-1, 0),
    LEFT_UP(-1, -1);

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
