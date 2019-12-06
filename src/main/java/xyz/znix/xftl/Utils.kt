package xyz.znix.xftl

// Make <int>.f a shorthand for <int>.toFloat(), cleaning things up a lot
val Int.f get() = toFloat()
