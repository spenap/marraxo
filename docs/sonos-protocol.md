# Sonos protocol notes

Findings from testing against the real speakers (roadmap track B0).

## Speakers (2026-10-29)

| Room | IP | Model | Firmware | Generation |
|---|---|---|---|---|
| Estudio | 192.168.1.129 | Sonos One SL (S38) | 18.6 (96.0-79160) | S2 |
| Cocina | 192.168.1.151 | Sonos One SL (S38) | 18.6 (96.0-79160) | S2 |

IPs come from DHCP and may change; always find the speakers by discovery.

- Same household, but **two separate rooms**: each is its own zone group
  and coordinator. They are neither a stereo pair nor grouped right now.
- The generation shows up as `<swGen>2</swGen>` in the device description.

## Discovery (works)
- SSDP `M-SEARCH` to `239.255.255.250:1900` with
  `ST: urn:schemas-upnp-org:device:ZonePlayer:1`. Both speakers answer.
- Responses include `LOCATION` (device description URL, port 1400) and
  `X-RINCON-HOUSEHOLD`.

## Services advertised (S2 firmware 18.6)
The local UPnP services are still present: AVTransport, RenderingControl,
GroupRenderingControl, ZoneGroupTopology, ConnectionManager,
ContentDirectory, Queue, DeviceProperties, GroupManagement.

- `ZoneGroupTopology#GetZoneGroupState` works (read-only SOAP POST to
  `/ZoneGroupTopology/Control`) and returns groups and coordinators.
- Playback commands go to the **group coordinator**, not to every speaker.

## Still to test
- `SetAVTransportURI` + `Play` with an MP3 served over HTTP from the laptop.
- Seek, duration reporting, byte-range requests and the HTTP headers Sonos
  sends.
- Pause/stop, volume, GENA event subscriptions.
- Grouping the two rooms and playing to the group.
- Behaviour with the router's Internet uplink disconnected.
