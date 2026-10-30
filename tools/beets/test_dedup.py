"""Tests for dedup: one file per song from a folder holding several copies of an album."""
from dedup import Song, pick, song_key


def f(name, audio, kbps=192, cover=False, seconds=200):
    return Song(name=name, key=song_key(name), audio=audio, kbps=kbps, cover=cover, seconds=seconds)


def test_song_keys_ignore_numbering_style_case_and_accents():
    assert song_key("01 - Rachas de viento.mp3") == song_key("01 Rachas de Viento.mp3")
    assert song_key("03. Corazón.mp3") == song_key("03 Corazon.mp3")
    assert song_key("1-02 Riots.mp3") == song_key("02 Riots.mp3")  # disc-track numbering
    assert song_key("02 Riots.mp3") != song_key("03 Riots (Remix).mp3")


def test_exact_doubles_keep_one_copy_preferring_the_one_with_cover_art():
    files = [f("01 - A.mp3", "x1"), f("01 A.mp3", "x1", cover=True), f("02 - B.mp3", "x2"), f("02 B.mp3", "x2")]
    kept = pick(files)
    assert sorted(s.key for s in kept) == [song_key("A.mp3"), song_key("B.mp3")]
    assert [s.name for s in kept if s.key == song_key("A.mp3")] == ["01 A.mp3"]


def test_a_superset_gives_the_larger_set():
    big = [f(f"0{i} - S{i}.mp3", f"a{i}") for i in range(1, 6)]
    small = [f(f"0{i} S{i}.mp3", f"a{i}") for i in range(1, 4)]
    assert len(pick(big + small)) == 5


def test_mixed_sets_combine():
    one = [f("01 - S1.mp3", "a1"), f("02 - S2.mp3", "a2")]
    two = [f("02 S2.mp3", "a2"), f("03 S3.mp3", "a3")]
    assert sorted(s.key for s in pick(one + two)) == sorted(song_key(n) for n in ["S1", "S2", "S3"])


def test_two_different_rips_of_a_song_keep_the_better_one():
    files = [f("01 - S1.mp3", "rip1", kbps=128), f("01 S1.mp3", "rip2", kbps=320)]
    assert [s.kbps for s in pick(files)] == [320]


def test_a_folder_without_doubles_is_unchanged():
    files = [f("01 A.mp3", "a"), f("02 B.mp3", "b")]
    assert pick(files) == files


def test_same_title_but_another_length_is_another_song():
    # Telemann: six movements all named after the work; Marea: two "Trasegando".
    movements = [f(f"0{i} Ouverture for flute.mp3", f"m{i}", seconds=100 + 40 * i) for i in range(1, 7)]
    assert len(pick(movements)) == 6
    assert len(pick([f("03 Trasegando.mp3", "t1", seconds=167), f("05 Trasegando.mp3", "t2", seconds=219)])) == 2


def test_rips_of_one_song_differ_by_a_second_or_two_at_most():
    assert len(pick([f("02 Quedate a dormir.mp3", "q1", seconds=303), f("02 Quédate A Dormir.mp3", "q2", seconds=304)])) == 1
