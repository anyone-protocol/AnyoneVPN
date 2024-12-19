package io.anyone.anyonebot.utils

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtils(private val files: Array<String>, private val zipFile: Uri,
               private val contentResolver: ContentResolver
) {

    fun canZip(): Boolean {
        try {
            val pdf = contentResolver.openFileDescriptor(zipFile, "w") ?: return false

            val dest = FileOutputStream(pdf.fileDescriptor)
            val out = ZipOutputStream(BufferedOutputStream(dest))
            val data = ByteArray(BUFFER)
            var origin: BufferedInputStream

            for (file in files) {
                val entry = ZipEntry(file.substring(file.lastIndexOf("/") + 1))
                out.putNextEntry(entry)
                var count: Int

                val fi = FileInputStream(file)
                origin = BufferedInputStream(fi, BUFFER)

                while ((origin.read(data, 0, BUFFER).also { count = it }) != -1) {
                    out.write(data, 0, count)
                }

                origin.close()
            }

            out.close()
            dest.close()
            pdf.close()
        }
        catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        return true
    }

    fun unzip(outputPath: String): Boolean {
        val `is`: InputStream?

        try {
            `is` = contentResolver.openInputStream(zipFile)
            val zis = ZipInputStream(BufferedInputStream(`is`))
            val success = extractFromZipInputStream(outputPath, zis)

            `is`?.close()

            return success
        }
        catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    private fun extractFromZipInputStream(outputPath: String, zis: ZipInputStream): Boolean {
        val outputDir = File(outputPath)

        try {
            var ze: ZipEntry
            val buffer = ByteArray(1024)
            var count: Int

            outputDir.mkdirs()

            while ((zis.nextEntry.also { ze = it }) != null) {
                val filename = ze.name

                if (!HOSTED_SERVICE_CONFIG_FILES.contains(filename)) { // *any* kind of foreign file
                    for (writtenFile in outputDir.listFiles() ?: emptyArray()) {
                        writtenFile.delete()
                    }

                    outputDir.delete()

                    return false
                }

                // Need to create directories if not existing, or it will throw an Exception...
                if (ze.isDirectory) {
                    val fmd = File("$outputPath/$filename")
                    fmd.mkdirs()
                    continue
                }

                val fout = FileOutputStream("$outputPath/$filename")

                while ((zis.read(buffer).also { count = it }) != -1) {
                    fout.write(buffer, 0, count)
                }

                fout.close()
                zis.closeEntry()
            }

            zis.close()
        }
        catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        return true
    }

    companion object {
        private const val BUFFER = 2048
        const val ZIP_MIME_TYPE: String = "application/zip"

        private val HOSTED_SERVICE_CONFIG_FILES: List<String> = mutableListOf(
            "config.json",
            "hostname",
            "hs_ed25519_public_key",
            "hs_ed25519_secret_key"
        )
    }
}