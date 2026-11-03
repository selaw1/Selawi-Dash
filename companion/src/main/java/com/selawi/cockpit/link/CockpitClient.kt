package com.selawi.cockpit.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

/**
 * Keeps one connection to the cockpit and sends it the latest navigation step.
 * The cockpit is found on the Wi-Fi automatically, or at the address the user typed.
 */
object CockpitClient {
    private const val SERVICE_TYPE = "_selawicockpit._tcp."

    private val _status = MutableStateFlow("Not started")
    val status: StateFlow<String> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    // Only the newest step matters; older ones are dropped.
    private val outbox = Channel<String>(Channel.CONFLATED)
    @Volatile private var lastNav: String? = null

    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            while (isActive) {
                val code = LinkPrefs.code(app)
                if (code.isBlank()) {
                    _status.value = "Enter the pairing code shown in the cockpit's Settings"
                    delay(3000)
                    continue
                }
                val target = LinkPrefs.address(app) ?: discover(app)
                if (target == null) {
                    _status.value = "Looking for the cockpit… make sure the phone and car share a Wi-Fi"
                    delay(4000)
                    continue
                }
                session(target, code)
                delay(3000)
            }
        }
    }

    fun restart(context: Context) {
        job?.cancel()
        job = null
        start(context)
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = "Stopped"
    }

    fun send(json: String) {
        if (JSONObject(json).optString("type") == "nav") lastNav = json
        outbox.trySend(json)
    }

    private suspend fun session(target: Pair<String, Int>, code: String) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(target.first, target.second), 5000)
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val hello = JSONObject().put("type", "hello").put("code", code).put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
            writer.write(hello.toString() + "\n")
            writer.flush()
            socket.soTimeout = 5000
            val reply = JSONObject(reader.readLine() ?: "{}")
            if (reply.optString("type") != "welcome") {
                _status.value = "The cockpit refused the code. Check it in the cockpit's Settings."
                delay(10_000)
                return
            }
            _status.value = "Connected to the car"
            lastNav?.let { outbox.trySend(it) }
            while (true) {
                // A heartbeat every 15 s shows quickly when the car has gone.
                val message = withTimeoutOrNull(15_000) { outbox.receive() } ?: """{"type":"ping"}"""
                writer.write(message + "\n")
                writer.flush()
            }
        } catch (e: Exception) {
            if (_status.value == "Connected to the car") _status.value = "Connection to the car lost; retrying"
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Listens for the cockpit announcing itself on the local network. */
    private suspend fun discover(context: Context): Pair<String, Int>? {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("cockpit-link").apply { setReferenceCounted(false) }
        runCatching { lock.acquire() }
        try {
            return withTimeoutOrNull(8000) { resolve(context) }
        } finally {
            runCatching { lock.release() }
        }
    }

    private suspend fun resolve(context: Context): Pair<String, Int>? = suspendCancellableCoroutine { cont ->
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        lateinit var discovery: NsdManager.DiscoveryListener
        fun finish(result: Pair<String, Int>?) {
            runCatching { nsd.stopServiceDiscovery(discovery) }
            if (cont.isActive) runCatching { cont.resume(result) }
        }
        discovery = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = resolved.host?.hostAddress ?: return
                        finish(host to resolved.port)
                    }
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {}
                })
            }
            override fun onStartDiscoveryFailed(type: String, error: Int) {
                if (cont.isActive) runCatching { cont.resume(null) }
            }
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
        }
        cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(discovery) } }
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
            .onFailure { if (cont.isActive) cont.resume(null) }
    }
}

/** The pairing code and optional fixed car address, kept on the phone. */
object LinkPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("link", Context.MODE_PRIVATE)

    fun code(context: Context): String = prefs(context).getString("code", "").orEmpty()

    /** "192.168.1.20:47800" typed by the user, when automatic discovery does not work on their network. */
    fun address(context: Context): Pair<String, Int>? {
        val raw = prefs(context).getString("address", "").orEmpty().trim()
        if (raw.isEmpty()) return null
        val host = raw.substringBefore(':')
        val port = raw.substringAfter(':', "47800").toIntOrNull() ?: 47800
        return host to port
    }

    fun rawAddress(context: Context): String = prefs(context).getString("address", "").orEmpty()

    fun save(context: Context, code: String, address: String) {
        prefs(context).edit().putString("code", code.trim()).putString("address", address.trim()).apply()
    }
}
