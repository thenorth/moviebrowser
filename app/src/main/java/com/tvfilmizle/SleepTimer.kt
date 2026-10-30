package com.tvfilmizle

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.lang.ref.WeakReference

/** Uyku zamanlayicisi: sure bitince uygulama kapanir. */
object SleepTimer {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var host = WeakReference<Activity>(null)

    fun attach(activity: Activity) {
        host = WeakReference(activity)
        val minutes = Prefs.sleepMinutes(activity)
        if (minutes > 0) schedule(minutes) else cancel()
    }

    fun set(activity: Activity, minutes: Int) {
        Prefs.setSleepMinutes(activity, minutes)
        host = WeakReference(activity)
        if (minutes > 0) schedule(minutes) else cancel()
    }

    fun remainingText(ctx: Context): String {
        val m = Prefs.sleepMinutes(ctx)
        return if (m > 0) ctx.getString(R.string.sleep_on_fmt, m) else ctx.getString(R.string.sleep_off)
    }

    private fun schedule(minutes: Int) {
        cancel()
        val app = host.get()?.applicationContext
        val r = Runnable {
            if (app != null) Toast.makeText(app, R.string.sleep_done, Toast.LENGTH_LONG).show()
            Prefs.setSleepMinutes(app ?: return@Runnable, 0)
            host.get()?.finishAffinity()
        }
        runnable = r
        handler.postDelayed(r, minutes * 60_000L)
    }

    private fun cancel() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }
}
