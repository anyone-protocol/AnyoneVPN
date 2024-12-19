package io.anyone.anyonebot.ui.hostedservices

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import io.anyone.anyonebot.BuildConfig
import io.anyone.anyonebot.utils.AnyoneBotDatabase

class HostedServicesContentProvider : ContentProvider() {

    private lateinit var mDatabase: AnyoneBotDatabase

    override fun onCreate(): Boolean {
        mDatabase = AnyoneBotDatabase(context)

        return true
    }

    @Suppress("NAME_SHADOWING")
    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        var selection = selection

        if (uriMatcher.match(uri) == ANON_ID) {
            selection = "_id = ${uri.lastPathSegment}"
        }

        return mDatabase.readableDatabase.query(
            AnyoneBotDatabase.HOSTED_SERVICES_TABLE_NAME,
            projection, selection, selectionArgs, null, null, sortOrder)
    }

    override fun getType(uri: Uri): String? {
        val match = uriMatcher.match(uri)

        return when (match) {
            ANONS -> "vnd.android.cursor.dir/vnd.anyone.anons"
            ANON_ID -> "vnd.android.cursor.item/vnd.anyone.anon"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val regId = mDatabase.writableDatabase.insert(
            AnyoneBotDatabase.HOSTED_SERVICES_TABLE_NAME,
            null, values)

        context?.contentResolver?.notifyChange(CONTENT_URI, null)

        return ContentUris.withAppendedId(CONTENT_URI, regId)
    }

    @Suppress("NAME_SHADOWING")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        var selection = selection

        if (uriMatcher.match(uri) == ANON_ID) {
            selection = "_id = ${uri.lastPathSegment}"
        }
        val rows = mDatabase.writableDatabase.delete(
            AnyoneBotDatabase.HOSTED_SERVICES_TABLE_NAME,
            selection, selectionArgs)

        context?.contentResolver?.notifyChange(CONTENT_URI, null)

        return rows
    }

    @Suppress("NAME_SHADOWING")
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?
    ): Int {
        var selection = selection

        if (uriMatcher.match(uri) == ANON_ID) selection = "_id = ${uri.lastPathSegment}"

        val rows = mDatabase.writableDatabase.update(
            AnyoneBotDatabase.HOSTED_SERVICES_TABLE_NAME,
            values, selection, null)

        context?.contentResolver?.notifyChange(CONTENT_URI, null)

        return rows
    }

    object HostedService : BaseColumns {
        const val ID: String = "_id"
        const val NAME: String = "name"
        const val PORT: String = "port"
        const val ANON_PORT: String = "anon_port"
        const val DOMAIN: String = "domain"
        const val CREATED_BY_USER: String = "created_by_user"
        const val ENABLED: String = "enabled"
        const val PATH: String = "filepath"
    }

    companion object {
        @JvmField
        val PROJECTION: Array<String> = arrayOf(
            HostedService.ID,
            HostedService.NAME,
            HostedService.PORT,
            HostedService.DOMAIN,
            HostedService.ANON_PORT,
            HostedService.CREATED_BY_USER,
            HostedService.ENABLED,
            HostedService.PATH
        )

        private const val ANONS = 1
        private const val ANON_ID = 2
        private const val AUTH = "${BuildConfig.APPLICATION_ID}.ui.hostedservices"

        @JvmField
        val CONTENT_URI: Uri = Uri.parse("content://$AUTH/v3")

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            uriMatcher.addURI(AUTH, "v3", ANONS)
            uriMatcher.addURI(AUTH, "v3/#", ANON_ID)
        }
    }
}
