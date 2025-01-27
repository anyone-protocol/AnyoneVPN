package io.anyone.anyonebot.service.util;

import android.content.Context;
import android.content.SharedPreferences;

import io.anyone.anyonebot.service.AnyoneBotConstants;

import java.util.Locale;

public class Prefs {

    private final static String PREF_DEFAULT_LOCALE = "pref_default_locale";
    private final static String PREF_DETECT_ROOT = "pref_detect_root";
    private final static String PREF_ENABLE_LOGGING = "pref_enable_logging";
    private final static String PREF_START_ON_BOOT = "pref_start_boot";
    private final static String PREF_ALLOW_BACKGROUND_STARTS = "pref_allow_background_starts";
    private final static String PREF_OPEN_PROXY_ON_ALL_INTERFACES = "pref_open_proxy_on_all_interfaces";
    private final static String PREF_USE_VPN = "pref_vpn";
    private final static String PREF_EXIT_NODES = "pref_exit_nodes";
    private static final String PREF_POWER_USER_MODE = "pref_power_user";


    private final static String PREF_HOST_ONION_SERVICES = "pref_host_onionservices";

    private static final String PREF_CURRENT_VERSION = "pref_current_version";

    public static final String PREF_SECURE_WINDOW_FLAG = "pref_flag_secure";

    private static SharedPreferences prefs;

    public static int getCurrentVersionForUpdate() {
        return prefs.getInt(PREF_CURRENT_VERSION, 0);
    }

    public static void setCurrentVersionForUpdate(int version) {
        putInt(PREF_CURRENT_VERSION, version);
    }

    private static final String PREF_REINSTALL_GEOIP = "pref_geoip";
    public static boolean isGeoIpReinstallNeeded() {
        return prefs.getBoolean(PREF_REINSTALL_GEOIP, true);
    }
    public static void setIsGeoIpReinstallNeeded(boolean reinstallNeeded) {
        putBoolean(PREF_REINSTALL_GEOIP, reinstallNeeded);
    }

    public static void setContext(Context context) {
        if (prefs == null) {
            prefs = getSharedPrefs(context);
        }

    }

    private static void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    private static void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    private static void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public static boolean hostOnionServicesEnabled () {
        return prefs.getBoolean(PREF_HOST_ONION_SERVICES, true);
    }

    public static String getDefaultLocale() {
        return prefs.getString(PREF_DEFAULT_LOCALE, Locale.getDefault().getLanguage());
    }

    public static void setDefaultLocale(String value) {
        putString(PREF_DEFAULT_LOCALE, value);
    }

    public static boolean detectRoot () {
        return prefs.getBoolean(PREF_DETECT_ROOT,true);
    }

    public static boolean useDebugLogging() {
        return prefs.getBoolean(PREF_ENABLE_LOGGING, false);
    }

    public static boolean allowBackgroundStarts() {
        return prefs.getBoolean(PREF_ALLOW_BACKGROUND_STARTS, true);
    }

    public static boolean openProxyOnAllInterfaces() {
        return prefs.getBoolean(PREF_OPEN_PROXY_ON_ALL_INTERFACES, false);
    }

    public static boolean useVpn() {
        return prefs.getBoolean(PREF_USE_VPN, false);
    }

    public static void putUseVpn(boolean value) {
        putBoolean(PREF_USE_VPN, value);
    }

    public static boolean startOnBoot() {
        return prefs.getBoolean(PREF_START_ON_BOOT, true);
    }

    public static String getExitNodes() {
        return prefs.getString(PREF_EXIT_NODES, "");
    }

    public static void setExitNodes(String country) {
        putString(PREF_EXIT_NODES, country);
    }

    public static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences(AnyoneBotConstants.PREF_TOR_SHARED_PREFS, Context.MODE_MULTI_PROCESS);
    }

    public static boolean isPowerUserMode() {
        return prefs.getBoolean(PREF_POWER_USER_MODE, false);
    }

    public static void setSecureWindow(boolean isFlagSecure) {
        putBoolean(PREF_SECURE_WINDOW_FLAG, isFlagSecure);
    }

    public static boolean isSecureWindow() {
        return prefs.getBoolean(PREF_SECURE_WINDOW_FLAG, true);
    }
}
