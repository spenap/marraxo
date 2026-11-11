#!/usr/bin/env python3
"""Marraxo's icon: a mako's dorsal fin, outlined, with sound waves inside.

Writes the editable SVGs (512 grid) next to this file and the Android
drawables they become: the launcher foreground and monochrome layers
(108 dp, art centred in the safe zone) and the status bar icon (24 dp).
Usage: python3 docs/icon/build.py
"""
import math
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "res")
f = lambda v: f"{v:.1f}".rstrip("0").rstrip(".")

# ---- the fin (512 grid) --------------------------------------------------------
A = (108, 410); B = (352, 410)                                   # base corners
LEAD = [A, (124, 296), (206, 126), (372, 92)]                    # convex leading edge
HOOK = [(372, 92), (390, 86), (386, 102)]                        # rounded tip (quadratic)
TRAIL = [(386, 102), (344, 200), (318, 334), B]                  # concave trailing edge
OUTLINE = (f"M{f(A[0])} {f(A[1])}C{' '.join(f'{f(x)} {f(y)}' for x, y in LEAD[1:])}"
           f"Q{' '.join(f'{f(x)} {f(y)}' for x, y in HOOK[1:])}"
           f"C{' '.join(f'{f(x)} {f(y)}' for x, y in TRAIL[1:])}Z")
EXTENT = (108, 390, 86, 410)                                     # the outline's x0, x1, y0, y1


def cub(P, t):
    u = 1 - t
    return (u**3*P[0][0] + 3*u*u*t*P[1][0] + 3*u*t*t*P[2][0] + t**3*P[3][0],
            u**3*P[0][1] + 3*u*u*t*P[1][1] + 3*u*t*t*P[2][1] + t**3*P[3][1])


def quad(P, t):
    u = 1 - t
    return (u*u*P[0][0] + 2*u*t*P[1][0] + t*t*P[2][0], u*u*P[0][1] + 2*u*t*P[1][1] + t*t*P[2][1])


POLY = ([cub(LEAD, i/80) for i in range(81)] + [quad(HOOK, i/20) for i in range(1, 21)]
        + [cub(TRAIL, i/80) for i in range(1, 81)] + [(A[0] + (B[0]-A[0])*i/40, A[1]) for i in range(39, 0, -1)])


def inside(p):
    x, y = p; c = False
    for (x1, y1), (x2, y2) in zip(POLY, POLY[1:] + POLY[:1]):
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            c = not c
    return c


def dist(p):
    best = 1e9
    for (x1, y1), (x2, y2) in zip(POLY, POLY[1:] + POLY[:1]):
        dx, dy = x2 - x1, y2 - y1; L = dx*dx + dy*dy or 1
        t = max(0, min(1, ((p[0]-x1)*dx + (p[1]-y1)*dy) / L))
        best = min(best, math.hypot(p[0] - x1 - t*dx, p[1] - y1 - t*dy))
    return best


def span(fn, clear, n=400):
    """The longest run of parameters in [0, 1] whose points keep `clear` from the outline."""
    good = [inside(fn(i/n)) and dist(fn(i/n)) >= clear for i in range(n + 1)]
    best, cur = None, None
    for i, g in enumerate(good + [False]):
        if g and cur is None:
            cur = i
        if not g and cur is not None:
            if best is None or i - 1 - cur > best[1] - best[0]:
                best = (cur, i - 1)
            cur = None
    return None if best is None else (best[0]/n, best[1]/n)


def split(P, t0, t1):
    """The piece of cubic P between t0 and t1, as one cubic (de Casteljau)."""
    def lerp(a, b, t): return (a[0] + (b[0]-a[0])*t, a[1] + (b[1]-a[1])*t)

    def at(P, t):
        a, b, c = lerp(P[0], P[1], t), lerp(P[1], P[2], t), lerp(P[2], P[3], t)
        d, e = lerp(a, b, t), lerp(b, c, t); g = lerp(d, e, t)
        return [P[0], a, d, g], [g, e, c, P[3]]
    _, right = at(P, t0)
    left, _ = at(right, (t1 - t0) / (1 - t0) if t0 < 1 else 0)
    return left


def circle(p1, p2, p3):
    ax, ay = p1; bx, by = p2; cx, cy = p3
    d = 2 * (ax*(by-cy) + bx*(cy-ay) + cx*(ay-by))
    ux = ((ax*ax+ay*ay)*(by-cy) + (bx*bx+by*by)*(cy-ay) + (cx*cx+cy*cy)*(ay-by)) / d
    uy = ((ax*ax+ay*ay)*(cx-bx) + (bx*bx+by*by)*(ax-cx) + (cx*cx+cy*cy)*(bx-ax)) / d
    return (ux, uy), math.hypot(ax-ux, ay-uy)


# ---- the launcher icon: arcs concentric with the leading edge's circle ---------
# The circle through the base corner, the middle of the leading edge and the
# tip; each wave is an arc of it, stepped inward, kept a stroke and a gap
# clear of the outline.
C0, R0 = circle(A, cub(LEAD, .5), LEAD[3])


def arc(r, clear):
    a0 = math.atan2(A[1] - C0[1], A[0] - C0[0]); a1 = math.atan2(LEAD[3][1] - C0[1], LEAD[3][0] - C0[0])
    lo, hi = min(a0, a1) - .4, max(a0, a1) + .4
    fn = lambda t: (C0[0] + r*math.cos(lo + (hi-lo)*t), C0[1] + r*math.sin(lo + (hi-lo)*t))
    s = span(fn, clear)
    if not s or s[1] - s[0] < .03:
        return ""
    p, q = fn(s[0]), fn(s[1])
    return f"M{f(p[0])} {f(p[1])}A{f(r)} {f(r)} 0 0 1 {f(q[0])} {f(q[1])}"


STROKE, GAP = 20, 20
LAUNCHER = [OUTLINE] + [w for w in (arc(R0 - (STROKE + GAP)*k, STROKE + GAP) for k in (1, 2, 3)) if w]

# ---- the status bar icon: heavier, one wave ------------------------------------
# At 24 dp only one wave fits: a copy of the leading edge, scaled toward the
# base's rear corner.
SMALL_STROKE, SMALL_GAP = 34, 30


def scaled_edge(k, clear):
    P = [(B[0] + (x - B[0])*k, B[1] + (y - B[1])*k) for x, y in LEAD]
    s = span(lambda t: cub(P, t), clear)
    Q = split(P, *s)
    return f"M{f(Q[0][0])} {f(Q[0][1])}C" + " ".join(f"{f(x)} {f(y)}" for x, y in Q[1:])


SMALL = [OUTLINE, scaled_edge(.60, SMALL_STROKE + SMALL_GAP)]


# ---- writers --------------------------------------------------------------------
def svg(paths, stroke, comment):
    body = "".join(f'\n    <path d="{d}"/>' for d in paths)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" color="#9FD6CF">\n'
            f'  <!-- {comment} -->\n'
            f'  <g fill="none" stroke="currentColor" stroke-width="{stroke}" stroke-linecap="round" stroke-linejoin="round">'
            f'{body}\n  </g>\n</svg>\n')


def to_dp(d, scale, centre):
    """A path from the 512 grid to an Android viewport: scaled about the art's middle, moved to `centre`."""
    x0, x1, y0, y1 = EXTENT; cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    g = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
    X = lambda x: g(centre + (x - cx) * scale); Y = lambda y: g(centre + (y - cy) * scale)
    out = []
    for cmd, args in re.findall(r"([MCQAZ])([^MCQAZ]*)", d):
        n = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", args)]
        if cmd == "A":
            rx, ry, rot, large, sweep, x, y = n
            out.append(f"A{g(rx*scale)},{g(ry*scale)} {g(rot)} {int(large)},{int(sweep)} {X(x)},{Y(y)}")
        elif cmd == "Z":
            out.append("Z")
        else:
            out.append(cmd + " ".join(f"{X(n[i])},{Y(n[i+1])}" for i in range(0, len(n), 2)))
    return "".join(out)


def vector(paths, comment, colour, size, scale, stroke):
    g = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
    body = "".join(f'''
    <path
        android:pathData="{to_dp(d, scale, size / 2)}"
        android:strokeColor="{colour}"
        android:strokeWidth="{g(stroke * scale)}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />''' for d in paths)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}">{body}
</vector>
'''


def write(path, text):
    with open(path, "w") as fh:
        fh.write(text)


if __name__ == "__main__":
    x0, x1, y0, y1 = EXTENT
    write(os.path.join(HERE, "launcher.svg"), svg(LAUNCHER, STROKE, "Launcher icon: the fin and three waves, concentric with its leading edge."))
    write(os.path.join(HERE, "status-bar.svg"), svg(SMALL, SMALL_STROKE, "Status bar: the fin, heavier, with one wave."))
    # launcher: 52 dp tall, inside the 66 dp safe zone with the stroke
    big = 52 / (y1 - y0)
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), vector(
        LAUNCHER, "A mako's dorsal fin, outlined, with sound waves inside: arcs concentric with the circle of its leading edge.",
        "#9FD6CF", 108, big, STROKE))
    write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), vector(
        LAUNCHER, "Themed icons: the foreground's strokes in one colour.", "#FFFFFFFF", 108, big, STROKE))
    # status bar: about 21 dp tall with the stroke, in a 24 dp viewport
    small = 21 / (y1 - y0 + SMALL_STROKE)
    write(os.path.join(RES, "drawable", "ic_notification.xml"), vector(
        SMALL, "Status bar and notifications: the launcher icon's fin, heavier, with one wave.", "#FFFFFFFF", 24, small, SMALL_STROKE))
    print("wrote launcher.svg, status-bar.svg and the three drawables")
