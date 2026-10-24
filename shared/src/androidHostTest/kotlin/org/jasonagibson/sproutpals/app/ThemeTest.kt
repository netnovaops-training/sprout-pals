package org.jasonagibson.sproutpals.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ThemeTest {
    @Test fun `the theme renders, loops after the fanfare, and never clips silent`() {
        val rate = 22_050
        val (pcm, loopFrom) = Theme.render(rate)
        assertTrue(loopFrom in 1 until pcm.size)
        assertTrue("about half a minute", pcm.size / rate in 25..35)
        assertTrue(pcm.any { it > 2000 })
        writeWav(File("build/theme-preview.wav"), pcm, rate)   // to listen to on a PC
    }

    private fun writeWav(f: File, pcm: ShortArray, rate: Int) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        f.parentFile?.mkdirs()
        f.writeBytes(h + data)
    }
}
