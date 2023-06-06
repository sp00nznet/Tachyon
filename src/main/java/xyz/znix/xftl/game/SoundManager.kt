package xyz.znix.xftl.game

import org.jdom2.Element
import org.lwjgl.openal.AL10
import org.newdawn.slick.openal.*
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.Ship
import java.io.InputStream

class SoundManager(private val df: Datafile) {
    private val sounds = HashMap<SoundSpec, FTLSound>()
    private val rawBuffers = HashMap<String, Int>()

    // Nebula is both a sample and a loop
    private val samples = HashMap<String, SoundSpec>()
    private val loops = HashMap<String, SoundSpec>()

    private val playingLoops = ArrayList<LoopHandle>()
    private val loopSounds = HashMap<SoundSpec, SoundInstance>()
    private val loopCounts = HashMap<SoundSpec, Int>()

    init {
        // Make sure we've classloaded our modified copy of OggInputStream
        OggInputStream.FTL_MARKER = 2

        loadXml("data/sounds.xml")
        loadXml("data/dlcSounds.xml")
    }

    private fun loadXml(path: String) {
        val doc = df.parseXML(df[path])
        for (elem in doc.rootElement.children) {
            if (elem.name == "music") {
                // TODO load music
                continue
            }

            val spec = SoundSpec(elem)
            if (spec.loop) {
                if (loops.containsKey(spec.name)) {
                    error("Duplicate sound loop '${spec.name}' in sounds.xml")
                }
                loops[spec.name] = spec
            } else {
                if (samples.containsKey(spec.name)) {
                    error("Duplicate sound sample '${spec.name}' in sounds.xml")
                }
                samples[spec.name] = spec
            }
        }
    }

    fun getSample(name: String): FTLSound {
        val spec = samples[name] ?: error("No sound sample listed in sounds.xml for '$name'")
        return get(spec)
    }

    /**
     * Create a new handle that can trigger this sound loop.
     *
     * This doesn't actually produce any audio unless [LoopHandle.continueLoopAnyShip] is called.
     */
    fun getLoop(name: String): LoopHandle {
        val spec = loops[name] ?: error("No sound loop listed in sounds.xml for '$name'")
        return LoopHandle(spec, this)
    }

    // Should only be called by SoundInstance!
    fun registerPlayingLoop(loop: LoopHandle) {
        playingLoops.add(loop)
    }

    fun updateLoopedSounds(gamePaused: Boolean) {
        if (gamePaused) {
            for (sound in loopSounds.values) {
                sound.isPaused = true
            }
            return
        }

        // Calculate how many handles are currently playing for each loop.
        for (handle in playingLoops) {
            loopCounts[handle.spec] = (loopCounts[handle.spec] ?: 0) + 1
        }
        playingLoops.clear()

        // Update the currently playing loops based on those counts.
        // Note this effectively clears the loop counts.

        // Removing one sound per update is fine - note we still stop
        // them playing in a single update.
        var toRemove: SoundSpec? = null

        for ((spec, sound) in loopSounds.entries) {
            val count = loopCounts.remove(spec) ?: 0

            // TODO fade down the loops when they stop, like FTL does
            if (count == 0) {
                sound.stop()
                toRemove = spec
                continue
            }

            // Un-pause sounds that were paused by pausing the game.
            sound.isPaused = false

            sound.setVolume(1f * count / spec.maxCount)
        }

        // Start any new sounds
        if (loopCounts.isNotEmpty()) {
            for ((spec, count) in loopCounts.entries) {
                val sound = get(spec).internalPlayRawLoop()
                sound.setVolume(1f * count / spec.maxCount)
                loopSounds[spec] = sound
            }
            loopCounts.clear()
        }

        // Remove stopped sounds so they can be GCed.
        // We can't do this in the loop above since that'd mean modifying
        // the map while it's being iterated over.
        if (toRemove != null) {
            loopSounds.remove(toRemove)
        }
    }

    private fun get(spec: SoundSpec): FTLSound {
        sounds[spec]?.let { return it }

        val sound = loadRawSound(spec)
        sounds[spec] = sound
        return sound
    }

    private fun loadRawSound(spec: SoundSpec): FTLSound {
        val path = "audio/waves/" + spec.path

        val buffer = loadBuffer(path)

        return FTLSound(spec, buffer)
    }

    private fun loadBuffer(path: String): Int {
        rawBuffers[path]?.let { return it }

        val soundStore = SoundStore.get()
        soundStore.init()

        if (!soundStore.soundWorks()) {
            return SoundInstance.NO_SOUND_BUFFER
        }

        // Rather than using Slick's audio abstractions, use OpenAL directly.
        // It'll be easier to handle stuff like playing the same sound multiple
        // times concurrently.
        val buffer = try {
            df.open(df[path]).use { stream ->
                if (path.endsWith(".ogg")) {
                    loadOGG(stream)
                } else if (path.endsWith(".wav")) {
                    getWAV(stream)
                } else {
                    error("Unsupported sound format for file '$path'")
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to load sound from path '$path'", e)
        }

        rawBuffers[path] = buffer
        return buffer
    }

    private fun loadOGG(stream: InputStream): Int {
        // Based off SoundStore.getOgg
        val decoder = OggDecoder()
        val ogg = decoder.getData(stream)
        val buf = AL10.alGenBuffers()
        require(buf != -1)
        AL10.alBufferData(
            buf,
            if (ogg.channels > 1) AL10.AL_FORMAT_STEREO16 else AL10.AL_FORMAT_MONO16,
            ogg.data,
            ogg.rate
        )
        return buf
    }

    private fun getWAV(stream: InputStream): Int {
        // Based off SoundStore.getWAV
        val data = WaveData.create(stream)
        val buf = AL10.alGenBuffers()
        require(buf != -1)
        AL10.alBufferData(buf, data.format, data.data, data.samplerate)
        return buf
    }
}

/**
 * This is our representation of a sound that can be played.
 *
 * Note it doesn't represent a currently-playing sound - since
 * a sound can be playing twice at once, [SoundInstance]s are
 * used for that purpose.
 */
class FTLSound(val spec: SoundSpec, private val buffer: Int) {
    /**
     * Play a non-looping sound.
     */
    fun play(): SoundInstance {
        require(!spec.loop)
        return SoundInstance(spec, buffer)
    }

    /**
     * Don't use this outside of SoundManager!
     *
     * This plays a loop by itself, which should instead be done via a [LoopHandle].
     */
    fun internalPlayRawLoop(): SoundInstance {
        require(spec.loop)
        return SoundInstance(spec, buffer)
    }
}

/**
 * A PlaybackInstance represents a single playback of a sound. It can
 * be paused or stopped independent of all other currently-playing instances
 * of this sound.
 *
 * Instances of this class should be created via [FTLSound.play].
 */
class SoundInstance(private val spec: SoundSpec, buffer: Int) {
    // This is only used by the single instance for a loop that the
    // sound manager owns and controls.
    private val loop: Boolean = spec.loop

    // The index into SoundStore's list of OpenAL sources, or NO_SOURCE.
    private var source: Int = NO_SOURCE

    val isStopped: Boolean
        get() {
            // Check if we've previously marked ourselves as stopped.
            if (source == NO_SOURCE)
                return true

            // Check if we finished and another sound reused our source.
            if (SOURCE_PLAYBACK[source] != this)
                return true

            // Check the OpenAL sound isn't in the stopped state.
            val sourceHandle = SoundStore.get().getSource(source)
            if (sourceHandle == -1) {
                source = NO_SOURCE
                return true
            }
            val state = AL10.alGetSourcei(sourceHandle, AL10.AL_SOURCE_STATE)

            if (state == AL10.AL_PLAYING || state == AL10.AL_PAUSED) {
                return false
            }

            // We've stopped playing, clear the source so it's
            // quicker to check next time.
            source = NO_SOURCE
            return true
        }

    private var isPausedInternal: Boolean = false
    var isPaused: Boolean
        get() {
            if (source == NO_SOURCE)
                return false
            return isPausedInternal
        }
        set(value) {
            // Don't call pause/play twice, since that can
            // do stuff like restart the track.
            if (isPausedInternal == value)
                return

            // We're not paused if we've finished
            if (isStopped) {
                return
            }

            val sourceHandle = SoundStore.get().getSource(source)
            if (value) {
                AL10.alSourcePause(sourceHandle)
            } else {
                AL10.alSourcePlay(sourceHandle)
            }
            isPausedInternal = value
        }

    init {
        // If this is a placeholder sound because audio isn't
        // working or something like that, do nothing.
        if (buffer == NO_SOUND_BUFFER) {
            source = NO_SOURCE
        } else {
            val volume = calculateGain(1f)
            source = SoundAccess.playAsSound(buffer, 1f, volume, loop)

            if (source == -1) {
                println("[WARN] No source available to play sound ${spec.name}")
            } else {
                SOURCE_PLAYBACK[source] = this
            }
        }
    }

    fun setVolume(volume: Float) {
        val sourceHandle = SoundStore.get().getSource(source)
        AL10.alSourcef(sourceHandle, AL10.AL_GAIN, calculateGain(volume))
    }

    private fun calculateGain(volume: Float): Float {
        return volume * (spec.volume / 10f) * SoundStore.get().soundVolume
    }

    /**
     * Stop playing this sound.
     */
    fun stop() {
        if (isStopped) {
            return
        }

        SoundAccess.stopSource(source)
        source = NO_SOURCE
    }

    companion object {
        const val NO_SOUND_BUFFER = -1

        private const val NO_SOURCE = -1

        // The mapping from an OpenAL source to the sound it's playing.
        // This lets us reliably check if we've been stopped, as otherwise
        // if our buffer is marked as playing we may have finished and
        // another sound started.
        private val SOURCE_PLAYBACK = HashMap<Int, SoundInstance>()
    }
}

/**
 * This represents a way to contribute to a sound loop. If multiple things
 * are all trying to play a sound loop, only a single copy of the sound
 * is played but its volume is increased, upto the limit of [SoundSpec.maxCount].
 *
 * Holding one of these instances is very cheap, and doesn't cause the
 * sound to be played unless [continueLoopAnyShip] is being called every update.
 *
 * A handle can be re-used, calling [continueLoopAnyShip] on some frames and not
 * on others. It doesn't have to be re-created after it's first stopped.
 */
class LoopHandle(val spec: SoundSpec, private val manager: SoundManager) {
    /**
     * Sound loops will play until you stop calling this every
     * update, rather than playing until you tell them to stop.
     *
     * This is intended to prevent cases where a loop isn't stopped
     * and gets stuck playing continuously.
     *
     * The name contains 'AnyShip' to remind the user that sounds
     * played from this will be audible regardless of what ship
     * is causing them - see [continueLoopPlayerOnly] for sounds that
     * should only be heard from the player's ship.
     */
    fun continueLoopAnyShip() {
        manager.registerPlayingLoop(this)
    }

    /**
     * This is like [continueLoopAnyShip], but only plays the sound on the player's ship.
     */
    fun continueLoopPlayerOnly(ship: Ship) {
        if (ship.isPlayerShip) {
            continueLoopAnyShip()
        }
    }
}

class SoundSpec(elem: Element) {
    val name: String = elem.name
    val path: String = elem.textTrim

    val volume: Int = elem.getAttributeValue("volume")!!.toInt()
    val loop: Boolean = elem.getAttributeValue("loop")?.toBoolean() ?: false

    /**
     * This is for loops, as they can be played by multiple sources.
     *
     * For something like fire, there's a count specified by whatever is
     * triggering the audio (eg, the number of fires on the ship). This is then
     * clamped to this, and divided by it to get a multiplier for a volume.
     *
     * So with fire set with a maxCount of 5, one fire would only play at 20% volume.
     */
    val maxCount: Int = elem.getAttributeValue("count")?.toInt() ?: 1
}
