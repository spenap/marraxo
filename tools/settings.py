"""Settings that differ per computer, such as where the music collection is.

Set them in the environment, or in tools/local.env (not committed; see
local.env.example), one NAME=value per line.
"""
import os
from pathlib import Path

LOCAL = Path(__file__).resolve().parent / "local.env"


def _get(name, local):
    if os.environ.get(name):
        return os.environ[name]
    if local.exists():
        for line in local.read_text().splitlines():
            key, sep, value = line.partition("=")
            if sep and key.strip() == name:
                return value.strip()
    raise SystemExit(f"Set {name} in the environment or in {local} (see local.env.example)")


def music(local=LOCAL):
    """The music collection: the folder the tools read and beets copies into."""
    return Path(_get("MARRAXO_MUSIC", local))
