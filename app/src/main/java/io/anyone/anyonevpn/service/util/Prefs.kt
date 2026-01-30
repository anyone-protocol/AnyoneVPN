package io.anyone.anyonevpn.service.util

import android.content.ContentResolver
import android.content.Context
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import java.net.URI
import java.util.Locale

object Prefs {
    private const val PREF_DEFAULT_LOCALE = "pref_default_locale"
    private const val PREF_DETECT_ROOT = "pref_detect_root"
    private const val PREF_ENABLE_LOGGING = "pref_enable_logging"
    private const val PREF_START_ON_BOOT = "pref_start_boot"
    private const val PREF_ALLOW_BACKGROUND_STARTS = "pref_allow_background_starts"
    private const val PREF_OPEN_PROXY_ON_ALL_INTERFACES = "pref_open_proxy_on_all_interfaces"
    private const val PREF_USE_VPN = "pref_vpn"
    private const val PREF_EXIT_NODES = "pref_exit_nodes"
    private const val PREF_STRICT_NODES = "pref_strict_nodes"
    private const val PREF_POWER_USER_MODE = "pref_power_user"
    private const val PREFS_KEY_EXCLUDED: String = "PrefAnond"


    private const val PREF_HOST_HIDDEN_SERVICES = "pref_host_onionservices"

    private const val PREF_CURRENT_VERSION = "pref_current_version"

    const val PREF_SECURE_WINDOW_FLAG: String = "pref_flag_secure"

    private var cr: ContentResolver? = null

    var currentVersionForUpdate: Int
        get() = cr?.getPrefInt(PREF_CURRENT_VERSION) ?: 0
        set(version) = cr?.putPref(PREF_CURRENT_VERSION, version) ?: Unit

    private const val PREF_REINSTALL_GEOIP = "pref_geoip"
    @JvmStatic
    var isGeoIpReinstallNeeded: Boolean
        get() = cr?.getPrefBoolean(PREF_REINSTALL_GEOIP, true) ?: true
        set(reinstallNeeded) = cr?.putPref(PREF_REINSTALL_GEOIP, reinstallNeeded) ?: Unit

    @JvmStatic
    fun setContext(context: Context) {
        if (cr == null) cr = context.contentResolver
    }

    @JvmStatic
    var hostHiddenServicesEnabled: Boolean
        get() = cr?.getPrefBoolean(PREF_HOST_HIDDEN_SERVICES, true) ?: true
        set(value) = cr?.putPref(PREF_HOST_HIDDEN_SERVICES, value) ?: Unit


    @JvmStatic
    var defaultLocale: String
        get() = cr?.getPrefString(PREF_DEFAULT_LOCALE) ?: Locale.getDefault().language
        set(value) = cr?.putPref(PREF_DEFAULT_LOCALE, value) ?: Unit

    val detectRoot: Boolean
        get() = cr?.getPrefBoolean(PREF_DETECT_ROOT, true) ?: true

    @JvmStatic
    val useDebugLogging: Boolean
        get() = cr?.getPrefBoolean(PREF_ENABLE_LOGGING) ?: false

    @JvmStatic
    val allowBackgroundStarts: Boolean
        get() = cr?.getPrefBoolean(PREF_ALLOW_BACKGROUND_STARTS, true) ?: true

    @JvmStatic
    val openProxyOnAllInterfaces: Boolean
        get() = cr?.getPrefBoolean(PREF_OPEN_PROXY_ON_ALL_INTERFACES) ?: false

    @JvmStatic
    var useVpn: Boolean
        get() = cr?.getPrefBoolean(PREF_USE_VPN) ?: false
        set(value) = cr?.putPref(PREF_USE_VPN, value) ?: Unit

    val startOnBoot: Boolean
        get() = cr?.getPrefBoolean(PREF_START_ON_BOOT, true) ?: false

    @JvmStatic
    var exitNodes: String
        get() = cr?.getPrefString(PREF_EXIT_NODES) ?: ""
        set(country) = cr?.putPref(PREF_EXIT_NODES, country) ?: Unit

    @JvmStatic
    val strictNodes: Boolean
        get() = cr?.getPrefBoolean(PREF_STRICT_NODES, false) ?: false

    val firstExitCountry: String?
        get() {
            val exitNodes = exitNodes

            return """(?:^|,)\s*\{([^,}]*)\}\s*(?:,|$)""".toRegex()
                .find(exitNodes)?.groupValues?.getOrNull(1)
        }

    val isPowerUserMode: Boolean
        get() = cr?.getPrefBoolean(PREF_POWER_USER_MODE, false) ?: false

    var isSecureWindow: Boolean
        get() = cr?.getPrefBoolean(PREF_SECURE_WINDOW_FLAG, true) ?: true
        set(isFlagSecure) = cr?.putPref(PREF_SECURE_WINDOW_FLAG, isFlagSecure) ?: Unit

    var excludedApps: Set<String>
        get() = (cr?.getPrefString(PREFS_KEY_EXCLUDED) ?: "").split("|").filter { it.isNotBlank() }.toSet()
        set(value) = cr?.putPref(PREFS_KEY_EXCLUDED, value.joinToString("|")) ?: Unit

    @JvmStatic
    val proxySocksPort: String
        get() = cr?.getPrefString(AnyoneVpnConstants.PREF_SOCKS) ?: AnyoneVpnConstants.SOCKS_PROXY_PORT_DEFAULT

    @JvmStatic
    val proxyHttpPort: String
        get() = cr?.getPrefString(AnyoneVpnConstants.PREF_HTTP) ?: AnyoneVpnConstants.HTTP_PROXY_PORT_DEFAULT

    @JvmStatic
    val isolateDest: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_ISOLATE_DEST) ?: false

    @JvmStatic
    val isolatePort: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_ISOLATE_PORT) ?: false

    @JvmStatic
    val isolateProtocol: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_ISOLATE_PROTOCOL) ?: false

    @JvmStatic
    val preferIpv6: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_PREFER_IPV6, true) ?: true

    @JvmStatic
    val disableIpv4: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_DISABLE_IPV4) ?: false

    @JvmStatic
    val connectionPadding: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_CONNECTION_PADDING) ?: false

    @JvmStatic
    val reducedConnectionPadding: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_REDUCED_CONNECTION_PADDING, true) ?: true

    @JvmStatic
    val circuitPadding: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_CIRCUIT_PADDING, true) ?: true

    @JvmStatic
    val reducedCircuitPadding: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_REDUCED_CIRCUIT_PADDING, true) ?: true

    @JvmStatic
    val anonTransPort: String
        get() = cr?.getPrefString("pref_transport") ?: AnyoneVpnConstants.ANON_TRANSPROXY_PORT_DEFAULT.toString()

    @JvmStatic
    val anonDnsPort: String
        get() = cr?.getPrefString("pref_dnsport") ?: AnyoneVpnConstants.ANON_DNS_PORT_DEFAULT.toString()

    @JvmStatic
    val customAnonRc: String
        get() = cr?.getPrefString("pref_custom_torrc") ?: ""

    @JvmStatic
    var anonDnsPortResolved: Int
        get() = cr?.getPrefInt(AnyoneVpnConstants.PREFS_DNS_PORT) ?: 0
        set(value) = cr?.putPref(AnyoneVpnConstants.PREFS_DNS_PORT, value) ?: Unit

    @JvmStatic
    val beRelay: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_OR) ?: false

    @JvmStatic
    val reachableAddresses: Boolean
        get() = cr?.getPrefBoolean(AnyoneVpnConstants.PREF_REACHABLE_ADDRESSES) ?: false

    @JvmStatic
    val entryNodes: String
        get() = cr?.getPrefString("pref_entrance_nodes") ?: ""

    @JvmStatic
    val excludeNodes: String
        get() = cr?.getPrefString("pref_exclude_nodes") ?: ""

    @JvmStatic
    val reachableAddressesPorts: String
        get() = cr?.getPrefString(AnyoneVpnConstants.PREF_REACHABLE_ADDRESSES_PORTS) ?: "*:80,*:443"

    @JvmStatic
    val orPort: String
        get() = cr?.getPrefString(AnyoneVpnConstants.PREF_OR_PORT) ?: "9001"

    @JvmStatic
    val orNickname: String
        get() = cr?.getPrefString(AnyoneVpnConstants.PREF_OR_NICKNAME) ?: "Anyone VPN"

    @JvmStatic
    val proxy: URI?
        get() {
            val scheme = cr?.getPrefString("pref_proxy_type")?.lowercase()?.trim()
            if (scheme.isNullOrEmpty()) return null

            val host = cr?.getPrefString("pref_proxy_host")?.trim()
            if (host.isNullOrEmpty()) return null

            val url = StringBuilder(scheme)
            url.append("://")

            var needsAt = false
            val username = cr?.getPrefString("pref_proxy_username")
            if (!username.isNullOrEmpty()) {
                url.append(username)
                needsAt = true
            }

            val password = cr?.getPrefString("pref_proxy_password")
            if (!password.isNullOrEmpty()) {
                url.append(":")
                url.append(password)
                needsAt = true
            }

            if (needsAt) url.append("@")

            url.append(host)

            val port = try {
                cr?.getPrefString("pref_proxy_port")?.trim()?.toInt() ?: 0
            } catch (_: Throwable) { 0 }

            if (port in 1..<65536) {
                url.append(":")
                url.append(port)
            }

            url.append("/")

            return URI(url.toString())
        }
}
