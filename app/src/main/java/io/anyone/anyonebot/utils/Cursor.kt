package io.anyone.anyonebot.utils

import android.database.Cursor

fun Cursor.getString(columnName: String): String? {
    val idx = getColumnIndex(columnName)
    if (idx < 0) return null

    return getString(idx)
}

fun Cursor.getInt(columnName: String): Int? {
    val idx = getColumnIndex(columnName)
    if (idx < 0) return null

    return getInt(idx)
}