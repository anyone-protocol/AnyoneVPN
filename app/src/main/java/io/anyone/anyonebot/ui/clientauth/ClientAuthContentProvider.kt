package io.anyone.anyonebot.ui.clientauth

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import io.anyone.anyonebot.BuildConfig
import io.anyone.anyonebot.utils.AnyoneBotDatabase

class ClientAuthContentProvider : ContentProvider() {
    private lateinit var mDatabase: AnyoneBotDatabase

    override fun onCreate(): Boolean {
        mDatabase = AnyoneBotDatabase(context)

        return true
    }

    override fun getType(uri: Uri): String? {
        val match = uriMatcher.match(uri)
        return when (match) {
            V3AUTHS -> "vnd.android.cursor.dir/vnd.anyone.v3auths"
            V3AUTH_ID -> "vnd.android.cursor.item/vnd.anyone.v3auth"
            else -> null
        }
    }

    @Suppress("NAME_SHADOWING")
    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        var selection = selection
        if (uriMatcher.match(uri) == V3AUTH_ID) selection = "_id = ${uri.lastPathSegment}"

        return mDatabase.readableDatabase.query(
            AnyoneBotDatabase.CLIENT_AUTHS_TABLE_NAME,
            projection, selection, selectionArgs, null, null, sortOrder)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val regId = mDatabase.writableDatabase.insert(
            AnyoneBotDatabase.CLIENT_AUTHS_TABLE_NAME,
            null, values)

        context?.contentResolver?.notifyChange(CONTENT_URI, null)

        return ContentUris.withAppendedId(CONTENT_URI, regId)
    }

    @Suppress("NAME_SHADOWING")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        var selection = selection
        if (uriMatcher.match(uri) == V3AUTH_ID) selection = "_id = ${uri.lastPathSegment}"

        val rows = mDatabase.writableDatabase.delete(
            AnyoneBotDatabase.CLIENT_AUTHS_TABLE_NAME,
            selection, selectionArgs)

        context?.contentResolver?.notifyChange(CONTENT_URI, null)

        return rows
    }

    @Suppress("NAME_SHADOWING")
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?
    ): Int {
        var selection = selection
        if (uriMatcher.match(uri) == V3AUTH_ID) selection = "id_ = ${uri.lastPathSegment}"

        val rows = mDatabase.writableDatabase.update(
            AnyoneBotDatabase.CLIENT_AUTHS_TABLE_NAME,
            values, selection, null)

        context?.contentResolver?.notifyChange(CONTENT_URI, null)

        return rows
    }

    object ClientAuth : BaseColumns {
        const val ID: String = "_id"
        const val DOMAIN: String = "domain"
        const val HASH: String = "hash"
        const val ENABLED: String = "enabled"
    }

    companion object {
        val PROJECTION: Array<String> = arrayOf(
            ClientAuth.ID,
            ClientAuth.DOMAIN,
            ClientAuth.HASH,
            ClientAuth.ENABLED,
        )

        private const val AUTH = BuildConfig.APPLICATION_ID + ".ui.hostedservices.clientauth"

        @JvmField
        val CONTENT_URI: Uri = Uri.parse("content://$AUTH/v3auth")

        private const val V3AUTHS = 1
        private const val V3AUTH_ID = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            uriMatcher.addURI(AUTH, "v3auth", V3AUTHS)
            uriMatcher.addURI(AUTH, "v3auth/#", V3AUTH_ID)
        }
    }
}
