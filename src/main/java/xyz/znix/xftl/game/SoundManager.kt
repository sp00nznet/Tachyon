package xyz.znix.xftl.game

import org.jdom2.Element
import org.lwjgl.openal.AL10
import org.newdawn.slick.openal.*
import xyz.znix.xftl.Datafile
import java.io.InputStream

class SoundManager(private val df: Datafile) {
    private val sounds = HashMap<SoundSpec, FTLSound>()
    private val rawBuffers = HashMap<String, Int>()

    // Nebula is both a sample and a loop
    private val samples = HashMap<String, SoundSpec>()
    private val loops = HashMap<String, SoundSpec>()

    private val loopInstances = ArrayList<SoundInstance>()

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

    fun getLoop(name: String): FTLSound {
        val spec = loops[name] ?: error("No sound loop listed in sounds.xml for '$name'")
        return get(spec)
    }

    // Should only be called by SoundInstance!
    fun registerLoopInstance(loop: SoundInstance) {
        loopInstances.add(loop)
    }

    fun updateLoopedSounds(gamePaused: Boolean) {
        // It's fine to only remove one instance per update,
        // calling update a few more times doesn't hurt.
        var toRemove: SoundInstance? = null

        for (instance in loopInstances) {
            val shouldRemove = instance.updateLoop(gamePaused)

            if (shouldRemove) {
                toRemove = instance
            }
        }

        // Remove stopped sounds so they can be GCed.
        if (toRemove != null) {
            loopInstances.remove(toRemove)
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

        return FTLSound(spec, buffer, this)
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
class FTLSound(val spec: SoundSpec, private val buffer: Int, private val manager: SoundManager) {
    fun play(): SoundInstance {
        return SoundInstance(this, buffer, manager)
    }
}

/**
 * A PlaybackInstance represents a single playback of a sound. It can
 * be paused or stopped independent of all other currently-playing instances
 * of this sound.
 */
class SoundInstance(sound: FTLSound, buffer: Int, manager: SoundManager) {
    private val loop: Boolean = sound.spec.loop

    // The index into SoundStore's list of OpenAL sources, or NO_SOURCE.
    private var source: Int = NO_SOURCE

    // This is set to true by the user of this loop, and set back to false
    // every update. If it's left as false, the sound will be stopped.
    // This is set to true by default, so it doesn't matter what order
    // the update and continueLoop function are called in.
    private var loopContinue: Boolean = true

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
            val volume = (sound.spec.volume / 10f) * SoundStore.get().soundVolume
            source = SoundAccess.playAsSound(buffer, 1f, volume, loop)

            if (source == -1) {
                println("[WARN] No source available to play sound ${sound.spec.name}")
            } else {
                SOURCE_PLAYBACK[source] = this

                // If we're a loop that can actually play, register
                // ourselves so we can automatically pause and stop etc.
                if (loop) {
                    manager.registerLoopInstance(this)
                }
            }
        }
    }

    /**
     * Sound loops will play until you stop calling this every
     * update, rather than playing until you tell them to stop.
     *
     * This is intended to prevent cases where a loop isn't stopped
     * and gets stuck playing continuously.
     */
    fun continueLoop() {
        loopContinue = true
    }

    // Returns true if this loop has been stopped.
    fun updateLoop(gamePaused: Boolean): Boolean {
        if (isStopped) {
            return true
        }

        // If whatever is playing the sound stops calling continueLoop,
        // we kill off the sound to avoid looping sounds being left on.
        // We don't expect users to call continueLoop while the game
        // is paused though - that'd be very inconvenient.
        if (!gamePaused) {
            if (!loopContinue) {
                stop()
                return true
            }
            loopContinue = false
        }

        // Pause the sound when the game is paused - this applies
        // to all looped sounds.
        if (gamePaused != isPaused) {
            isPaused = gamePaused
        }

        return false
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

class SoundSpec(elem: Element) {
    val name: String = elem.name
    val path: String = elem.textTrim

    val volume: Int = elem.getAttributeValue("volume")!!.toInt()
    val loop: Boolean = elem.getAttributeValue("loop")?.toBoolean() ?: false

    /**
     * This appears to be for sound files which are made up
     * of several concatenated parts, for example four punches
     * in the punching sound file.
     *
     * TODO figure out more about this.
     */
    val count: Int = elem.getAttributeValue("count")?.toInt() ?: 1
}
