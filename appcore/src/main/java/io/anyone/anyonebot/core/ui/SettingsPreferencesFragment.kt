/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package io.anyone.anyonebot.core.ui

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.preference.*
import io.anyone.anyonebot.core.Languages
import io.anyone.anyonebot.service.util.Prefs
import io.anyone.anyonebot.core.R

class SettingsPreferencesFragment : PreferenceFragmentCompat() {
    private var prefLocale: ListPreference? = null

    private fun initPrefs () {
        setNoPersonalizedLearningOnEditTextPreferences()

        prefLocale = findPreference("pref_default_locale")
        val languages = Languages[requireActivity()]
        prefLocale?.entries = languages?.allNames
        prefLocale?.entryValues = languages?.supportedLocales
        prefLocale?.value = Prefs.defaultLocale
        prefLocale?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                val language = newValue as String?
                val intentResult = Intent()
                intentResult.putExtra("locale", language)
                requireActivity().setResult(RESULT_OK, intentResult)
                requireActivity().finish()
                false
            }

        // kludge for #992
        val categoryNodeConfig = findPreference<Preference>("category_node_config")
        categoryNodeConfig?.title = "${categoryNodeConfig.title}" + "\n\n" + "${categoryNodeConfig.summary}"
        categoryNodeConfig?.summary = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // if defined in XML, disable the persistent notification preference on Oreo+
            findPreference<Preference>("pref_persistent_notifications")?.let {
                it.parent?.removePreference(it)
            }
        }

        val prefFlagSecure = findPreference<CheckBoxPreference>("pref_flag_secure")
        prefFlagSecure?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->

            Prefs.isSecureWindow = newValue as Boolean
            (activity as BaseActivity).resetSecureFlags()

            true
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences, rootKey)
        initPrefs()
    }

    private fun setNoPersonalizedLearningOnEditTextPreferences() {
        val preferenceScreen = preferenceScreen
        val categoryCount = preferenceScreen.preferenceCount

        for (i in 0 until categoryCount) {
            var p = preferenceScreen.getPreference(i)

            if (p is PreferenceCategory) {
                val pc = p
                val preferenceCount = pc.preferenceCount

                for (j in 0 until preferenceCount) {
                    p = pc.getPreference(j)

                    if (p is EditTextPreference) {
                        p.setOnBindEditTextListener {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                it.imeOptions = it.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                            }
                        }
                    }
                }
            }
        }
    }
}
