/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package io.anyone.anyonevpn.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.allViews
import io.anyone.anyonevpn.R
import io.anyone.anyonevpn.core.putNotSystem
import io.anyone.anyonevpn.core.ui.BaseActivity
import io.anyone.anyonevpn.databinding.ActivityAppsBinding
import io.anyone.anyonevpn.databinding.ActivityAppsItemBinding
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import io.anyone.anyonevpn.service.AnyoneVpnService
import io.anyone.anyonevpn.service.util.Prefs
import io.anyone.anyonevpn.service.vpn.OtherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsActivity : BaseActivity(), View.OnClickListener, TextWatcher,
    CompoundButton.OnCheckedChangeListener {

    private lateinit var mBinding: ActivityAppsBinding

    private val mJob = Job()
    private val mScope = CoroutineScope(Dispatchers.Main + mJob)

    private var mApps: List<OtherApp> = emptyList()

    private var mSearchText: CharSequence? = null
    private var mShowSystemApps = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mBinding.etSearch.addTextChangedListener(this)

        mBinding.cbSystemApps.setOnCheckedChangeListener(this)
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

        if (mApps.count { it.isExcluded } < mApps.size) {
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
                val select = mApps.count { it.isExcluded } < mApps.size

                for (app in mApps) {
                    app.isExcluded = select
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

        entry.app?.isExcluded = !(entry.app?.isExcluded ?: false)
        entry.update()

        saveAppSettings()

        invalidateOptionsMenu()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Ignored.
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        mSearchText = s
        mAdapterApps.filter.filter(s)
    }

    override fun afterTextChanged(s: Editable?) {
        // Ignored.
    }

    override fun onCheckedChanged(view: CompoundButton, checked: Boolean) {
        mShowSystemApps = checked
        mAdapterApps.filter.filter(mSearchText)
    }

    override fun onPause() {
        super.onPause()

        mJob.cancel()
    }

    private fun reloadApps() {
        mScope.launch {
            withContext(Dispatchers.IO) {
                packageManager?.let {
                    mApps = OtherApp.getAll(it)
                }

                invalidateOptionsMenu()
            }

            mBinding.appList.adapter = mAdapterApps
        }
    }

    private val mAdapterApps by lazy {
        object : ArrayAdapter<OtherApp>(this, R.layout.activity_apps_item, R.id.tvName, mApps) {

            private val appsPrefiltered: List<OtherApp>
                get() = mApps.filter { it.isExcluded || !it.isSystem || mShowSystemApps }

            private var data: List<OtherApp> = appsPrefiltered

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
                entry.box?.setBackgroundResource(if (entry.app?.isExcluded == true) R.drawable.btn_apps_selected else R.drawable.btn_apps)
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
                    if (entry.app?.isExcluded == true) View.VISIBLE else View.INVISIBLE
                entry.active?.tag = entry
                entry.active?.setOnClickListener(this@AppsActivity)

                return view
            }

            override fun getFilter(): Filter {
                val filter = object : Filter() {

                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        var filtered = appsPrefiltered

                        if (!constraint.isNullOrBlank()) {
                            filtered = filtered.filter {
                                // ignore apps, which don't contain the filter string in their id or label.
                                 (it.packageName.contains(constraint, true)
                                         || it.name?.contains(constraint, true) ?: false)
                            }
                        }

                        return FilterResults().apply {
                            values = filtered
                            count = filtered.count()
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        data = results?.values as? List<OtherApp> ?: mApps
                        notifyDataSetChanged()
                    }
                }

                return filter
            }
        }
    }

    private fun saveAppSettings() {
        Prefs.excludedApps = mApps.filter { it.isExcluded }.map { it.packageName }.toSet()

        val intent = Intent(this, AnyoneVpnService::class.java).putNotSystem()
        intent.action = AnyoneVpnConstants.ACTION_RESTART_VPN

        ContextCompat.startForegroundService(this, intent)
    }

    private class ListEntry {
        var box: View? = null
        var icon: ImageView? = null
        var text: TextView? = null // app name
        var active: TextView? = null
        var app: OtherApp? = null

        fun update() {
            box?.setBackgroundResource(if (app?.isExcluded == true) R.drawable.btn_apps_selected else R.drawable.btn_apps)
            active?.visibility = if (app?.isExcluded == true) View.VISIBLE else View.INVISIBLE
        }
    }
}
