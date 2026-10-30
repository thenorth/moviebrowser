package com.tvfilmizle

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cloudflare WARP istemcisi (WireGuard uzerinden).
 * Kayit API'sinden ucretsiz WARP profili alinir, GoBackend ile tunel acilir.
 * Tum uygulama trafigi (WebView dahil) tunelden gecer.
 */
object Warp {

    private const val API = "https://api.cloudflareclient.com/v0a2158/reg"
    private const val TAG = "Warp"

    @Volatile
    var connected = false
        private set

    private var backend: GoBackend? = null
    private var tunnel: Tunnel? = null
    private var config: Config? = null

    fun connect(ctx: Context, done: (ok: Boolean, error: String) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            try {
                val kp = KeyPair()
                val body = JSONObject()
                    .put("key", kp.publicKey.toBase64())
                    .put("install_id", "")
                    .put("fcm_token", "")
                    .put(
                        "tos",
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
                    )
                    .put("model", "PC")
                    .put("serial_number", "")
                    .put("locale", "tr_TR")

                val cfg = JSONObject(post(API, body.toString())).getJSONObject("config")
                val peerJson = cfg.getJSONArray("peers").getJSONObject(0)
                val addresses = cfg.getJSONObject("interface").getJSONObject("addresses")
                val endpoint = peerJson.getJSONObject("endpoint")
                val endpointIp = endpoint.getString("v4").substringBeforeLast(":")
                val endpointPort = endpoint.getJSONArray("ports").getInt(0)

                val iface = Interface.Builder()
                    .parsePrivateKey(kp.privateKey.toBase64())
                    .addAddress(InetNetwork.parse(addresses.getString("v4") + "/32"))
                    .addAddress(InetNetwork.parse(addresses.getString("v6") + "/128"))
                    .addDnsServer(InetAddress.getByName("1.1.1.1"))
                    .addDnsServer(InetAddress.getByName("1.0.0.1"))
                    .setMtu(1280)
                    .build()

                val peer = Peer.Builder()
                    .parsePublicKey(peerJson.getString("public_key"))
                    .parseEndpoint(endpointIp + ":" + endpointPort)
                    .parseAllowedIPs("0.0.0.0/0, ::/0")
                    .parsePersistentKeepalive("25")
                    .build()

                val cfgObject = Config.Builder().setInterface(iface).addPeer(peer).build()
                val t = object : Tunnel {
                    override fun getName() = "warp"
                    override fun onStateChange(newState: Tunnel.State) = Unit
                }
                val be = GoBackend(app)
                val state = be.setState(t, Tunnel.State.UP, cfgObject)
                if (state != Tunnel.State.UP) {
                    throw IllegalStateException("Tünel açılmadı (" + state + ")")
                }

                backend = be
                tunnel = t
                config = cfgObject
                connected = true
                done(true, "")
            } catch (e: Exception) {
                Log.w(TAG, "WARP baglantisi kurulamadi", e)
                connected = false
                done(false, e.message ?: e.javaClass.simpleName)
            }
        }.start()
    }

    fun disconnect() {
        val be = backend
        val t = tunnel
        val c = config
        connected = false
        backend = null
        tunnel = null
        config = null
        if (be == null) return
        Thread {
            try {
                if (t != null && c != null) be.setState(t, Tunnel.State.DOWN, c)
            } catch (e: Exception) {
                Log.w(TAG, "WARP kapatilamadi", e)
            }
        }.start()
    }

    private fun post(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("CF-Client-Version", "a-6.30-2158")
        c.setRequestProperty("User-Agent", "okhttp/3.12.1")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP " + code)
        return text
    }
}
