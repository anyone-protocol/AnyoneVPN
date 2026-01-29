package io.anyone.anyonevpn.service.vpn

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import io.anyone.anyonevpn.service.util.Prefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.text.Normalizer

@Serializable
class ExcludedApp : Comparable<ExcludedApp> {
    @Serializable
    var isEnabled: Boolean = false

    @Serializable
    var uid: Int = 0

    @Serializable
    var username: String? = null

    @Serializable
    var procname: String? = null

    @Serializable
    var name: String? = null

    // Drawable is not serializable
    @Transient
    var icon: Drawable? = null

    @Serializable
    var packageName: String = ""

    @Serializable
    var isExcluded: Boolean = false

    @Serializable
    var usesInternet: Boolean = false

    override fun compareTo(other: ExcludedApp): Int =
         (name ?: "").compareTo(other.name ?: "", ignoreCase = true)

    override fun toString(): String = name ?: ""

    companion object {
        fun getApps(context: Context): ArrayList<ExcludedApp> {
            val torifiedPackages = Prefs.anonifiedApps
                .split("|")
                .filter { it.isNotBlank() }
                .sorted()

            val pMgr = context.packageManager
            val lAppInfo = pMgr.getInstalledApplications(0)
            val apps = ArrayList<ExcludedApp>()
            lAppInfo.forEach {
                val app = ExcludedApp()
                try {
                    val pInfo = pMgr.getPackageInfo(it.packageName, PackageManager.GET_PERMISSIONS)
                    if (AnyoneVpnConstants.BYPASS_VPN_PACKAGES.contains(it.packageName)) {
                        app.usesInternet = false
                    } else if (pInfo?.requestedPermissions != null) {
                        for (permInfo in pInfo.requestedPermissions!!) {
                            if (permInfo == Manifest.permission.INTERNET) {
                                app.usesInternet = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if ((it.flags and ApplicationInfo.FLAG_SYSTEM) == 1)
                    app.usesInternet = true // System app

                if (!app.usesInternet) return@forEach
                else apps.add(app)

                app.apply {
                    isEnabled = it.enabled
                    uid = it.uid
                    username = pMgr.getNameForUid(it.uid)
                    procname = it.processName
                    packageName = it.packageName
                }

                try {
                    app.name = pMgr.getApplicationLabel(it).toString()
                } catch (_: Exception) {
                    app.name = it.packageName
                }

                // Check if this application is allowed
                app.isExcluded = torifiedPackages.binarySearch(app.packageName) >= 0
            }

            apps.sort()
            return apps
        }

        fun sortAppsForTorifiedAndAbc(apps: List<ExcludedApp>?) {
            apps?.sortedWith(compareBy<ExcludedApp> { !it.isExcluded }.thenBy {
                Normalizer.normalize(it.name ?: "", Normalizer.Form.NFD)
            })
        }
    }
}
