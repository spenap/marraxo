package com.simonpena.marraxo

import android.net.Uri
import org.json.JSONObject
import java.io.File

data class StationPick(
    val artist: String,
    val album: String,
    val albumId: Long,
    val title: String,
    val year: Int,
    val reasons: Set<Reason>,
    val widened: Boolean,
)

data class StationMetrics(
    val onlyGenreAndEra: Double,
    val albums: Int,
    val artists: Int,
    val mostFromOneAlbum: Int,
    val widened: Int,
) {
    companion object {
        fun from(picks: List<StationPick>): StationMetrics {
            val coherent = picks.count { pick ->
                pick.reasons.isNotEmpty() && pick.reasons.all { it is Reason.Genre || it is Reason.Decade }
            }
            return StationMetrics(
                if (picks.isEmpty()) 0.0 else coherent.toDouble() / picks.size,
                picks.map { it.albumId }.distinct().size,
                picks.map { it.artist }.distinct().size,
                picks.groupingBy { it.albumId }.eachCount().values.maxOrNull() ?: 0,
                picks.count { it.widened },
            )
        }
    }
}

enum class ReferenceJudgement { GOOD, WEAK }
data class ReferencePick(val seed: String, val candidate: String, val judgement: ReferenceJudgement)

fun referenceScore(references: List<ReferencePick>, selected: Set<String>): Double =
    if (references.isEmpty()) 0.0 else references.count {
        (it.judgement == ReferenceJudgement.GOOD) == selected.any { choice -> choice.equals(it.candidate, true) }
    }.toDouble() / references.size

object RealCollection {
    fun load(inventory: File): List<Album> {
        val tracks = inventory.readLines().map(::JSONObject).filter { it.optString("kind") == "audio" && it.has("tags") }
        val folders = tracks.groupBy { it.getString("path").substringBeforeLast('/') }
        return folders.entries.mapIndexed { a, (_, files) ->
            val tags = files.first().getJSONObject("tags")
            val albumId = a + 1L
            Album(
                albumId, tags.optString("album"), tags.optString("albumartist").ifEmpty { tags.optString("artist") },
                tags.optString("date").take(4).toIntOrNull() ?: 0,
                files.mapIndexed { i, f ->
                    val t = f.getJSONObject("tags")
                    Track(albumId * 1000 + i, t.optString("title"), t.optString("artist"), t.optString("album"), albumId, 0, i, Uri.EMPTY, f.getString("path"))
                }, tags.optString("genre"),
            )
        }
    }
}
