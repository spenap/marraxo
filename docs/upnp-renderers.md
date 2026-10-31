# UPnP renderers

Findings from testing against the real devices on the LAN (roadmap track
B0). Every playback target here is a UPnP media renderer with an
AVTransport service, so one controller implementation should cover all of
them, with per-device quirks noted below.

IPs come from DHCP and may change; always find devices by discovery.

## Discovery (works)
- SSDP `M-SEARCH` to `239.255.255.250:1900`.
  - `ST: urn:schemas-upnp-org:device:MediaRenderer:1` finds all three
    devices below.
  - `ST: urn:schemas-upnp-org:device:ZonePlayer:1` finds only Sonos.
- Responses include `LOCATION` (the device description URL). Sonos also
  sends `X-RINCON-HOUSEHOLD`.

## Sonos One SL × 2 (2026-10-29)

| Room | IP | Model | Firmware | Generation |
|---|---|---|---|---|
| Estudio | 192.168.1.129 | Sonos One SL (S38) | 18.6 (96.0-79160) | S2 |
| Cocina | 192.168.1.151 | Sonos One SL (S38) | 18.6 (96.0-79160) | S2 |

- Same household, but **two separate rooms**: each is its own zone group
  and coordinator. They are neither a stereo pair nor grouped right now.
- The generation shows up as `<swGen>2</swGen>` in the device description.
- Description at `http://<ip>:1400/xml/device_description.xml`. The
  friendly name is `<ip> - Sonos One SL - RINCON_…`; the room name is in
  `<roomName>`.
- Services still present on S2 18.6: AVTransport, RenderingControl,
  GroupRenderingControl, ZoneGroupTopology, ConnectionManager,
  ContentDirectory, Queue, DeviceProperties, GroupManagement.
  Control URLs live under `/MediaRenderer/…`.
- `ZoneGroupTopology#GetZoneGroupState` works (read-only SOAP POST to
  `/ZoneGroupTopology/Control`) and returns groups and coordinators.
- Playback commands go to the **group coordinator**, not to every speaker.
- `ConnectionManager#GetProtocolInfo` (Estudio, 2026-11-01; read-only, at
  `/MediaRenderer/ConnectionManager/Control`) lists these audio sinks:
  `audio/mpeg` (also `mp3`, `mpeg3`), `audio/mp4`, `audio/x-m4a`,
  `audio/flac`, `audio/ogg`, `audio/wav`, `audio/aiff`, `audio/wma`, and
  playlists (`audio/mpegurl`). No Opus or Musepack. Sonos documents
  `audio/ogg` as Ogg Vorbis.

## Samsung The Frame TV (2026-10-29)

| Name | IP | Model | Platform |
|---|---|---|---|
| [TV] The Frame | 192.168.1.128 | UE55LS003 | 2017 KantM (Tizen) |

- Generic DLNA renderer ("Samsung TV DMR"), server header
  `Samsung-Linux/4.1, UPnP/1.0, Samsung_UPnP_SDK/1.0`.
- Description at `http://192.168.1.128:9197/dmr`.
- Services: AVTransport (`/upnp/control/AVTransport1`), RenderingControl,
  ConnectionManager, plus a vendor StreamSplicing service.
- It answered discovery late at night, so the network stack stays up in
  standby or Art Mode.
- `ConnectionManager#GetProtocolInfo` lists 292 sink formats. Audio:
  `audio/mpeg`, `audio/mp4`, `audio/x-m4a`, `audio/x-flac`, `audio/ogg`,
  `audio/x-wav`, `audio/L16`, `audio/vnd.dlna.adts`, `audio/x-ms-wma`,
  among others. MP3 needs no transcoding.

## Playback test: Estudio (2026-10-30)

`tools/upnp/probe.py estudio <file>` served one MP3 (7.1 MB, 4:53,
Altan, *Daily Growing*) from the laptop and drove AVTransport. Everything
worked on the first try, with DIDL-Lite metadata and
`protocolInfo="http-get:*:audio/mpeg:*"`.

- `SetAVTransportURI`, `Play`, `Seek` (`REL_TIME`), `Pause`, `Stop`,
  `GetPositionInfo`, `GetTransportInfo` and `Get/SetVolume` all succeed.
  The position is reported to the second; the duration (0:04:53) comes
  from the file.
- On `SetAVTransportURI` the speaker probes the file before `Play`: a
  plain GET (abandoned after ~190 KB), then `Range: bytes=20695-` (the
  end of the ID3 tag) and `Range: bytes=10-`. **Byte ranges must work.**
- It then **downloads the whole file in ~2.6 s** and plays from its own
  buffer. A seek within the track made no new HTTP request.
  Consequence for the phone: a burst of a few MB per track, then idle;
  the HTTP server need not stay busy during playback, but the phone must
  stay reachable for the next track.
- Request headers: `User-Agent: Linux UPnP/1.0 Sonos/96.0-79160 (ZPS38)`,
  `X-Sonos-SWGen: 2`, `X-Sonos-Firmware`, `X-Sonos-Muse-Api: 1.53.1`.
  No `getcontentFeatures.dlna.org` request.
- No laptop firewall changes were needed for an ephemeral port.

## Playback test: The Frame TV (2026-10-30)

Same probe, `--next` to test queueing. Works, once the server speaks DLNA:

- **DLNA headers are required for seeking.** The TV sends
  `getcontentFeatures.dlna.org: 1` on every request. Without a
  `contentFeatures.dlna.org` answer containing `DLNA.ORG_OP=01` (byte
  ranges supported), `Seek` fails with `701 Transition not available` and
  the duration shows as 0:00:00. With the header, plus `duration` on the
  DIDL `<res>`, both work. Use the same flags in the DIDL `protocolInfo`.
- **Metadata comes only from the DIDL.** Unlike Sonos, it doesn't read the
  file's tags. It fetched `upnp:albumArtURI` for each track (plain GET,
  `Accept: */*`), a few seconds after playback started.
- **It has a one-track queue.** `SetNextAVTransportURI` is supported and
  echoed back as `NextURI`; at the end of the track it moved on by itself,
  with ~2 s of `TRANSITIONING` (not gapless). The controller only has to
  supply the following track each time.
- **Request pattern:** HEAD first, then dozens of small `Range` requests
  in a burst (8 KB steps, plus probes near the end of the file), then
  streams at roughly playback speed rather than downloading everything.
  The phone's server must handle many short concurrent requests.
- **State reporting:** `GetTransportInfo` gives `NO_MEDIA_PRESENT` when
  idle, `TRANSITIONING`, `PLAYING`, `STOPPED`; `GetPositionInfo` gives the
  position in milliseconds. It only knows about media sent over UPnP, not
  apps or live TV. The Samsung API (`:8001/api/v2/`) on this 2017 model
  doesn't report power state.
- AVTransport actions: Play, Stop, Pause, Seek, Next, Previous,
  SetPlayMode, SetAVTransportURI, SetNextAVTransportURI,
  GetMediaInfo, GetTransportInfo, GetPositionInfo, GetTransportSettings,
  GetDeviceCapabilities, GetCurrentTransportActions.
- No "allow this device" prompt appeared from the laptop.
- Slow to start: 8.4 s from `Play` to `PLAYING` on one run (screen waking,
  long `TRANSITIONING`), against well under a second for Sonos. The app
  must not treat that as a failure.

## Remote control from a second phone

Both renderers report what they are playing to anyone on the LAN (polling
or GENA). Sonos even fills in title, artist, album and art from the
file's own tags. The track URL names the serving phone's IP and port, so
a second phone running the app can recognise "ours, from that phone".
Pause, seek and volume can go straight to the renderer. Skipping needs
whoever owns the queue: the serving phone, or the Sonos queue
(`AddURIToQueue`, Sonos only) if we choose to use it.

## Still to test
- GENA event subscriptions (track end, state changes) on both.
- Gapless or queued playback: `SetNextAVTransportURI`, or the Sonos queue.
- Sonos: grouping the two rooms and playing to the group.
- TV: what the screen shows while audio plays (title, art?); waking from
  standby or Art Mode.
- Behaviour with the router's Internet uplink disconnected.
