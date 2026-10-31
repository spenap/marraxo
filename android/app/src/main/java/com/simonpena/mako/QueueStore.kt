package com.simonpena.mako

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the queue, the current track and its position, and the repeat and
 * shuffle modes, across restarts of the app or the phone. Saved when playback changes and every 15 s while
 * playing; restored paused, on this phone, never on a speaker.
 */
class QueueStore(context: Context) : Player.Listener {
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var player: Player? = null

    private val periodic = object : Runnable {
        override fun run() {
            save()
            handler.postDelayed(this, 15_000)
        }
    }

    /** Saves whichever player is current. */
    fun follow(player: Player) {
        this.player?.removeListener(this)
        this.player = player
        player.addListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
        ) save()
        handler.removeCallbacks(periodic)
        if (player.isPlaying) handler.postDelayed(periodic, 15_000)
    }

    fun save() {
        val p = player ?: return
        val items = JSONArray()
        for (i in 0 until p.mediaItemCount) items.put(toJson(p.getMediaItemAt(i)) ?: continue)
        prefs.edit()
            .putString("items", items.toString())
            .putInt("index", p.currentMediaItemIndex)
            .putLong("position", p.currentPosition)
            .putInt("repeat", p.repeatMode)
            .putBoolean("shuffle", p.shuffleModeEnabled)
            .apply()
    }

    /** Loads the saved queue into [player], paused. */
    fun restore(player: Player) {
        val items = runCatching {
            val array = JSONArray(prefs.getString("items", null) ?: return)
            List(array.length()) { fromJson(array.getJSONObject(it)) }
        }.getOrNull() ?: return
        if (items.isEmpty()) return
        val index = prefs.getInt("index", 0).coerceIn(0, items.size - 1)
        player.setMediaItems(items, index, prefs.getLong("position", 0))
        player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
        player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
        player.prepare()
    }

    fun stop() {
        save()
        handler.removeCallbacks(periodic)
        player?.removeListener(this)
    }

    private fun toJson(item: MediaItem): JSONObject? {
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return null
        val m = item.mediaMetadata
        return JSONObject()
            .put("id", item.mediaId)
            .put("uri", uri.toString())
            .put("title", m.title?.toString())
            .put("artist", m.artist?.toString())
            .put("album", m.albumTitle?.toString())
            .put("albumId", m.extras?.getLong(EXTRA_ALBUM_ID) ?: -1)
            .put("duration", m.durationMs ?: -1)
            .put("artwork", m.artworkUri?.toString())
    }

    private fun fromJson(o: JSONObject): MediaItem {
        val uri = Uri.parse(o.getString("uri"))
        val albumId = o.optLong("albumId", -1)
        return MediaItem.Builder()
            .setMediaId(o.getString("id"))
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(o.optString("title").ifEmpty { null })
                    .setArtist(o.optString("artist").ifEmpty { null })
                    .setAlbumTitle(o.optString("album").ifEmpty { null })
                    .setDurationMs(o.optLong("duration", -1).takeIf { it > 0 })
                    .setArtworkUri(o.optString("artwork").ifEmpty { null }?.let(Uri::parse))
                    .setExtras(Bundle().apply { if (albumId >= 0) putLong(EXTRA_ALBUM_ID, albumId) })
                    .build()
            )
            .build()
    }
}
