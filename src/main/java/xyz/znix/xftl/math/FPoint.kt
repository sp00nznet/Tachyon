package xyz.znix.xftl.math

/**
 * A floating-point version of [IPoint].
 */
interface FPoint : IPoint {
    val xf: Float
    val yf: Float

    override val x: Int get() = xf.toInt()
    override val y: Int get() = yf.toInt()

    operator fun plus(other: FPoint): ConstFPoint {
        return ConstFPoint(xf + other.xf, yf + other.yf)
    }

    operator fun minus(other: FPoint): ConstFPoint {
        return ConstFPoint(xf - other.xf, yf - other.yf)
    }
}
