package io.anyone.anyonevpn.service.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.anyone.anyonevpn.BuildConfig
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import io.anyone.anyonevpn.service.util.Prefs
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Serializable
class OtherApp(
    @Serializable
    var packageName: String = "",

    @Serializable
    var name: String? = null,

    @Serializable
    var isExcluded: Boolean = false,

    @Serializable
    var isSystem: Boolean = false
) : Comparable<OtherApp> {
    override fun compareTo(other: OtherApp): Int =
        (name ?: "").compareTo(other.name ?: "", ignoreCase = true)

    override fun toString(): String = name ?: ""

    companion object {

        @SuppressLint("QueryPermissionsNeeded")
        fun getAll(
            packageManager: PackageManager,
            include: List<String>? = null,
            exclude: List<String>? = null
        ): List<OtherApp> {

            val excludedApps = Prefs.excludedApps

            return packageManager.getInstalledApplications(0)
                .filter {
                    it.enabled // Ignore disabled apps,
                            // ignore apps which bring their own Tor,
                            && !AnyoneVpnConstants.BYPASS_VPN_PACKAGES.contains(it.packageName)
                            // ignore ourselves,
                            && it.packageName != BuildConfig.APPLICATION_ID
                            // ignore apps, which aren't on the include list, if there is one,
                            && include?.contains(it.packageName) != false
                            // ignore apps, which are on the exclude list, if there is one,
                            && exclude?.contains(it.packageName) != true
                            // ignore apps, which don't connect to the net.
                            && packageManager.getPackageInfo(it.packageName, PackageManager.GET_PERMISSIONS)
                                ?.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                }
                .map {
                    OtherApp(
                        it.packageName,
                        packageManager.getApplicationLabel(it).toString(),
                        excludedApps.contains(it.packageName),
                        it.flags and ApplicationInfo.FLAG_SYSTEM != 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    )
                }
                .sortedWith(compareBy<OtherApp> { !it.isExcluded }.thenBy {
                    Normalizer.normalize(it.name ?: "", Normalizer.Form.NFD)
                })
        }
    }
}
