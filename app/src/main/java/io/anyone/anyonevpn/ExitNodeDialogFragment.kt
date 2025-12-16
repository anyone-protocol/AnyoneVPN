package io.anyone.anyonevpn

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.anyone.anyonevpn.service.util.Utils
import java.text.Collator
import java.util.*

class ExitNodeDialogFragment(private val callback: ExitNodeSelectedCallback) : DialogFragment() {

    interface ExitNodeSelectedCallback {
        fun onExitNodeSelected(countryCode: String, displayCountryName: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val countries = COUNTRY_CODES
            .map { Locale("", it) }
            .sortedWith(compareBy(Collator.getInstance()) { it.displayCountry } )

        val items = mutableListOf("${getString(R.string.globe)} ${getString(R.string.vpn_default_world)}")
        items.addAll(countries.map {
            "${Utils.convertCountryCodeToFlagEmoji(it.country)} ${it.displayCountry}"
        })

        return AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle(R.string.btn_change_exit)
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .setItems(items.toTypedArray()) { _, pos ->
                callback.onExitNodeSelected(if (pos < 1) "" else countries[pos - 1].country, items[pos])
            }
            .create()
    }

    companion object {
        private val COUNTRY_CODES = arrayOf(
            "AD",
            "AE",
            "AT",
            "CA",
            "CH",
            "DE",
            "EG",
            "ES",
            "FR",
            "GB",
            "HU",
            "IT",
            "LV",
            "MD",
            "NL",
            "PL",
            "RO",
            "SE",
            "TR",
            "UA",
            "US",
            "ZA",
        )
    }
}