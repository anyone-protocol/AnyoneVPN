package io.anyone.anyonevpn.ui.hostedservices

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.CursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import io.anyone.anyonevpn.R
import io.anyone.anyonevpn.databinding.HsListItemBinding
import io.anyone.anyonevpn.utils.getInt
import io.anyone.anyonevpn.utils.getString

class HostedServiceListAdapter internal constructor(context: Context, cursor: Cursor?) :
    CursorAdapter(context, cursor, 0) {

    private val mLayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        return HsListItemBinding.inflate(mLayoutInflater, parent, false).root
    }

    @SuppressLint("SetTextI18n")
    override fun bindView(view: View, context: Context, cursor: Cursor) {
        view.findViewById<TextView>(R.id.tvLocalPort).text =
            "${context.getString(R.string.local_port)}\n${cursor.getString(HostedServicesContentProvider.HostedService.PORT) ?: ""}"

        view.findViewById<TextView>(R.id.tvAnonPort).text =
            "${context.getString(R.string.anon_port)}\n${cursor.getString(HostedServicesContentProvider.HostedService.ANON_PORT) ?: ""}"

        view.findViewById<TextView>(R.id.tvServiceName).text =
            cursor.getString(HostedServicesContentProvider.HostedService.NAME)

        view.findViewById<TextView>(R.id.tvServiceDomain).text =
            cursor.getString(HostedServicesContentProvider.HostedService.DOMAIN)

        val enabled = view.findViewById<SwitchCompat>(R.id.swService)
        enabled.isChecked = cursor.getInt(HostedServicesContentProvider.HostedService.ENABLED) == 1

        enabled.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            val fields = ContentValues()
            fields.put(HostedServicesContentProvider.HostedService.ENABLED, isChecked)

            val id = cursor.getInt(HostedServicesContentProvider.HostedService.ID) ?: -1

            context.contentResolver.update(HostedServicesContentProvider.CONTENT_URI, fields,
                HostedServicesContentProvider.HostedService.ID + "=" + id, null)

            Toast.makeText(context, R.string.please_restart_to_enable_the_changes,
                Toast.LENGTH_SHORT).show()
        }
    }
}
