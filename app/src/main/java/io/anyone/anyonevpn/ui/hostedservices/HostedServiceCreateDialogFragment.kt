package io.anyone.anyonevpn.ui.hostedservices

import android.app.Dialog
import android.content.ContentValues
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonevpn.R
import io.anyone.anyonevpn.databinding.DialogHsDataBinding

class HostedServiceCreateDialogFragment : DialogFragment() {

    private lateinit var mBinding: DialogHsDataBinding

    private var inputValidator: TextWatcher? = null

    override fun onStart() {
        super.onStart()

        inputValidator?.afterTextChanged(null) // initially disable positive button
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mBinding = DialogHsDataBinding.inflate(layoutInflater)

        val ad = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle(R.string.hidden_services)
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
            }
            .setPositiveButton(
                R.string.save
            ) { _: DialogInterface?, _: Int ->
                doSave()
            }
            .setView(mBinding.root)
            .create()

        inputValidator = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // ignored
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // ignored
            }

            override fun afterTextChanged(s: Editable?) {
                ad.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    toInt(mBinding.etLocalPort) in 1..65535
                            && toInt(mBinding.etAnonPort) in 1 .. 65535
                            && !mBinding.edServiceName.text.isNullOrBlank()
            }
        }

        mBinding.edServiceName.addTextChangedListener(inputValidator)
        mBinding.etLocalPort.addTextChangedListener(inputValidator)
        mBinding.etAnonPort.addTextChangedListener(inputValidator)

        return ad
    }

    private fun toInt(et: EditText): Int {
        return try {
            et.text.toString().toInt()
        }
        catch (e: NumberFormatException) {
            0
        }
    }

    private fun doSave() {
        val serverName = mBinding.edServiceName.text.toString().trim { it <= ' ' }
        val localPort = toInt(mBinding.etLocalPort)
        val onionPort = toInt(mBinding.etAnonPort)

        val fields = ContentValues()
        fields.put(HostedServicesContentProvider.HostedService.NAME, serverName)
        fields.put(HostedServicesContentProvider.HostedService.PORT, localPort)
        fields.put(HostedServicesContentProvider.HostedService.ANON_PORT, onionPort)
        fields.put(HostedServicesContentProvider.HostedService.CREATED_BY_USER, 1)

        val cr = context?.contentResolver
        cr?.insert(HostedServicesContentProvider.CONTENT_URI, fields)

        Toast.makeText(context, R.string.please_restart_to_enable_the_changes, Toast.LENGTH_SHORT)
            .show()

        (activity as? HostedServicesActivity)?.showBatteryOptimizationsMessageIfAppropriate()
    }
}