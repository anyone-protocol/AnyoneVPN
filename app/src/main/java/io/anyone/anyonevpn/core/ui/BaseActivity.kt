package io.anyone.anyonevpn.core.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.anyone.anyonevpn.service.util.Prefs

open class BaseActivity : AppCompatActivity(), OnApplyWindowInsetsListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resetSecureFlags()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), this)
    }

    override fun onResume() {
        super.onResume()
        resetSecureFlags()
    }

    open fun resetSecureFlags() {
        if (Prefs.isSecureWindow)
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val spacing = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout())

        v.setPadding(spacing.left, spacing.top, spacing.right, spacing.bottom)

        return insets
    }
}