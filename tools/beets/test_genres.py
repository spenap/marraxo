"""Tests for genres: approved genre changes, applied only where the tag is still what was approved."""
from genres import Album, Rule, plan, read_rules


def rule(artist, album, old, new):
    return Rule(artist, album, old, new)


def test_an_album_still_tagged_as_approved_gets_the_new_genre():
    albums = [Album(1, "Milladoiro", "Galicia no país das maravillas", "Celta")]
    changes, problems = plan(albums, [rule("Milladoiro", "Galicia no país das maravillas", "Celta", "Celtic")])
    assert [(c.album.id, c.new) for c in changes] == [(1, "Celtic")]
    assert problems == []


def test_running_again_changes_nothing():
    albums = [Album(1, "Milladoiro", "Galicia no país das maravillas", "Celtic")]
    changes, problems = plan(albums, [rule("Milladoiro", "Galicia no país das maravillas", "Celta", "Celtic")])
    assert changes == [] and problems == []


def test_an_album_retagged_since_the_decision_is_left_alone_and_reported():
    albums = [Album(1, "ABBA", "The Definitive Collection", "Europop")]
    changes, problems = plan(albums, [rule("ABBA", "The Definitive Collection", "Oldies", "Pop")])
    assert changes == []
    assert len(problems) == 1 and "Europop" in problems[0]


def test_a_rule_for_an_album_not_in_the_library_is_reported():
    changes, problems = plan([], [rule("Oasis", "MTV Live & Unplugged", "Acoustic", "Britpop")])
    assert changes == []
    assert len(problems) == 1 and "MTV Live & Unplugged" in problems[0]


def test_names_match_exactly_whatever_the_unicode_composition():
    decomposed = "Échate un cantecito"  # as a macOS file system may write it
    albums = [Album(1, "Kiko Veneno", decomposed, "Rock Español"), Album(2, "Kiko Veneno", "Échate", "Rock Español")]
    changes, _ = plan(albums, [rule("Kiko Veneno", "Échate un cantecito", "Rock Español", "Rock/Latin")])
    assert [c.album.id for c in changes] == [1]


def test_rules_are_tab_separated_with_comments_and_a_header():
    text = "# why\nalbumartist\talbum\tfrom\tto\nAC/DC\tHigh Voltage\tClassic Rock\tHard Rock\n\n"
    assert read_rules(text) == [Rule("AC/DC", "High Voltage", "Classic Rock", "Hard Rock")]
