package com.tvfilmizle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Ayarlar: VPN (WARP), masaüstü görünüm, otomatik tam ekran, uyku zamanlayıcısı, veri temizleme. */
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
        Panel.apply(this)

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

        val fs = findViewById<Switch>(R.id.fs_switch)
        fs.isChecked = Prefs.autoFullscreen(this)
        fs.setOnCheckedChangeListener { _, isChecked -> Prefs.setAutoFullscreen(this, isChecked) }

        val sleepValue = findViewById<TextView>(R.id.sleep_value)
        sleepValue.text = SleepTimer.remainingText(this)
        findViewById<android.view.View>(R.id.sleep_row).setOnClickListener { pickSleep(sleepValue) }
        findViewById<android.view.View>(R.id.clear_row).setOnClickListener { confirmClear() }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        findViewById<TextView>(R.id.version).text = getString(R.string.version_fmt, version)
    }

    override fun onResume() {
        super.onResume()
        SleepTimer.attach(this)
    }

    /** SOL = menuye don. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
        ) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun pickSleep(value: TextView) {
        val labels = arrayOf(getString(R.string.sleep_off), "30 dk", "60 dk", "90 dk")
        val minutes = intArrayOf(0, 30, 60, 90)
        AlertDialog.Builder(this)
            .setTitle(R.string.sleep_title)
            .setItems(labels) { _, which ->
                SleepTimer.set(this, minutes[which])
                value.text = SleepTimer.remainingText(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.clear_confirm)
            .setPositiveButton(R.string.clear_title) { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                WebView(this).apply {
                    clearCache(true)
                    clearFormData()
                    destroy()
                }
                Toast.makeText(this, R.string.clear_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
