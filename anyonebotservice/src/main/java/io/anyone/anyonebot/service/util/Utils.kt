/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package io.anyone.anyonebot.service.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

object Utils {

    @JvmStatic
    fun isPortOpen(ip: String?, port: Int, timeout: Int): Boolean {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeout)
            socket.close()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    @JvmStatic
    fun readInputStreamAsString(stream: InputStream?): String {
        var line: String?

        val out = StringBuilder()

        try {
            val reader = BufferedReader(InputStreamReader(stream))

            while ((reader.readLine().also { line = it }) != null) {
                out.append(line)
                out.append('\n')
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return out.toString()
    }

    @JvmStatic
    fun convertCountryCodeToFlagEmoji(countryCode: String): String {
        var countryCode = countryCode
        countryCode = countryCode.uppercase(Locale.getDefault())
        val flagOffset = 0x1F1E6
        val asciiOffset = 0x41
        val firstChar = Character.codePointAt(countryCode, 0) - asciiOffset + flagOffset
        val secondChar = Character.codePointAt(countryCode, 1) - asciiOffset + flagOffset
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    fun emojiToDrawable(context: Context?, emoji: String, size: Float = 48f): BitmapDrawable? {
        val context = context ?: return null

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
        }
        val width = paint.measureText(emoji).toInt()
        val height = (paint.fontMetrics.bottom - paint.fontMetrics.top).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        canvas.drawText(emoji, 0f, -paint.fontMetrics.top, paint)

        return bitmap.toDrawable(context.resources)
    }
}
