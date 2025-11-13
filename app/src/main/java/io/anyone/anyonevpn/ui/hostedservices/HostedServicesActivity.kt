package io.anyone.anyonevpn.ui.hostedservices

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import io.anyone.anyonevpn.R
import io.anyone.anyonevpn.core.DiskUtils.createReadFileIntent
import io.anyone.anyonevpn.core.LocaleHelper.onAttach
import io.anyone.anyonevpn.core.ui.BaseActivity
import io.anyone.anyonevpn.databinding.ActivityHostedServicesBinding
import io.anyone.anyonevpn.ui.hostedservices.PermissionManager.requestBatteryPermissions
import io.anyone.anyonevpn.ui.hostedservices.PermissionManager.requestDropBatteryPermissions
import io.anyone.anyonevpn.utils.BackupUtils
import io.anyone.anyonevpn.utils.ZipUtils
import io.anyone.anyonevpn.utils.getInt
import io.anyone.anyonevpn.utils.getString

class HostedServicesActivity : BaseActivity(), View.OnClickListener {

    private lateinit var mBinding: ActivityHostedServicesBinding

    private lateinit var mAdapter: HostedServiceListAdapter

    private val mReadBackupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult

            BackupUtils(this).restoreZipBackup(uri)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityHostedServicesBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        setSupportActionBar(mBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mBinding.fab.setOnClickListener {
            HostedServiceCreateDialogFragment().show(
                supportFragmentManager,
                HostedServiceCreateDialogFragment::class.java.simpleName)
        }

        mBinding.rbAppServices.setOnClickListener(this)

        mAdapter = HostedServiceListAdapter(
            this,
            contentResolver.query(
                HostedServicesContentProvider.CONTENT_URI,
                HostedServicesContentProvider.PROJECTION,
                BASE_WHERE_SELECTION_CLAUSE + '1',
                null,
                null))

        contentResolver.registerContentObserver(
            HostedServicesContentProvider.CONTENT_URI, true, OnionServiceObserver(
                Handler(Looper.getMainLooper())))

        val showUserServices = mBinding.rbAppServices.isChecked
                || savedInstanceState == null
                || savedInstanceState.getBoolean(BUNDLE_KEY_SHOW_USER_SERVICES, false)

        if (showUserServices) {
            mBinding.rbUserServices.isChecked = true
        }
        else {
            mBinding.rbAppServices.isChecked = true
        }

        filterServices(showUserServices)

        mBinding.listServices.adapter = mAdapter
        mBinding.listServices.onItemClickListener =
            AdapterView.OnItemClickListener { parent: AdapterView<*>, _: View?, position: Int, _: Long ->
                val item = parent.getItemAtPosition(position) as Cursor
                val arguments = Bundle()

                val id = item.getInt(HostedServicesContentProvider.HostedService.ID)
                if (id != null) arguments.putInt(BUNDLE_KEY_ID, id)

                for (i in mapOf(HostedServicesContentProvider.HostedService.PORT to BUNDLE_KEY_PORT,
                    HostedServicesContentProvider.HostedService.DOMAIN to BUNDLE_KEY_DOMAIN,
                    HostedServicesContentProvider.HostedService.PATH to BUNDLE_KEY_PATH)
                ) {
                    arguments.putString(i.value, item.getString(i.key))
                }

                val dialog = HostedServiceActionsDialogFragment(arguments)
                dialog.show(supportFragmentManager,
                    HostedServiceActionsDialogFragment::class.java.simpleName)
            }
    }

    private fun filterServices(showUserServices: Boolean) {
        val predicate: String
        if (showUserServices) {
            predicate = "1"
            mBinding.fab.show()
        } else {
            predicate = "0"
            mBinding.fab.hide()
        }

        mAdapter.changeCursor(
            contentResolver.query(
                HostedServicesContentProvider.CONTENT_URI, HostedServicesContentProvider.PROJECTION,
                BASE_WHERE_SELECTION_CLAUSE + predicate, null, null))
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(onAttach(base))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.hs_menu, menu)

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(BUNDLE_KEY_SHOW_USER_SERVICES, mBinding.rbUserServices.isChecked)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_restore_backup) {
            mReadBackupLauncher.launch(createReadFileIntent(ZipUtils.ZIP_MIME_TYPE))
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onClick(view: View?) {
        filterServices(mBinding.rbUserServices.isChecked)
    }

    private inner class OnionServiceObserver(handler: Handler?) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            filterServices(mBinding.rbUserServices.isChecked) // updates adapter

            showBatteryOptimizationsMessageIfAppropriate()
        }
    }

    fun showBatteryOptimizationsMessageIfAppropriate() {
        val activeServices = contentResolver.query(
            HostedServicesContentProvider.CONTENT_URI, HostedServicesContentProvider.PROJECTION,
            HostedServicesContentProvider.HostedService.ENABLED + "=1", null, null)
            ?: return

        if (activeServices.count > 0) {
            requestBatteryPermissions(this, mBinding.root)
        }
        else {
            requestDropBatteryPermissions(this, mBinding.root)
        }

        activeServices.close()
    }

    companion object {
        const val BUNDLE_KEY_ID = "id"
        const val BUNDLE_KEY_PORT = "port"
        const val BUNDLE_KEY_DOMAIN = "domain"
        const val BUNDLE_KEY_PATH = "path"

        private const val BASE_WHERE_SELECTION_CLAUSE =
            "${HostedServicesContentProvider.HostedService.CREATED_BY_USER} = "
        private const val BUNDLE_KEY_SHOW_USER_SERVICES = "show_user_key"
    }
}
