package io.anyone.anyonebot.ui.hostedservices

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HostedServicesDatabase internal constructor(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(HOSTED_SERVICES_CREATE_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Nothing to upgrade, yet.
    }

    companion object {
        const val DATABASE_NAME: String = "hosted_services"
        const val HOSTED_SERVICES_TABLE_NAME: String = "hosted_services"
        private const val DATABASE_VERSION = 1

        private const val HOSTED_SERVICES_CREATE_SQL =
            "CREATE TABLE " + HOSTED_SERVICES_TABLE_NAME + " (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, " +
                    "domain TEXT, " +
                    "anon_port INTEGER, " +
                    "created_by_user INTEGER DEFAULT 0, " +
                    "enabled INTEGER DEFAULT 1, " +
                    "port INTEGER, " +
                    "filepath TEXT);"
    }
}
