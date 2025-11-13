package io.anyone.anyonevpn

import android.app.Application
import android.content.res.Configuration
import io.anyone.anyonevpn.core.Languages
import io.anyone.anyonevpn.core.LocaleHelper
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import io.anyone.anyonevpn.service.util.Prefs
import java.util.Locale

class AnyoneVpnApp : Application(),
    AnyoneVpnConstants {


    override fun onCreate() {
        super.onCreate()

//      useful for finding unclosed sockets...
//        StrictMode.setVmPolicy(
//            VmPolicy.Builder()
//                .detectLeakedClosableObjects()
//                .penaltyLog()
//                .build()
//        )

        Prefs.setContext(applicationContext)
        LocaleHelper.onAttach(applicationContext)

        Languages.setup(AnyoneVpnActivity::class.java, R.string.menu_settings)

        if (Prefs.defaultLocale != Locale.getDefault().language) {
            Languages.setLanguage(this, Prefs.defaultLocale, true)
        }

        // this code only runs on first install and app updates
        if (Prefs.currentVersionForUpdate < BuildConfig.VERSION_CODE) {
            Prefs.currentVersionForUpdate = BuildConfig.VERSION_CODE
            // don't do anything resource intensive here, instead set a flag to do the task later

            // tell AnyoneVpnService it needs to reinstall geoip
            Prefs.isGeoIpReinstallNeeded = true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Prefs.defaultLocale != Locale.getDefault().language) {
            Languages.setLanguage(this, Prefs.defaultLocale, true)
        }
    }

    fun setLocale() {
        val appLocale = Prefs.defaultLocale
        val systemLoc = Locale.getDefault().language

        if (appLocale != systemLoc) {
            Languages.setLanguage(this, appLocale, true)
        }
    }
}
