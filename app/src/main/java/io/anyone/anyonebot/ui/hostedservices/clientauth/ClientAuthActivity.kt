package io.anyone.anyonebot.ui.hostedservices.clientauth

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.anyone.anyonebot.R
import io.anyone.anyonebot.core.DiskUtils.createReadFileIntent
import io.anyone.anyonebot.core.DiskUtils.readFileFromInputStream
import io.anyone.anyonebot.core.LocaleHelper.onAttach
import io.anyone.anyonebot.databinding.ActivityClientAuthBinding
import io.anyone.anyonebot.utils.BackupUtils
import io.anyone.anyonebot.utils.getInt
import io.anyone.anyonebot.utils.getString

class ClientAuthActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityClientAuthBinding

    private lateinit var mAdapter: ClientAuthListAdapter

    private val readBackupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult

            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.moveToFirst()
            val filename = cursor?.getString(OpenableColumns.DISPLAY_NAME)
            cursor?.close()

            if (filename?.endsWith(CLIENT_AUTH_FILE_EXTENSION) != true) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show()

                return@registerForActivityResult
            }

            val authText = readFileFromInputStream(contentResolver, uri)
            BackupUtils(this).restoreClientAuthBackup(authText)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityClientAuthBinding.inflate(layoutInflater)

        setContentView(mBinding.root)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setSupportActionBar(mBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mAdapter = ClientAuthListAdapter(
            this,
            contentResolver.query(ClientAuthContentProvider.CONTENT_URI,
                ClientAuthContentProvider.PROJECTION, null, null, null))

        contentResolver.registerContentObserver(ClientAuthContentProvider.CONTENT_URI,
            true, V3ClientAuthContentObserver(Handler(Looper.getMainLooper())))

        mBinding.fab.setOnClickListener { _: View? ->
            ClientAuthCreateDialogFragment().show(
                supportFragmentManager,
                ClientAuthCreateDialogFragment::class.java.simpleName)
        }

        mBinding.listClientAuth.adapter = mAdapter
        mBinding.listClientAuth.emptyView = mBinding.empty
        mBinding.listClientAuth.onItemClickListener =
            AdapterView.OnItemClickListener { parent: AdapterView<*>, _: View?, position: Int, _: Long ->
                val item = parent.getItemAtPosition(position) as Cursor
                val args = Bundle()

                val id = item.getInt(ClientAuthContentProvider.ClientAuth.ID)
                if (id != null) {
                    args.putInt(BUNDLE_KEY_ID, id)
                }

                args.putString(BUNDLE_KEY_DOMAIN,
                    item.getString(ClientAuthContentProvider.ClientAuth.DOMAIN))

                args.putString(BUNDLE_KEY_HASH,
                    item.getString(ClientAuthContentProvider.ClientAuth.HASH))

                ClientAuthActionsDialogFragment(args).show(supportFragmentManager,
                    ClientAuthActionsDialogFragment::class.java.simpleName)
            }
    }


    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(onAttach(base))
    }

    private inner class V3ClientAuthContentObserver(handler: Handler?) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            mAdapter.changeCursor(
                contentResolver.query(
                    ClientAuthContentProvider.CONTENT_URI,
                    ClientAuthContentProvider.PROJECTION, null, null, null))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_import_auth_priv) {
            // unfortunately no good way to filter .auth_private files
            readBackupLauncher.launch(createReadFileIntent(CLIENT_AUTH_SAF_MIME_TYPE))

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.v3_client_auth_menu, menu)

        return true
    }

    companion object {
        const val BUNDLE_KEY_ID: String = "_id"
        const val BUNDLE_KEY_DOMAIN: String = "domain"
        const val BUNDLE_KEY_HASH: String = "key_hash_value"

        const val CLIENT_AUTH_FILE_EXTENSION: String = ".auth_private"
        const val CLIENT_AUTH_SAF_MIME_TYPE: String = "*/*"
    }
}
