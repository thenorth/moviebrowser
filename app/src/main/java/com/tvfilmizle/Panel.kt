package com.tvfilmizle

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager

/** Bolumu ana ekranin saginda acar; soldaki sabit menu gorunur kalir. */
object Panel {

    fun apply(a: Activity) {
        val w = a.window
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dm = a.resources.displayMetrics
        val width = (dm.widthPixels - (248 * dm.density).toInt()).coerceAtLeast(dm.widthPixels / 2)
        // setLayout bazi cihazlarda yok sayiliyor; pencere ozelliklerini de yaz.
        val lp = w.attributes
        lp.gravity = Gravity.END
        lp.width = width
        lp.height = WindowManager.LayoutParams.MATCH_PARENT
        w.attributes = lp
        w.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT)
    }
}
