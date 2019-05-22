package xyz.znix.xftl.math

data class Point(override var x: Int, override var y: Int) : IPoint {
    constructor(point: IPoint) : this(point.x, point.y)

    override fun equals(other: Any?): Boolean {
        if (other !is IPoint)
            return false

        return other.x == x && other.y == y
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }

    override fun toString(): String {
        return "Point(x=$x, y=$y)"
    }

    operator fun plusAssign(other: IPoint) {
        x += other.x
        y += other.y
    }

    operator fun minusAssign(other: IPoint) {
        x -= other.x
        y -= other.y
    }
}