package xyz.znix.xftl.math

interface IPoint {
    val x: Int
    val y: Int

    val isVertical: Boolean get() = x == 0 && y != 0
    val isHorizontal: Boolean get() = x != 0 && y == 0
    val isZero: Boolean get() = x == 0 && y == 0
    val isDiagonal: Boolean get() = x != 0 && y != 0

    operator fun plus(other: IPoint): ConstPoint {
        return ConstPoint(x + other.x, y + other.y)
    }

    operator fun minus(other: IPoint): ConstPoint {
        return ConstPoint(x - other.x, y - other.y)
    }

    operator fun unaryMinus(): ConstPoint {
        return ConstPoint(-x, -y)
    }
}