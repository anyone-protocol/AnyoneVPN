package io.anyone.anyonebot.ui.clientauth

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
import io.anyone.anyonebot.R
import io.anyone.anyonebot.databinding.ClientAuthItemBinding
import io.anyone.anyonebot.utils.getInt
import io.anyone.anyonebot.utils.getString

class ClientAuthListAdapter internal constructor(context: Context, cursor: Cursor?) :
    CursorAdapter(context, cursor, 0) {

    private val mLayoutInflator =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        return ClientAuthItemBinding.inflate(mLayoutInflator, parent, false).root
    }

    @SuppressLint("SetTextI18n")
    override fun bindView(view: View, context: Context, cursor: Cursor) {

        view.findViewById<TextView>(R.id.tvDomain).text =
            cursor.getString(ClientAuthContentProvider.ClientAuth.DOMAIN) + context.getString(R.string.anon)

        val enabled = view.findViewById<SwitchCompat>(R.id.swAuth)

        enabled.isChecked = cursor.getInt(ClientAuthContentProvider.ClientAuth.ENABLED) == 1

        enabled.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            val fields = ContentValues()
            fields.put(ClientAuthContentProvider.ClientAuth.ENABLED, isChecked)

            val id = cursor.getInt(ClientAuthContentProvider.ClientAuth.ID)

            context.contentResolver.update(
                ClientAuthContentProvider.CONTENT_URI, fields,
                "${ClientAuthContentProvider.ClientAuth.ID} = $id", null)

            Toast.makeText(context, R.string.please_restart_to_enable_the_changes,
                Toast.LENGTH_LONG).show()
        }
    }
}

