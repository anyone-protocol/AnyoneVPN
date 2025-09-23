package io.anyone.anyonebot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.anyone.anyonebot.core.putNotSystem
import io.anyone.anyonebot.core.ui.SettingsActivity
import io.anyone.anyonebot.databinding.FragmentMoreBinding
import io.anyone.anyonebot.service.AnyoneBotConstants
import io.anyone.anyonebot.service.AnyoneBotService
import io.anyone.anyonebot.service.util.Prefs
import io.anyone.anyonebot.ui.AboutDialogFragment
import io.anyone.anyonebot.ui.AppsFragment
import io.anyone.anyonebot.ui.MenuAction
import io.anyone.anyonebot.ui.MoreActionAdapter
import io.anyone.anyonebot.ui.hostedservices.HostedServicesActivity
import io.anyone.anyonebot.ui.clientauth.ClientAuthActivity

class MoreFragment : Fragment(), AppsFragment.OnChangeListener {

    private lateinit var mBinding: FragmentMoreBinding

    private var httpPort = -1
    private var socksPort = -1

    private val mSettingsActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Prefs.defaultLocale = result.data?.getStringExtra("locale")

            sendIntentToService(AnyoneBotConstants.ACTION_LOCAL_LOCALE_SET)

            (activity?.application as? AnyoneBotApp)?.setLocale()
            activity?.finish()

            startActivity(Intent(activity, AnyoneBotActivity::class.java))
        }
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)

        (context as? AnyoneBotActivity)?.fragMore = this
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        mBinding = FragmentMoreBinding.inflate(inflater, container, false)

        updateStatus()

        val listItems = arrayListOf(
            MenuAction(R.string.v3_hosted_services, R.drawable.ic_data) {
                startActivity(Intent(requireActivity(), HostedServicesActivity::class.java))
            },
            MenuAction(
                R.string.v3_client_auth_activity_title, R.drawable.ic_shield_security
            ) {
                startActivity(Intent(requireActivity(), ClientAuthActivity::class.java))
              },
            MenuAction(R.string.btn_choose_apps, R.drawable.ic_add_square) {
                activity?.supportFragmentManager?.let {
                    val fragment = AppsFragment(this)
                    fragment.show(it, fragment.tag)
                }
            },
            MenuAction(R.string.menu_settings, R.drawable.ic_settings) {
                mSettingsActivityLauncher.launch(Intent(context, SettingsActivity::class.java))
            },
            MenuAction(R.string.menu_log, R.drawable.ic_textalign_left) {
                (activity as? AnyoneBotActivity)?.showLog()
            },
            MenuAction(R.string.menu_about, R.drawable.ic_anyone) {
                AboutDialogFragment().show(
                    requireActivity().supportFragmentManager, AboutDialogFragment.TAG
                )
            },
            MenuAction(R.string.menu_exit, R.drawable.ic_logout) {
                val activity = activity ?: return@MenuAction

                val killIntent = Intent(activity, AnyoneBotService::class.java)
                    .setAction(AnyoneBotConstants.ACTION_STOP)
                    .putExtra(AnyoneBotConstants.ACTION_STOP_FOREGROUND_TASK, true)

                sendIntentToService(AnyoneBotConstants.ACTION_STOP_VPN)
                sendIntentToService(killIntent)

                activity.finish()
            })

        mBinding.lvMoreActions.adapter = MoreActionAdapter(requireActivity(), listItems)

        return mBinding.root
    }

    override fun onAppsChange() {
        sendIntentToService(AnyoneBotConstants.ACTION_RESTART_VPN)
    }

    fun setPorts(newHttpPort: Int, newSocksPort: Int) {
        httpPort = newHttpPort
        socksPort = newSocksPort

        if (view != null) updateStatus()
    }

    private fun updateStatus() {
        val sb = StringBuilder()

        sb.append(getString(R.string.proxy_ports))
            .append(" ")

        if (httpPort != -1 && socksPort != -1) {
            sb.append("\nHTTP: ")
                .append(httpPort)
                .append(" - ")
                .append(" SOCKS: ")
                .append(socksPort)
        }
        else {
            sb.append(": " + getString(R.string.ports_not_set))
        }

        sb.append("\n\n")
            .append(getString(R.string.app_name))

        val activity = activity
        if (activity != null) {
            val info = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_ACTIVITIES)

            sb.append(" ")
                .append(info.versionName)
        }

        sb.append("\n")
            .append("Anon v")
            .append(AnyoneBotService.BINARY_ANON_VERSION.split("-").firstOrNull() ?: "?")

        mBinding.tvVersion.text = sb.toString()
    }

    private fun sendIntentToService(intent: Intent) {
        val activity = activity ?: return

        ContextCompat.startForegroundService(activity, intent.putNotSystem())
    }

    private fun sendIntentToService(action: String) {
        val activity = activity ?: return
        val i = Intent(activity, AnyoneBotService::class.java)
        i.action = action

        sendIntentToService(i)
    }
}