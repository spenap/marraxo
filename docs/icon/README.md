# App icon

A mako's dorsal fin, outlined, with sound waves inside (decision log,
2026-11-11). `build.py` draws it and writes every file from the same
geometry:

- `launcher.svg`: the launcher icon on a 512 grid. Three waves, arcs of the
  circle through the fin's leading edge (base corner, middle, tip), stepped
  inward and kept a stroke and a gap clear of the outline.
- `status-bar.svg`: the status bar and notification icon, with a heavier
  stroke and one wave, a copy of the leading edge scaled toward the base's
  rear corner.
- `android/app/src/main/res/drawable/`: `ic_launcher_foreground.xml` and
  `ic_launcher_monochrome.xml` (108 dp, art inside the 66 dp safe zone) and
  `ic_notification.xml` (24 dp).

Change the geometry in `build.py`, not the drawables, then run
`python3 docs/icon/build.py`. Colours: Glow's teal `#9FD6CF` on deep teal
`#24525A` (`glow_tint`).
