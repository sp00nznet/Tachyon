package xyz.znix.xftl.sys;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.newdawn.slick.openal.OggInputStream;
import org.newdawn.slick.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

// Copied from Slick, modified for XFTL

/**
 * A generic tool to work on a supplied stream, pulling out PCM data and buffered it to OpenAL
 * as required.
 *
 * @author Kevin Glass
 * @author Nathan Sweet <misc@n4te.com>
 * @author Rockstar play and setPosition cleanup
 * @author Campbell Suter - XFTL modifications
 */
public class OpenALStreamPlayer {
    /**
     * The number of buffers to maintain
     */
    public static final int BUFFER_COUNT = 3;
    /**
     * The size of the sections to stream from the stream
     */
    private static final int sectionSize = 4096 * 20;

    /**
     * The buffer read from the data stream
     */
    private final byte[] buffer = new byte[sectionSize];
    /**
     * Holds the OpenAL buffer names
     */
    private IntBuffer bufferNames;
    /**
     * The byte buffer passed to OpenAL containing the section
     */
    private final ByteBuffer bufferData = BufferUtils.createByteBuffer(sectionSize);
    /**
     * The buffer holding the names of the OpenAL buffer thats been fully played back
     */
    private final IntBuffer unqueued = BufferUtils.createIntBuffer(1);
    /**
     * The source we're playing back on
     */
    private final int source;
    /**
     * True if we've completed play back
     */
    private boolean done = true;
    /**
     * The stream we're currently reading from
     */
    private OggInputStream audio;
    /**
     * The pitch of the music
     */
    private float pitch = 1;
    /**
     * The volume of the music
     */
    private float volume = 1;
    /**
     * Position in seconds of the previously played buffers
     */
    private float positionOffset;
    /**
     * The rate (in volume/second) to adjust the volume at.
     * <p>
     * This is only applied while {@link #volumeFadeTimer} is non-zero.
     */
    private float volumeFadeRate;
    /**
     * The amount of time during which the volume should be faded for.
     * <p>
     * This counts down towards zero.
     */
    private float volumeFadeTimer;

    /**
     * Create a new player to work on an audio stream
     *
     * @param source The source on which we'll play the audio
     */
    public OpenALStreamPlayer(int source) {
        if (source == -1) {
            throw new IllegalArgumentException("Invalid OpenAL source: " + source);
        }

        this.source = source;

        bufferNames = BufferUtils.createIntBuffer(BUFFER_COUNT);
        AL10.alGenBuffers(bufferNames);
    }

    /**
     * Clean up the buffers applied to the sound source
     */
    private void removeBuffers() {
        IntBuffer buffer = BufferUtils.createIntBuffer(1);
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);

        while (queued > 0) {
            AL10.alSourceUnqueueBuffers(source, buffer);
            queued--;
        }
    }

    /**
     * Start this stream playing
     *
     * @param stream The source of the sound data to play
     * @throws IOException Indicates a failure to read from the stream
     */
    public void play(@NotNull OggInputStream stream) throws IOException {
        if (audio != null) {
            audio.close();
        }

        audio = Objects.requireNonNull(stream);
        positionOffset = 0;
        done = false;

        AL10.alSourceStop(source);
        removeBuffers();

        startPlayback(0);
    }

    public void stop() {
        AL10.alSourceStop(source);
        removeBuffers();
        done = true;
    }

    /**
     * Set up the playback properties
     *
     * @param pitch  The pitch to play back at
     * @param volume The volume to play at
     */
    public void setup(float pitch, float volume) {
        this.pitch = pitch;
        this.volume = volume;

        // Stop fading
        volumeFadeTimer = 0f;

        applyAudioSettings();
    }

    private void applyAudioSettings() {
        AL10.alSourcef(source, AL10.AL_PITCH, pitch);
        AL10.alSourcef(source, AL10.AL_GAIN, volume);
    }

    /**
     * Linearly fade the volume towards the given level, over the given duration.
     */
    public void fadeVolume(float finalVolume, float duration) {
        if (finalVolume == volume) {
            volumeFadeTimer = 0f;
            return;
        }

        if (duration == 0) {
            volume = finalVolume;
            volumeFadeTimer = 0f;
            applyAudioSettings();
            return;
        }

        volumeFadeTimer = duration;
        volumeFadeRate = (finalVolume - volume) / duration;
    }

    /**
     * Check if the playback is complete. Note this will never
     * return true if we're looping
     *
     * @return True if we're looping
     */
    public boolean done() {
        return done;
    }

    /**
     * Poll the bufferNames - check if we need to fill the bufferNames with another
     * section.
     * <p>
     * Most of the time this should be reasonably quick
     */
    public void update(float deltaTime) {
        if (done) {
            return;
        }

        float sampleRate = audio.getRate();
        float sampleSize;
        if (audio.getChannels() > 1) {
            sampleSize = 4; // AL10.AL_FORMAT_STEREO16
        } else {
            sampleSize = 2; // AL10.AL_FORMAT_MONO16
        }

        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed > 0) {
            unqueued.clear();
            AL10.alSourceUnqueueBuffers(source, unqueued);

            int bufferIndex = unqueued.get(0);

            float bufferLength = (AL10.alGetBufferi(bufferIndex, AL10.AL_SIZE) / sampleSize) / sampleRate;
            positionOffset += bufferLength;

            if (stream(bufferIndex)) {
                AL10.alSourceQueueBuffers(source, unqueued);
            } else {
                // We're playing the last few buffers, can't queue any more.
                // Since stream() has now set done=true, this won't be called repeatedly.
                break;
            }
            processed--;
        }

        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);

        if (state != AL10.AL_PLAYING) {
            // Switch back to playing if we stopped. This can happen if we run
            // out of buffers because something else is using all the CPU time.
            AL10.alSourcePlay(source);
        }

        if (volumeFadeTimer != 0f) {
            volumeFadeTimer -= deltaTime;
            if (volumeFadeTimer < 0f) {
                volumeFadeTimer = 0f;
            }

            volume += volumeFadeRate * deltaTime;
            applyAudioSettings();
        }
    }

    /**
     * Stream some data from the audio stream to the buffer indicates by the ID
     *
     * @param bufferId The ID of the buffer to fill
     * @return True if another section was available
     */
    public boolean stream(int bufferId) {
        try {
            int count = audio.read(buffer);

            if (count != -1) {
                loadBufferToAL(bufferId, count);
            } else {
                done = true;
                return false;
            }

            return true;
        } catch (IOException e) {
            Log.error(e);
            return false;
        }
    }

    /**
     * Move the contents of {@link #buffer} to the given OpenAL buffer.
     *
     * @param count The number of bytes to copy.
     */
    private void loadBufferToAL(int bufferId, int count) {
        bufferData.clear();
        bufferData.put(buffer, 0, count);
        bufferData.flip();

        int format = audio.getChannels() > 1 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
        AL10.alBufferData(bufferId, format, bufferData, audio.getRate());
    }

    /**
     * Seeks to a position in the music.
     *
     * @param position Position in seconds.
     * @return True if the setting of the position was successful
     */
    public boolean setPosition(float position) {
        try {
            if (getPosition() > position) {
                // We can't restart the stream ourselves, so do nothing.
                return false;
            }

            float sampleRate = audio.getRate();
            float sampleSize;
            if (audio.getChannels() > 1) {
                sampleSize = 4; // AL10.AL_FORMAT_STEREO16
            } else {
                sampleSize = 2; // AL10.AL_FORMAT_MONO16
            }

            int count = 0;
            float bufferLength = 0f;
            while (positionOffset < position) {
                count = audio.read(buffer);
                if (count != -1) {
                    bufferLength = (count / sampleSize) / sampleRate;
                    positionOffset += bufferLength;
                } else {
                    done = true;
                    return false;
                }
            }

            // Use the first buffer we just loaded, so we end up earlier than intended (rather than later).
            positionOffset -= bufferLength;
            loadBufferToAL(bufferNames.get(0), count);
            startPlayback(1); // Use an offset of 1 to not clobber the buffer we just loaded

            // Seek forwards a little bit, to get to the final position within
            // this sample. Without this, we always start a little behind.
            float timeError = position - positionOffset;
            int sampleOffset = (int) (timeError * sampleRate);
            AL11.alSourcei(source, AL11.AL_SAMPLE_OFFSET, sampleOffset);

            return true;
        } catch (IOException e) {
            Log.error(e);
            return false;
        }
    }

    /**
     * Starts the streaming.
     *
     * @param bufferOffset The index of the first buffer to populate. This is used by {@link #setPosition(float)}.
     */
    private void startPlayback(int bufferOffset) {
        // If we don't remove the buffers here, we'll end up in a big mess
        // due to queueing them twice.
        stop();
        done = false;

        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        applyAudioSettings();

        for (int i = bufferOffset; i < BUFFER_COUNT; i++) {
            stream(bufferNames.get(i));
        }

        AL10.alSourceQueueBuffers(source, bufferNames);
        AL10.alSourcePlay(source);
    }

    /**
     * Return the current playing position in the sound
     *
     * @return The current position in seconds.
     */
    public float getPosition() {
        return positionOffset + AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET);
    }

    /**
     * Returns true if this stream is currently playing, and a volume fade is ongoing.
     */
    public boolean isVolumeFading() {
        return !done && volumeFadeTimer != 0f;
    }

    /**
     * Stop playing, and release all of the resources for this stream.
     * <p>
     * This player cannot be used again after this call.
     */
    public void close() {
        // First un-queue the buffers, otherwise we can't delete them.
        AL10.alSourceStop(source);
        removeBuffers();

        // Then delete them.
        AL10.alDeleteBuffers(bufferNames);

        // Make sure we can't accidentally use them again.
        bufferNames = null;

        positionOffset = 0;
        done = true;
    }
}
