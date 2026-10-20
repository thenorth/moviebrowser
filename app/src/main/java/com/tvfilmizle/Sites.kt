package com.tvfilmizle

enum class SiteKind { SITE, CUSTOM, REFRESH }

data class Site(
    val name: String,
    val url: String,
    val kind: SiteKind = SiteKind.SITE
)

object Sites {
    /** Cevrimdisi ilk acilista kullanilan varsayilan liste (guncel liste GitHub'dan gelir). */
    val DEFAULT = listOf(
        Site("HD Film Cehennemi", "https://www.hdfilmcehennemi.nl/"),
        Site("Film Makinesi", "https://filmmakinesi.to/"),
        Site("Film Dozu", "https://filmdozu.website/"),
        Site("HD Film Izle", "https://www.hdfilmizle.to/"),
        Site("U Film Izle", "https://ufilmizle.com/")
    )
}
