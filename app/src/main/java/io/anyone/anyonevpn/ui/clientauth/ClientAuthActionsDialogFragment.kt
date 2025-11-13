package io.anyone.anyonevpn.ui.clientauth

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonevpn.R

class ClientAuthActionsDialogFragment(args: Bundle?) : DialogFragment() {

    init {
        arguments = args
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ad = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle(R.string.v3_client_auth_activity_title)
            .setItems(
                arrayOf(
                    Html.fromHtml(getString(R.string.v3_backup_key), Html.FROM_HTML_MODE_LEGACY),
                    getString(R.string.v3_delete_client_authorization)
                ), null
            )
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()

        ad.listView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val fm = activity?.supportFragmentManager ?: return@OnItemClickListener

                if (position == 0) {
                    ClientAuthBackupDialogFragment(arguments).show(fm,
                        ClientAuthBackupDialogFragment::class.java.simpleName)
                }
                else {
                    ClientAuthDeleteDialogFragment(arguments).show(fm,
                        ClientAuthDeleteDialogFragment::class.java.simpleName)
                }

                ad.dismiss()
            }

        return ad
    }
}
