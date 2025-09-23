package io.anyone.anyonebot.service.util

import android.content.Context
import android.content.SharedPreferences
import io.anyone.anyonebot.service.AnyoneBotConstants
import java.util.Locale
import androidx.core.content.edit

object Prefs {
    private const val PREF_DEFAULT_LOCALE = "pref_default_locale"
    private const val PREF_DETECT_ROOT = "pref_detect_root"
    private const val PREF_ENABLE_LOGGING = "pref_enable_logging"
    private const val PREF_START_ON_BOOT = "pref_start_boot"
    private const val PREF_ALLOW_BACKGROUND_STARTS = "pref_allow_background_starts"
    private const val PREF_OPEN_PROXY_ON_ALL_INTERFACES = "pref_open_proxy_on_all_interfaces"
    private const val PREF_USE_VPN = "pref_vpn"
    private const val PREF_EXIT_NODES = "pref_exit_nodes"
    private const val PREF_POWER_USER_MODE = "pref_power_user"


    private const val PREF_HOST_ONION_SERVICES = "pref_host_onionservices"

    private const val PREF_CURRENT_VERSION = "pref_current_version"

    const val PREF_SECURE_WINDOW_FLAG: String = "pref_flag_secure"

    private var prefs: SharedPreferences? = null

    var currentVersionForUpdate: Int
        get() = prefs?.getInt(PREF_CURRENT_VERSION, 0) ?: 0
        set(version) {
            putInt(PREF_CURRENT_VERSION, version)
        }

    private const val PREF_REINSTALL_GEOIP = "pref_geoip"
    @JvmStatic
    var isGeoIpReinstallNeeded: Boolean
        get() = prefs?.getBoolean(PREF_REINSTALL_GEOIP, true) ?: true
        set(reinstallNeeded) {
            putBoolean(PREF_REINSTALL_GEOIP, reinstallNeeded)
        }

    @JvmStatic
    fun setContext(context: Context) {
        if (prefs == null) {
            prefs = getSharedPrefs(context)
        }
    }

    private fun putBoolean(key: String?, value: Boolean) {
        prefs?.edit { putBoolean(key, value) }
    }

    private fun putInt(key: String?, value: Int) {
        prefs?.edit { putInt(key, value) }
    }

    private fun putString(key: String?, value: String?) {
        prefs?.edit { putString(key, value) }
    }

    @JvmStatic
    fun hostOnionServicesEnabled(): Boolean {
        return prefs?.getBoolean(PREF_HOST_ONION_SERVICES, true) ?: true
    }

    @JvmStatic
    var defaultLocale: String?
        get() = prefs?.getString(
            PREF_DEFAULT_LOCALE,
            Locale.getDefault().language
        )
        set(value) {
            putString(PREF_DEFAULT_LOCALE, value)
        }

    fun detectRoot(): Boolean {
        return prefs?.getBoolean(PREF_DETECT_ROOT, true) ?: true
    }

    @JvmStatic
    fun useDebugLogging(): Boolean {
        return prefs?.getBoolean(PREF_ENABLE_LOGGING, false) ?: false
    }

    @JvmStatic
    fun allowBackgroundStarts(): Boolean {
        return prefs?.getBoolean(PREF_ALLOW_BACKGROUND_STARTS, true) ?: true
    }

    @JvmStatic
    fun openProxyOnAllInterfaces(): Boolean {
        return prefs?.getBoolean(PREF_OPEN_PROXY_ON_ALL_INTERFACES, false) ?: false
    }

    @JvmStatic
    fun useVpn(): Boolean {
        return prefs?.getBoolean(PREF_USE_VPN, false) ?: false
    }

    @JvmStatic
    fun putUseVpn(value: Boolean) {
        putBoolean(PREF_USE_VPN, value)
    }

    fun startOnBoot(): Boolean {
        return prefs?.getBoolean(PREF_START_ON_BOOT, true) ?: false
    }

    @JvmStatic
    var exitNodes: String?
        get() = prefs?.getString(PREF_EXIT_NODES, null)
        set(country) {
            putString(PREF_EXIT_NODES, country)
        }

    val firstExitCountry: String?
        get() {
            val exitNodes = exitNodes ?: return null

            return """(?:^|,)\s*\{([^,}]*)\}\s*(?:,|$)""".toRegex()
                .find(exitNodes)?.groupValues?.getOrNull(1)
        }

    @JvmStatic
    fun getSharedPrefs(context: Context?): SharedPreferences? {
        return context?.getSharedPreferences(
            AnyoneBotConstants.PREF_TOR_SHARED_PREFS,
            Context.MODE_MULTI_PROCESS)
    }

    val isPowerUserMode: Boolean
        get() = prefs?.getBoolean(PREF_POWER_USER_MODE, false) ?: false

    var isSecureWindow: Boolean
        get() = prefs?.getBoolean(PREF_SECURE_WINDOW_FLAG, true) ?: true
        set(isFlagSecure) {
            putBoolean(PREF_SECURE_WINDOW_FLAG, isFlagSecure)
        }
}
