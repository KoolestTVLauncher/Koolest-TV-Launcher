package dev.koolest

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshots taken by holding Home (see HomeOverride). They live in Koolest's own folder, so no storage
 * permission is needed; adb reaches them at /sdcard/Android/data/dev.koolest/files/Screenshots.
 */
internal fun screenshotDir(c: Context) = File(c.getExternalFilesDir(null), "Screenshots")

internal fun screenshots(c: Context) =
    screenshotDir(c).listFiles { f -> SHOT_NAME.matches(f.name) }.orEmpty().sortedByDescending { it.name }

/**
 * True when every sampled pixel is black or nearly so: what this TV returns while video plays, because the
 * video goes straight to a hardware plane no capture can read. Anything on top (player controls, menus) makes
 * it false, so a partly useful screenshot is still kept.
 */
internal fun isBlank(pixels: IntArray) = pixels.all { p ->
    maxOf(p shr 16 and 0xff, p shr 8 and 0xff, p and 0xff) <= 20
}

/** A 64×36 sample of [bmp], enough for [isBlank]. */
internal fun samplePixels(bmp: Bitmap): IntArray {
    val small = Bitmap.createScaledBitmap(bmp, 64, 36, true)
    return IntArray(64 * 36).also { small.getPixels(it, 0, 64, 0, 0, 64, 36); small.recycle() }
}

/** Saves [bmp] as a PNG and keeps only the newest [KEEP_SHOTS]. Runs off the UI thread. */
internal fun saveScreenshot(c: Context, bmp: Bitmap): File {
    val dir = screenshotDir(c).apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val file = generateSequence(0) { it + 1 }.map { File(dir, if (it == 0) "Koolest_$stamp.png" else "Koolest_${stamp}_$it.png") }
        .first { !it.exists() }
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    // shortcut: a fixed cap of 50 (~1–3 MB each at 1080p); make it a setting if people want more.
    screenshots(c).drop(KEEP_SHOTS).forEach { it.delete() }
    return file
}

/**
 * A read-only page that shows the screenshots on a phone, for saving there. Like UploadServer it runs only
 * while its settings page is open and answers only under the random /<code> from the QR code.
 * Files are served by exact name from the folder's own listing, so a path can never reach anything else.
 */
internal class ScreenshotServer(val code: String, private val c: Context) {
    private val socket = try {
        ServerSocket(8081)
    } catch (e: IOException) {
        ServerSocket(0)
    }
    val port get() = socket.localPort

    init {
        Thread(::serve, "screenshot-share").start()
    }

    fun stop() = socket.close()

    private fun serve() {
        while (!socket.isClosed) {
            val s = try {
                socket.accept()
            } catch (e: IOException) {
                return // closed
            }
            try {
                s.use { handle(it) }
            } catch (e: Exception) {
                android.util.Log.w("Koolest", "screenshot request failed ($e)")
            }
        }
    }

    private fun handle(s: Socket) {
        s.soTimeout = 30_000
        val input = s.getInputStream().buffered()
        val out = s.getOutputStream()
        val request = httpLine(input) ?: return
        while (httpLine(input)?.isNotEmpty() == true) Unit // headers: not needed
        val method = request.substringBefore(' ')
        val path = request.substringAfter(' ').substringBefore(' ').substringBefore('?')
        if (method != "GET") return httpReply(out, 405, "text/plain", "Not allowed")
        val shots = screenshots(c)
        when {
            path == "/$code" -> httpReply(out, 200, "text/html; charset=utf-8", page(shots))
            path.startsWith("/$code/") -> {
                val name = path.substringAfter("/$code/")
                val file = shots.firstOrNull { it.name == name } ?: return httpReply(out, 404, "text/plain", "That screenshot is gone.")
                sendFile(out, file)
            }
            else -> httpReply(out, 404, "text/plain", "Not found. Scan the code on the TV again.")
        }
    }

    private fun sendFile(out: OutputStream, file: File) {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${file.length()}\r\n" +
            "Content-Disposition: inline; filename=\"${file.name}\"\r\nConnection: close\r\n\r\n").toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.flush()
    }

    private fun page(shots: List<File>): String {
        val items = shots.joinToString("") { f ->
            val taken = f.name.removePrefix("Koolest_").removeSuffix(".png").replace('_', ' ').let { n ->
                // 2026-10-25 18-07-03 → 2026-10-25 18:07:03
                n.replaceFirst(Regex("(\\d{2})-(\\d{2})-(\\d{2})"), "$1:$2:$3")
            }
            """<figure><a href="/$code/${f.name}"><img src="/$code/${f.name}" loading="lazy" alt=""></a>
               <figcaption>$taken <a href="/$code/${f.name}" download="${f.name}">Save</a></figcaption></figure>"""
        }
        val body = if (shots.isEmpty()) "<p>No screenshots yet. On the TV, hold Home in any app to take one.</p>" else items
        return """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Koolest screenshots</title>
            <style>
              body{font:16px system-ui,sans-serif;margin:0;padding:24px 16px;background:#f7f8fa;color:#111214}
              @media (prefers-color-scheme:dark){body{background:#09090b;color:#f5f5f7}}
              h1{font-size:24px;margin:0 0 4px} p{opacity:.7;margin:0 0 20px}
              figure{margin:0 0 20px} img{display:block;width:100%;border-radius:10px;background:#000}
              figcaption{display:flex;justify-content:space-between;margin-top:8px;opacity:.8}
              a{color:inherit}
            </style></head><body>
            <h1>Screenshots from your TV</h1>
            <p>Newest first. Tap Save, or open one and long-press it.</p>
            $body
            </body></html>
        """.trimIndent()
    }
}

private val SHOT_NAME = Regex("Koolest_[0-9_-]+\\.png")
private const val KEEP_SHOTS = 50
