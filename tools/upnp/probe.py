#!/usr/bin/env python3
"""Play one track on a UPnP renderer and log what it does (roadmap B0).

Serves a single selected file over HTTP (nothing else is reachable),
logs every request the renderer makes (method, Range and other headers),
and drives AVTransport: SetAVTransportURI, Play, position polling, Seek,
Pause, Stop. Volume is set low first and restored at the end.

With --next, it queues a second file with SetNextAVTransportURI, seeks
near the end of the first and checks that the renderer moves on by itself.

Each step is also checked; the run ends with PASS or FAIL (exit code 1),
so it works as a smoke test of a real speaker or TV (roadmap T6).

Usage: probe.py RENDERER FILE [--next FILE] [--volume N] [--listen SECONDS]
RENDERER is one of the names in RENDERERS below.
"""

import argparse
import html
import io
import mimetypes
import os
import re
import socket
import sys
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

RENDERERS = {
    "estudio": ("192.168.1.129", 1400, "/MediaRenderer/AVTransport/Control",
                "/MediaRenderer/RenderingControl/Control"),
    "cocina": ("192.168.1.151", 1400, "/MediaRenderer/AVTransport/Control",
               "/MediaRenderer/RenderingControl/Control"),
    "tv": ("192.168.1.128", 9197, "/upnp/control/AVTransport1",
           "/upnp/control/RenderingControl1"),
}
AVT = "urn:schemas-upnp-org:service:AVTransport:1"
RC = "urn:schemas-upnp-org:service:RenderingControl:1"

# DLNA: OP=01 says byte-range seeking works; DLNA renderers (the TV) won't
# seek without it. The flags mark a streaming transfer with background
# transfer allowed, as most DLNA servers send.
DLNA_FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

start = time.monotonic()
failures = []


def check(what, ok, detail=""):
    """Records one expectation about the renderer; failures fail the run."""
    log(f"{'ok  ' if ok else 'FAIL'} {what}{f' ({detail})' if detail else ''}")
    if not ok:
        failures.append(what)


def seconds(t):
    """'0:01:30' or '0:00:02.035' → seconds; None if unknown."""
    try:
        h, m, s = t.split(":")
        return int(h) * 3600 + int(m) * 60 + float(s)
    except (AttributeError, ValueError):
        return None


def describe(path):
    """Title, artist, album, H:MM:SS duration and embedded cover (bytes, mime).

    Falls back to the file name alone without mutagen."""
    info = {"title": os.path.splitext(os.path.basename(path))[0]}
    try:
        import mutagen
    except ImportError:
        return info
    f = mutagen.File(path, easy=True)
    seconds = int(f.info.length)
    info["duration"] = f"{seconds // 3600}:{seconds // 60 % 60:02}:{seconds % 60:02}"
    for key in ("title", "artist", "album"):
        if f.get(key):
            info[key] = f[key][0]
    pictures = [t for t in (mutagen.File(path).tags or {}).values() if getattr(t, "FrameID", "") == "APIC"]
    if pictures:
        info["cover"] = (pictures[0].data, pictures[0].mime or "image/jpeg")
    return info


def log(msg):
    print(f"[{time.monotonic() - start:6.1f}s] {msg}", flush=True)


def make_handler(files):
    """files: {url_path: (path, mime)}. Nothing else is served."""

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args):
            pass

        def do_HEAD(self):
            self.serve(body=False)

        def do_GET(self):
            self.serve(body=True)

        def serve(self, body):
            interesting = {k: v for k, v in self.headers.items()
                           if k.lower() not in ("host", "connection")}
            log(f"HTTP {self.command} {self.path} from {self.client_address[0]} {interesting}")
            if self.path not in files:
                self.send_error(404)
                return
            path, mime = files[self.path]
            size = len(path) if isinstance(path, bytes) else os.path.getsize(path)
            first, last = 0, size - 1
            m = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
            if m:
                if m[1]:
                    first = int(m[1])
                    last = int(m[2]) if m[2] else size - 1
                else:  # suffix range: last N bytes
                    first = size - int(m[2])
                last = min(last, size - 1)
                self.send_response(206)
                self.send_header("Content-Range", f"bytes {first}-{last}/{size}")
            else:
                self.send_response(200)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(last - first + 1))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("transferMode.dlna.org", "Streaming")
            if "getcontentFeatures.dlna.org" in self.headers:
                self.send_header("contentFeatures.dlna.org", DLNA_FEATURES)
            self.end_headers()
            if not body:
                return
            sent = 0
            try:
                with (io.BytesIO(path) if isinstance(path, bytes) else open(path, "rb")) as f:
                    f.seek(first)
                    remaining = last - first + 1
                    while remaining:
                        chunk = f.read(min(65536, remaining))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        sent += len(chunk)
                        remaining -= len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass
            log(f"HTTP sent {sent} of {last - first + 1} bytes ({first}-{last})")

    return Handler


def soap(host, port, control, service, action, args=()):
    body = "".join(f"<{k}>{html.escape(str(v))}</{k}>" for k, v in args)
    envelope = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
        's:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>'
        f'<u:{action} xmlns:u="{service}">{body}</u:{action}>'
        "</s:Body></s:Envelope>"
    ).encode()
    req = urllib.request.Request(
        f"http://{host}:{port}{control}", data=envelope, method="POST",
        headers={"Content-Type": 'text/xml; charset="utf-8"',
                 "SOAPACTION": f'"{service}#{action}"'})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            text = r.read().decode()
            ok = True
    except urllib.error.HTTPError as e:
        text, ok = e.read().decode(errors="replace"), False
    fields = dict(re.findall(r"<(\w+)>([^<]*)</\1>", text))
    if not ok:
        log(f"SOAP {action} FAILED: {fields.get('errorCode')} {fields.get('errorDescription', '')}")
    return ok, fields


def didl(url, mime, info, art_url=None):
    item = (
        '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" '
        'xmlns:dc="http://purl.org/dc/elements/1.1/" '
        'xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">'
        '<item id="1" parentID="0" restricted="1">'
        f"<dc:title>{html.escape(info['title'])}</dc:title>"
        + "".join(f"<{tag}>{html.escape(info[key])}</{tag}>"
                  for key, tag in (("artist", "dc:creator"), ("artist", "upnp:artist"),
                                   ("album", "upnp:album")) if key in info)
        + (f"<upnp:albumArtURI>{html.escape(art_url)}</upnp:albumArtURI>" if art_url else "")
        + "<upnp:class>object.item.audioItem.musicTrack</upnp:class>"
        f'<res protocolInfo="http-get:*:{mime}:{DLNA_FEATURES}"'
        + (f' duration="{info["duration"]}.000"' if "duration" in info else "")
        + f">{html.escape(url)}</res>"
        "</item></DIDL-Lite>"
    )
    return item


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("renderer", choices=RENDERERS)
    p.add_argument("file")
    p.add_argument("--next", help="second file, queued with SetNextAVTransportURI")
    p.add_argument("--volume", type=int, default=12)
    p.add_argument("--listen", type=int, default=20, help="seconds to play before seeking")
    a = p.parse_args()

    host, port, avt, rc = RENDERERS[a.renderer]
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    probe.connect((host, port))
    my_ip = probe.getsockname()[0]
    files = {}
    infos = {}
    for name, path in (("track", a.file), ("next", a.next)):
        if path:
            mime = mimetypes.guess_type(path)[0] or "audio/mpeg"
            files[f"/{name}" + os.path.splitext(path)[1].lower()] = (path, mime)
            infos[name] = describe(path)
            if "cover" in infos[name]:
                files[f"/{name}-cover"] = infos[name]["cover"]
    server = ThreadingHTTPServer((my_ip, 0), make_handler(files))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    urls = {}
    for url_path, (path, mime) in files.items():
        urls[url_path] = f"http://{my_ip}:{server.server_port}{url_path}"
        what = f"cover, {len(path)} bytes" if isinstance(path, bytes) else os.path.basename(path)
        log(f"Serving {what} as {urls[url_path]} ({mime})")
    audio = [u for p, u in urls.items() if not p.endswith("-cover")]
    url, next_url = audio + [None] * (2 - len(audio))
    mime = files[next(iter(files))][1]
    art = {n: urls.get(f"/{n}-cover") for n in infos}

    def avt_call(action, *args):
        return soap(host, port, avt, AVT, action, (("InstanceID", 0),) + args)

    def status():
        _, t = avt_call("GetTransportInfo")
        _, pos = avt_call("GetPositionInfo")
        log(f"state={t.get('CurrentTransportState')} status={t.get('CurrentTransportStatus')} "
            f"pos={pos.get('RelTime')} / {pos.get('TrackDuration')} uri=…{pos.get('TrackURI', '')[-10:]}")
        return t.get("CurrentTransportState"), pos

    _, v = soap(host, port, rc, RC, "GetVolume", (("InstanceID", 0), ("Channel", "Master")))
    old_volume = v.get("CurrentVolume")
    log(f"Volume was {old_volume}, setting {a.volume}")
    soap(host, port, rc, RC, "SetVolume",
         (("InstanceID", 0), ("Channel", "Master"), ("DesiredVolume", a.volume)))
    try:
        ok, _ = avt_call("SetAVTransportURI", ("CurrentURI", url),
                         ("CurrentURIMetaData", didl(url, mime, infos["track"], art["track"])))
        check("SetAVTransportURI accepted", ok)
        ok, _ = avt_call("Play", ("Speed", 1))
        check("Play accepted", ok)
        # The TV can take several seconds to start (TRANSITIONING); measure from there.
        asked = time.monotonic()
        state, pos = status()
        while state != "PLAYING" and time.monotonic() - asked < 15:
            time.sleep(1)
            state, pos = status()
        check("starts playing", state == "PLAYING", f"after {time.monotonic() - asked:.1f} s")
        positions = []
        for _ in range(a.listen // 2):
            time.sleep(2)
            state, pos = status()
            positions.append(seconds(pos.get("RelTime")))
        check("keeps playing", state == "PLAYING", state)
        heard = [p for p in positions if p is not None]
        check("position advances", len(heard) > 1 and heard[-1] - heard[0] >= a.listen / 2,
              f"{heard[0] if heard else '?'} → {heard[-1] if heard else '?'} s")
        if next_url:
            next_mime = files["/next" + os.path.splitext(a.next)[1].lower()][1]
            ok, _ = avt_call("SetNextAVTransportURI", ("NextURI", next_url),
                             ("NextURIMetaData", didl(next_url, next_mime, infos["next"], art["next"])))
            check("SetNextAVTransportURI accepted", ok)
            _, m = avt_call("GetMediaInfo")
            check("next track reported back", m.get("NextURI") == next_url, m.get("NextURI") or "empty")
            reported = pos.get("TrackDuration", "0:00:00")
            known = reported if reported != "0:00:00" else infos["track"].get("duration", reported)
            h, mnt, sec = (int(float(x)) for x in known.split(":"))
            end = max(0, h * 3600 + mnt * 60 + sec - 8)
            target = f"{end // 3600}:{end // 60 % 60:02}:{end % 60:02}"
            ok, _ = avt_call("Seek", ("Unit", "REL_TIME"), ("Target", target))
            check(f"Seek to {target} (8 s before the end) accepted", ok)
            moved_on = False
            for _ in range(10):
                time.sleep(2)
                _, pos = status()
                moved_on = moved_on or pos.get("TrackURI") == next_url
            check("moves to the next track by itself", moved_on)
            return
        ok, _ = avt_call("Seek", ("Unit", "REL_TIME"), ("Target", "0:01:30"))
        check("Seek to 1:30 accepted", ok)
        time.sleep(2)
        _, pos = status()
        landed = seconds(pos.get("RelTime"))
        check("seek lands near 1:30", landed is not None and 89 <= landed <= 96, f"{landed} s")
        ok, _ = avt_call("Pause")
        time.sleep(3)
        state, _ = status()
        check("pauses", ok and state == "PAUSED_PLAYBACK", state)
        ok, _ = avt_call("Play", ("Speed", 1))
        time.sleep(4)
        state, _ = status()
        check("resumes", ok and state == "PLAYING", state)
    finally:
        ok, _ = avt_call("Stop")
        time.sleep(1)
        state, _ = status()
        check("stops", ok and state == "STOPPED", state)
        if old_volume is not None:
            soap(host, port, rc, RC, "SetVolume",
                 (("InstanceID", 0), ("Channel", "Master"), ("DesiredVolume", old_volume)))
            _, v = soap(host, port, rc, RC, "GetVolume", (("InstanceID", 0), ("Channel", "Master")))
            check("volume restored", v.get("CurrentVolume") == old_volume, f"{v.get('CurrentVolume')}, was {old_volume}")
        server.shutdown()
        log(f"FAIL: {', '.join(failures)}" if failures else "PASS")
        sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
