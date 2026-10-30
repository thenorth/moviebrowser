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
 * Cloudflare WARP istemcisi (WireGuard).
 * Kayit API'sinden ucretsiz profil alir, tuneli acar ve trafik gercekten gectigini
 * dogrular. Gecmezse farkli portlari dener, hepsi basarisizsa tuneli kapatir
 * (boylece kullanici internsiz kalmaz).
 */
object Warp {

    private const val API = "https://api.cloudflareclient.com/v0a2158/reg"
    private const val CHECK = "https://www.cloudflare.com/cdn-cgi/trace"
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
                val reg = register(kp.publicKey.toBase64())
                val cfgJson = reg.getJSONObject("config")
                val peerJson = cfgJson.getJSONArray("peers").getJSONObject(0)
                val addresses = cfgJson.getJSONObject("interface").getJSONObject("addresses")
                val endpoint = peerJson.getJSONObject("endpoint")
                val endpointIp = endpoint.getString("v4").substringBeforeLast(":")

                val ports = ArrayList<Int>()
                val arr = endpoint.getJSONArray("ports")
                for (i in 0 until arr.length()) ports.add(arr.getInt(i))
                if (ports.isEmpty()) ports.add(2408)

                var lastError = "bilinmeyen hata"
                for (port in ports.take(3)) {
                    val cfg = buildConfig(kp, peerJson, addresses, endpointIp, port)
                    val t = object : Tunnel {
                        override fun getName() = "warp"
                        override fun onStateChange(newState: Tunnel.State) = Unit
                    }
                    val be = GoBackend(app)
                    val state = try {
                        be.setState(t, Tunnel.State.UP, cfg)
                    } catch (e: Exception) {
                        Log.w(TAG, "port $port: tunel acilamadi", e)
                        lastError = e.message ?: "tünel açılamadı"
                        continue
                    }
                    if (state != Tunnel.State.UP) {
                        lastError = "tünel durumu " + state
                        continue
                    }
                    if (trafficFlows()) {
                        backend = be
                        tunnel = t
                        config = cfg
                        connected = true
                        done(true, "")
                        return@Thread
                    }
                    lastError = "port $port: trafik geçmedi"
                    try {
                        be.setState(t, Tunnel.State.DOWN, cfg)
                    } catch (e: Exception) {
                        Log.w(TAG, "port $port kapatilamadi", e)
                    }
                }
                connected = false
                done(false, lastError)
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

    private fun buildConfig(
        kp: KeyPair,
        peerJson: JSONObject,
        addresses: JSONObject,
        endpointIp: String,
        port: Int
    ): Config {
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
            .parseEndpoint(endpointIp + ":" + port)
            .parseAllowedIPs("0.0.0.0/0, ::/0")
            .parsePersistentKeepalive("25")
            .build()

        return Config.Builder().setInterface(iface).addPeer(peer).build()
    }

    /** Tunelden gercekten trafik geciyor mu? Kisa bir istek atar. */
    private fun trafficFlows(): Boolean {
        return try {
            val c = URL(CHECK).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.instanceFollowRedirects = true
            val code = c.responseCode
            val body = c.inputStream.bufferedReader().use(BufferedReader::readText)
            c.disconnect()
            code in 200..299 && body.contains("ip=")
        } catch (e: Exception) {
            Log.w(TAG, "trafik testi basarisiz: " + e.message)
            false
        }
    }

    private fun register(publicKey: String): JSONObject {
        val body = JSONObject()
            .put("key", publicKey)
            .put("install_id", "")
            .put("fcm_token", "")
            .put("tos", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
            .put("model", "PC")
            .put("serial_number", "")
            .put("locale", "tr_TR")

        val c = URL(API).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("CF-Client-Version", "a-6.30-2158")
        c.setRequestProperty("User-Agent", "okhttp/3.12.1")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP " + code)
        return JSONObject(text)
    }
}
