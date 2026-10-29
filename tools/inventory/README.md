# Collection inventory (roadmap C0)

Read-only scan of the music collection: tags, audio properties, artwork,
duplicates and problems. It never modifies the collection.

```sh
python3 -m venv tools/.venv
tools/.venv/bin/pip install -r tools/inventory/requirements.txt
tools/.venv/bin/python -I tools/inventory/inventory.py ~/Music tools/inventory/out
```

Outputs (git-ignored):
- `out/inventory.jsonl`: one JSON object per file.
- `out/report.html`: statistics and problem lists.
