package com.tvfilmizle

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.WindowManager
import android.widget.Switch
import android.widget.TextView

/** Ayarlar: VPN (WARP) ac/kapa. */
class SettingsActivity : Activity() {

    private companion object {
        const val REQ_VPN = 42
    }

    private lateinit var toggle: Switch
    private lateinit var status: TextView
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        toggle = findViewById(R.id.vpn_switch)
        status = findViewById(R.id.vpn_status)

        toggle.isChecked = Warp.connected
        renderStatus(if (Warp.connected) getString(R.string.vpn_on) else getString(R.string.vpn_off))

        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (updating) return@setOnCheckedChangeListener
            if (isChecked) enableVpn() else disableVpn()
        }

        val ua = findViewById<Switch>(R.id.ua_switch)
        ua.isChecked = Prefs.desktopUa(this)
        ua.setOnCheckedChangeListener { _, isChecked -> Prefs.setDesktopUa(this, isChecked) }
    }

    private fun enableVpn() {
        if (Warp.connected) return
        val prep = VpnService.prepare(this)
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN)
            return
        }
        startWarp()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) startWarp() else {
                updating = true
                toggle.isChecked = false
                updating = false
                renderStatus(getString(R.string.vpn_off))
            }
        }
    }

    private fun startWarp() {
        renderStatus(getString(R.string.vpn_connecting))
        toggle.isEnabled = false
        Warp.connect(this) { ok, error ->
            runOnUiThread {
                toggle.isEnabled = true
                updating = true
                toggle.isChecked = ok
                updating = false
                renderStatus(if (ok) getString(R.string.vpn_on) else getString(R.string.vpn_failed, error))
            }
        }
    }

    private fun disableVpn() {
        Warp.disconnect()
        renderStatus(getString(R.string.vpn_off))
    }

    private fun renderStatus(text: String) {
        status.text = text
    }
}
