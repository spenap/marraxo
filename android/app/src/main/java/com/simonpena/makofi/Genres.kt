package com.simonpena.makofi

/**
 * One genre as the library shows it. Tags that differ only in case, accents
 * or punctuation are one genre ("Pop Rock", "Pop-Rock"; "Pop español" and
 * the mis-encoded "Pop espańol"), named as most of its albums spell it.
 */
data class Genre(val name: String, val key: String, val albums: List<Album>)

/**
 * A row on the Genres tab: one genre, or a family of genres that share a
 * word (Indie: Indie, Indie Pop, Indie Rock). A genre can be in two
 * families (Indie Pop is also Pop). [isOther]: the genres with a single
 * album, gathered at the end; its name is empty, for the screen to give.
 */
data class GenreShelf(val name: String, val genres: List<Genre>, val isOther: Boolean = false) {
    val isFamily get() = genres.size > 1
    /** Each album once, in library order, though it may be in several of the genres. */
    val albums: List<Album> by lazy { genres.flatMap { it.albums }.distinctBy { it.id }.sortedBy { order(it) } }
    private val positions by lazy { genres.flatMap { it.albums }.mapIndexed { i, a -> a.id to i }.toMap() }
    private fun order(album: Album) = positions.getValue(album.id)
}

/** Several genres in one tag: "Rock/Classic Rock/70'S". */
fun genreTags(tag: String): List<String> = tag.split('/', ';').map { it.trim() }.filter { it.isNotEmpty() }

fun genreKey(name: String): String = fold(name).replace(NOT_A_WORD, " ").trim()

private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

/** The library's genres, sorted by name; albums without one are left out. */
fun genres(library: List<Album>): List<Genre> =
    library.flatMap { album -> genreTags(album.genre).map { it to album } }
        .groupBy { (tag, _) -> genreKey(tag) }
        .map { (key, tagged) ->
            val name = tagged.groupingBy { it.first }.eachCount().maxBy { it.value }.key
            Genre(name, key, tagged.map { it.second }.distinctBy { it.id })
        }
        .sortedBy { fold(it.name) }

/** Words that would group unrelated genres: "en", "Children's Music" with "Film Music". */
private val GENERIC = setOf("and", "the", "music", "musica", "del", "los", "las")

private fun words(genre: Genre) = genre.key.split(' ').filter { it.length >= 3 && it !in GENERIC }

/**
 * The Genres tab's top level: a family for each word that two or more
 * genres share, and every genre that's in no family. Words that always come
 * together ("Heavy Metal") make one family between them.
 */
fun genreShelves(genres: List<Genre>): List<GenreShelf> {
    val byWord = LinkedHashMap<String, MutableList<Genre>>()
    genres.forEach { g -> words(g).distinct().forEach { byWord.getOrPut(it) { mutableListOf() } += g } }
    val families = byWord.filterValues { it.size > 1 }.entries
        .groupBy({ it.value.toSet() }, { it.key })
        .map { (members, familyWords) ->
            val first = members.first()
            val ordered = familyWords.sortedBy { words(first).indexOf(it) }
            GenreShelf(ordered.joinToString(" ") { displayWord(it, members) }, members.sortedBy { fold(it.name) })
        }
    val inFamilies = families.flatMap { it.genres }.toSet()
    // A genre with one album is a row to scroll past: those go in Other, unless it's the only one.
    val (rare, singles) = genres.filter { it !in inFamilies }.partition { it.albums.size == 1 }
    val other = if (rare.size > 1) listOf(GenreShelf("", rare, isOther = true)) else emptyList()
    val alone = if (other.isEmpty()) singles + rare else singles
    return (families + alone.map { GenreShelf(it.name, listOf(it)) }).sortedBy { fold(it.name) } + other
}

/** A family's word as the genres spell it, capitalised if any of them does ("Español", not "español"). */
private fun displayWord(word: String, genres: Collection<Genre>): String {
    val spellings = genres.flatMap { it.name.split(NOT_A_WORD) }.filter { genreKey(it) == word }
    return spellings.firstOrNull { it.first().isUpperCase() } ?: spellings.first().replaceFirstChar { it.uppercase() }
}

/** About two screens of albums: a family that small opens straight to them. */
const val FAMILY_OPENS_ALBUMS_UP_TO = 12

fun opensAlbums(shelf: GenreShelf) = !shelf.isFamily || shelf.albums.size <= FAMILY_OPENS_ALBUMS_UP_TO

/**
 * The albums a genre's tile tries for its cover on this [visit] to the
 * tab, in order: the first with artwork is shown. Each visit starts one
 * album further on, and genres start at different albums, so tiles don't
 * change in step. The same visit always gives the same order.
 */
fun coverCandidates(shelf: GenreShelf, visit: Int): List<Album> = coverCandidates(shelf.albums, visit, shelf.name)

/** The same for any group of albums (an artist's); [name] keeps groups from changing in step. */
fun coverCandidates(albums: List<Album>, visit: Int, name: String): List<Album> {
    if (albums.isEmpty()) return albums
    val start = Math.floorMod(visit + name.hashCode(), albums.size)
    return albums.drop(start) + albums.take(start)
}

/**
 * The album whose cover a tile shows: the first candidate not known to lack
 * artwork. Not simply the first already in memory: that is usually last
 * visit's cover, which would then never change.
 */
fun coverToShow(candidates: List<Album>, hasNoArt: (albumId: Long) -> Boolean): Album? =
    candidates.firstOrNull { !hasNoArt(it.id) }

/** How long a finger holds a tile grid before covers load under it: longer than a swipe that flings. */
const val HOLD_MS = 200L

/**
 * Whether a tile not yet in memory may decode its cover. Not mid-fling or
 * mid-swipe: on the Nexus 4 decoding then made Artists stutter. But under a
 * finger held [HOLD_MS] it may: covers otherwise appeared only once the
 * finger lifted.
 */
fun coversMayLoad(scrolling: Boolean, heldMs: Long) = !scrolling || heldMs >= HOLD_MS
