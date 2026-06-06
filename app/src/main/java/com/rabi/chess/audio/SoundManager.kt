package com.rabi.chess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.rabi.chess.R

/** Distinct audio cues for in-game events. */
enum class Sfx { MOVE, CAPTURE, CHECK, GAME_OVER }

/**
 * Thin [SoundPool] wrapper. Loads the four short clips from res/raw and plays the
 * matching cue. Respects a [muted] flag (persisted by the caller). Call [release]
 * from the owning Activity's onDestroy.
 */
class SoundManager(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.MOVE to pool.load(context, R.raw.move, 1),
        Sfx.CAPTURE to pool.load(context, R.raw.capture, 1),
        Sfx.CHECK to pool.load(context, R.raw.check, 1),
        Sfx.GAME_OVER to pool.load(context, R.raw.gameover, 1)
    )

    var muted: Boolean = false

    fun play(sfx: Sfx) {
        if (muted) return
        val id = ids[sfx] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
