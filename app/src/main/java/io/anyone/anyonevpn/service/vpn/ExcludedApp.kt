package io.anyone.anyonevpn.service.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import io.anyone.anyonevpn.BuildConfig
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

        @SuppressLint("QueryPermissionsNeeded")
        fun getAllApps(
            packageManager: PackageManager,
            include: List<String>? = null,
            exclude: List<String>? = null
        ): List<ExcludedApp> {

            val excludedApps = Prefs.excludedApps

            val apps = packageManager.getInstalledApplications(0)
                .filter {
                    it.enabled // Ignore disabled apps,
                            // ignore apps which bring their own Tor,
                            && !AnyoneVpnConstants.BYPASS_VPN_PACKAGES.contains(it.packageName)
                            // ignore ourselves,
                            && it.packageName != BuildConfig.APPLICATION_ID
                            // ignore system apps, which haven't been updated (filters the most obscure ones),
                            && (it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
                            // ignore apps, which aren't on the include list, if there is one,
                            && include?.contains(it.packageName) != false
                            // ignore apps, which are on the exclude list, if there is one,
                            && exclude?.contains(it.packageName) != true
                            // ignore apps, which don't connect to the net.
                            && packageManager.getPackageInfo(it.packageName, PackageManager.GET_PERMISSIONS)
                        ?.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                }
                .map {
                    val app = ExcludedApp()
                    app.packageName = it.packageName
                    app.name = packageManager.getApplicationLabel(it).toString()
                    app.uid = it.uid
                    app.procname = it.processName
                    app.username = packageManager.getNameForUid(app.uid)
                    app.isEnabled = true
                    app.usesInternet = true
                    app.isExcluded = excludedApps.contains(it.packageName)
                    app
                }

            return apps.sortedWith(compareBy<ExcludedApp> { !it.isExcluded }.thenBy {
                Normalizer.normalize(it.name ?: "", Normalizer.Form.NFD)
            })
        }
    }
}
