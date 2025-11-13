package io.anyone.anyonevpn.ui.hostedservices

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonevpn.R

import io.anyone.anyonevpn.core.DiskUtils.recursivelyDeleteDirectory
import io.anyone.anyonevpn.service.AnyoneVpnConstants

import java.io.File

class HostedServiceDeleteDialogFragment internal constructor(arguments: Bundle?) : DialogFragment() {

    init {
        this.arguments = arguments
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle(R.string.confirm_service_deletion)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                doDelete()
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
            }
            .create()
    }

    private fun doDelete() {
        val context = context ?: return
        val id = arguments?.getInt(HostedServicesActivity.BUNDLE_KEY_ID) ?: return

        context.contentResolver.delete(
            HostedServicesContentProvider.CONTENT_URI,
            "${HostedServicesContentProvider.HostedService.ID} = $id",
            null)

        val base = context.filesDir.absolutePath + "/" + AnyoneVpnConstants.ANON_SERVICES_DIR
        arguments?.getString(HostedServicesActivity.BUNDLE_KEY_PATH)?.let {
            recursivelyDeleteDirectory(File(base, it))
        }

        (activity as? HostedServicesActivity)?.showBatteryOptimizationsMessageIfAppropriate()
    }
}
