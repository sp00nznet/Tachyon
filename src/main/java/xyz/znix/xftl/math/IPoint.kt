package xyz.znix.xftl.math

import kotlin.math.absoluteValue

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

    operator fun times(other: IPoint): ConstPoint {
        return ConstPoint(x * other.x, y * other.y)
    }

    operator fun times(other: Int): ConstPoint {
        return ConstPoint(x * other, y * other)
    }

    /**
     * Return a point where the X and Y are the minimum value between this and the [other] point.
     */
    fun min(other: IPoint): ConstPoint {
        return ConstPoint(kotlin.math.min(x, other.x), kotlin.math.min(y, other.y))
    }

    /**
     * Return this point, but with it's X and Y coordinates each with their sign inverted if they are negative.
     */
    fun abs(): ConstPoint {
        return ConstPoint(x.absoluteValue, y.absoluteValue)
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