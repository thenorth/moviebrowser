package com.tvfilmizle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockTest {

    @Test
    fun videoKaynaklariEngellenmez() {
        assertFalse(AdBlock.isBlocked("https://cdn.example.com/hls/film.m3u8?token=abc"))
        assertFalse(AdBlock.isBlocked("https://cdn.example.com/movie.mp4"))
        assertFalse(AdBlock.isBlocked("https://cdn.example.com/stream.webm"))
    }

    @Test
    fun reklamKaynaklariEngellenir() {
        assertTrue(AdBlock.isBlocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlock.isBlocked("https://cdn.adsterra.com/script.js"))
        assertTrue(AdBlock.isBlocked("https://site.com/adservice/ad.php"))
        assertTrue(AdBlock.isBlocked("https://site.com/reklam/katman.html"))
    }

    @Test
    fun siteAdresleriEngellenmez() {
        assertFalse(AdBlock.isBlocked("https://www.hdfilmizle.to/film-izle"))
        assertFalse(AdBlock.isBlocked("https://filmmakinesi.to/movies/123"))
        assertFalse(AdBlock.isBlocked("https://www.hdfilmcehennemi.nl/wp-content/uploads/poster.jpg"))
    }
}
