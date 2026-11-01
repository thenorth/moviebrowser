package com.tvfilmizle

/** Oynatilan bolum bitince otomatik gecilecek sonraki bolum. */
object AutoNext {

    data class Item(
        val label: String,
        val url: String,
        val seriesTitle: String,
        val seriesUrl: String,
        val poster: String,
        val site: String,
        val seriesKey: String
    )

    /** Bir sonraki adim; tarayici video bitince kullanip temizler. */
    @Volatile
    var pending: Item? = null
}
