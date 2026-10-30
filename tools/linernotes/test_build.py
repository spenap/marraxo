"""Tests for building linernotes.db from the fetched cache."""
import build
import fetch

RELEASE = {
    "id": "r1", "title": "The Blue Idol", "date": "2002-02-26", "country": "GB",
    "label-info": [{"catalog-number": "VERTCD 060", "label": {"name": "Vertical Records"}}],
    "release-group": {"id": "rg1"},
    "artist-credit": [{"artist": {"id": "a1", "name": "Altan"}}],
    "relations": [
        {"type": "producer", "artist": {"name": "Brian Masterson"}, "attributes": []},
        {"type": "recorded at", "place": {"name": "Windmill Lane Studios"}, "attributes": []},
    ],
    "media": [{"position": 1, "tracks": [{"position": 1, "recording": {
        "id": "rec1", "title": "Daily Growing",
        "relations": [
            {"type": "instrument", "artist": {"name": "Mairéad Ní Mhaonaigh"}, "attributes": ["fiddle"]},
            {"type": "vocal", "artist": {"name": "Mairéad Ní Mhaonaigh"}, "attributes": ["lead vocals"]},
            {"type": "performance", "work": {"title": "Daily Growing", "relations": [
                {"type": "composer", "artist": {"name": "Traditional"}, "attributes": []}]}},
        ]}}]}],
}
WIKI = {"extract": "The Blue Idol is the eleventh album by Altan…", "lang": "en",
        "content_urls": {"desktop": {"page": "https://en.wikipedia.org/wiki/The_Blue_Idol"}}}


def make(tmp_path):
    cache = fetch.Cache(tmp_path / "cache")
    cache.put("release", "r1", RELEASE)
    cache.put("wikipedia", "rg1", WIKI)
    albums = [{"mb_albumid": "r1", "albumartist": "Altan", "album": "The Blue Idol", "year": 2002}]
    items = [{"mb_albumid": "r1", "mb_trackid": "rec1", "title": "Daily Growing",
              "path": "Altan/The Blue Idol/01 Daily Growing.mp3"}]
    return build.build(tmp_path / "linernotes.db", albums, items, cache)


def rows(db, sql, *args):
    return db.execute(sql, args).fetchall()


def test_the_database_says_which_schema_it_has(tmp_path):
    db = make(tmp_path)
    assert rows(db, "SELECT value FROM meta WHERE key = 'schema'") == [(str(build.SCHEMA),)]


def test_an_album_has_its_label_catalogue_number_and_story(tmp_path):
    db = make(tmp_path)
    (label, catno, summary, lang) = rows(db, "SELECT label, catno, summary, summary_lang FROM album")[0]
    assert (label, catno, lang) == ("Vertical Records", "VERTCD 060", "en")
    assert summary.startswith("The Blue Idol is")


def test_album_credits_include_the_producer_and_the_studio(tmp_path):
    db = make(tmp_path)
    credits = rows(db, "SELECT role, name FROM credit WHERE path IS NULL ORDER BY role")
    assert credits == [("producer", "Brian Masterson"), ("recorded at", "Windmill Lane Studios")]


def test_track_credits_are_found_by_the_file_path_the_phone_sees(tmp_path):
    db = make(tmp_path)
    credits = rows(db, "SELECT role, name, detail FROM credit WHERE path = ? ORDER BY role",
                   "Altan/The Blue Idol/01 Daily Growing.mp3")
    assert ("instrument", "Mairéad Ní Mhaonaigh", "fiddle") in credits
    assert ("vocal", "Mairéad Ní Mhaonaigh", "lead vocals") in credits
    assert ("composer", "Traditional", "") in credits  # from the work the recording performs


def test_albums_without_fetched_data_are_still_listed(tmp_path):
    cache = fetch.Cache(tmp_path / "cache")
    db = build.build(tmp_path / "x.db", [{"mb_albumid": "", "albumartist": "An Danzza", "album": "Demo", "year": 0}], [], cache)
    assert rows(db, "SELECT artist, title FROM album") == [("An Danzza", "Demo")]
