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
        w.setGravity(Gravity.END)
        val dm = a.resources.displayMetrics
        w.setLayout(
            dm.widthPixels - (248 * dm.density).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }
}
