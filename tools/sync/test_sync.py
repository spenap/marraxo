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
