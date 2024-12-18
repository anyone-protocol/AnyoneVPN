package io.anyone.anyonebot.ui.hostedservices

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonebot.R
import io.anyone.anyonebot.core.ClipboardUtils.copyToClipboard
import io.anyone.anyonebot.core.DiskUtils.createWriteFileIntent

class HostedServiceActionsDialogFragment internal constructor(arguments: Bundle?) : DialogFragment() {

    init {
        setArguments(arguments)
    }

    private val mWriteFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            attemptToWriteBackup(result.data?.data)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ad = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setItems(
                arrayOf(
                    getString(R.string.copy_address_to_clipboard),
                    Html.fromHtml(getString(R.string.backup_service), Html.FROM_HTML_MODE_LEGACY),
                    getString(R.string.delete_service)
                ), null)
            .setNegativeButton(
                android.R.string.cancel
            ) {
                dialog: DialogInterface, _: Int -> dialog.dismiss()
            }
            .setTitle(R.string.hidden_services)
            .create()

        // done this way so we can startActivityForResult on backup without the dialog vanishing
        ad.listView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                when (position) {
                    0 -> {
                        doCopy(arguments)
                    }
                    1 -> {
                        doBackup(arguments)
                    }
                    2 -> {
                        HostedServiceDeleteDialogFragment(arguments).show(
                            parentFragmentManager,
                            HostedServiceDeleteDialogFragment::class.java.simpleName)
                    }
                }
                if (position != 1) dismiss()
            }
        return ad
    }

    private fun doCopy(arguments: Bundle?) {
        val anon = arguments?.getString(HostedServicesActivity.BUNDLE_KEY_DOMAIN)
        if (anon == null) {
            Toast.makeText(context,
                R.string.please_restart_to_enable_the_changes,
                Toast.LENGTH_LONG).show()
        }
        else {
            copyToClipboard("anon", anon, getString(R.string.done), context)
        }
    }

    private fun doBackup(arguments: Bundle?) {
        val filename =
            "anon_service" + arguments?.getString(HostedServicesActivity.BUNDLE_KEY_PORT) + ".zip"

        if (arguments?.getString(HostedServicesActivity.BUNDLE_KEY_DOMAIN) == null) {
            Toast.makeText(context,
                R.string.please_restart_to_enable_the_changes,
                Toast.LENGTH_LONG).show()

            return
        }

        mWriteFileLauncher.launch(createWriteFileIntent(filename, "application/zip"))
    }

    private fun attemptToWriteBackup(outputFile: Uri?) {
        if (outputFile == null) return
        if (context == null) return

        val relativePath = arguments?.getString(HostedServicesActivity.BUNDLE_KEY_PATH) ?: return
        val v3BackupUtils = V3BackupUtils(context)
        val backup = v3BackupUtils.createV3ZipBackup(relativePath, outputFile)

        Toast.makeText(context,
            if (backup != null) R.string.backup_saved_at_external_storage else R.string.error,
            Toast.LENGTH_LONG).show()

        dismiss()
    }
}