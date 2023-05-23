package org.newdawn.slick.openal

/**
 * Helper class that lets us access some of Slick's package-internal stuff.
 */
object SoundAccess {
    fun playAsSound(buffer: Int, pitch: Float, gain: Float, loop: Boolean): Int {
        return SoundStore.get().playAsSound(buffer, pitch, gain, loop)
    }

    fun stopSource(index: Int) {
        SoundStore.get().stopSource(index)
    }
}
