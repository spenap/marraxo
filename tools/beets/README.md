# Collection cleanup with beets (roadmap C1–C2)

[beets](https://beets.io) matches albums against MusicBrainz (plus AcoustID
fingerprints), normalises tags, fetches and embeds artwork, and renames.
It **copies** into `~/Music-clean`; the source collection is
never modified.

```sh
tools/.venv/bin/pip install -r tools/beets/requirements.txt
tools/.venv/bin/pip uninstall -y audioread   # see below
sudo apt install libchromaprint-tools        # fpcalc
tools/beets/beet import -q ~/Music   # automatic pass
tools/beets/beet import /path/to/album              # interactive review
```

- `beet` wraps beets with `config.yaml` and keeps its state in `out/`
  (library database, import log), which is git-ignored.
- The automatic pass (`-q`) accepts matches of about 92% or better and
  skips the rest; skipped albums are listed in `out/import.log`.
- `audioread` must not be installed: it needs `audioop`, which Python 3.13
  removed, so fingerprinting fails. Without it, pyacoustid uses `fpcalc`.
