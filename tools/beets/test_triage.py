"""Tests for triage: deciding which skipped albums are safe to import unattended."""
import triage

CLEAR = """
~/Music/Altan/Local Ground (9 items)

  Match (88.4%):
  Altan - Local Ground
  ≠ tracks, missing tracks
  MusicBrainz, CD, 2005, GB, Vertical Records, None, None
  https://musicbrainz.org/release/a10b8f12-b30e-4a87-8faa-b2fdd15943f6
  * Artist: Altan
  * Album: Local Ground
     * (#1) Dúlamán (3:01)
     ≠ (#2) The Snowy Path (4:12) -> (#2) The Snowy Path (4:14)
     ≠ (#3) Mo Choill (3:20) -> (#3) Mo Choill' (3:21)
Missing tracks (2/11 - 18.2%):
 ! Gleanntáin Ghlas' Ghaoth Dobhair (#4) (3:44)
"""

WRONG_LENGTH = CLEAR.replace("(4:12) -> (#2) The Snowy Path (4:14)", "(4:12) -> (#2) The Snowy Path (6:40)")

EXTRA = CLEAR + """Unmatched tracks (1):
 ! Bonus Jam (#12) (2:30)
"""

WEAK = CLEAR.replace("Match (88.4%)", "Match (72.0%)")

LIST = """
~/Music/Compilations/KROSE (45 items)

Finding tags for album "GTA: San Andreas - KROSE".
  Candidates:
  1. (28.3%) Various Artists - Hardbass, Chapter 7.Seven
"""


def test_a_clear_match_with_missing_tracks_is_accepted():
    d = triage.decide(CLEAR)
    assert d.accept, d.reason
    assert d.release == "a10b8f12-b30e-4a87-8faa-b2fdd15943f6"
    assert d.folder == "~/Music/Altan/Local Ground"
    assert "2 of 11 missing" in d.reason


def test_a_track_of_a_different_length_is_left_for_a_person():
    d = triage.decide(WRONG_LENGTH)
    assert not d.accept
    assert "The Snowy Path" in d.reason


def test_local_tracks_without_a_match_are_left_for_a_person():
    assert not triage.decide(EXTRA).accept


def test_weak_matches_are_left_for_a_person():
    assert not triage.decide(WEAK).accept


def test_a_list_of_candidates_is_left_for_a_person():
    d = triage.decide(LIST)
    assert not d.accept
    assert "28.3%" in d.reason


def test_titles_differing_only_in_case_and_punctuation_are_the_same():
    assert triage.same_title("Mo Choill", "Mo Choill'")
    assert triage.same_title("Rock 'n' Roll Singer", "Rock ’n’ Roll Singer")
    assert not triage.same_title("The Rime of the Ancient Mariner", "An Danzza Theme")


def test_a_release_with_another_title_is_left_for_a_person():
    compilation = CLEAR.replace("/Altan/Local Ground (9 items)", "/Los Rodriguez/Sin documentos (9 items)").replace(
        "  Altan - Local Ground\n", '  Los Rodríguez - Sus mejores temas y su álbum "Sin documentos" completo\n')
    d = triage.decide(compilation)
    assert not d.accept
    assert "title" in d.reason


def test_small_title_differences_still_pass():
    ost = CLEAR.replace("/Altan/Local Ground (9 items)", "/Varios Artistas/Rob Roy OST (9 items)").replace(
        "  Altan - Local Ground\n", "  Carter Burwell - Rob Roy\n")
    assert triage.decide(ost).accept
