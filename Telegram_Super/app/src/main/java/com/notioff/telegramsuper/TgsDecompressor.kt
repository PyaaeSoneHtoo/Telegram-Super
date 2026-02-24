package com.notioff.telegramsuper

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

object TgsDecompressor {
    /**
     * Decompresses a .tgs file (which is GZipped Lottie JSON) into its string representation.
     */
    fun decompressTgs(file: File): String? {
        return try {
            FileInputStream(file).use { fis ->
                decompress(fis)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decompress(inputStream: InputStream): String {
        return GZIPInputStream(inputStream).bufferedReader().use { it.readText() }
    }
}
