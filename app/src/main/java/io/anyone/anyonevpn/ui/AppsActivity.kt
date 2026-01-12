/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package io.anyone.anyonevpn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.allViews
import io.anyone.anyonevpn.BuildConfig
import io.anyone.anyonevpn.R
import io.anyone.anyonevpn.core.putNotSystem
import io.anyone.anyonevpn.core.ui.BaseActivity
import io.anyone.anyonevpn.databinding.ActivityAppsBinding
import io.anyone.anyonevpn.databinding.ActivityAppsItemBinding
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import io.anyone.anyonevpn.service.AnyoneVpnService
import io.anyone.anyonevpn.service.util.Prefs
import io.anyone.anyonevpn.service.vpn.AnonifiedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsActivity : BaseActivity(), View.OnClickListener, TextWatcher {

    private lateinit var mBinding: ActivityAppsBinding

    private val mJob = Job()
    private val mScope = CoroutineScope(Dispatchers.Main + mJob)

    private var mApps: List<AnonifiedApp> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mBinding.etSearch.addTextChangedListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.choose_apps, menu)

        return true
    }

    override fun onResume() {
        super.onResume()

        reloadApps()
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val item = menu?.findItem(R.id.select_all) ?: return true

        if (mApps.count { it.isTorified } < mApps.size) {
            item.icon = AppCompatResources.getDrawable(this, R.drawable.select_all_24px)
            item.setTitle(R.string.select_all)
        }
        else {
            item.icon = AppCompatResources.getDrawable(this, R.drawable.deselect_24px)
            item.setTitle(R.string.deselect_all)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.select_all -> {
                val select = mApps.count { it.isTorified } < mApps.size

                for (app in mApps) {
                    app.isTorified = select
                }

                for (v in mBinding.appList.allViews) {
                    (v.tag as? ListEntry)?.update()
                }

                saveAppSettings()

                invalidateOptionsMenu()

                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onClick(v: View) {
        val entry = v.tag as? ListEntry ?: return

        entry.app?.isTorified = !(entry.app?.isTorified ?: false)
        entry.update()

        saveAppSettings()

        invalidateOptionsMenu()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Ignored.
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        mAdapterApps.filter.filter(s)
    }

    override fun afterTextChanged(s: Editable?) {
        // Ignored.
    }

    override fun onPause() {
        super.onPause()

        mJob.cancel()
    }

    private fun reloadApps() {
        mScope.launch {
            withContext(Dispatchers.IO) {
                packageManager?.let {
                    mApps = getApps(it, null, null)
                }

                invalidateOptionsMenu()
            }

            mBinding.appList.adapter = mAdapterApps
        }
    }

    private val mAdapterApps by lazy {
        object : ArrayAdapter<AnonifiedApp>(this, R.layout.activity_apps_item, R.id.tvName, mApps) {

            private var data: List<AnonifiedApp> = mApps

            override fun getCount(): Int {
                return data.size
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view =
                    convertView ?: ActivityAppsItemBinding.inflate(layoutInflater, parent, false).root
                var entry = view.tag as? ListEntry

                if (entry == null) {
                    // Inflate a new view
                    entry = ListEntry()
                    entry.box = view.findViewById(R.id.selection)
                    entry.icon = view.findViewById(R.id.ivIcon)
                    entry.text = view.findViewById(R.id.tvName)
                    entry.active = view.findViewById(R.id.tvActive)
                    view.tag = entry
                }

                entry.app = data[position]

                entry.box?.tag = entry
                entry.box?.setBackgroundResource(if (entry.app?.isTorified == true) R.drawable.btn_apps_selected else R.drawable.btn_apps)
                entry.box?.setOnClickListener(this@AppsActivity)

                try {
                    entry.icon?.setImageDrawable(
                        context.packageManager?.getApplicationIcon(entry.app?.packageName ?: ""))
                    entry.icon?.tag = entry
                    entry.icon?.setOnClickListener(this@AppsActivity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                entry.text?.text = entry.app?.name
                entry.text?.tag = entry
                entry.text?.setOnClickListener(this@AppsActivity)

                entry.active?.visibility =
                    if (entry.app?.isTorified == true) View.VISIBLE else View.INVISIBLE
                entry.active?.tag = entry
                entry.active?.setOnClickListener(this@AppsActivity)

                return view
            }

            override fun getFilter(): Filter {
                val filter = object : Filter() {

                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()

                        if (constraint.isNullOrBlank()) {
                            results.values = mApps
                            results.count = mApps.count()
                        }
                        else {
                            val filtered = mApps.filter {
                                // ignore apps, which don't contain the filter string in their id or label.
                                it.packageName.contains(constraint, true)
                                        || it.name?.contains(constraint, true) ?: false
                            }

                            results.values = filtered
                            results.count = filtered.count()
                        }

                        return results
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        data = results?.values as? List<AnonifiedApp> ?: mApps
                        notifyDataSetChanged()
                    }
                }

                return filter
            }
        }
    }

    private fun saveAppSettings() {
        val apps = HashSet<String>()

        apps.addAll(mApps.filter { it.isTorified }.map { it.packageName })

        Prefs.anonifiedApps = apps.joinToString("|")

        val intent = Intent(this, AnyoneVpnService::class.java).putNotSystem()
        intent.action = AnyoneVpnConstants.ACTION_RESTART_VPN

        ContextCompat.startForegroundService(this, intent)
    }

    private class ListEntry {
        var box: View? = null
        var icon: ImageView? = null
        var text: TextView? = null // app name
        var active: TextView? = null
        var app: AnonifiedApp? = null

        fun update() {
            box?.setBackgroundResource(if (app?.isTorified == true) R.drawable.btn_apps_selected else R.drawable.btn_apps)
            active?.visibility = if (app?.isTorified == true) View.VISIBLE else View.INVISIBLE
        }
    }

    companion object {

        fun getApps(
            packageManager: PackageManager,
            include: List<String>?,
            exclude: List<String>?
        ): List<AnonifiedApp> {

            val anondApps = Prefs.anonifiedApps
                .split("|")
                .filter { it.isNotEmpty() }

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
                    val app = AnonifiedApp()
                    app.packageName = it.packageName
                    app.name = packageManager.getApplicationLabel(it).toString()
                    app.uid = it.uid
                    app.procname = it.processName
                    app.username = packageManager.getNameForUid(app.uid)
                    app.isEnabled = true
                    app.usesInternet = true
                    app.isTorified = anondApps.contains(it.packageName)
                    app
                }

            AnonifiedApp.sortAppsForTorifiedAndAbc(apps)

            return apps
        }
    }
}
