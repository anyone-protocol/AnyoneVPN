package io.anyone.anyonevpn.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.anyone.anyonevpn.R
import io.anyone.anyonevpn.databinding.ActionListViewBinding
import java.util.*


class MoreActionAdapter(context: Context, list: ArrayList<MenuAction>)
    : ArrayAdapter<MenuAction>(context, R.layout.action_list_view, list) {

    private val layoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: ActionListViewBinding.inflate(layoutInflater, parent, false).root

        getItem(position)?.let { model ->
            view.findViewById<ImageView>(R.id.ivAction)
                .setImageResource(model.imgId)

            view.findViewById<TextView>(R.id.tvLabel)
                .text = context.getString(model.textId)

            view.setOnClickListener { model.action() }
        }

        return view
    }
}