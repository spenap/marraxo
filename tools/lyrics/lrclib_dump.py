#!/usr/bin/env python3
"""Extract the collection's artists and their lyrics from LRCLIB's dump, as a stream.

LRCLIB publishes its whole database as a gzipped SQLite file: 49 GB, and
~270 GB unpacked (docs/lyrics.md). This never unpacks it to disk: it reads
the compressed stream front to back, in memory, a few times, and keeps only
the tracks of the collection's artists and their lyrics, in a small SQLite
file (out/lrclib-mine.sqlite3) that `lyrics_fetch.py --dump` looks songs up
in, offline.

How: a SQLite file is a sequence of fixed-size pages, each saying what it
is (https://sqlite.org/fileformat2.html). A table is a tree: interior pages
point to other pages, leaves hold the rows, and a row too long for its page
continues on a chain of overflow pages, each naming the next. Pass 1 keeps
every table's interior pages (a small fraction) to know the trees. Then the
leaves of `tracks` are read, the artists' rows kept, and their lyrics found
by id in the `lyrics` tree. A page needed after the stream has gone past it
(a chain pointing backwards) waits for the next pass, and a pass stops as
soon as nothing further ahead is needed. Rows are read only as far as the
columns used, so the long word timings at the end of a lyrics row are
skipped.

Usage: lrclib_dump.py DUMP.sqlite3.gz [--out out/lrclib-mine.sqlite3]
"""
import argparse
import bisect
import collections
import contextlib
import functools
import gzip
import json
import re
import shutil
import sqlite3
import struct
import subprocess
import sys
import time
import unicodedata
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # lyrics_fetch.py, also under python -I
import lyrics_fetch  # noqa: E402

HERE = Path(__file__).resolve().parent
OUT = HERE / "out" / "lrclib-mine.sqlite3"
SCHEMA = "1"
TABLE_INTERIOR = 0x05
WINDOW = 8 << 30  # bytes of recent pages kept in memory, for chains that run backwards

TRACK_COLUMNS = ("name", "artist_name", "album_name", "duration", "last_lyrics_id")
LYRICS_COLUMNS = ("plain_lyrics", "synced_lyrics", "track_id", "instrumental")


# --- Matching ------------------------------------------------------------

# Between artists in one credit: "A. R. Rahman feat. Blaaze & Tanvi Shah", "Quique González y La Aristocracia del Barrio".
BETWEEN_ARTISTS = re.compile(r"\s*(?:,|;|/|\+|\bfeat\b\.?|\bft\b\.?|\bfeaturing\b|\band\b|\bwith\b|\bvs\b\.?|\by\b)\s*")
NOT_AN_ARTIST = {"variousartists", "various", "va", "unknown", "unknownartist"}


def fold(s):
    """Lower case, without accents: "Ábba" → "abba"."""
    s = unicodedata.normalize("NFKD", s or "")
    return "".join(c for c in s if not unicodedata.combining(c)).casefold()


def title_key(s):
    """A title or album as compared: folded, letters and digits only ("Llamando a La Tierra" → "llamandoalatierra")."""
    return re.sub(r"[\W_]", "", fold(s))


@functools.lru_cache(maxsize=1 << 20)
def artist_keys(name):
    """Keys for a credit and each artist in it, without a leading "the": two credits match if they share one."""
    s = fold(name).replace("&", " and ")
    keys = {re.sub(r"[\W_]", "", re.sub(r"^the\s+", "", part.strip())) for part in [s, *BETWEEN_ARTISTS.split(s)]}
    return frozenset(k for k in keys if len(k) > 1 and k not in NOT_AN_ARTIST)


# --- Reading SQLite pages from a stream -----------------------------------

@dataclass
class Header:
    page_size: int
    page_count: int
    usable: int  # bytes of each page in use: the page size minus a reserved tail


def header(path):
    """The dump's own header, from its first 100 bytes: how big it is unpacked, without unpacking it."""
    with gzip.open(path, "rb") as f:
        h = f.read(100)
    if h[:16] != b"SQLite format 3\x00":
        raise ValueError(f"{path} isn't a gzipped SQLite database")
    if int.from_bytes(h[56:60], "big") != 1:
        raise ValueError(f"{path}: text isn't UTF-8")
    if h[24:28] != h[92:96]:
        raise ValueError(f"{path}: the page count in its header is out of date")
    size = int.from_bytes(h[16:18], "big")
    size = 65536 if size == 1 else size
    return Header(size, int.from_bytes(h[28:32], "big"), size - h[20])


@contextlib.contextmanager
def unpacked(path):
    """The unpacked bytes as a stream, never written anywhere. ISA-L (the isal package) decompresses ~2.5 times
    as fast as gzip (884 against 350 MB/s here), in a thread of its own; else gzip in a process of its own, or
    Python's gzip where there is no gzip command."""
    try:
        from isal import igzip_threaded
    except ImportError:
        igzip_threaded = None
    if igzip_threaded:
        with igzip_threaded.open(path, "rb", threads=1) as f:
            yield f
        return
    if not shutil.which("gzip"):
        with gzip.open(path, "rb") as f:
            yield f
        return
    proc = subprocess.Popen(["gzip", "-dc", str(path)], stdout=subprocess.PIPE, bufsize=1 << 24)
    try:
        yield proc.stdout
    finally:
        proc.kill()  # a pass may stop early
        proc.wait()
        proc.stdout.close()


def chunks(stream, size):
    """(number of its first page, whole pages) for each chunk of the stream, in order."""
    n, rest = 1, b""
    while chunk := stream.read(size * 4096):
        if rest:
            chunk = rest + chunk
        whole = len(chunk) - len(chunk) % size
        yield n, memoryview(chunk)[:whole]
        n += whole // size
        rest = chunk[whole:]


def pages(stream, size):
    """(page number, page) for each page in order."""
    for first, view in chunks(stream, size):
        for k in range(len(view) // size):
            yield first + k, view[k * size:(k + 1) * size]


def varint(b, i):
    """SQLite's variable-length integer at b[i]: (value, index after it)."""
    v = 0
    for k in range(8):
        c = b[i + k]
        v = (v << 7) | (c & 0x7F)
        if c < 0x80:
            return v, i + k + 1
    return (v << 8) | b[i + 8], i + 9


def width(serial_type):
    return (0, 1, 2, 3, 4, 6, 8, 8, 0, 0, 0, 0)[serial_type] if serial_type < 12 else (serial_type - 12) // 2


def value(serial_type, b):
    if serial_type == 0:
        return None
    if serial_type <= 6:
        return int.from_bytes(b, "big", signed=True)
    if serial_type == 7:
        return struct.unpack(">d", b)[0]
    if serial_type in (8, 9):
        return serial_type - 8
    return bytes(b).decode("utf-8", "replace") if serial_type % 2 else bytes(b)


def layout(record):
    """[(serial type, start, end)] per column of a record, or None while its header isn't all there."""
    try:
        size, i = varint(record, 0)
        if len(record) < size:
            return None
        cols, pos = [], size
        while i < size:
            t, i = varint(record, i)
            cols.append((t, pos, pos + width(t)))
            pos += width(t)
        return cols
    except IndexError:
        return None


def end_of(cols, upto):
    """Where column [upto] ends (all columns if None): how much of a record must be read."""
    want = cols if upto is None else cols[:upto + 1]
    return want[-1][2] if want else 0


def decode(record, cols, upto):
    """The values of columns 0..upto (all if None); columns the row predates (added later) are None."""
    n = len(cols) if upto is None else upto + 1
    return [value(t, record[s:e]) for t, s, e in cols[:n]] + [None] * (n - len(cols))


def local_size(payload, usable):
    """How much of a table leaf cell's payload is on its page; the rest is on overflow pages."""
    most = usable - 35
    if payload <= most:
        return payload
    least = (usable - 12) * 32 // 255 - 23
    k = least + (payload - least) % (usable - 4)
    return k if k <= most else least


def leaf_cells(page, n, usable):
    """(rowid, payload size, bytes on this page, first overflow page or 0) per cell of a table leaf."""
    h = 100 if n == 1 else 0
    for k in range(int.from_bytes(page[h + 3:h + 5], "big")):
        off = int.from_bytes(page[h + 8 + 2 * k:h + 10 + 2 * k], "big")
        size, i = varint(page, off)
        rowid, i = varint(page, i)
        local = local_size(size, usable)
        first = int.from_bytes(page[i + local:i + local + 4], "big") if local < size else 0
        yield rowid, size, page[i:i + local], first


class Trees:
    """Every table's interior pages, from pass 1: the shape of each table's tree."""

    def __init__(self, interiors):
        self.raw, self.parsed = interiors, {}

    def node(self, n):
        """(keys, children) of an interior page, or None for a leaf: child i holds rowids up to keys[i]."""
        if n not in self.raw:
            return None
        if n not in self.parsed:
            page, h = self.raw[n], 100 if n == 1 else 0
            keys, children = [], []
            for k in range(int.from_bytes(page[h + 3:h + 5], "big")):
                off = int.from_bytes(page[h + 12 + 2 * k:h + 14 + 2 * k], "big")
                children.append(int.from_bytes(page[off:off + 4], "big"))
                keys.append(varint(page, off + 4)[0])
            children.append(int.from_bytes(page[h + 8:h + 12], "big"))
            self.parsed[n] = keys, children
        return self.parsed[n]

    def leaves(self, root):
        out, stack = [], [root]
        while stack:
            node = self.node(n := stack.pop())
            if node is None:
                out.append(n)
            else:
                stack.extend(node[1])
        return out

    def leaf_for(self, root, rowid):
        n = root
        while (node := self.node(n)) is not None:
            keys, children = node
            n = children[bisect.bisect_left(keys, rowid)]
        return n


class Row:
    __slots__ = ("rowid", "size", "record", "upto", "done")

    def __init__(self, rowid, size, local, upto, done):
        self.rowid, self.size, self.record, self.upto, self.done = rowid, size, bytearray(local), upto, done

    def columns(self):
        """The record's layout once enough of it is read for the columns wanted, else None."""
        cols = layout(self.record)
        if cols is not None and len(self.record) >= min(self.size, end_of(cols, self.upto)):
            return cols
        return None


class Dump:
    """A gzipped SQLite file, read only as a stream."""

    def __init__(self, path, log=lambda msg: None, window=WINDOW):
        self.path, self.log, self.window = path, log, window
        self.h = header(path)
        self.passes = self.pages_read = 0
        self.leaves = {}  # leaf page → pick(rowid): (last column wanted, done(rowid, values)) or None
        self.chains = {}  # overflow page → the Row it continues

    def interiors(self):
        """Pass 1: every table's interior pages."""
        self.passes += 1
        size, found, start = self.h.page_size, {}, time.time()
        with unpacked(self.path) as s:
            for first, view in chunks(s, size):
                if first == 1 and view[100] == TABLE_INTERIOR:  # page 1's own header comes after the file's
                    found[1] = bytes(view[:size])
                kinds = view[::size].tobytes()  # each page's first byte, sliced in C: Python per page is too slow
                k = kinds.find(TABLE_INTERIOR, 1 if first == 1 else 0)
                while k >= 0:
                    found[first + k] = bytes(view[k * size:(k + 1) * size])
                    k = kinds.find(TABLE_INTERIOR, k + 1)
        self.pages_read += self.h.page_count
        self.log(f"pass 1: {len(found):,} interior pages of {self.h.page_count:,} in {time.time() - start:.0f} s")
        return Trees(found)

    def scan(self, wanted):
        """Reads the wanted pages, and whatever they lead to, in as few passes as it takes. A page behind the
        stream is read from the recent pages kept in memory if it's there, else on the next pass."""
        size, pending = self.h.page_size, set(wanted)
        while pending:
            self.passes += 1
            last, start, n = max(pending), time.time(), 0
            recent, firsts = collections.deque(), collections.deque()

            def kept(m):
                if (n - m) * size > self.window:
                    return None
                i = bisect.bisect_right(firsts, m) - 1
                if i >= 0 and m < firsts[i] + len(recent[i]) // size:
                    return recent[i][(m - firsts[i]) * size:(m - firsts[i] + 1) * size]
                return None

            with unpacked(self.path) as s:
                for first, view in chunks(s, size):
                    recent.append(view)
                    firsts.append(first)
                    while (first - firsts[0] - len(recent[0]) // size) * size > self.window:
                        recent.popleft()  # all of it more than a window behind
                        firsts.popleft()
                    for n in range(first, first + len(view) // size):
                        if n not in pending:
                            continue
                        pending.discard(n)
                        todo = self.read(n, view[(n - first) * size:(n - first + 1) * size])
                        while todo:
                            m = todo.pop()
                            if m < n and (page := kept(m)) is not None:
                                pending.discard(m)
                                todo.extend(self.read(m, page))
                            else:
                                pending.add(m)
                                last = max(last, m)
                    if n >= last:
                        break
            self.pages_read += n
            self.log(f"pass {self.passes}: read {n:,} pages in {time.time() - start:.0f} s, {len(pending):,} pages left behind")

    def read(self, n, page):
        """Handles one wanted page; returns the pages it leads to."""
        more = []
        if (row := self.chains.pop(n, None)) is not None:
            row.record += page[4:4 + min(self.h.usable - 4, row.size - len(row.record))]
            self.follow(row, int.from_bytes(page[:4], "big"), more)
        elif (pick := self.leaves.get(n)) is not None:
            for rowid, size, local, first in leaf_cells(page, n, self.h.usable):
                if (wanted := pick(rowid)) is not None:
                    self.follow(Row(rowid, size, local, *wanted), first, more)
        return more

    def follow(self, row, nxt, more):
        if (cols := row.columns()) is not None:
            more.extend(row.done(row.rowid, decode(row.record, cols, row.upto)) or ())
        elif nxt:
            self.chains[nxt] = row
            more.append(nxt)
        else:
            raise ValueError(f"row {row.rowid}: overflow chain ends early")

    def rows(self, root, trees, upto=None):
        """(rowid, values) of every row of the table rooted at [root]."""
        found = []
        for leaf in trees.leaves(root):
            self.leaves[leaf] = lambda rowid: (upto, lambda r, v: found.append((r, v)))
        self.scan(trees.leaves(root))
        return sorted(found)


@dataclass
class Table:
    root: int
    columns: list


def column_names(sql):
    """Column names from a CREATE TABLE statement, in order (ALTER TABLE ADD COLUMN appends to it)."""
    body = sql[sql.index("(") + 1:sql.rindex(")")]
    parts, depth, cur = [], 0, ""
    for ch in body:
        depth += (ch == "(") - (ch == ")")
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    names = [re.split(r"[\s(]", p.strip(), maxsplit=1)[0].strip("\"'`[]") for p in [*parts, cur] if p.strip()]
    return [n for n in names if n.upper() not in ("UNIQUE", "FOREIGN", "PRIMARY", "CHECK", "CONSTRAINT")]


def schema(dump, trees):
    """The dump's tables: name → Table(root page, columns)."""
    return {name: Table(root, column_names(sql))
            for _, (kind, name, _, root, sql) in dump.rows(1, trees) if kind == "table" and sql and "(" in sql}


def table_rows(path, name):
    """Every row of one table: (rowid, values). For tests and small dumps; extract() is the way for LRCLIB's."""
    dump = Dump(path)
    trees = dump.interiors()
    return dump.rows(schema(dump, trees)[name].root, trees)


# --- The extract -----------------------------------------------------------

def create(out):
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.unlink(missing_ok=True)
    db = sqlite3.connect(out)
    db.executescript("""
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
        CREATE TABLE artists (key TEXT PRIMARY KEY, name TEXT);
        CREATE TABLE tracks (id INTEGER PRIMARY KEY, name TEXT, artist_name TEXT, album_name TEXT, duration REAL,
                             lyrics_id INTEGER, name_key TEXT);
        CREATE TABLE lyrics (id INTEGER PRIMARY KEY, track_id INTEGER, plain TEXT, synced TEXT, instrumental INTEGER);
    """)
    return db


def finish(db, artists, **meta):
    """Records which artists the extract covers, and indexes it for lookups."""
    db.executemany("INSERT OR IGNORE INTO artists VALUES (?, ?)", [(k, a) for a in artists for k in artist_keys(a)])
    db.executemany("INSERT OR REPLACE INTO meta VALUES (?, ?)", [("schema", SCHEMA), *((k, str(v)) for k, v in meta.items())])
    db.create_function("title_key", 1, title_key)
    db.execute("UPDATE tracks SET name_key = title_key(name) WHERE name_key IS NULL")
    db.execute("CREATE INDEX tracks_name ON tracks (name_key)")
    db.commit()
    db.close()


def extract(path, artists, out, log=lambda msg: None, window=WINDOW):
    """Every track by [artists] in the dump at [path], with its latest lyrics, into a small SQLite file at [out]."""
    start = time.time()
    dump = Dump(path, log, window)
    trees = dump.interiors()
    tables = schema(dump, trees)
    t, ly = tables["tracks"], tables["lyrics"]
    ti, li = [t.columns.index(c) for c in TRACK_COLUMNS], [ly.columns.index(c) for c in LYRICS_COLUMNS]
    mine = frozenset().union(*(artist_keys(a) for a in artists))
    db = create(out)
    lyric_ids = {}  # lyrics leaf page → the ids wanted on it
    stats = {"tracks": 0, "lyrics": 0}

    def track(rowid, values):
        name, artist, album, duration, lyrics_id = (values[i] for i in ti)
        if not artist_keys(artist or "") & mine:
            return ()
        db.execute("INSERT INTO tracks (id, name, artist_name, album_name, duration, lyrics_id) VALUES (?, ?, ?, ?, ?, ?)",
                   (rowid, name, artist, album, duration, lyrics_id))
        stats["tracks"] += 1
        if lyrics_id is None:
            return ()
        leaf = trees.leaf_for(ly.root, lyrics_id)
        lyric_ids.setdefault(leaf, set()).add(lyrics_id)
        dump.leaves.setdefault(leaf, functools.partial(pick_lyrics, leaf))
        return (leaf,)

    def pick_lyrics(leaf, rowid):
        ids = lyric_ids.get(leaf, ())
        if rowid in ids:
            ids.discard(rowid)
            return max(li), lyrics

    def lyrics(rowid, values):
        plain, synced, track_id, instrumental = (values[i] for i in li)
        db.execute("INSERT INTO lyrics VALUES (?, ?, ?, ?, ?)", (rowid, track_id, plain, synced, instrumental))
        stats["lyrics"] += 1

    for leaf in (leaves := trees.leaves(t.root)):
        dump.leaves[leaf] = lambda rowid: (max(ti), track)
    dump.scan(leaves)
    stats.update(passes=dump.passes, pages_read=dump.pages_read, seconds=round(time.time() - start))
    finish(db, artists, dump=Path(path).name, **stats)
    return stats


class Mine:
    """Looks songs up in an extract, answering as lyrics_fetch.lookup does (LRCLIB's API)."""

    def __init__(self, path):
        self.db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)

    def lookup(self, song):
        rows = self.db.execute("""
            SELECT t.id, t.name, t.artist_name, t.album_name, t.duration, l.plain, l.synced, l.instrumental
            FROM tracks t JOIN lyrics l ON l.id = t.lyrics_id WHERE t.name_key = ?""", (title_key(song["title"]),))
        artist = artist_keys(song["artist"])
        close = [r for r in rows if r[4] is not None and artist_keys(r[2] or "") & artist
                 and abs(r[4] - song["duration"]) <= lyrics_fetch.TOLERANCE]
        if not close:
            return {"status": "missing"}
        # As LRCLIB: the same album first (its /api/get), then any close version, timed lyrics first (its search).
        album = title_key(song["album"] or "")
        r = min(close, key=lambda r: (title_key(r[3] or "") != album, not r[6], abs(r[4] - song["duration"])))
        return {"status": "found", "id": r[0], "trackName": r[1], "artistName": r[2], "albumName": r[3], "duration": r[4],
                "instrumental": bool(r[7]), "plainLyrics": r[5], "syncedLyrics": r[6]}


def collection_artists(inventory):
    """Every artist credited in the collection, as song or album artist."""
    names = set()
    for line in Path(inventory).read_text().splitlines():
        tags = json.loads(line).get("tags") or {}
        names.update(n for n in (tags.get("artist"), tags.get("albumartist")) if n)
    return sorted(names)


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("dump", type=Path)
    p.add_argument("--out", type=Path, default=OUT)
    a = p.parse_args()
    artists = collection_artists(lyrics_fetch.INVENTORY)
    h = header(a.dump)
    print(f"{a.dump.name}: {h.page_count:,} pages of {h.page_size} bytes ({h.page_count * h.page_size / 1e9:.1f} GB unpacked); "
          f"{len(artists)} artists", flush=True)
    stats = extract(a.dump, artists, a.out, log=lambda msg: print(msg, flush=True))
    print(f"Done: {json.dumps(stats)} → {a.out}")


if __name__ == "__main__":
    sys.exit(main())
