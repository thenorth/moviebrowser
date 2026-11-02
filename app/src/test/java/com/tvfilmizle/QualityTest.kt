package com.tvfilmizle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityTest {

    private val master = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="tr",URI="audio.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=426x240,CODECS="avc1.42e00a",AUDIO="aac"
        240/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720,CODECS="avc1.4d401f",AUDIO="aac"
        720/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080,CODECS="avc1.640028",AUDIO="aac"
        1080/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="avc1.640028",AUDIO="aac"
        1080h/index.m3u8
    """.trimIndent()

    @Test
    fun dusukCozunurluklerAtilir() {
        val out = Quality.filterForTest(master)
        assertFalse("240p kalmamali", out.contains("426x240"))
        assertFalse("720p kalmamali", out.contains("1280x720"))
        assertTrue("1080p kalmali", out.contains("1080/index.m3u8"))
        assertTrue("1080p kalmali", out.contains("1080h/index.m3u8"))
        // Ses grubu korunmali
        assertTrue(out.contains("#EXT-X-MEDIA:TYPE=AUDIO"))
    }

    @Test
    fun hepsiDusukseEnIyiKalir() {
        val low = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=426x240
            a.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=854x480
            b.m3u8
        """.trimIndent()
        val out = Quality.filterForTest(low)
        assertFalse(out.contains("426x240"))
        assertTrue(out.contains("854x480") && out.contains("b.m3u8"))
    }

    @Test
    fun tekKaliteDegismez() {
        val one = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080
            1080.m3u8
        """.trimIndent()
        assertEquals(one, Quality.filterForTest(one))
    }

    @Test
    fun medyaListesiDegismez() {
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg1.ts\n"
        assertEquals(media, Quality.filterForTest(media))
    }
}
