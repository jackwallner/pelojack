package com.jackwallner.pelojack.devlink

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONObject
import com.jackwallner.pelojack.MainActivity

const val DEV_LINK_PORT = 8765
private const val TAG = "PelojackLink"
private val FOLDERS = setOf("workouts", "videos", "import")

/**
 * A small HTTP server on the home network so the Mac can update and inspect Pelojack after the
 * tablet restarts, when adb over Wi-Fi is gone (Android 10 cannot keep it on). Every request needs
 * the token `scripts/bike-link.sh` gave the app over USB; with no token set the server stays off.
 *
 *   GET  /ping                         version and whether Pelojack is device owner
 *   POST /install/begin?package=       starts an install, returns its session
 *   POST /install/add?session=&name=   adds one APK (body) to it
 *   POST /install/commit?session=      installs and returns Android's answer
 *   PUT  /files/<folder>/<name>        writes into the workouts, videos or import folder
 *   POST /import                       merges the import folder into the ride history
 *   GET  /log                          this app's recent log
 *   GET  /screenshot                   PNG of Pelojack's window, when it is in front
 *   POST /home                         brings Pelojack to the front
 *   POST /tap?x=&y=                    taps inside Pelojack's own focused window
 *   POST /reboot                       restarts the tablet, as device owner
 *   POST /release-owner                gives device owner back before an uninstall
 */
class DevLink(
    private val context: Context,
    private val onImport: () -> Unit,
    private val canReboot: () -> Boolean,
) {
    private val installer = Installer(context)
    private val tokenFile = File(context.filesDir, "devlink-token")
    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())

    /** The window shown in /screenshot. */
    var window: WeakReference<View>? = null

    val hasToken: Boolean get() = tokenFile.exists()

    /** Sets the token the first time; later only a caller that knows the current one may change it. */
    fun setToken(token: String, current: String?): Boolean {
        if (token.length < 16) return false
        if (hasToken && !matches(current)) return false
        tokenFile.writeText(token)
        start()
        return true
    }

    fun matches(candidate: String?): Boolean {
        if (candidate == null || !hasToken) return false
        return MessageDigest.isEqual(tokenFile.readText().trim().toByteArray(), candidate.trim().toByteArray())
    }

    @Synchronized
    fun start() {
        if (server != null || !hasToken) return
        val socket = runCatching { ServerSocket(DEV_LINK_PORT) }.getOrElse {
            Log.w(TAG, "Could not listen on $DEV_LINK_PORT: ${it.message}")
            return
        }
        server = socket
        thread(name = "devlink", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                thread(name = "devlink-client", isDaemon = true) { serve(client) }
            }
        }
        Log.i(TAG, "Listening on $DEV_LINK_PORT")
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: InputStream) {
        val length: Long get() = headers["content-length"]?.toLongOrNull() ?: 0
    }

    private fun serve(client: Socket) {
        client.use {
            val input = BufferedInputStream(it.getInputStream())
            val out = it.getOutputStream()
            try {
                val request = read(input) ?: return
                if (!matches(request.headers["x-pelojack-token"])) return respond(out, 401, json("error" to "bad token"))
                route(request, out)
            } catch (e: Exception) {
                Log.w(TAG, "Request failed", e)
                runCatching { respond(out, 500, json("error" to (e.message ?: e.javaClass.simpleName))) }
            }
        }
    }

    private fun route(request: Request, out: OutputStream) {
        val path = request.path
        when {
            request.method == "GET" && path == "/ping" -> {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                @Suppress("DEPRECATION")
                respond(out, 200, json("versionCode" to info.versionCode, "versionName" to info.versionName,
                    "lastUpdate" to info.lastUpdateTime, "owner" to Owner.isActive(context),
                    "uptimeMillis" to SystemClock.elapsedRealtime(),
                    "pictureInPicture" to context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)))
            }
            request.method == "POST" && path == "/install/begin" ->
                respond(out, 200, json("session" to installer.begin(request.query["package"])))
            request.method == "POST" && path == "/install/add" -> {
                val session = request.query.getValue("session").toInt()
                installer.add(session, request.query["name"] ?: "base.apk", Bounded(request.body, request.length), request.length)
                respond(out, 200, json("added" to request.length))
            }
            request.method == "POST" && path == "/install/commit" -> {
                val session = request.query.getValue("session").toInt()
                val info = context.packageManager.packageInstaller.getSessionInfo(session)
                if (info?.appPackageName == context.packageName) {
                    // Updating Pelojack itself ends this process, so answer first.
                    respond(out, 200, json("status" to "installing; check /ping for the new version"))
                    installer.commitAndForget(session)
                } else {
                    val status = installer.commit(session)
                    respond(out, if (status == "success") 200 else 500, json("status" to status))
                }
            }
            request.method == "PUT" && path.startsWith("/files/") -> {
                val (folder, name) = path.removePrefix("/files/").split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                require(folder in FOLDERS && name.isNotBlank() && !name.contains("/") && !name.startsWith(".")) { "bad path" }
                val dir = checkNotNull(context.getExternalFilesDir(folder)) { "storage unavailable" }
                File(dir, name).outputStream().use { Bounded(request.body, request.length).copyTo(it) }
                respond(out, 200, json("written" to request.length))
            }
            request.method == "POST" && path == "/import" -> {
                main.post(onImport)
                respond(out, 200, json("status" to "importing"))
            }
            request.method == "GET" && path == "/log" -> {
                val log = ProcessBuilder("logcat", "-d", "-t", request.query["lines"] ?: "500", "--pid=${Process.myPid()}")
                    .redirectErrorStream(true).start().inputStream.readBytes()
                respond(out, 200, log, "text/plain")
            }
            request.method == "GET" && path == "/screenshot" -> {
                val png = screenshot() ?: return respond(out, 409, json("error" to "Pelojack is not in front"))
                respond(out, 200, png, "image/png")
            }
            request.method == "POST" && path == "/home" -> {
                main.post {
                    context.startActivity(Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
                respond(out, 200, json("status" to "opening Pelojack"))
            }
            request.method == "POST" && path == "/tap" -> {
                val x = request.query.getValue("x").toInt()
                val y = request.query.getValue("y").toInt()
                if (!tap(x, y)) return respond(out, 409, json("error" to "tap is outside Pelojack's focused window"))
                respond(out, 200, json("status" to "tapped"))
            }
            request.method == "POST" && path == "/reboot" -> {
                check(Owner.isActive(context)) { "Pelojack is not device owner" }
                if (!canReboot()) return respond(out, 409, json("error" to "Save or discard the ride before restarting"))
                respond(out, 200, json("status" to "rebooting"))
                main.postDelayed({
                    runCatching { Owner.reboot(context) }.onFailure { Log.e(TAG, "Reboot failed", it) }
                }, 1000)
            }
            request.method == "POST" && path == "/release-owner" -> {
                Owner.release(context)
                respond(out, 200, json("owner" to Owner.isActive(context)))
            }
            else -> respond(out, 404, json("error" to "no such endpoint"))
        }
    }

    /** Dispatch only to our own foreground window, never to another app or the system UI. */
    private fun tap(x: Int, y: Int): Boolean {
        var tapped = false
        val done = CountDownLatch(1)
        main.post {
            try {
                val view = window?.get() ?: return@post
                if (!view.hasWindowFocus() || x !in 0 until view.width || y !in 0 until view.height) return@post
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now, action, x.toFloat(), y.toFloat(), 0)
                    try { view.dispatchTouchEvent(event) } finally { event.recycle() }
                }
                tapped = true
            } finally { done.countDown() }
        }
        return done.await(5, TimeUnit.SECONDS) && tapped
    }

    private fun screenshot(): ByteArray? {
        val view = window?.get() ?: return null
        if (!view.isAttachedToWindow || !view.hasWindowFocus()) return null
        var png: ByteArray? = null
        val done = CountDownLatch(1)
        main.post {
            runCatching {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }
            done.countDown()
        }
        done.await(5, TimeUnit.SECONDS)
        return png
    }

    private fun read(input: InputStream): Request? {
        val requestLine = line(input) ?: return null
        val (method, target) = requestLine.split(" ").let { if (it.size < 2) return null else it[0] to it[1] }
        val headers = mutableMapOf<String, String>()
        while (true) {
            val header = line(input) ?: break
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon > 0) headers[header.substring(0, colon).trim().lowercase()] = header.substring(colon + 1).trim()
        }
        val path = URLDecoder.decode(target.substringBefore('?'), "UTF-8")
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return Request(method, path, query, headers, input)
    }

    private fun line(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (b == '\n'.code) return bytes.toString("UTF-8").trimEnd('\r')
            bytes.write(b)
            if (bytes.size() > 8192) return null
        }
    }

    private fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }.toString().toByteArray()

    private fun respond(out: OutputStream, status: Int, body: ByteArray, type: String = "application/json") {
        val reason = mapOf(200 to "OK", 401 to "Unauthorized", 404 to "Not Found", 409 to "Conflict", 500 to "Error")[status] ?: "OK"
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    /** Reads at most [remaining] bytes, so a request body never runs into the next request. */
    private class Bounded(private val source: InputStream, private var remaining: Long) : InputStream() {
        override fun read(): Int = if (remaining <= 0) -1 else source.read().also { if (it >= 0) remaining-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }
    }
}
