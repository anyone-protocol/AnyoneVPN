package io.anyone.anyonevpn

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
import io.anyone.anyonevpn.core.putNotSystem
import io.anyone.anyonevpn.core.ui.SettingsActivity
import io.anyone.anyonevpn.databinding.FragmentMoreBinding
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import io.anyone.anyonevpn.service.AnyoneVpnService
import io.anyone.anyonevpn.service.util.Prefs
import io.anyone.anyonevpn.ui.AboutDialogFragment
import io.anyone.anyonevpn.ui.AppsFragment
import io.anyone.anyonevpn.ui.MenuAction
import io.anyone.anyonevpn.ui.MoreActionAdapter
import io.anyone.anyonevpn.ui.hostedservices.HostedServicesActivity
import io.anyone.anyonevpn.ui.clientauth.ClientAuthActivity

class MoreFragment : Fragment(), AppsFragment.OnChangeListener {

    private lateinit var mBinding: FragmentMoreBinding

    private var httpPort = -1
    private var socksPort = -1

    private val mSettingsActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Prefs.defaultLocale = result.data?.getStringExtra("locale")

            sendIntentToService(AnyoneVpnConstants.ACTION_LOCAL_LOCALE_SET)

            (activity?.application as? AnyoneVpnApp)?.setLocale()
            activity?.finish()

            startActivity(Intent(activity, AnyoneVpnActivity::class.java))
        }
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)

        (context as? AnyoneVpnActivity)?.fragMore = this
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
                (activity as? AnyoneVpnActivity)?.showLog()
            },
            MenuAction(R.string.menu_about, R.drawable.ic_anyone) {
                AboutDialogFragment().show(
                    requireActivity().supportFragmentManager, AboutDialogFragment.TAG
                )
            },
            MenuAction(R.string.menu_exit, R.drawable.ic_logout) {
                val activity = activity ?: return@MenuAction

                val killIntent = Intent(activity, AnyoneVpnService::class.java)
                    .setAction(AnyoneVpnConstants.ACTION_STOP)
                    .putExtra(AnyoneVpnConstants.ACTION_STOP_FOREGROUND_TASK, true)

                sendIntentToService(AnyoneVpnConstants.ACTION_STOP_VPN)
                sendIntentToService(killIntent)

                activity.finish()
            })

        mBinding.lvMoreActions.adapter = MoreActionAdapter(requireActivity(), listItems)

        return mBinding.root
    }

    override fun onAppsChange() {
        sendIntentToService(AnyoneVpnConstants.ACTION_RESTART_VPN)
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
            .append(AnyoneVpnService.BINARY_ANON_VERSION.split("-").firstOrNull() ?: "?")

        mBinding.tvVersion.text = sb.toString()
    }

    private fun sendIntentToService(intent: Intent) {
        val activity = activity ?: return

        ContextCompat.startForegroundService(activity, intent.putNotSystem())
    }

    private fun sendIntentToService(action: String) {
        val activity = activity ?: return
        val i = Intent(activity, AnyoneVpnService::class.java)
        i.action = action

        sendIntentToService(i)
    }
}