package com.example.virtualtwitchdroid.feature.voice.assets

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class TarGzTest {

    private fun header(name: String, size: Long, type: Char): ByteArray {
        val h = ByteArray(512)
        name.toByteArray().copyInto(h, 0)
        "0000644".toByteArray().copyInto(h, 100)
        "0000000".toByteArray().copyInto(h, 108)
        "0000000".toByteArray().copyInto(h, 116)
        size.toString(8).padStart(11, '0').toByteArray().copyInto(h, 124)
        "00000000000".toByteArray().copyInto(h, 136)
        h[156] = type.code.toByte()
        "ustar".toByteArray().copyInto(h, 257)
        "00".toByteArray().copyInto(h, 263)
        // checksum: spaces while summing, then the octal value
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        val sum = h.sumOf { it.toInt() and 0xFF }
        sum.toString(8).padStart(6, '0').toByteArray().copyInto(h, 148)
        h[154] = 0
        h[155] = ' '.code.toByte()
        return h
    }

    private fun tarGz(entries: List<Triple<String, ByteArray?, Char>>): ByteArray {
        val raw = ByteArrayOutputStream()
        for ((name, data, type) in entries) {
            raw.write(header(name, data?.size?.toLong() ?: 0L, type))
            if (data != null) {
                raw.write(data)
                val pad = (512 - data.size % 512) % 512
                raw.write(ByteArray(pad))
            }
        }
        raw.write(ByteArray(1024))
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw.toByteArray()) }
        return out.toByteArray()
    }

    @Test
    fun extractsDirectoriesAndFiles_withCorrectContent() {
        val payload = ByteArray(700) { (it % 251).toByte() } // spans two 512-byte blocks
        val archive = tarGz(
            listOf(
                Triple("dic/", null, '5'),
                Triple("dic/sys.dic", payload, '0'),
                Triple("dic/COPYING", "BSD".toByteArray(), '0'),
            ),
        )
        val dir = File.createTempFile("targz", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            TarGz.extract(archive.inputStream(), dir)
            assertTrue(File(dir, "dic").isDirectory)
            assertEquals(payload.toList(), File(dir, "dic/sys.dic").readBytes().toList())
            assertEquals("BSD", File(dir, "dic/COPYING").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectsEntriesThatEscapeTheTargetDirectory() {
        val dir = File.createTempFile("targz", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            assertFailsWith<IllegalArgumentException> {
                TarGz.extract(tarGz(listOf(Triple("../evil.txt", "x".toByteArray(), '0'))).inputStream(), dir)
            }
            // A sibling directory that merely shares the target's name prefix is still outside it.
            assertFailsWith<IllegalArgumentException> {
                TarGz.extract(tarGz(listOf(Triple("../${dir.name}2/x", "x".toByteArray(), '0'))).inputStream(), dir)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun truncatedHeader_isAnError_notASilentEnd() {
        val whole = tarGz(listOf(Triple("a.txt", "hello".toByteArray(), '0')))
        // Re-gzip a stream cut in the middle of the first header.
        val raw = java.util.zip.GZIPInputStream(whole.inputStream()).readBytes()
        val cut = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(raw, 0, 200) }
        }.toByteArray()
        val dir = File.createTempFile("targz", "").also {
            it.delete()
            it.mkdirs()
        }
        try {
            assertFailsWith<IllegalStateException> { TarGz.extract(cut.inputStream(), dir) }
        } finally {
            dir.deleteRecursively()
        }
    }
}
