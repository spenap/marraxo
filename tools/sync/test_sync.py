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

    def __init__(self, notes_md5=None):
        self.notes_md5, self.pushed = notes_md5, []

    def __call__(self, serial, *args, check=True):
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
