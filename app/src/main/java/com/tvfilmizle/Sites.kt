package com.tvfilmizle

enum class SiteKind { SITE, CUSTOM, REFRESH }

data class Site(
    val name: String,
    val url: String,
    val kind: SiteKind = SiteKind.SITE
)

object Sites {
    /** Cevrimdisi ilk acilista kullanilan varsayilan liste (guncel liste GitHub'daki sites.json'dan gelir). */
    val DEFAULT = listOf(
        Site("HD Film Cehennemi", "https://www.hdfilmcehennemi.nl/"),
        Site("Film Makinesi", "https://filmmakinesi.to/"),
        Site("Film Dozu", "https://filmdozu.website/"),
        Site("HD Film Izle", "https://www.hdfilmizle.to/"),
        Site("U Film Izle", "https://ufilmizle.com/"),
        Site("JetFilmizle", "https://jetfilmizle.sbs/"),
        Site("SineMerkez", "https://sinemerkez.com/"),
        Site("İzle Son HD Film", "https://izlesonhdfilm.com/"),
        Site("Film İzle Ya", "https://filmizleya.com/"),
        Site("HDFilmizlesende", "https://hdfilmizlesende.com/"),
        Site("HDFilmZevki", "https://hdfilmzevki.co/"),
        Site("FilmIzzle", "https://filmizzle.com/"),
        Site("HD Film İzle", "https://film-izle.com.tr/"),
        Site("HD Film ve Dizi İzle", "https://filmcenneti.org/")
    )
}
