"""Tests for syncing part of the cleaned collection to a phone."""
import sync

GB = 1024 ** 3


def album(path, gb, plays=0):
    return sync.Album(path=path, bytes=int(gb * GB), plays=plays)


def test_selection_fits_the_space_left_after_the_margin():
    albums = [album(f"A/{i}", 1) for i in range(10)]
    chosen = sync.select(albums, free=6 * GB, margin=GB, already=[])
    assert sum(a.bytes for a in chosen) <= 5 * GB
    assert len(chosen) == 5


def test_albums_already_on_the_phone_count_as_room():
    albums = [album("A/1", 2), album("A/2", 2), album("A/3", 2)]
    # 1 GB free, but A/1 (2 GB) is already there and may stay or go.
    chosen = sync.select(albums, free=1 * GB, margin=0, already=["A/1"])
    assert sum(a.bytes for a in chosen) <= 3 * GB


def test_played_albums_come_first():
    albums = [album("A/quiet", 1), album("A/loved", 1, plays=12), album("A/liked", 1, plays=3)]
    chosen = sync.select(albums, free=2 * GB, margin=0, already=[])
    assert [a.path for a in chosen] == ["A/loved", "A/liked"]


def test_the_rest_is_filled_the_same_way_each_time():
    albums = [album(f"A/{i}", 1) for i in range(20)]
    first = sync.select(albums, free=5 * GB, margin=0, already=[], seed=7)
    again = sync.select(albums, free=5 * GB, margin=0, already=[], seed=7)
    assert [a.path for a in first] == [a.path for a in again]


def test_plan_copies_new_albums_and_removes_only_what_sync_put_there():
    managed = ["A/old", "A/keep"]  # what earlier syncs copied
    on_phone = ["A/old", "A/keep", "Sample/by hand"]
    plan = sync.plan(selected=["A/keep", "A/new"], managed=managed, on_phone=on_phone)
    assert plan.copy == ["A/new"]
    assert plan.remove == ["A/old"]
    assert "Sample/by hand" not in plan.remove  # never touched unless asked


def test_replacing_everything_must_be_asked_for():
    plan = sync.plan(selected=["A/new"], managed=[], on_phone=["Sample/by hand"], replace_unmanaged=True)
    assert plan.remove == ["Sample/by hand"]


def test_an_album_copied_by_hand_is_replaced_only_when_asked():
    asked = sync.plan(selected=["A/1"], managed=[], on_phone=["A/1"], replace_unmanaged=True)
    assert asked.remove == ["A/1"] and asked.copy == ["A/1"]  # the cleaned copy replaces it
    left = sync.plan(selected=["A/1"], managed=[], on_phone=["A/1"])
    assert left.remove == [] and left.copy == [] and left.kept == ["A/1"]


def test_space_freed_by_removals_counts():
    albums = [album(f"A/{i}", 1) for i in range(10)]
    chosen = sync.select(albums, free=2 * GB, margin=0, already=[], reclaimable=3 * GB)
    assert len(chosen) == 5


def test_a_second_run_right_after_a_sync_changes_nothing():
    # Files take a little more room on the phone, so free space ends just under the margin.
    albums = [album(f"A/{i}", 1) for i in range(10)]
    first = sync.select(albums, free=6 * GB, margin=GB, already=[])
    on_phone = [a.path for a in first]
    again = sync.select(albums, free=int(0.9 * GB), margin=GB, already=on_phone)
    assert [a.path for a in again] == on_phone


class FakePhone:
    """adb, for the liner notes copy: remembers what is on the phone and what was pushed."""

    def __init__(self, notes_md5=None, sdk=35):
        self.notes_md5, self.pushed, self.sdk, self.commands = notes_md5, [], sdk, []

    def __call__(self, serial, *args, check=True):
        self.commands.append(args[-1])
        if args[-1] == "getprop ro.build.version.sdk":
            return f"{self.sdk}\n"
        if args[0] == "push":
            self.pushed.append(args[2])
            return ""
        if "md5sum" in args[-1]:
            return f"{self.notes_md5}  {sync.PHONE_NOTES}\n" if self.notes_md5 else ""
        return ""


def test_liner_notes_go_to_the_apps_own_folder_when_they_changed(tmp_path, monkeypatch):
    db = tmp_path / "linernotes.db"
    db.write_bytes(b"new notes")
    phone = FakePhone(notes_md5="0" * 32)
    monkeypatch.setattr(sync, "adb", phone)
    assert sync.sync_notes("serial", db) is True
    assert phone.pushed == [sync.PHONE_NOTES]
    assert sync.PHONE_NOTES == "/sdcard/Android/data/com.simonpena.mako/files/linernotes.db"  # readable without a permission


def test_unchanged_liner_notes_are_not_copied_again(tmp_path, monkeypatch):
    import hashlib
    db = tmp_path / "linernotes.db"
    db.write_bytes(b"same notes")
    phone = FakePhone(notes_md5=hashlib.md5(b"same notes").hexdigest())
    monkeypatch.setattr(sync, "adb", phone)
    assert sync.sync_notes("serial", db) is False
    assert phone.pushed == []


def test_no_liner_notes_built_is_not_an_error(tmp_path, monkeypatch):
    phone = FakePhone()
    monkeypatch.setattr(sync, "adb", phone)
    assert sync.sync_notes("serial", tmp_path / "missing.db") is False


def test_android_10_and_later_is_asked_to_index_the_copied_music(monkeypatch):
    # adb push doesn't index; since Android 11 Mako can't see unindexed files to index them.
    phone = FakePhone(sdk=35)
    monkeypatch.setattr(sync, "adb", phone)
    assert sync.index_music("serial") is True
    assert "content call --uri content://media --method scan_volume --arg external_primary" in phone.commands


def test_older_android_is_left_to_makos_rescan(monkeypatch):
    phone = FakePhone(sdk=27)  # the Nexus 4: Mako's rescan finds the files there
    monkeypatch.setattr(sync, "adb", phone)
    assert sync.index_music("serial") is False
    assert not any("scan_volume" in c for c in phone.commands)


def test_new_albums_in_the_collection_dont_reshuffle_what_is_on_the_phone():
    # After the cleaning added two albums, every unplayed album changed place and a sync
    # planned to copy 79 and remove 70 (2026-11-01). What's there stays; new ones fill the room.
    albums = [album(f"A/{i}", 1) for i in range(20)]
    first = sync.select(albums, free=8 * GB, margin=0, already=[])
    on_phone = [a.path for a in first]
    grown = albums + [album("New/1", 1), album("New/2", 1)]
    again = sync.select(grown, free=1 * GB, margin=0, already=on_phone)
    assert set(on_phone) <= {a.path for a in again}


def test_an_albums_place_in_the_fill_order_doesnt_depend_on_the_others():
    albums = [album(f"A/{i}", 1) for i in range(20)]
    order = [a.path for a in sync.select(albums, free=20 * GB, margin=0, already=[])]
    fewer = [a.path for a in sync.select(albums[5:], free=20 * GB, margin=0, already=[])]
    assert [p for p in order if p in fewer] == fewer


def test_rotating_is_asked_for():
    albums = [album(f"A/{i}", 1) for i in range(20)]
    first = [a.path for a in sync.select(albums, free=5 * GB, margin=0, already=[])]
    rotated = [a.path for a in sync.select(albums, free=0, margin=0, already=first, seed=1, rotate=True)]
    assert set(rotated) != set(first)


def test_included_albums_come_in_and_push_out_unplayed_ones_only():
    played = album("P/1", 1, plays=3)
    on_phone = [album(f"A/{i}", 1) for i in range(3)]
    new = album("New/Trance", 1)
    chosen = sync.select([played, new] + on_phone, free=0, margin=0, already=["P/1"] + [a.path for a in on_phone], include=["New/Trance"])
    paths = {a.path for a in chosen}
    assert {"P/1", "New/Trance"} <= paths
    assert len(paths & {a.path for a in on_phone}) == 2  # one unplayed album made room


def test_making_room_for_new_albums_keeps_the_margin():
    # The margin may be eaten only to avoid churn, never to fit something new.
    on_phone = [album(f"A/{i}", 1) for i in range(3)]
    new = album("New/Trance", 1)
    free, margin = int(0.5 * GB), GB
    chosen = sync.select(on_phone + [new], free=free, margin=margin, already=[a.path for a in on_phone], include=["New/Trance"])
    paths = {a.path for a in chosen}
    removed = sum(a.bytes for a in on_phone if a.path not in paths)
    assert "New/Trance" in paths
    assert free - new.bytes + removed >= margin


def test_lyrics_go_to_the_apps_own_folder_like_the_notes(tmp_path, monkeypatch):
    db = tmp_path / "lyrics.db"
    db.write_bytes(b"lyrics")
    phone = FakePhone()
    monkeypatch.setattr(sync, "adb", phone)
    assert sync.sync_file("serial", db, sync.PHONE_LYRICS) is True
    assert phone.pushed == ["/sdcard/Android/data/com.simonpena.mako/files/lyrics.db"]


def test_an_album_changed_since_it_was_copied_is_refreshed_file_by_file():
    local = {"01-01 Man on the Moon.mp3": 10, "02-01 Pop Song.mp3": 8, "cover.jpg": 5}
    phone = {"01 Man on the Moon.mp3": 10, "cover.jpg": 4}
    push, delete = sync.refresh(local, phone)
    assert push == ["01-01 Man on the Moon.mp3", "02-01 Pop Song.mp3", "cover.jpg"]  # new names, new tracks, new cover
    assert delete == ["01 Man on the Moon.mp3"]  # the old name


def test_an_unchanged_album_is_left_alone():
    assert sync.refresh({"01 a.mp3": 10}, {"01 a.mp3": 10}) == ([], [])


def test_a_copy_by_hand_identical_to_the_collection_counts_as_synced():
    local = {"A/same": {"01 a.mp3": 10}, "A/other rip": {"01 a.mp3": 10}}
    phone = {"A/same": {"01 a.mp3": 10}, "A/other rip": {"01 track 1.mp3": 11}}
    assert sync.adoptable(["A/same", "A/other rip"], local, phone) == ["A/same"]


def test_the_phones_files_are_grouped_by_album_folder():
    out = "10 /sdcard/Music/A/X/01 a.mp3\n5 /sdcard/Music/A/X/cover.jpg\n3 /sdcard/Music/.mako-sync\n"
    assert sync.parse_phone_files(out) == {"A/X": {"01 a.mp3": 10, "cover.jpg": 5}, "": {".mako-sync": 3}}
