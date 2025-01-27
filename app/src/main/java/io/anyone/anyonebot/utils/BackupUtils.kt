package io.anyone.anyonebot.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import io.anyone.anyonebot.R
import io.anyone.anyonebot.service.AnyoneBotConstants
import io.anyone.anyonebot.service.AnyoneBotService
import io.anyone.anyonebot.ui.hostedservices.HostedServicesContentProvider
import io.anyone.anyonebot.ui.clientauth.ClientAuthContentProvider
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.Charset

class BackupUtils(private val mContext: Context) {

    private val basePath: File
        get() = File(mContext.filesDir.absolutePath, AnyoneBotConstants.ANON_SERVICES_DIR)


    fun createZipBackup(relativePath: String, zipFile: Uri): String? {
        val files = createFilesForZipping(relativePath) ?: return null

        val zip = ZipUtils(files, zipFile, mContext.contentResolver)

        if (!zip.canZip()) return null

        return zipFile.path
    }

    fun createAuthBackup(domain: String?, keyHash: String?, backupFile: Uri): String? {
        val fileText = AnyoneBotService.buildV3ClientAuthFile(domain, keyHash)

        try {
            val pfd = mContext.contentResolver.openFileDescriptor(backupFile, "w") ?: return null
            val fos = FileOutputStream(pfd.fileDescriptor)
            fos.write(fileText.toByteArray())
            fos.close()
            pfd.close()
        }
        catch (ioe: IOException) {
            return null
        }

        return backupFile.path
    }

    // todo this doesn't export data for onions that orbot hosts which have authentication (not supported yet...)
    private fun createFilesForZipping(relativePath: String): Array<String>? {
        val basePath = "$basePath/$relativePath/"
        val hostnamePath = "${basePath}hostname"
        val configFilePath = "$basePath$CONFIG_FILE_NAME"
        val privKeyPath = "${basePath}hs_ed25519_secret_key"
        val pubKeyPath = "${basePath}hs_ed25519_public_key"

        val portData = mContext.contentResolver.query(
            HostedServicesContentProvider.CONTENT_URI,
            HostedServicesContentProvider.PROJECTION,
            "${HostedServicesContentProvider.HostedService.PATH} = \"$relativePath\"",
            null,
            null
        )

        val config = JSONObject()
        try {
            if (portData == null || portData.count != 1) return null

            portData.moveToNext()

            config.put(
                HostedServicesContentProvider.HostedService.NAME,
                portData.getString(HostedServicesContentProvider.HostedService.NAME))

            config.put(
                HostedServicesContentProvider.HostedService.PORT,
                portData.getString(HostedServicesContentProvider.HostedService.PORT))

            config.put(
                HostedServicesContentProvider.HostedService.ANON_PORT,
                portData.getString(HostedServicesContentProvider.HostedService.ANON_PORT))

            config.put(
                HostedServicesContentProvider.HostedService.DOMAIN,
                portData.getString(HostedServicesContentProvider.HostedService.DOMAIN))

            config.put(
                HostedServicesContentProvider.HostedService.CREATED_BY_USER,
                portData.getString(HostedServicesContentProvider.HostedService.CREATED_BY_USER))

            config.put(
                HostedServicesContentProvider.HostedService.ENABLED,
                portData.getString(HostedServicesContentProvider.HostedService.ENABLED))

            portData.close()

            val fileWriter = FileWriter(configFilePath)
            fileWriter.write(config.toString())
            fileWriter.close()
        }
        catch (ioe: JSONException) {
            ioe.printStackTrace()
            return null
        }
        catch (ioe: IOException) {
            ioe.printStackTrace()
            return null
        }

        return arrayOf(hostnamePath, configFilePath, privKeyPath, pubKeyPath)
    }

    private fun extractConfigFromUnzippedBackup(backupName: String) {
        val basePath = basePath
        val dir = backupName.substring(0, backupName.lastIndexOf('.'))
        val configFilePath = "$basePath/$dir/$CONFIG_FILE_NAME"

        val path = File(basePath.absolutePath, dir)
        if (!path.isDirectory) path.mkdirs()

        val configFile = File(configFilePath)

        try {
            val fis = FileInputStream(configFile)
            val fc = fis.channel
            val bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size())
            val jsonString = Charset.defaultCharset().decode(bb).toString()
            val savedValues = JSONObject(jsonString)
            val fields = ContentValues()

            val port = savedValues.getInt(HostedServicesContentProvider.HostedService.PORT)
            fields.put(HostedServicesContentProvider.HostedService.PORT, port)

            fields.put(
                HostedServicesContentProvider.HostedService.NAME,
                savedValues.getString(HostedServicesContentProvider.HostedService.NAME))

            fields.put(
                HostedServicesContentProvider.HostedService.ANON_PORT,
                savedValues.getInt(HostedServicesContentProvider.HostedService.ANON_PORT))

            fields.put(
                HostedServicesContentProvider.HostedService.DOMAIN,
                savedValues.getString(HostedServicesContentProvider.HostedService.DOMAIN))

            fields.put(
                HostedServicesContentProvider.HostedService.CREATED_BY_USER,
                savedValues.getInt(HostedServicesContentProvider.HostedService.CREATED_BY_USER))

            fields.put(
                HostedServicesContentProvider.HostedService.ENABLED,
                savedValues.getInt(HostedServicesContentProvider.HostedService.ENABLED))

            val dbService = mContext.contentResolver.query(
                HostedServicesContentProvider.CONTENT_URI, HostedServicesContentProvider.PROJECTION,
                "${HostedServicesContentProvider.HostedService.PORT} = $port", null, null)

            if (dbService == null || dbService.count == 0) {
                mContext.contentResolver.insert(HostedServicesContentProvider.CONTENT_URI, fields)
            }
            else {
                mContext.contentResolver.update(
                    HostedServicesContentProvider.CONTENT_URI, fields,
                    "${HostedServicesContentProvider.HostedService.PORT} = $port", null)
            }

            dbService?.close()

            configFile.delete()

            if (path.renameTo(File(basePath, "/v3$port"))) {
                Toast.makeText(mContext, R.string.backup_restored, Toast.LENGTH_LONG).show()
            }
            else {
                // collision, clean up files
                for (file in path.listFiles() ?: emptyArray()) {
                    file.delete()
                }

                path.delete()

                Toast.makeText(mContext, mContext.getString(R.string.backup_port_exist, ("" + port)),
                    Toast.LENGTH_LONG).show()
            }
        }
        catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show()
        }
        catch (e: JSONException) {
            e.printStackTrace()
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show()
        }
    }

    fun restoreZipBackup(zipUri: Uri) {
        val cursor = mContext.contentResolver.query(zipUri, null, null, null, null) ?: return

        cursor.moveToFirst()
        val backupName = cursor.getString(OpenableColumns.DISPLAY_NAME)
        cursor.close()

        if (backupName.isNullOrBlank()) return

        val dir = backupName.substring(0, backupName.lastIndexOf('.'))
        val path = File(basePath.absolutePath, dir)

        if (ZipUtils(emptyArray(), zipUri, mContext.contentResolver).unzip(path.absolutePath)) {
            extractConfigFromUnzippedBackup(backupName)
        }
        else {
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show()
        }
    }

    fun restoreClientAuthBackup(authFileContents: String) {
        val split = authFileContents.split(":").dropLastWhile { it.isEmpty() }.toTypedArray()

        if (split.size != 4) {
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show()
            return
        }

        val fields = ContentValues()
        fields.put(ClientAuthContentProvider.ClientAuth.DOMAIN, split[0])
        fields.put(ClientAuthContentProvider.ClientAuth.HASH, split[3])

        mContext.contentResolver.insert(ClientAuthContentProvider.CONTENT_URI, fields)

        Toast.makeText(mContext, R.string.backup_restored, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val CONFIG_FILE_NAME = "config.json"
    }
}
