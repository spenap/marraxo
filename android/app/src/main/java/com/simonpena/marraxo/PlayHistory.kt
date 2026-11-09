package com.simonpena.marraxo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.util.concurrent.Executors

/**
 * Every track that was heard: when, for how long, and how it ended. Stats
 * are built from this later and it can't be reconstructed, so it is kept
 * from the first version on.
 *
 * Rows store title, artist, album and duration instead of relying on the
 * MediaStore id, which changes when files are rescanned, renamed or rotated.
 */
class PlayHistory(context: Context) : SQLiteOpenHelper(context, "history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE plays (
                id INTEGER PRIMARY KEY,
                started_at INTEGER NOT NULL,  -- wall clock, ms since the epoch
                media_id TEXT,                -- MediaStore id at the time (not stable)
                title TEXT,
                artist TEXT,
                album TEXT,
                duration_ms INTEGER,
                listened_ms INTEGER NOT NULL, -- time actually playing
                ended TEXT NOT NULL,          -- completed | skipped | stopped
                output TEXT NOT NULL          -- "phone", or the speaker or TV's name
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** The latest [limit] plays, newest first. */
    fun recent(limit: Int): List<Play> =
        readableDatabase.rawQuery("SELECT title, artist, album, started_at, ended FROM plays ORDER BY started_at DESC, id DESC LIMIT $limit", null).use { c ->
            buildList { while (c.moveToNext()) add(Play(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), c.getString(4))) }
        }
}

/** Follows the player and writes one [PlayHistory] row per track heard. */
class PlayRecorder(private val history: PlayHistory, private var player: Player) : Player.Listener {
    private var output = "phone"

    private val writer = Executors.newSingleThreadExecutor()
    private var item: MediaItem? = null
    private var startedAt = 0L
    private var listenedMs = 0L
    private var playingSince = -1L // elapsedRealtime while playing, else -1

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            if (item == null) item = player.currentMediaItem // replay after the queue ended
            if (startedAt == 0L) startedAt = System.currentTimeMillis()
            playingSince = SystemClock.elapsedRealtime()
        } else {
            stopClock()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val completed = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        finish(if (completed) "completed" else "skipped")
        item = mediaItem
        if (player.isPlaying) {
            startedAt = System.currentTimeMillis()
            playingSince = SystemClock.elapsedRealtime()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) finish("completed")
    }

    /** Follows [player] from now on; the track in progress ends as "stopped". */
    fun attach(player: Player, output: String) {
        finish("stopped")
        this.player.removeListener(this)
        this.player = player
        this.output = output
        player.addListener(this)
    }

    /** Call before releasing the player: records the track in progress. */
    fun close() {
        finish("stopped")
        writer.shutdown()
    }

    private fun stopClock() {
        if (playingSince >= 0) listenedMs += SystemClock.elapsedRealtime() - playingSince
        playingSince = -1
    }

    private fun finish(ended: String) {
        stopClock()
        val heard = item
        if (heard != null && listenedMs > 0) {
            val meta = heard.mediaMetadata
            val row = ContentValues().apply {
                put("started_at", startedAt)
                put("media_id", heard.mediaId.takeIf { it.toLongOrNull() != null }) // null for a file opened from another app
                put("title", meta.title?.toString())
                put("artist", meta.artist?.toString())
                put("album", meta.albumTitle?.toString())
                // From the item, not player.duration: during a transition the
                // player already describes the next track.
                put("duration_ms", meta.durationMs)
                put("listened_ms", listenedMs)
                put("ended", ended)
                put("output", output)
            }
            writer.execute { history.writableDatabase.insert("plays", null, row) }
        }
        item = null
        startedAt = 0
        listenedMs = 0
    }
}
