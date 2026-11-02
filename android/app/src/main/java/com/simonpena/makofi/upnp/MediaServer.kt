package com.simonpena.makofi.upnp

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * A small HTTP server for renderers on the LAN. Only published tracks and
 * covers are reachable, each under a random token; paths are never exposed
 * (CLAUDE.md). Supports HEAD, byte ranges and the DLNA headers the TV needs.
 *
 * Sonos downloads a whole track in a few seconds; the TV fires dozens of
 * short range requests and then streams at playback speed. A thread per
 * connection copes with both.
 */
class MediaServer(context: Context) {
    private class Entry(val uri: Uri?, val bytes: ByteArray?, val mime: String)

    private val resolver = context.applicationContext.contentResolver
    private val entries = ConcurrentHashMap<String, Entry>()
    private val tokens = ConcurrentHashMap<String, String>() // key → path, so republishing reuses it
    private val random = SecureRandom()
    private val socket = ServerSocket(0)
    private val workers = Executors.newCachedThreadPool()

    init {
        thread(name = "mako-http", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                workers.execute { runCatching { handle(client) } }
            }
        }
    }

    /** Base URL as seen from [host], e.g. "http://192.168.1.135:41234". Blocking. */
    fun baseUrlFor(host: String): String {
        val local = DatagramSocket().use { it.connect(InetSocketAddress(host, 9)); it.localAddress }
        return "http://${local.hostAddress}:${socket.localPort}"
    }

    /** Publishes a track; returns its path, e.g. "/t/3f9a….mp3". */
    fun publishTrack(key: String, uri: Uri): Pair<String, String> {
        val mime = resolver.getType(uri) ?: "audio/mpeg"
        val ext = when (mime) {
            "audio/mpeg" -> ".mp3"
            "audio/mp4", "audio/x-m4a", "audio/aac" -> ".m4a"
            "audio/ogg" -> ".ogg"
            "audio/flac", "audio/x-flac" -> ".flac"
            else -> ""
        }
        val path = tokens.getOrPut("t:$key") { "/t/${token()}$ext" }
        entries[path] = Entry(uri, null, mime)
        return path to mime
    }

    fun publishCover(key: String, jpeg: ByteArray): String {
        val path = tokens.getOrPut("c:$key") { "/c/${token()}.jpg" }
        entries[path] = Entry(null, jpeg, "image/jpeg")
        return path
    }

    fun close() {
        runCatching { socket.close() }
        workers.shutdownNow()
    }

    private fun token() = ByteArray(9).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun handle(client: Socket) = client.use {
        it.soTimeout = 20_000
        val input = it.getInputStream().buffered()
        val request = readLine(input)?.split(' ') ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val method = request.getOrNull(0)
        val out = BufferedOutputStream(it.getOutputStream(), 64 * 1024)
        val entry = entries[request.getOrNull(1)]
        if (entry == null || (method != "GET" && method != "HEAD")) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            return
        }
        val pfd = entry.uri?.let { uri -> resolver.openFileDescriptor(uri, "r") }
        pfd.use {
            val size = entry.bytes?.size?.toLong() ?: pfd!!.statSize
            val range = byteRange(headers["range"], size)
            val first = range?.first ?: 0L
            val last = range?.last ?: (size - 1)
            if (range != null && range.isEmpty()) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                return
            }
            val length = last - first + 1
            val head = StringBuilder()
            head.append(if (range != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            if (range != null) head.append("Content-Range: bytes $first-$last/$size\r\n")
            head.append("Content-Type: ${entry.mime}\r\n")
            head.append("Content-Length: $length\r\n")
            head.append("Accept-Ranges: bytes\r\n")
            head.append("Connection: close\r\n")
            if (entry.uri != null) {
                head.append("transferMode.dlna.org: Streaming\r\n")
                if ("getcontentfeatures.dlna.org" in headers) head.append("contentFeatures.dlna.org: $DLNA_FEATURES\r\n")
            }
            head.append("\r\n")
            out.write(head.toString().toByteArray())
            if (method == "GET") {
                if (entry.bytes != null) {
                    out.write(entry.bytes, first.toInt(), length.toInt())
                } else {
                    FileInputStream(pfd!!.fileDescriptor).use { file ->
                        file.channel.position(first)
                        val buf = ByteArray(64 * 1024)
                        var remaining = length
                        while (remaining > 0) {
                            val n = file.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (n < 0) break
                            out.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                }
            }
            out.flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return null
            sb.append(c.toChar())
        }
    }

}

private val RANGE = Regex("bytes=(\\d*)-(\\d*)")

/**
 * The bytes a Range header asks for, clamped to the file: null for the
 * whole file (no or unparseable header), an empty range if unsatisfiable.
 */
internal fun byteRange(header: String?, size: Long): LongRange? {
    val (a, b) = RANGE.matchEntire(header?.trim().orEmpty())?.destructured ?: return null
    val range = when {
        a.isNotEmpty() -> a.toLong()..(if (b.isNotEmpty()) minOf(b.toLong(), size - 1) else size - 1)
        b.isNotEmpty() -> maxOf(0, size - b.toLong())..(size - 1) // suffix: the last N bytes
        else -> return null
    }
    return if (range.first > range.last || range.first >= size) LongRange.EMPTY else range
}
