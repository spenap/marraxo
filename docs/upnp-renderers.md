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

## Still to test
- `SetAVTransportURI` + `Play` with an MP3 served over HTTP from the laptop,
  on one Sonos and on the TV.
- Seek, duration reporting, byte-range requests and the HTTP headers each
  device sends.
- Pause/stop, volume, GENA event subscriptions.
- Sonos: grouping the two rooms and playing to the group.
- TV: does it need DIDL-Lite metadata with a matching `protocolInfo`? Does
  it show an "allow this device" prompt the first time? Does it wake from
  standby or Art Mode, and what does the screen show while audio plays?
- Behaviour with the router's Internet uplink disconnected.
