package io.anyone.anyonevpn.ui.clientauth

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonevpn.R

class ClientAuthDeleteDialogFragment(args: Bundle?) : DialogFragment() {

    init {
        arguments = args
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle(R.string.v3_delete_client_authorization)
            .setPositiveButton(R.string.v3_delete_client_authorization_confirm) { _: DialogInterface?, _: Int ->
                doDelete()
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()
    }

    private fun doDelete() {
        val id = arguments?.getInt(ClientAuthActivity.BUNDLE_KEY_ID)

        context?.contentResolver?.delete(
            ClientAuthContentProvider.CONTENT_URI,
            "${ClientAuthContentProvider.ClientAuth.ID} = $id",
            null)
    }
}
