package com.simonpena.makofi.upnp

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

/**
 * A UPnP renderer on localhost that behaves like the real ones
 * (docs/upnp-renderers.md):
 * - [Kind.SONOS] downloads the whole track when it gets a URI and seeks
 *   within it freely;
 * - [Kind.TV] asks for DLNA content features and refuses to seek unless
 *   the server says byte ranges work (DLNA.ORG_OP=01).
 *
 * The test moves the playback position and the transport state; the
 * player under test sees them by polling, as it does with real devices.
 */
class FakeRenderer(private val kind: Kind, private val room: String = if (kind == Kind.SONOS) "Estudio" else "The Frame") {
    enum class Kind { SONOS, TV }

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                thread(isDaemon = true) { client.use(::handle) }
            }
        }
    }

    val id = "RINCON_$room"
    val renderer = Renderer(
        room, if (kind == Kind.SONOS) "Sonos One SL" else "UE55LS003",
        "http://127.0.0.1:${socket.localPort}/avt", "http://127.0.0.1:${socket.localPort}/rc",
        id = id, groupRenderingControl = if (kind == Kind.SONOS) "http://127.0.0.1:${socket.localPort}/grc" else null,
    )
    /** Sonos: the coordinator this speaker follows (x-rincon:…), or null when on its own. */
    @Volatile var joinedTo: String? = null
    @Volatile var groupVolume = 20

    @Volatile var state = "NO_MEDIA_PRESENT"
    @Volatile var uri = ""
    @Volatile var metadata = ""
    @Volatile var positionMs = 0L
    @Volatile var volume = 15
    @Volatile var downloaded: ByteArray? = null
    @Volatile var contentFeatures: String? = null
    /** Like the TV: Play leaves it TRANSITIONING until the test says it started. */
    @Volatile var slowStart = false
    /** Real-time delay before answering Play, like a busy TV on Wi-Fi. */
    @Volatile var playDelayMs = 0L
    /** State right after Play, if not the usual one (the TV can stay STOPPED for a moment). */
    @Volatile var stateAfterPlay: String? = null
    /** Play requests fully handled (after any [playDelayMs]). */
    @Volatile var plays = 0
    val actions: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** Plays to the end of the track and stops, as Sonos and the TV do. */
    fun finishTrack() {
        positionMs = durationMs()
        state = "STOPPED"
    }

    /** Another controller (the Sonos app) starts something else. */
    fun takeOver(other: String) {
        uri = other
        state = "PLAYING"
    }

    fun close() = socket.close()

    private fun durationMs() = Regex("duration=\"([^\"]+)\"").find(unescape(metadata))?.groupValues?.get(1)
        ?.let(::parseUpnpTime) ?: 0

    private fun avTransport(action: String, args: Map<String, String>): Map<String, String> {
        actions += action
        when (action) {
            "SetAVTransportURI" -> {
                if (args.getValue("CurrentURI").startsWith("x-rincon:")) { // Sonos: join a group
                    joinedTo = args.getValue("CurrentURI").removePrefix("x-rincon:")
                    return emptyMap()
                }
                uri = args.getValue("CurrentURI")
                metadata = args["CurrentURIMetaData"].orEmpty()
                positionMs = 0
                state = "STOPPED"
                fetch(uri)
            }
            "Play" -> {
                Thread.sleep(playDelayMs)
                state = stateAfterPlay ?: if (slowStart) "TRANSITIONING" else "PLAYING"
                plays++
            }
            "Pause" -> state = "PAUSED_PLAYBACK"
            "Stop" -> { state = "STOPPED"; positionMs = 0 }
            "BecomeCoordinatorOfStandaloneGroup" -> joinedTo = null
            "Seek" -> {
                if (kind == Kind.TV && contentFeatures?.contains("DLNA.ORG_OP=01") != true) {
                    throw UpnpFault(701, "Transition not available")
                }
                positionMs = parseUpnpTime(args["Target"]) ?: throw UpnpFault(711, "Illegal seek target")
            }
            "GetTransportInfo" -> return mapOf("CurrentTransportState" to state, "CurrentTransportStatus" to "OK")
            "GetPositionInfo" -> return mapOf(
                "TrackURI" to uri, "RelTime" to formatUpnpTime(positionMs), "TrackDuration" to formatUpnpTime(durationMs()),
            )
            else -> throw UpnpFault(401, "Invalid Action")
        }
        return emptyMap()
    }

    private fun groupRenderingControl(action: String, args: Map<String, String>): Map<String, String> {
        actions += action
        when (action) {
            "GetGroupVolume" -> return mapOf("CurrentVolume" to "$groupVolume")
            "SetGroupVolume" -> groupVolume = args.getValue("DesiredVolume").toInt()
            else -> throw UpnpFault(401, "Invalid Action")
        }
        return emptyMap()
    }

    private fun renderingControl(action: String, args: Map<String, String>): Map<String, String> {
        actions += action
        when (action) {
            "GetVolume" -> return mapOf("CurrentVolume" to "$volume")
            "SetVolume" -> volume = args.getValue("DesiredVolume").toInt()
            else -> throw UpnpFault(401, "Invalid Action")
        }
        return emptyMap()
    }

    /** What each renderer does with a new URI. */
    private fun fetch(url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (kind == Kind.TV) {
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("getcontentFeatures.dlna.org", "1")
            conn.responseCode
            contentFeatures = conn.getHeaderField("contentFeatures.dlna.org")
            conn.disconnect()
        } else {
            downloaded = conn.inputStream.use { it.readBytes() } // Sonos buffers the whole track
        }
    }

    private class UpnpFault(val code: Int, message: String) : Exception(message)

    /** Just enough HTTP for SOAP: a POST with a Content-Length body. */
    private fun handle(client: Socket) {
        val input = client.getInputStream().buffered()
        fun line() = generateSequence { input.read().takeIf { it >= 0 } }.takeWhile { it != '\n'.code }
            .map { it.toChar() }.joinToString("").trimEnd('\r')
        val path = line().split(' ').getOrNull(1).orEmpty()
        val headers = generateSequence(::line).takeWhile { it.isNotEmpty() }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val body = String(ByteArray(headers["content-length"]?.toInt() ?: 0).also { b ->
            var n = 0
            while (n < b.size) n += input.read(b, n, b.size - n).takeIf { it > 0 } ?: break
        })
        val action = headers["soapaction"].orEmpty().substringAfter('#').trim('"')
        val args = Regex("<(\\w+)>([^<]*)</\\1>").findAll(body).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        val handler: (String, Map<String, String>) -> Map<String, String> = when (path) {
            "/rc" -> ::renderingControl
            "/grc" -> ::groupRenderingControl
            else -> ::avTransport
        }
        val (status, inner) = try {
            "200 OK" to handler(action, args).entries.joinToString("") { (k, v) -> "<$k>${escape(v)}</$k>" }
                .let { "<u:${action}Response>$it</u:${action}Response>" }
        } catch (f: UpnpFault) {
            "500 Internal Server Error" to "<s:Fault><detail><UPnPError><errorCode>${f.code}</errorCode>" +
                "<errorDescription>${f.message}</errorDescription></UPnPError></detail></s:Fault>"
        }
        val response = "<?xml version=\"1.0\"?><s:Envelope><s:Body>$inner</s:Body></s:Envelope>".toByteArray()
        client.getOutputStream().run {
            write("HTTP/1.1 $status\r\nContent-Type: text/xml\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(response)
            flush()
        }
    }
}
