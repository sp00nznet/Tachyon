package xyz.znix.xftl.testkit

import xyz.znix.xftl.game.*

class NoOpSoundManager : SoundManager {
    override fun getSample(name: String): FTLSound {
        return NoOpSound()
    }

    override fun getSampleOrWarn(name: String): FTLSound {
        return NoOpSound()
    }

    override fun getLoop(name: String): LoopHandle {
        TODO("Not yet implemented")
    }

    override fun getTrack(name: String): MusicSpec {
        TODO("Not yet implemented")
    }

    override fun switchMusicList(tracks: List<MusicSpec>) {
    }
}

private class NoOpSound : FTLSound {
    override fun play(): SoundInstance {
        return NoOpSoundInstance()
    }
}

private class NoOpSoundInstance : SoundInstance {
    override var isPaused: Boolean = false
    override var volume: Float = 1f

    override fun stop() {
    }
}
