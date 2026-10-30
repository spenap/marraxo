"""Tests for the read-only inventory (run: .venv/bin/pytest tools)."""
import hashlib
import io
import os
import wave

import pytest
from PIL import Image

import inventory as inv


def test_norm_ignores_accents_case_punctuation_and_leading_the():
    assert inv.norm("The Beatles") == inv.norm("beatles")
    assert inv.norm("Camarón de la Isla") == inv.norm("CAMARON DE LA ISLA")
    assert inv.norm("Los Planetas!") == "losplanetas"
    assert inv.norm(None) == ""


@pytest.mark.parametrize("fmt", ["PNG", "JPEG"])
def test_image_size_reads_headers_only(fmt):
    buf = io.BytesIO()
    Image.new("RGB", (600, 400)).save(buf, fmt)
    assert tuple(inv.image_size(buf.getvalue())) == (600, 400)


def test_image_size_of_garbage_is_none():
    assert inv.image_size(b"not an image") is None


def test_first_takes_the_first_non_blank_tag():
    assert inv.first({"TPE1": [" "], "TPE2": ["Altan"]}, "TPE1", "TPE2") == "Altan"
    assert inv.first({}, "TPE1") is None


def test_has_id3v1(tmp_path):
    with_tag = tmp_path / "a.mp3"
    with_tag.write_bytes(b"\0" * 500 + b"TAG" + b"\0" * 125)
    without = tmp_path / "b.mp3"
    without.write_bytes(b"\0" * 500)
    assert inv.has_id3v1(with_tag)
    assert not inv.has_id3v1(without)


def write_wav(path, seconds=1.0, rate=8000):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\0\0" * int(seconds * rate))


@pytest.fixture
def collection(tmp_path):
    album = tmp_path / "Altan" / "The Blue Idol"
    album.mkdir(parents=True)
    write_wav(album / "01 Daily Growing.wav")
    Image.new("RGB", (500, 500)).save(album / "cover.jpg")
    (album / ".DS_Store").write_bytes(b"junk")
    (album / "02 Broken.mp3").write_bytes(b"this is not an mp3" * 100)
    (tmp_path / "notes.pdf").write_bytes(b"%PDF")
    return tmp_path


def test_scan_classifies_files_and_reads_audio(collection):
    records = {r["path"]: r for r in inv.scan(str(collection))}
    song = records["Altan/The Blue Idol/01 Daily Growing.wav"]
    assert song["kind"] == "audio"
    assert (song["folder_artist"], song["folder_album"]) == ("Altan", "The Blue Idol")
    assert song["duration"] == pytest.approx(1.0, abs=0.01)
    assert records["Altan/The Blue Idol/cover.jpg"]["kind"] == "image"
    assert records["Altan/The Blue Idol/.DS_Store"]["kind"] == "junk"
    assert records["notes.pdf"]["kind"] == "other"


def test_unreadable_audio_is_reported_not_fatal(collection):
    broken = {r["path"]: r for r in inv.scan(str(collection))}["Altan/The Blue Idol/02 Broken.mp3"]
    assert "error" in broken


def test_scan_never_modifies_the_collection(collection):
    def snapshot():
        return {p: (os.stat(p).st_mtime_ns, hashlib.sha1(open(p, "rb").read()).hexdigest())
                for p in map(str, collection.rglob("*")) if os.path.isfile(p)}
    before = snapshot()
    inv.scan(str(collection))
    assert snapshot() == before


def test_exact_duplicates_compares_content_not_just_size(tmp_path):
    for name, data in [("a.mp3", b"x" * 100), ("b.mp3", b"x" * 100), ("c.mp3", b"y" * 100)]:
        (tmp_path / name).write_bytes(data)
    audio = [{"path": n, "bytes": 100} for n in ("a.mp3", "b.mp3", "c.mp3")]
    assert [sorted(g) for g in inv.exact_duplicates(str(tmp_path), audio)] == [["a.mp3", "b.mp3"]]
