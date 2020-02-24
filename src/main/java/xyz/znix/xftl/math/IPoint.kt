package xyz.znix.xftl.math

interface IPoint {
    val x: Int
    val y: Int

    val isVertical: Boolean get() = x == 0 && y != 0
    val isHorizontal: Boolean get() = x != 0 && y == 0
    val isZero: Boolean get() = x == 0 && y == 0
    val isDiagonal: Boolean get() = x != 0 && y != 0

    open val const: ConstPoint get() = ConstPoint(this)

    operator fun plus(other: IPoint): ConstPoint {
        return ConstPoint(x + other.x, y + other.y)
    }

    operator fun minus(other: IPoint): ConstPoint {
        return ConstPoint(x - other.x, y - other.y)
    }

    operator fun unaryMinus(): ConstPoint {
        return ConstPoint(-x, -y)
    }

    /**
     * Compare two points, matching if they have the same X or Y, regardless of their type
     */
    infix fun posEq(other: IPoint): Boolean {
        return x == other.x && y == other.y
    }

    fun distToSq(other: IPoint): Int {
        val dx = other.x - x
        val dy = other.y - y
        return dx * dx + dy * dy
    }
}