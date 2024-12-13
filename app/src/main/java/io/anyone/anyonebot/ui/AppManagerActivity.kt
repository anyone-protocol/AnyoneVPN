/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package io.anyone.anyonebot.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
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
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.anyone.anyonebot.BuildConfig
import io.anyone.anyonebot.R
import io.anyone.anyonebot.databinding.LayoutAppsBinding
import io.anyone.anyonebot.databinding.LayoutAppsItemBinding
import io.anyone.anyonebot.service.AnyoneBotConstants
import io.anyone.anyonebot.service.util.Prefs
import io.anyone.anyonebot.service.vpn.AnonifiedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppManagerActivity : AppCompatActivity(), View.OnClickListener,
    AnyoneBotConstants, TextWatcher {

    private lateinit var mBinding: LayoutAppsBinding

    private var mPrefs: SharedPreferences? = null

    private val mJob = Job()
    private val mScope = CoroutineScope(Dispatchers.Main + mJob)

    private var mApps: List<AnonifiedApp> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = LayoutAppsBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mBinding.etSearch.addTextChangedListener(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()

        mPrefs = Prefs.getSharedPrefs(applicationContext)
        reloadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.app_main, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save_apps) {
            saveAppSettings()
            finish()

            return true
        }
        else if (item.itemId == android.R.id.home) {
            finish()

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onClick(v: View) {
        val entry = v.tag as? ListEntry ?: return

        entry.app?.isTorified = !(entry.app?.isTorified ?: false)
        entry.box?.setBackgroundResource(if (entry.app?.isTorified == true) R.drawable.btn_apps_selected else R.drawable.btn_apps)
        entry.active?.visibility = if (entry.app?.isTorified == true) View.VISIBLE else View.INVISIBLE
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Ignored.
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        reloadApps()
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
                mApps = getApps(packageManager, mPrefs, null, null, mBinding.etSearch.text)
            }

            mBinding.appList.adapter = mAdapterApps
        }
    }

    private val mAdapterApps by lazy {
        object : ArrayAdapter<AnonifiedApp>(this, R.layout.layout_apps_item, R.id.tvName, mApps) {

            override fun getCount(): Int {
                return mApps.size
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view =
                    convertView ?: LayoutAppsItemBinding.inflate(layoutInflater, parent, false).root
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

                entry.app = mApps[position]

                entry.box?.tag = entry
                entry.box?.setBackgroundResource(if (entry.app?.isTorified == true) R.drawable.btn_apps_selected else R.drawable.btn_apps)
                entry.box?.setOnClickListener(this@AppManagerActivity)

                try {
                    entry.icon?.setImageDrawable(
                        packageManager.getApplicationIcon(
                            entry.app?.packageName ?: ""
                        )
                    )
                    entry.icon?.tag = entry
                    entry.icon?.contentDescription = entry.app?.name
                    entry.icon?.setOnClickListener(this@AppManagerActivity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                entry.text?.text = entry.app?.name
                entry.text?.tag = entry
                entry.text?.setOnClickListener(this@AppManagerActivity)

                entry.active?.visibility =
                    if (entry.app?.isTorified == true) View.VISIBLE else View.INVISIBLE
                entry.active?.tag = entry
                entry.active?.setOnClickListener(this@AppManagerActivity)

                return view
            }
        }
    }

    private fun saveAppSettings() {
        val apps = HashSet<String>()

        apps.addAll(mApps.filter { it.isTorified }.mapNotNull { it.packageName })

        val response = Intent()
        apps.forEach { response.putExtra(it, true) }

        mPrefs?.edit()
            ?.putString(AnyoneBotConstants.PREFS_KEY_ANONIFIED, apps.joinToString("|"))
            ?.apply()

        setResult(RESULT_OK, response)
    }

    private class ListEntry {
        var box: View? = null
        var icon: ImageView? = null
        var text: TextView? = null // app name
        var active: TextView? = null
        var app: AnonifiedApp? = null
    }

    companion object {

        fun getApps(
            packageManager: PackageManager,
            prefs: SharedPreferences?,
            include: List<String>?,
            exclude: List<String>?,
            filter: CharSequence?
        ): List<AnonifiedApp> {

            val anondApps = prefs?.getString(AnyoneBotConstants.PREFS_KEY_ANONIFIED, "")
                ?.split("|")
                ?.filter { it.isNotEmpty() }

            val apps = packageManager.getInstalledApplications(0)
                .filter {
                    it.enabled // Ignore disabled apps,
                            // ignore apps which bring their own Tor,
                            && !AnyoneBotConstants.BYPASS_VPN_PACKAGES.contains(it.packageName)
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
                            // ignore apps, which don't contain the filter string in their id or label, if any filter.
                            && (filter.isNullOrBlank()
                                || it.packageName.contains(filter, true)
                                || packageManager.getApplicationLabel(it).contains(filter, true))
                }
                .map {
                    val app = AnonifiedApp()
                    app.packageName = it.packageName
                    app.name = packageManager.getApplicationLabel(it).toString()
                    app.uid = it.uid
                    app.procname = it.processName
                    app.username = packageManager.getNameForUid(app.uid)
                    app.isEnabled = true
                    app.setUsesInternet(true)
                    app.isTorified = anondApps?.contains(it.packageName) ?: false
                    app
                }

            AnonifiedApp.sortAppsForTorifiedAndAbc(apps)

            return apps
        }
    }
}
