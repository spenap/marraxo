package com.simonpena.marraxo.upnp

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

// UPnP control of Sonos speakers and the TV. Protocol findings, including
// why each detail is here: docs/upnp-renderers.md.

const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"
const val GROUP_RENDERING_CONTROL = "urn:schemas-upnp-org:service:GroupRenderingControl:1"

/** DLNA flags: OP=01 says byte ranges work. The TV refuses to seek without it. */
const val DLNA_FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

/**
 * A device that can play what we serve: a Sonos room, the TV, or several
 * Sonos rooms together. A group is driven through its first room (the
 * coordinator); [members] join it while it plays.
 */
data class Renderer(
    val name: String,
    val model: String,
    val avTransport: String,
    val renderingControl: String?,
    val id: String? = null, // the device's UDN without "uuid:" (Sonos: RINCON_…)
    val groupRenderingControl: String? = null, // Sonos: volume for the whole group
    val members: List<Renderer> = emptyList(),
) {
    val isSpeaker get() = model.contains("Sonos", ignoreCase = true) || name.contains("Sonos", ignoreCase = true)
    val isGroup get() = members.isNotEmpty()

    fun toBundle(): Bundle = Bundle().apply {
        putString("name", name)
        putString("model", model)
        putString("avTransport", avTransport)
        putString("renderingControl", renderingControl)
        putString("id", id)
        putString("groupRenderingControl", groupRenderingControl)
        putParcelableArrayList("members", ArrayList(members.map { it.toBundle() }))
    }

    companion object {
        @Suppress("DEPRECATION") // the typed getParcelableArrayList needs API 33
        fun fromBundle(b: Bundle): Renderer = Renderer(
            b.getString("name").orEmpty(), b.getString("model").orEmpty(),
            b.getString("avTransport").orEmpty(), b.getString("renderingControl"),
            b.getString("id"), b.getString("groupRenderingControl"),
            b.getParcelableArrayList<Bundle>("members").orEmpty().map(::fromBundle),
        )
    }
}

/**
 * Adds "all Sonos rooms together" when there are two or more. The first
 * room by name leads; the others join it while it plays.
 */
fun withSonosGroup(renderers: List<Renderer>): List<Renderer> {
    val rooms = renderers.filter { it.isSpeaker && it.id != null && !it.isGroup }.sortedBy { it.name }
    if (rooms.size < 2) return renderers
    val lead = rooms.first()
    return renderers + lead.copy(name = rooms.joinToString(" + ") { it.name }, model = "Sonos group", members = rooms.drop(1))
}

private const val SEARCH = "M-SEARCH * HTTP/1.1\r\n" +
    "HOST: 239.255.255.250:1900\r\n" +
    "MAN: \"ssdp:discover\"\r\n" +
    "MX: 2\r\n" +
    "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

/** Finds renderers on the LAN. Blocks for about [timeoutMs]; call off the main thread. */
fun discoverRenderers(context: Context, timeoutMs: Long = 2500): List<Renderer> {
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    // Without it, Wi-Fi drivers may drop multicast replies.
    val lock = wifi.createMulticastLock("mako-ssdp").apply { setReferenceCounted(false); acquire() }
    val locations = linkedSetOf<String>()
    try {
        // Unconnected on purpose: Sonos replies from a different port.
        DatagramSocket().use { socket ->
            socket.soTimeout = 300
            val search = SEARCH.toByteArray()
            val group = InetAddress.getByName("239.255.255.250")
            repeat(2) { socket.send(DatagramPacket(search, search.size, group, 1900)) }
            val buf = ByteArray(2048)
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                LOCATION.find(String(packet.data, 0, packet.length))?.let { locations += it.groupValues[1] }
            }
        }
    } finally {
        lock.release()
    }
    val found = locations.mapNotNull { runCatching { describe(it) }.getOrNull() }
        .distinctBy { it.avTransport }
        .sortedBy { it.name }
    return withSonosGroup(found)
}

private val LOCATION = Regex("(?im)^location:\\s*(\\S+)")
private val SERVICE = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL)

private fun describe(location: String): Renderer? {
    val conn = URL(location).openConnection().apply { connectTimeout = 2000; readTimeout = 2000 }
    return parseDescription(conn.getInputStream().bufferedReader().use { it.readText() }, location)
}

/** A renderer from its device description XML, fetched from [location]. */
fun parseDescription(xml: String, location: String): Renderer? {
    fun tag(name: String, s: String = xml) = Regex("<$name>([^<]*)</$name>").find(s)?.groupValues?.get(1)?.let(::unescape)
    val services = SERVICE.findAll(xml).map { it.groupValues[1] }.toList()
    fun control(type: String) = services.firstOrNull { "service:$type:" in it }
        ?.let { tag("controlURL", it) }
        ?.let { URL(URL(location), it).toString() }
    val avTransport = control("AVTransport") ?: return null
    // Sonos: friendlyName is "<ip> - Sonos One SL - RINCON_…"; the room is in roomName.
    val name = tag("roomName") ?: tag("friendlyName")?.removePrefix("[TV] ") ?: return null
    return Renderer(
        name, tag("modelName").orEmpty(), avTransport, control("RenderingControl"),
        id = tag("UDN")?.removePrefix("uuid:"), // the first UDN is the device itself
        groupRenderingControl = control("GroupRenderingControl"),
    )
}

class UpnpException(message: String) : Exception(message)

/** One SOAP action. Returns the response's leaf elements by name. Blocking. */
fun soap(controlUrl: String, service: String, action: String, vararg args: Pair<String, Any>): Map<String, String> {
    val body = args.joinToString("") { (k, v) -> "<$k>${escape(v.toString())}</$k>" }
    val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
        "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
        "<u:$action xmlns:u=\"$service\">$body</u:$action></s:Body></s:Envelope>"
    val conn = URL(controlUrl).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        conn.setRequestProperty("SOAPACTION", "\"$service#$action\"")
        conn.outputStream.use { it.write(envelope.toByteArray()) }
        val ok = conn.responseCode in 200..299
        val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val fields = LEAF.findAll(text).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        if (!ok) throw UpnpException("$action: ${fields["errorCode"]} ${fields["errorDescription"].orEmpty()}")
        return fields
    } finally {
        conn.disconnect()
    }
}

private val LEAF = Regex("<(\\w+)>([^<]*)</\\1>")

fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&apos;", "'").replace("&amp;", "&")

/** "H:MM:SS(.mmm)" → ms; null for NOT_IMPLEMENTED and the like. */
fun parseUpnpTime(s: String?): Long? {
    val parts = s?.split(":")?.takeIf { it.size == 3 } ?: return null
    val h = parts[0].toLongOrNull() ?: return null
    val m = parts[1].toLongOrNull() ?: return null
    val sec = parts[2].toDoubleOrNull() ?: return null
    return (h * 3600 + m * 60) * 1000 + (sec * 1000).toLong()
}

fun formatUpnpTime(ms: Long): String {
    val s = ms / 1000
    return "${s / 3600}:${(s / 60 % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * DIDL-Lite for one track. The TV shows only what is sent here (title,
 * artist, art); Sonos reads the file's tags too.
 */
fun didl(url: String, mime: String, title: String?, artist: String?, album: String?, durationMs: Long?, artUrl: String?): String {
    val sb = StringBuilder()
    sb.append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
    sb.append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
    sb.append("<item id=\"1\" parentID=\"0\" restricted=\"1\">")
    sb.append("<dc:title>${escape(title ?: "Unknown")}</dc:title>")
    artist?.let { sb.append("<dc:creator>${escape(it)}</dc:creator><upnp:artist>${escape(it)}</upnp:artist>") }
    album?.let { sb.append("<upnp:album>${escape(it)}</upnp:album>") }
    artUrl?.let { sb.append("<upnp:albumArtURI>${escape(it)}</upnp:albumArtURI>") }
    sb.append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
    sb.append("<res protocolInfo=\"http-get:*:$mime:$DLNA_FEATURES\"")
    durationMs?.let { sb.append(" duration=\"${formatUpnpTime(it)}.000\"") }
    sb.append(">${escape(url)}</res></item></DIDL-Lite>")
    return sb.toString()
}
