#!/usr/bin/env python3
"""Play one track on a UPnP renderer and log what it does (roadmap B0).

Serves a single selected file over HTTP (nothing else is reachable),
logs every request the renderer makes (method, Range and other headers),
and drives AVTransport: SetAVTransportURI, Play, position polling, Seek,
Pause, Stop. Volume is set low first and restored at the end.

Usage: probe.py RENDERER FILE [--volume N] [--listen SECONDS]
RENDERER is one of the names in RENDERERS below.
"""

import argparse
import html
import mimetypes
import os
import re
import socket
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

start = time.monotonic()


def log(msg):
    print(f"[{time.monotonic() - start:6.1f}s] {msg}", flush=True)


def make_handler(path, url_path, mime):
    size = os.path.getsize(path)

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
            if self.path != url_path:
                self.send_error(404)
                return
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
            self.end_headers()
            if not body:
                return
            sent = 0
            try:
                with open(path, "rb") as f:
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


def didl(url, mime, title):
    item = (
        '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" '
        'xmlns:dc="http://purl.org/dc/elements/1.1/" '
        'xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">'
        '<item id="1" parentID="0" restricted="1">'
        f"<dc:title>{html.escape(title)}</dc:title>"
        "<upnp:class>object.item.audioItem.musicTrack</upnp:class>"
        f'<res protocolInfo="http-get:*:{mime}:*">{html.escape(url)}</res>'
        "</item></DIDL-Lite>"
    )
    return item


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("renderer", choices=RENDERERS)
    p.add_argument("file")
    p.add_argument("--volume", type=int, default=12)
    p.add_argument("--listen", type=int, default=20, help="seconds to play before seeking")
    a = p.parse_args()

    host, port, avt, rc = RENDERERS[a.renderer]
    mime = mimetypes.guess_type(a.file)[0] or "audio/mpeg"
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    probe.connect((host, port))
    my_ip = probe.getsockname()[0]
    url_path = "/track" + os.path.splitext(a.file)[1].lower()
    server = ThreadingHTTPServer((my_ip, 0), make_handler(a.file, url_path, mime))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f"http://{my_ip}:{server.server_port}{url_path}"
    log(f"Serving {os.path.basename(a.file)} as {url} ({mime})")

    def avt_call(action, *args):
        return soap(host, port, avt, AVT, action, (("InstanceID", 0),) + args)

    def status():
        _, t = avt_call("GetTransportInfo")
        _, pos = avt_call("GetPositionInfo")
        log(f"state={t.get('CurrentTransportState')} status={t.get('CurrentTransportStatus')} "
            f"pos={pos.get('RelTime')} / {pos.get('TrackDuration')}")
        return t.get("CurrentTransportState")

    _, v = soap(host, port, rc, RC, "GetVolume", (("InstanceID", 0), ("Channel", "Master")))
    old_volume = v.get("CurrentVolume")
    log(f"Volume was {old_volume}, setting {a.volume}")
    soap(host, port, rc, RC, "SetVolume",
         (("InstanceID", 0), ("Channel", "Master"), ("DesiredVolume", a.volume)))
    try:
        title = os.path.splitext(os.path.basename(a.file))[0]
        ok, _ = avt_call("SetAVTransportURI", ("CurrentURI", url),
                         ("CurrentURIMetaData", didl(url, mime, title)))
        log(f"SetAVTransportURI ok={ok}")
        ok, _ = avt_call("Play", ("Speed", 1))
        log(f"Play ok={ok}")
        for _ in range(a.listen // 2):
            time.sleep(2)
            status()
        ok, _ = avt_call("Seek", ("Unit", "REL_TIME"), ("Target", "0:01:30"))
        log(f"Seek to 1:30 ok={ok}")
        for _ in range(4):
            time.sleep(2)
            status()
        ok, _ = avt_call("Pause")
        log(f"Pause ok={ok}")
        time.sleep(3)
        status()
        ok, _ = avt_call("Play", ("Speed", 1))
        log(f"Resume ok={ok}")
        time.sleep(4)
        status()
    finally:
        ok, _ = avt_call("Stop")
        log(f"Stop ok={ok}")
        time.sleep(1)
        status()
        if old_volume is not None:
            soap(host, port, rc, RC, "SetVolume",
                 (("InstanceID", 0), ("Channel", "Master"), ("DesiredVolume", old_volume)))
            log(f"Volume restored to {old_volume}")
        server.shutdown()


if __name__ == "__main__":
    main()
