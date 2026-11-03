package com.selawi.cockpit.features.phone

import android.content.Context
import android.graphics.BitmapFactory
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.selawi.cockpit.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

/** One turn-by-turn step from Google Maps on the phone. */
data class NavGuidance(
    val distance: String,
    val instruction: String,
    val eta: String,
    val icon: ImageBitmap?,
)

/**
 * Link to the Cockpit Link app on the driver's phone, over the local Wi-Fi (the car's hotspot or the
 * phone's). The cockpit announces itself on the network; the phone finds it, proves it knows the
 * pairing code, then sends Google Maps' turn-by-turn steps as JSON lines.
 */
class PhoneLink(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _guidance = MutableStateFlow<NavGuidance?>(null)
    val guidance: StateFlow<NavGuidance?> = _guidance

    private val _status = MutableStateFlow("Waiting for your phone")
    val status: StateFlow<String> = _status

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code

    private val _address = MutableStateFlow("")
    /** The car's address on the local network, for typing into the phone app if needed. */
    val address: StateFlow<String> = _address

    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun start() {
        if (server != null) return
        scope.launch(Dispatchers.IO) {
            _code.value = settings.pairingCode()
            // A fixed port lets the phone use a typed address when the network blocks discovery.
            val socket = runCatching { ServerSocket(PORT) }.recoverCatching { ServerSocket(0) }.getOrElse {
                _status.value = "Phone link could not start: ${it.message}"
                return@launch
            }
            server = socket
            _address.value = localAddresses().joinToString { "$it:${socket.localPort}" }
            announce(socket.localPort)
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) { serve(client) }
            }
        }
    }

    private fun announce(port: Int) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                _status.value = "Phone link could not announce itself (error $error)"
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 0
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val hello = runCatching { JSONObject(reader.readLine() ?: return) }.getOrNull() ?: return
            if (hello.optString("type") != "hello" || hello.optString("code") != _code.value) {
                writer.write("""{"type":"denied"}""" + "\n")
                writer.flush()
                _status.value = "A phone tried to connect with the wrong code"
                return
            }
            val phone = hello.optString("name", "your phone")
            writer.write("""{"type":"welcome","name":"Selawi Cockpit"}""" + "\n")
            writer.flush()
            _status.value = "Connected to $phone"
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    val message = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (message.optString("type") == "nav") handleNav(message)
                }
            } catch (_: Exception) {
                // Connection dropped; fall through.
            }
            _guidance.value = null
            _status.value = "Waiting for your phone"
        }
    }

    private fun handleNav(message: JSONObject) {
        if (!message.optBoolean("active")) {
            _guidance.value = null
            return
        }
        val icon = message.optString("icon").takeIf { it.isNotEmpty() }?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        _guidance.value = NavGuidance(
            distance = message.optString("title"),
            instruction = message.optString("text"),
            eta = message.optString("sub"),
            icon = icon ?: _guidance.value?.icon,
        )
    }

    private fun localAddresses(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>()
            .map { it.hostAddress.orEmpty() }
    }.getOrDefault(emptyList())

    companion object {
        const val PORT = 47800
        const val SERVICE_TYPE = "_selawicockpit._tcp."
        const val SERVICE_NAME = "Selawi Cockpit"
    }
}
