package io.anyone.anyonebot.ui.clientauth

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonebot.R
import io.anyone.anyonebot.core.DiskUtils.createWriteFileIntent
import io.anyone.anyonebot.core.ui.NoPersonalizedLearningEditText
import io.anyone.anyonebot.utils.BackupUtils

class ClientAuthBackupDialogFragment(args: Bundle?) : DialogFragment() {

    private lateinit var etFilename: NoPersonalizedLearningEditText
    private lateinit var fileNameTextWatcher: TextWatcher

    private val writeFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data?.data ?: return@registerForActivityResult

            attemptToWriteBackup(data)
        }
    }

    init {
        arguments = args
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ad = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle(R.string.v3_backup_key)
            .setMessage(R.string.v3_backup_key_warning)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()

        ad.setOnShowListener {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                doBackup()
            }
        }

        val container = FrameLayout(ad.context)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)

        val margin = resources.getDimensionPixelOffset(R.dimen.alert_dialog_margin)
        params.leftMargin = margin
        params.rightMargin = margin

        etFilename = NoPersonalizedLearningEditText(ad.context, null)
        etFilename.isSingleLine = true
        etFilename.setHint(R.string.v3_backup_name_hint)

        if (savedInstanceState != null) {
            etFilename.setText(savedInstanceState.getString(BUNDLE_KEY_FILENAME, ""))
        }

        fileNameTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                ad.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = !s.isNullOrBlank()
            }
        }

        etFilename.addTextChangedListener(fileNameTextWatcher)
        etFilename.layoutParams = params
        container.addView(etFilename)

        ad.setView(container)

        return ad
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(BUNDLE_KEY_FILENAME, etFilename.text.toString())
    }


    override fun onStart() {
        super.onStart()

        fileNameTextWatcher.afterTextChanged(etFilename.editableText)
    }


    private fun doBackup() {
        var filename = etFilename.text.toString().trim()

        if (!filename.endsWith(ClientAuthActivity.CLIENT_AUTH_FILE_EXTENSION)) {
            filename += ClientAuthActivity.CLIENT_AUTH_FILE_EXTENSION
        }

        writeFileLauncher.launch(createWriteFileIntent(filename,
            ClientAuthActivity.CLIENT_AUTH_SAF_MIME_TYPE
        ))
    }

    private fun attemptToWriteBackup(outputFile: Uri) {
        val context = context ?: return

        val backupUtils = BackupUtils(context)

        val domain = arguments?.getString(ClientAuthActivity.BUNDLE_KEY_DOMAIN) ?: return
        val hash = arguments?.getString(ClientAuthActivity.BUNDLE_KEY_HASH) ?: return
        val backup = backupUtils.createAuthBackup(domain, hash, outputFile)

        Toast.makeText(context,
            if (backup != null) R.string.backup_saved_at_external_storage else R.string.error,
            Toast.LENGTH_LONG).show()

        dismiss()
    }

    companion object {
        private const val BUNDLE_KEY_FILENAME = "filename"
    }
}
