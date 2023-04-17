package xyz.znix.xftl.game

import org.jdom2.Element
import org.newdawn.slick.SlickException
import org.newdawn.slick.Sound
import org.newdawn.slick.openal.OggInputStream
import xyz.znix.xftl.Datafile

class SoundManager(private val df: Datafile) {
    private val sounds = HashMap<SoundSpec, FTLSound>()

    // Nebula is both a sample and a loop
    private val samples = HashMap<String, SoundSpec>()
    private val loops = HashMap<String, SoundSpec>()

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

    private fun get(spec: SoundSpec): FTLSound {
        sounds[spec]?.let { return it }

        val sound = loadRawSound(spec)
        sounds[spec] = sound
        return sound
    }

    private fun loadRawSound(spec: SoundSpec): FTLSound {
        val path = "audio/waves/" + spec.path

        val sound = try {
            df.open(df[path]).use { stream ->
                Sound(stream, path)
            }
        } catch (e: SlickException) {
            throw RuntimeException("Failed to load sound '${spec.name}' from path '$path'")
        }

        return FTLSound(spec, sound)
    }

}

// Wrap Sound in a wrapper class that can apply the volume and loop set in XML.
class FTLSound(private val spec: SoundSpec, val sound: Sound) {
    fun play() {
        val volume = spec.volume / 10f
        sound.play(1f, volume)
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
