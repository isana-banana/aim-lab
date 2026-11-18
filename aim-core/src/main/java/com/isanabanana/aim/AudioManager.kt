package com.isanabanana.aim

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator

/**
 * Lightweight Android audio service for short sound effects and background music.
 *
 * Sound effects are loaded asynchronously. Call playSound only after the sound
 * has finished loading (or check isSoundLoaded). pause()/resume() follow the
 * host game's foreground lifecycle. release() permanently closes this manager.
 */
class AudioManager {

    private var soundPool: SoundPool? = null
    private var musicPlayer: MediaPlayer? = null
    private var testTone: ToneGenerator? = null
    private val soundIdsByResource = mutableMapOf<Int, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private var released = false
    private var paused = false

    var sfxVolume: Float = 1f
        private set
    var musicVolume: Float = 1f
        private set

    init {
        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    synchronized(this) {
                        if (!released && status == 0 && sampleId in soundIdsByResource.values) {
                            loadedSoundIds.add(sampleId)
                        }
                    }
                }
            }
    }

    /** Loads a short sound effect once and returns its SoundPool sample ID, or 0 after release. */
    @Synchronized
    fun loadSound(context: Context, resourceId: Int): Int {
        require(resourceId != 0) { "A valid sound resource ID is required." }
        if (released) return 0
        soundIdsByResource[resourceId]?.let { return it }
        val id = soundPool?.load(context.applicationContext, resourceId, 1) ?: 0
        if (id != 0) soundIdsByResource[resourceId] = id
        return id
    }

    @Synchronized
    fun isSoundLoaded(soundId: Int): Boolean = !released && soundId in loadedSoundIds

    /** Plays a loaded sound. Returns 0 while paused, before loading, or after release. */
    @Synchronized
    fun playSound(
        soundId: Int,
        volume: Float = sfxVolume,
        loop: Int = 0,
        rate: Float = 1f
    ): Int {
        require(volume.isFinite() && rate.isFinite()) { "Sound volume and playback rate must be finite." }
        if (released || paused || soundId !in loadedSoundIds) return 0
        val safeVolume = volume.coerceIn(0f, 1f)
        return soundPool?.play(
            soundId,
            safeVolume,
            safeVolume,
            1,
            loop.coerceAtLeast(-1),
            rate.coerceIn(0.5f, 2f)
        ) ?: 0
    }

    @Synchronized
    fun setSfxVolume(volume: Float) {
        require(volume.isFinite()) { "SFX volume must be finite." }
        sfxVolume = volume.coerceIn(0f, 1f)
    }

    /** Starts or replaces the current background music resource. */
    @Synchronized
    fun playMusic(
        context: Context,
        resourceId: Int,
        loop: Boolean = true
    ): Boolean {
        require(resourceId != 0) { "A valid music resource ID is required." }
        if (released) return false
        stopMusic()
        val player = MediaPlayer.create(context.applicationContext, resourceId) ?: return false
        musicPlayer = player
        player.isLooping = loop
        player.setVolume(musicVolume, musicVolume)
        player.setOnCompletionListener {
            synchronized(this) {
                if (musicPlayer === it) {
                    it.release()
                    musicPlayer = null
                }
            }
        }
        if (!paused) player.start()
        return true
    }

    /** Pauses all audio output while preserving loaded effects and music position. */
    @Synchronized
    fun pause() {
        if (released || paused) return
        paused = true
        soundPool?.autoPause()
        pauseMusic()
        testTone?.run {
            stopTone()
            release()
        }
        testTone = null
    }

    /** Resumes audio output after pause(); safe to call repeatedly. */
    @Synchronized
    fun resume() {
        if (released || !paused) return
        paused = false
        soundPool?.autoResume()
        resumeMusic()
    }

    @Synchronized
    fun isPaused(): Boolean = paused

    @Synchronized
    fun pauseMusic() {
        musicPlayer?.let { if (it.isPlaying) it.pause() }
    }

    @Synchronized
    fun resumeMusic() {
        if (released || paused) return
        musicPlayer?.let { if (!it.isPlaying) it.start() }
    }

    @Synchronized
    fun stopMusic() {
        musicPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        musicPlayer = null
    }

    @Synchronized
    fun isMusicPlaying(): Boolean = !released && musicPlayer?.isPlaying == true

    @Synchronized
    fun setMusicVolume(volume: Float) {
        require(volume.isFinite()) { "Music volume must be finite." }
        musicVolume = volume.coerceIn(0f, 1f)
        musicPlayer?.setVolume(musicVolume, musicVolume)
    }

    /** Short diagnostic beep for checking audio output before game assets are added. */
    @Synchronized
    fun playTestTone(durationMs: Int = 120) {
        if (released || paused) return
        testTone?.run {
            stopTone()
            release()
        }
        testTone = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80).also {
            it.startTone(ToneGenerator.TONE_PROP_BEEP2, durationMs.coerceIn(50, 1000))
        }
    }

    /** Releases players and native audio resources. Safe to call more than once. */
    @Synchronized
    fun release() {
        if (released) return
        released = true
        paused = true
        stopMusic()
        soundPool?.autoPause()
        soundPool?.release()
        soundPool = null
        soundIdsByResource.clear()
        loadedSoundIds.clear()
        testTone?.run {
            stopTone()
            release()
        }
        testTone = null
    }
}
