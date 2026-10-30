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

## Still to test
- `SetAVTransportURI` + `Play` on the TV (done on Estudio).
- Seek, duration reporting, byte ranges and request headers on the TV.
- GENA event subscriptions (track end, state changes) on both.
- Gapless or queued playback: `SetNextAVTransportURI`, or the Sonos queue.
- Sonos: grouping the two rooms and playing to the group.
- TV: does it need DIDL-Lite metadata with a matching `protocolInfo`? Does
  it show an "allow this device" prompt the first time? Does it wake from
  standby or Art Mode, and what does the screen show while audio plays?
- Behaviour with the router's Internet uplink disconnected.
