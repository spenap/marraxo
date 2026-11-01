"""Tests for reading LRCLIB's dump as a stream (run: .venv/bin/pytest tools)."""
import gzip
import random
import sqlite3

import lrclib_dump as dump

# As in LRCLIB's dump (2026-10-30), trimmed; lyricsfile is added later, as there, so old rows lack it.
SCHEMA = """
CREATE TABLE tracks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT, name_lower TEXT, artist_name TEXT, artist_name_lower TEXT,
  album_name TEXT, album_name_lower TEXT, duration FLOAT, last_lyrics_id INTEGER,
  created_at DATETIME, updated_at DATETIME,
  UNIQUE(name_lower, artist_name_lower, album_name_lower, duration)
);
CREATE TABLE lyrics (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  plain_lyrics TEXT, synced_lyrics TEXT, track_id INTEGER,
  has_plain_lyrics BOOLEAN, has_synced_lyrics BOOLEAN, instrumental BOOLEAN, source TEXT,
  created_at DATETIME, updated_at DATETIME,
  FOREIGN KEY (track_id) REFERENCES tracks (id)
);
CREATE TABLE missing_tracks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, artist_name TEXT, duration FLOAT);
CREATE INDEX idx_lyrics_source ON lyrics (source);
CREATE VIRTUAL TABLE tracks_fts USING fts5(name_lower, album_name_lower, artist_name_lower, content='tracks', content_rowid='id');
"""
ARTISTS = ["ABBA", "Amy Winehouse", "A.R. Rahman, Blaaze", "War On Drugs", "ABBA Tribute Band", "Nobody We Have",
           "Someone Else", "Alasdair Fraser & Paul Machlis"]
MINE = ["ABBA", "Amy Winehouse", "A. R. Rahman feat. Blaaze & Tanvi Shah", "The War on Drugs",
        "Alasdair Fraser and Paul Machlis"]


def words(rnd, n):
    return " ".join(rnd.choice(["la", "noche", "fernando", "rehab", "drums", "ñu", "é"]) for _ in range(n))


def make_dump(tmp_path, seed=1, n=500):
    """A small dump, gzipped as LRCLIB's is, fragmented by deletes and re-inserts; returns its path and the database."""
    rnd = random.Random(seed)
    raw = tmp_path / "dump.sqlite3"
    db = sqlite3.connect(raw)
    db.execute("PRAGMA page_size = 1024")  # small pages: deep trees and long overflow chains
    db.executescript(SCHEMA)

    def add_lyrics(track, big=False):
        plain = words(rnd, rnd.choice([0, 5, 300, 2000])) or None
        synced = "\n".join(f"[00:{i % 60:02d}.00] {words(rnd, 6)}" for i in range(rnd.choice([0, 10, 200]))) or None
        cur = db.execute("INSERT INTO lyrics (plain_lyrics, synced_lyrics, track_id, instrumental, source) VALUES (?, ?, ?, ?, 'lrclib')",
                         (plain, synced, track, int(plain is None and synced is None)))
        if big:
            db.execute("UPDATE lyrics SET lyricsfile = ? WHERE id = ?", (words(rnd, 4000), cur.lastrowid))
        db.execute("UPDATE tracks SET last_lyrics_id = ? WHERE id = ?", (cur.lastrowid, track))

    for i in range(n):
        artist = rnd.choice(ARTISTS)
        db.execute("INSERT INTO tracks (name, name_lower, artist_name, artist_name_lower, album_name, album_name_lower, duration) VALUES (?, ?, ?, ?, ?, ?, ?)",
                   (f"Song {i}", f"song {i}", artist, artist.lower(), f"Album {i % 7}", f"album {i % 7}", rnd.uniform(90, 400)))
        if rnd.random() < 0.9:
            add_lyrics(i + 1)
        db.execute("INSERT INTO missing_tracks (name, artist_name, duration) VALUES (?, ?, 1.0)", (words(rnd, 30), artist))
    db.execute("INSERT INTO tracks_fts(tracks_fts) VALUES ('rebuild')")
    db.execute("ALTER TABLE lyrics ADD COLUMN lyricsfile TEXT")
    # Fragment: free pages in the middle, then fill them with newer lyrics, so some chains point backwards.
    db.execute("DELETE FROM lyrics WHERE id % 3 = 0")
    db.execute("DELETE FROM missing_tracks WHERE id % 2 = 0")
    for track in rnd.sample(range(1, n + 1), n // 3):
        add_lyrics(track, big=rnd.random() < 0.5)
    db.commit()
    db.close()
    gz = tmp_path / "dump.sqlite3.gz"
    gz.write_bytes(gzip.compress(raw.read_bytes(), compresslevel=1))
    return gz, sqlite3.connect(raw)


def expected(db, mine):
    keys = set().union(*(dump.artist_keys(a) for a in mine))
    tracks = [t for t in db.execute("SELECT id, name, artist_name, album_name, duration, last_lyrics_id FROM tracks ORDER BY id")
              if dump.artist_keys(t[2]) & keys]
    ids = [t[5] for t in tracks if t[5] is not None]
    lyrics = [r for r in db.execute("SELECT id, track_id, plain_lyrics, synced_lyrics, instrumental FROM lyrics ORDER BY id")
              if r[0] in ids]
    return tracks, lyrics


def test_the_header_gives_the_unpacked_size_without_unpacking(tmp_path):
    gz, db = make_dump(tmp_path, n=50)
    h = dump.header(gz)
    assert h.page_size == 1024
    assert h.page_count * h.page_size == (tmp_path / "dump.sqlite3").stat().st_size


def test_every_kind_of_value_reads_back_as_sqlite_wrote_it(tmp_path):
    raw = tmp_path / "values.sqlite3"
    db = sqlite3.connect(raw)
    db.execute("PRAGMA page_size = 512")
    db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, v)")
    values = [None, 0, 1, 127, -129, 70000, 2**31 - 1, -(2**40), 2**62, 3.5, -0.25, "", "tëxt ñ", b"\x00blob", "x" * 5000]
    db.executemany("INSERT INTO t (v) VALUES (?)", [(v,) for v in values])
    db.commit()
    db.close()
    gz = tmp_path / "values.sqlite3.gz"
    gz.write_bytes(gzip.compress(raw.read_bytes()))
    assert [row for _, row in dump.table_rows(gz, "t")] == [[None, v] for v in values]


def test_your_artists_tracks_and_their_lyrics_come_out_whole(tmp_path):
    gz, db = make_dump(tmp_path)
    out = tmp_path / "mine.sqlite3"
    stats = dump.extract(gz, MINE, out)
    tracks, lyrics = expected(db, MINE)
    assert len(tracks) > 100 and len(lyrics) > 100  # the fixture really exercises something
    got = sqlite3.connect(out)
    assert list(got.execute("SELECT id, name, artist_name, album_name, duration, lyrics_id FROM tracks ORDER BY id")) == tracks
    assert list(got.execute("SELECT id, track_id, plain, synced, instrumental FROM lyrics ORDER BY id")) == lyrics
    assert stats["passes"] >= 2


def test_chains_running_backwards_are_read_from_memory_not_another_pass(tmp_path):
    gz, db = make_dump(tmp_path)
    tracks, lyrics = expected(db, MINE)
    stats = {}
    for window in (0, 1 << 20, 1 << 30):  # nothing kept; the last MB of pages; more than the whole dump
        out = tmp_path / f"mine-{window}.sqlite3"
        stats[window] = dump.extract(gz, MINE, out, window=window)
        got = sqlite3.connect(out)
        assert list(got.execute("SELECT id, track_id, plain, synced, instrumental FROM lyrics ORDER BY id")) == lyrics
    assert stats[0]["passes"] > 5  # the fixture's chains do run backwards
    # Pass 1 (the trees), the table list (a few pages at the start), the reading pass, and one for far jumps.
    assert stats[1 << 20]["passes"] <= 4
    assert stats[1 << 30]["passes"] == 3


def test_extraction_reads_only_as_far_as_it_must(tmp_path):
    gz, db = make_dump(tmp_path, seed=2)
    stats = dump.extract(gz, ["Nobody We Have"], tmp_path / "mine.sqlite3")
    assert stats["tracks"] > 0
    assert stats["pages_read"] < stats["passes"] * dump.header(gz).page_count


def test_artists_match_across_spelling_featuring_and_the():
    def same(a, b):
        return bool(dump.artist_keys(a) & dump.artist_keys(b))
    assert same("A. R. Rahman feat. Blaaze & Tanvi Shah", "A.R. Rahman")
    assert same("The War on Drugs", "War On Drugs")
    assert same("Alasdair Fraser and Paul Machlis", "Alasdair Fraser & Paul Machlis")
    assert same("Ábba", "ABBA")
    assert not same("ABBA", "ABBA Tribute Band")
    assert not same("Various Artists", "Various Artists")


def song(**kw):
    s = {"path": "M-Clan/Usar y tirar/02 Llamando a la Tierra.mp3", "title": "Llamando a la Tierra",
         "artist": "M-Clan", "album": "Usar y tirar", "duration": 280}
    s.update(kw)
    return s


def mine_with(tmp_path, *tracks):
    """tracks: (id, name, artist, album, duration, plain, synced, instrumental)."""
    out = tmp_path / "mine.sqlite3"
    db = dump.create(out)
    for t in tracks:
        db.execute("INSERT INTO tracks (id, name, artist_name, album_name, duration, lyrics_id) VALUES (?, ?, ?, ?, ?, ?)", (*t[:5], t[0] * 10))
        db.execute("INSERT INTO lyrics (id, track_id, plain, synced, instrumental) VALUES (?, ?, ?, ?, ?)", (t[0] * 10, t[0], *t[5:]))
    dump.finish(db, artists=["M-Clan"])
    return dump.Mine(out)


def test_a_song_is_found_by_title_artist_album_and_duration_as_lrclib_would(tmp_path):
    mine = mine_with(tmp_path,
                     (1, "Llamando a La Tierra", "M-Clan", "Usar y tirar", 281.4, "Plain", "[00:01.00] Timed", 0),
                     (2, "Llamando a la Tierra", "M-Clan", "Grandes éxitos", 280.0, "Other", None, 0))
    answer = mine.lookup(song())
    assert answer["status"] == "found" and answer["id"] == 1
    assert answer["syncedLyrics"] == "[00:01.00] Timed" and answer["plainLyrics"] == "Plain"
    assert answer["instrumental"] is False


def test_on_another_album_a_close_version_will_do_timed_first(tmp_path):
    mine = mine_with(tmp_path,
                     (1, "Llamando a la Tierra", "M-Clan", "Live", 279.0, "Plain only", None, 0),
                     (2, "Llamando a la tierra", "M-Clan", "Singles", 281.0, "Plain", "[00:01.00] Timed", 0))
    assert mine.lookup(song())["id"] == 2


def test_more_than_two_seconds_off_is_missing(tmp_path):
    mine = mine_with(tmp_path, (1, "Llamando a la Tierra", "M-Clan", "Usar y tirar", 283.0, "Plain", None, 0))
    assert mine.lookup(song()) == {"status": "missing"}


def test_column_names_skip_constraints_however_they_are_spaced():
    sql = """CREATE TABLE tracks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, duration FLOAT,
             UNIQUE(name, duration), FOREIGN KEY (id) REFERENCES x (id), lyricsfile TEXT)"""
    assert dump.column_names(sql) == ["id", "name", "duration", "lyricsfile"]
