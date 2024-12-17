package io.anyone.anyonebot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.anyone.anyonebot.AnyoneBotActivity.Companion.REQUEST_CODE_SETTINGS
import io.anyone.anyonebot.core.putNotSystem
import io.anyone.anyonebot.core.ui.SettingsActivity
import io.anyone.anyonebot.service.AnyoneBotConstants
import io.anyone.anyonebot.service.AnyoneBotService
import io.anyone.anyonebot.ui.AboutDialogFragment
import io.anyone.anyonebot.ui.AppsFragment
import io.anyone.anyonebot.ui.MenuAction
import io.anyone.anyonebot.ui.MoreActionAdapter
import io.anyone.anyonebot.ui.v3onionservice.OnionServiceActivity
import io.anyone.anyonebot.ui.v3onionservice.clientauth.ClientAuthActivity

class MoreFragment : Fragment(), AppsFragment.OnChangeListener {
    private lateinit var lvMore: ListView

    private var httpPort = -1
    private var socksPort = -1

    private lateinit var tvStatus: TextView

    override fun onAttach(context: Context) {
        super.onAttach(context)

        (context as? AnyoneBotActivity)?.fragMore = this
    }


    fun setPorts(newHttpPort: Int, newSocksPort: Int) {
        httpPort = newHttpPort
        socksPort = newSocksPort
        if (view != null) updateStatus()
    }

    private fun updateStatus() {
        val sb = StringBuilder()

        sb.append(getString(R.string.proxy_ports)).append(" ")

        if (httpPort != -1 && socksPort != -1) {
            sb.append("\nHTTP: ").append(httpPort).append(" - ").append(" SOCKS: ")
                .append(socksPort)
        } else {
            sb.append(": " + getString(R.string.ports_not_set))
        }

        sb.append("\n\n")

        val manager = requireActivity().packageManager
        val info =
            manager.getPackageInfo(requireActivity().packageName, PackageManager.GET_ACTIVITIES)
        sb.append(getString(R.string.app_name)).append(" ").append(info.versionName).append("\n")
        sb.append("Tor v").append(getTorVersion())

        tvStatus.text = sb.toString()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_more, container, false)
        tvStatus = view.findViewById(R.id.tvVersion)

        updateStatus()
        lvMore = view.findViewById(R.id.lvMoreActions)

        val listItems =
            arrayListOf(MenuAction(R.string.v3_hosted_services, R.drawable.ic_menu_onion) {
                startActivity(Intent(requireActivity(), OnionServiceActivity::class.java))
            },
                MenuAction(
                    R.string.v3_client_auth_activity_title, R.drawable.ic_shield
                ) { startActivity(Intent(requireActivity(), ClientAuthActivity::class.java)) },
                MenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                    activity?.supportFragmentManager?.let {
                        val fragment = AppsFragment(this)
                        fragment.show(it, fragment.tag)
                    }
                },
                MenuAction(R.string.menu_settings, R.drawable.ic_settings_gear) {

                    activity?.startActivityForResult(
                        Intent(context, SettingsActivity::class.java), REQUEST_CODE_SETTINGS
                    )
                },
                MenuAction(R.string.menu_log, R.drawable.ic_log) { showLog() },
                MenuAction(R.string.menu_about, R.drawable.ic_about) {
                    AboutDialogFragment().show(
                        requireActivity().supportFragmentManager, AboutDialogFragment.TAG
                    )
                },
                MenuAction(R.string.menu_exit, R.drawable.ic_exit) { doExit() })
        lvMore.adapter = MoreActionAdapter(requireActivity(), listItems)

        return view
    }

    override fun onAppsChange() {
        sendIntentToService(AnyoneBotConstants.ACTION_RESTART_VPN)
    }

    private fun getTorVersion(): String {
        return AnyoneBotService.BINARY_TOR_VERSION.split("-").toTypedArray()[0]
    }


    private fun doExit() {
        val killIntent = Intent(
            requireActivity(), AnyoneBotService::class.java
        ).setAction(AnyoneBotConstants.ACTION_STOP)
            .putExtra(AnyoneBotConstants.ACTION_STOP_FOREGROUND_TASK, true)
        sendIntentToService(AnyoneBotConstants.ACTION_STOP_VPN)
        sendIntentToService(killIntent)
        requireActivity().finish()
    }

    private fun sendIntentToService(intent: Intent) =
        ContextCompat.startForegroundService(requireActivity(), intent.putNotSystem())

    private fun sendIntentToService(action: String) =
        sendIntentToService(Intent(requireActivity(), AnyoneBotService::class.java).apply {
            this.action = action
        })

    private fun showLog() {
        (activity as AnyoneBotActivity).showLog()
    }
}