package io.anyone.anyonevpn.core.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : BaseActivity(), OnApplyWindowInsetsListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsPreferencesFragment())
            .commit()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val spacing = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout())

        v.setPadding(spacing.left, spacing.top, spacing.right, spacing.bottom)

        return insets
    }
}
