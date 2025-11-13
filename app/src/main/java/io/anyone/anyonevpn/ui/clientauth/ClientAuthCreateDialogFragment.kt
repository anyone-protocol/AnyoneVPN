package io.anyone.anyonevpn.ui.clientauth

import android.app.Dialog
import android.content.ContentValues
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonevpn.R
import io.anyone.anyonevpn.databinding.DialogAddClientAuthBinding

class ClientAuthCreateDialogFragment : DialogFragment() {

    private lateinit var mBinding: DialogAddClientAuthBinding

    private lateinit var inputValidator: TextWatcher

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mBinding = DialogAddClientAuthBinding.inflate(layoutInflater)

        val ad = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setView(mBinding.root)
            .setTitle(R.string.v3_client_auth_activity_title)
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.save) { _: DialogInterface?, _: Int ->
                doSave()
            }
            .create()

        inputValidator = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                ad.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    sanitizeDomain(mBinding.etDomain.text).matches("([a-z0-9]{56})".toRegex())
                            && mBinding.etPrivateKey.text?.matches("([A-Z2-7]{52})".toRegex()) == true
            }
        }

        mBinding.etDomain.addTextChangedListener(inputValidator)
        mBinding.etPrivateKey.addTextChangedListener(inputValidator)

        return ad
    }

    override fun onStart() {
        super.onStart()

        inputValidator.afterTextChanged(null)
    }

    private fun doSave() {
        val domain = sanitizeDomain(mBinding.etDomain.text)
        val hash = mBinding.etPrivateKey.text.toString()

        val fields = ContentValues()
        fields.put(ClientAuthContentProvider.ClientAuth.DOMAIN, domain)
        fields.put(ClientAuthContentProvider.ClientAuth.HASH, hash)

        context?.contentResolver?.insert(ClientAuthContentProvider.CONTENT_URI, fields)

        Toast.makeText(context, R.string.please_restart_to_enable_the_changes, Toast.LENGTH_LONG)
            .show()
    }

    private fun sanitizeDomain(domain: Editable?): String {
        val tld = context?.getString(R.string.anon) ?: ".anon"

        return if (domain?.endsWith(tld) == true) domain.substring(0, domain.indexOf(tld)) else domain.toString()
    }
}
