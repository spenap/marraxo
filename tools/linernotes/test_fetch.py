"""Tests for the liner-notes fetcher (run: .venv/bin/pytest tools)."""
import json

import fetch


def test_wikidata_id_comes_from_url_relations():
    rels = [{"type": "discogs", "url": {"resource": "https://www.discogs.com/master/1"}},
            {"type": "wikidata", "url": {"resource": "https://www.wikidata.org/wiki/Q1386870"}}]
    assert fetch.wikidata_id(rels) == "Q1386870"
    assert fetch.wikidata_id([]) is None


def test_wikipedia_title_prefers_spanish_then_english():
    entity = {"entities": {"Q1": {"sitelinks": {
        "enwiki": {"title": "Castillo de arena (album)"}, "eswiki": {"title": "Castillo de arena"}}}}}
    assert fetch.wikipedia_title(entity, "Q1") == ("es", "Castillo de arena")
    only_en = {"entities": {"Q2": {"sitelinks": {"enwiki": {"title": "The Blue Idol"}}}}}
    assert fetch.wikipedia_title(only_en, "Q2") == ("en", "The Blue Idol")
    assert fetch.wikipedia_title({"entities": {"Q3": {"sitelinks": {}}}}, "Q3") is None


def test_rate_limiter_spaces_requests():
    now = [0.0]
    slept = []
    limit = fetch.RateLimiter(1.1, clock=lambda: now[0], sleep=lambda s: (slept.append(s), now.__setitem__(0, now[0] + s)))
    limit.wait()          # first request: no wait
    now[0] += 0.3
    limit.wait()          # 0.3 s later: wait the rest
    assert slept == [0.8] or abs(slept[0] - 0.8) < 1e-9


class FakeWeb:
    """Answers by URL substring; records what was asked."""
    def __init__(self, answers):
        self.answers, self.asked = answers, []

    def __call__(self, url):
        self.asked.append(url)
        for part, body in self.answers.items():
            if part in url:
                return body
        raise fetch.NotFound(url)


RELEASE = {"id": "r1", "title": "The Blue Idol",
           "release-group": {"id": "rg1"},
           "artist-credit": [{"artist": {"id": "a1", "name": "Altan"}}],
           "media": [{"tracks": [{"recording": {"id": "rec1", "title": "Daily Growing"}}]}]}


def test_an_album_fetches_release_group_artists_and_wikipedia_once(tmp_path):
    web = FakeWeb({
        "/release/r1": RELEASE,
        "/release-group/rg1": {"id": "rg1", "relations": [
            {"type": "wikidata", "url": {"resource": "https://www.wikidata.org/wiki/Q42"}}]},
        "/artist/a1": {"id": "a1", "name": "Altan", "relations": []},
        "Special:EntityData/Q42": {"entities": {"Q42": {"sitelinks": {"enwiki": {"title": "The Blue Idol"}}}}},
        "en.wikipedia.org/api/rest_v1/page/summary/The%20Blue%20Idol": {"extract": "The Blue Idol is an album…"},
    })
    cache = fetch.Cache(tmp_path)
    fetch.album(web, cache, "r1")
    assert cache.get("release", "r1")["title"] == "The Blue Idol"
    assert cache.get("wikipedia", "rg1")["extract"].startswith("The Blue Idol")
    assert cache.get("artist", "a1")["name"] == "Altan"

    asked = len(web.asked)
    fetch.album(web, cache, "r1")  # a second run reads the cache only
    assert len(web.asked) == asked


def test_missing_pages_are_remembered_not_retried(tmp_path):
    web = FakeWeb({"/release/r1": RELEASE, "/release-group/rg1": {"id": "rg1", "relations": []},
                   "/artist/a1": {"id": "a1", "relations": []}})
    cache = fetch.Cache(tmp_path)
    fetch.album(web, cache, "r1")
    asked = len(web.asked)
    fetch.album(web, cache, "r1")
    assert len(web.asked) == asked  # no Wikidata link: nothing more to try


def test_cache_files_are_plain_json(tmp_path):
    cache = fetch.Cache(tmp_path)
    cache.put("release", "r1", {"title": "x"})
    assert json.loads((tmp_path / "release" / "r1.json").read_text()) == {"title": "x"}
