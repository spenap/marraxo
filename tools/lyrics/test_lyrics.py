"""Tests for the lyrics fetcher and database builder (run: .venv/bin/pytest tools)."""
import json
import sqlite3

import lyrics_build as build
import lyrics_fetch as fetch

FACE = {"path": "Phil Collins/Face Value/01 In the Air Tonight.mp3", "title": "In the Air Tonight",
        "artist": "Phil Collins", "album": "Face Value", "duration": 336}
SYNCED = "[00:17.12] Well, if you told me you were drowning\n[00:21.50] I would not lend a hand"


def record(**kw):
    r = {"id": 1, "trackName": "In the Air Tonight", "artistName": "Phil Collins", "albumName": "Face Value",
         "duration": 336, "instrumental": False, "plainLyrics": "Well, if you told me", "syncedLyrics": SYNCED}
    r.update(kw)
    return r


class FakeWeb:
    """Answers (status, headers, body) by URL substring, in order for repeated asks; records what was asked."""
    def __init__(self, answers):
        self.answers, self.asked = {k: list(v) for k, v in answers.items()}, []

    def __call__(self, url):
        self.asked.append(url)
        for part, replies in self.answers.items():
            if part in url:
                return replies.pop(0) if len(replies) > 1 else replies[0]
        raise AssertionError("unexpected " + url)


def test_a_song_is_asked_for_by_its_signature_and_kept():
    web = FakeWeb({"/api/get?": [(200, {}, json.dumps(record()))]})
    found = fetch.lookup(web, FACE)
    assert found["status"] == "found" and found["syncedLyrics"] == SYNCED
    asked = web.asked[0]
    assert "track_name=In+the+Air+Tonight" in asked and "artist_name=Phil+Collins" in asked
    assert "album_name=Face+Value" in asked and "duration=336" in asked


def test_without_an_exact_match_a_search_result_within_two_seconds_will_do_synced_first():
    results = [record(id=2, duration=300), record(id=3, duration=337, syncedLyrics=None), record(id=4, duration=335)]
    web = FakeWeb({"/api/get?": [(404, {}, '{"name":"TrackNotFound"}')], "/api/search?": [(200, {}, json.dumps(results))]})
    assert fetch.lookup(web, FACE)["id"] == 4  # 300 s is another version; 337 s has no timings


def test_nothing_close_enough_is_remembered_as_missing():
    web = FakeWeb({"/api/get?": [(404, {}, "{}")], "/api/search?": [(200, {}, json.dumps([record(duration=250)]))]})
    assert fetch.lookup(web, FACE) == {"status": "missing"}


def test_too_many_requests_waits_as_told_then_tries_again():
    slept = []
    web = FakeWeb({"/api/get?": [(429, {"Retry-After": "7"}, "{}"), (200, {}, json.dumps(record()))]})
    found = fetch.lookup(fetch.Patient(web, sleep=slept.append, jitter=lambda: 0), FACE)
    assert found["status"] == "found" and slept == [7]


def test_songs_already_asked_about_are_not_asked_again(tmp_path):
    cache = fetch.Cache(tmp_path)
    web = FakeWeb({"/api/get?": [(200, {}, json.dumps(record()))]})
    fetch.fetch_all(web, cache, [FACE])
    fetch.fetch_all(web, cache, [FACE])
    assert len(web.asked) == 1


def test_songs_come_from_the_inventory_with_their_own_artist(tmp_path):
    inv = tmp_path / "inventory.jsonl"
    rows = [
        {"path": FACE["path"], "kind": "audio", "duration": 336.4,
         "tags": {"title": "In the Air Tonight", "artist": "Phil Collins", "albumartist": "Phil Collins", "album": "Face Value"}},
        {"path": "Various/Hits/01 Song.mp3", "kind": "audio", "duration": 200.0,
         "tags": {"title": "Song", "artist": "Someone", "albumartist": "Various Artists", "album": "Hits"}},
        {"path": "Patrick Street/No. 2/04 Tom Joad.mpc", "kind": "audio"},  # no tags: not on the phones
        {"path": "Altan/cover.jpg", "kind": "image"},
    ]
    inv.write_text("\n".join(json.dumps(r) for r in rows))
    songs = list(fetch.songs(inv))
    assert songs == [FACE, {"path": "Various/Hits/01 Song.mp3", "title": "Song", "artist": "Someone", "album": "Hits", "duration": 200}]


def test_the_database_has_what_was_found_and_nothing_else(tmp_path):
    cache = fetch.Cache(tmp_path / "cache")
    cache.put(FACE["path"], {"status": "found", **record()})
    cache.put("Altan/Local Ground/03 Reel.mp3", {"status": "found", **record(id=9, instrumental=True, plainLyrics=None, syncedLyrics=None)})
    cache.put("Someone/Else/01 Gone.mp3", {"status": "missing"})
    out = tmp_path / "lyrics.db"
    build.build(out, cache)
    db = sqlite3.connect(out)
    assert db.execute("SELECT value FROM meta WHERE key = 'schema'").fetchone() == ("1",)
    rows = dict((r[0], r[1:]) for r in db.execute("SELECT path, synced, plain, instrumental FROM lyrics"))
    assert rows[FACE["path"]] == (SYNCED, "Well, if you told me", 0)
    assert "Altan/Local Ground/03 Reel.mp3" not in rows  # instrumental: no words, so nothing to open
    assert "Someone/Else/01 Gone.mp3" not in rows


def test_a_server_hiccup_is_waited_out():
    slept = []
    web = FakeWeb({"/api/get?": [(502, {}, "Bad Gateway"), (200, {}, json.dumps(record()))]})
    found = fetch.lookup(fetch.Patient(web, sleep=slept.append, jitter=lambda: 0), FACE)
    assert found["status"] == "found" and slept == [5]


def test_a_song_the_server_keeps_failing_on_is_left_for_the_next_run(tmp_path):
    cache = fetch.Cache(tmp_path)
    other = {**FACE, "path": "Other/Album/01 Fine.mp3", "title": "Fine"}
    web = FakeWeb({"Air+Tonight": [(500, {}, "oops")], "/api/get?": [(200, {}, json.dumps(record()))]})
    failed = fetch.fetch_all(fetch.Patient(web, sleep=lambda s: None, jitter=lambda: 0), cache, [FACE, other])
    assert not cache.has(FACE["path"]) and cache.has(other["path"])
    assert failed == [FACE["path"]]


def test_when_the_api_is_down_the_run_stops_instead_of_crawling(tmp_path):
    cache = fetch.Cache(tmp_path)
    many = [{**FACE, "path": f"A/B/{i:02} Song.mp3", "title": f"Song {i}"} for i in range(20)]
    web = FakeWeb({"/api/": [(502, {}, "Bad Gateway")]})
    failed = fetch.fetch_all(fetch.Patient(web, sleep=lambda s: None, jitter=lambda: 0), cache, many)
    assert len(failed) == fetch.GIVE_UP_AFTER  # it stopped there, nothing cached
    assert not any(cache.has(s["path"]) for s in many)


def test_only_the_albums_asked_for_are_fetched(tmp_path):
    inv = tmp_path / "inventory.jsonl"
    rows = [
        {"path": FACE["path"], "kind": "audio", "duration": 336.4,
         "tags": {"title": "In the Air Tonight", "artist": "Phil Collins", "album": "Face Value"}},
        {"path": "Various/Hits/01 Song.mp3", "kind": "audio", "duration": 200.0,
         "tags": {"title": "Song", "artist": "Someone", "album": "Hits"}},
        {"path": "Quique González/Avería y redención #7/02 Doble fila.mp3", "kind": "audio", "duration": 154.0,
         "tags": {"title": "Doble fila", "artist": "Quique González", "album": "Avería y redención #7"}},
    ]
    inv.write_text("\n".join(json.dumps(r) for r in rows))
    cleared = tmp_path / "cleared.txt"
    cleared.write_text("# from the cleaning session\nPhil Collins/Face Value/\n\nQuique González/Avería y redención #7\n")
    assert [s["path"] for s in fetch.songs(inv, fetch.read_albums(cleared))] == [FACE["path"], rows[2]["path"]]


def test_answers_can_come_from_somewhere_other_than_the_api(tmp_path):
    cache = fetch.Cache(tmp_path / "cache")
    asked = []

    def local(web, song):
        asked.append(song["path"])
        return {"status": "found", "id": 7, "syncedLyrics": SYNCED}

    assert fetch.fetch_all(None, cache, [FACE], ask=local) == []
    assert asked == [FACE["path"]]
    assert cache.has(FACE["path"])


def test_songs_remembered_as_missing_can_be_asked_again(tmp_path):
    cache = fetch.Cache(tmp_path / "cache")
    other = {**FACE, "path": "Other/Album/01 Song.mp3"}
    cache.put(FACE["path"], {"status": "missing"})
    cache.put(other["path"], {"status": "found", "id": 1, "syncedLyrics": SYNCED})
    asked = []

    def local(web, song):
        asked.append(song["path"])
        return {"status": "found", "id": 7, "syncedLyrics": SYNCED}

    fetch.fetch_all(None, cache, [FACE, other], ask=local, recheck_missing=True)
    assert asked == [FACE["path"]]  # what was found stays as it was
    assert cache.get(FACE["path"])["id"] == 7


def test_words_that_are_not_lyrics_are_caught():
    import lyrics_check as check
    # Seen in LRCLIB for songs in the collection.
    assert check.suspect("The Fairy Queen", "Clannad", "Clannad\nMagical Ring\nFairy Queen, The\nInstrumental") == "says instrumental"
    assert check.suspect("Gouryella", "Gouryella", "Я люблю слушать современный Rock'n'Roll. Он снова в моде") == "another alphabet"
    assert check.suspect("Mambo #5", "Fruko y sus Tesos", "Artist: Fruko y sus Tesos\nAlbum: Rumba, Mambo, Cha-Cha-Chá\nTitle: Mambo #5") == "song details"
    assert check.suspect("Cape Breton Song", "Capercaillie", "Cape Breton Song' cannot be translated as the words have mutated") == "a note about the song"


def test_real_lyrics_pass_the_check():
    import lyrics_check as check
    for title, artist, text in [
        ("Moon River", "Louis Armstrong", "Moon river, wider than a mile\nI'm crossin' you in style some day"),
        ("Ar Bhruach na Laoi", "Liam O'Flynn", "Nach brónach a bhí mise nuair a d'fhága mé an baile\nAgus dúirt mé slán"),
        ("Gollum's Song", "Howard Shore", "Ú i vethed nâ i onnad\nSi boe ú-dhanna"),
        ("Kernkraft 400", "Zombie Nation", "Zombie (Zombie, Zombie)\nZombie (Zombie, Zombie)"),
        ("ROCK AND ROLL", "Someone", "WE ARE THE CHAMPIONS\nOF THE WORLD AND ALL"),
        ("Мурка", "Someone", "Здравствуй, моя Мурка"),
        ("The Girl Is Mine", "Michael Jackson", "Every Night She Walks Right In My Dreams\nSince I Met Her From The Start"),
        ("Punk", "Ferry Corsten", "Digital Punk\nDigital Punk\nDigital Punk\nDigital Punk"),
    ]:
        assert check.suspect(title, artist, text) is None, title


def test_suspect_and_rejected_lyrics_stay_off_the_phone(tmp_path):
    cache = fetch.Cache(tmp_path / "cache")
    cache.put("Gouryella/Gouryella/01 Gouryella.mp3", {"status": "found", **record(id=4, trackName="Gouryella", artistName="Gouryella", syncedLyrics=None,
                                                                                    plainLyrics="Я люблю слушать современный Rock'n'Roll")})
    cache.put("Odd/One/01 Wrong.mp3", {"status": "found", **record(id=5)})
    cache.put(FACE["path"], {"status": "found", **record()})
    out = tmp_path / "lyrics.db"
    build.build(out, cache, rejected={"Odd/One/01 Wrong.mp3"})
    paths = [r[0] for r in sqlite3.connect(out).execute("SELECT path FROM lyrics")]
    assert paths == [FACE["path"]]


def test_lyrics_far_too_short_or_long_for_the_song_are_listed_for_review():
    import lyrics_check as check
    words = lambda n: " ".join(["la"] * n)
    assert check.review({"duration": 264, "plainLyrics": "Si de verdad fuera un poeta Te morder"}) == "too few words (2 a minute)"
    assert check.review({"duration": 126, "plainLyrics": words(776)}) == "too many words (370 a minute)"
    assert check.review({"duration": 200, "plainLyrics": words(200)}) is None
    assert check.review({"duration": 200, "instrumental": True}) is None  # nothing to judge
