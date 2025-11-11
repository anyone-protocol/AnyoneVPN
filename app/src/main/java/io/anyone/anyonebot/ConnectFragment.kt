package io.anyone.anyonebot

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.anyone.anyonebot.core.NetworkUtils.isNetworkAvailable
import io.anyone.anyonebot.core.putNotSystem
import io.anyone.anyonebot.databinding.FragmentConnectBinding
import io.anyone.anyonebot.service.AnyoneBotConstants
import io.anyone.anyonebot.service.AnyoneBotService
import io.anyone.anyonebot.service.util.Prefs
import io.anyone.anyonebot.service.util.Utils
import io.anyone.anyonebot.ui.AppsFragment
import io.anyone.jni.AnonControlCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class ConnectFragment : Fragment(), ConnectionHelperCallbacks,
    ExitNodeDialogFragment.ExitNodeSelectedCallback, AppsFragment.OnChangeListener {

    companion object {
        private var begin: Long = 0
    }

    // main screen UI
    private lateinit var binding: FragmentConnectBinding

    private var lastStatus: String? = ""

    private lateinit var mainHandler: Handler
    private val counterTask = object : Runnable {
        override fun run() {
            if (begin < 1) begin = System.currentTimeMillis()

            binding.tvCounter.text = DateUtils.formatElapsedTime((System.currentTimeMillis() - begin) / 1000)
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val requestCodeVpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            startAnonAndVpn()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        (context as? AnyoneBotActivity)?.fragConnect = this
        lastStatus = (context as? AnyoneBotActivity)?.previousReceivedTorStatus

        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentConnectBinding.inflate(inflater, container, false)

        binding.btSelectApps.setOnClickListener {
            activity?.supportFragmentManager?.let {
                val fragment = AppsFragment(this)
                fragment.show(it, fragment.tag)
            }
        }

        binding.btRefresh.setOnClickListener {
            sendNewnymSignal()
        }

        binding.btChangeExit.setOnClickListener {
            openExitNodeDialog()
        }

        if (!isNetworkAvailable(requireContext())) {
            doLayoutNoInternet()
        }
        else {
            when (lastStatus) {
                AnyoneBotConstants.STATUS_OFF -> doLayoutOff()
                AnyoneBotConstants.STATUS_STARTING -> doLayoutStarting(requireContext())
                AnyoneBotConstants.STATUS_ON -> doLayoutOn()
                AnyoneBotConstants.STATUS_STOPPING -> {}
                else -> doLayoutOff()
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        if (binding.tvCounter.isVisible) {
            mainHandler.post(counterTask)
        }

        updateExitFlag()
    }

    override fun onPause() {
        super.onPause()

        mainHandler.removeCallbacks(counterTask)
    }

    private fun stopAnonAndVpn() {
        sendIntentToService(AnyoneBotConstants.ACTION_STOP)
        sendIntentToService(AnyoneBotConstants.ACTION_STOP_VPN)

        begin = 0
    }

    private fun sendNewnymSignal() {
        sendIntentToService(AnonControlCommands.SIGNAL_NEWNYM)

        binding.ivStatus.animate().alpha(0f).duration = 500

        lifecycleScope.launch(Dispatchers.Main) {
            delay(600)

            binding.ivStatus.animate().alpha(1f).duration = 500
        }
    }

    private fun openExitNodeDialog() {
        ExitNodeDialogFragment(this).show(
            requireActivity().supportFragmentManager, "ExitNodeDialogFragment"
        )
    }

    fun startAnonAndVpn() {
        begin = 0

        val vpnIntent = VpnService.prepare(requireActivity())?.putNotSystem()
        if (vpnIntent != null && (!Prefs.isPowerUserMode)) {
            requestCodeVpnLauncher.launch(vpnIntent)
        } else {
            // todo we need to add a power user mode for users to start the VPN without tor
            Prefs.putUseVpn(!Prefs.isPowerUserMode)
            sendIntentToService(AnyoneBotConstants.ACTION_START)

            if (!Prefs.isPowerUserMode) sendIntentToService(AnyoneBotConstants.ACTION_START_VPN)
        }
    }

    private fun doLayoutNoInternet() {
        binding.controlGroup.visibility = View.GONE

        binding.ivStatus.setImageResource(R.drawable.nointernet)

        binding.unconnectedGroup.visibility = View.VISIBLE

        binding.tvTitle.text = getString(R.string.no_internet_title)

        binding.tvSubtitle.text = getString(R.string.no_internet_subtitle)
        binding.tvSubtitle.visibility = View.VISIBLE

        binding.connectedGroup.visibility = View.GONE
    }

    fun doLayoutOn() {
        binding.controlGroup.visibility = View.VISIBLE

        binding.ivStatus.setImageResource(R.drawable.logo_started)
        binding.ivStatus.setOnClickListener {
            stopAnonAndVpn()
        }

        binding.unconnectedGroup.visibility = View.GONE

        binding.connectedGroup.visibility = View.VISIBLE

        val apps = Prefs.getSharedPrefs(context)
            ?.getString(AnyoneBotConstants.PREFS_KEY_ANONIFIED, "")
            ?.split("|")
            ?.filter { it.isNotEmpty() }
            ?.toTypedArray()

        binding.appIconFlow.referencedIds = arrayOf<Int>().toIntArray()

        for (view in binding.root.children) {
            if (view.tag?.toString()?.startsWith("app_icon") == true) {
                view.visibility = View.GONE
            }
        }

        if (apps.isNullOrEmpty()) {
            binding.tvFullDeviceVpn.setText(R.string.full_device_vpn)
            binding.appIconFlow.visibility = View.GONE
        }
        else {
            binding.tvFullDeviceVpn.setText(R.string.app_shortcuts)
            binding.appIconFlow.visibility = View.VISIBLE

            for (app in apps) {
                val iv = binding.root.findViewWithTag("app_icon_${app}") ?: getAppIcon(app) ?: continue

                if (iv.parent == null) {
                    binding.root.addView(iv)
                }
                else {
                    iv.visibility = View.VISIBLE
                }

                val ids = binding.appIconFlow.referencedIds.toMutableList()
                ids.add(iv.id)
                binding.appIconFlow.referencedIds = ids.toIntArray()
            }
        }

        binding.root.requestLayout()

        mainHandler.post(counterTask)
    }

    fun doLayoutOff() {
        binding.controlGroup.visibility = View.GONE

        binding.ivStatus.setImageResource(R.drawable.logo_stopped)
        binding.ivStatus.setOnClickListener {
            startAnonAndVpn()
        }

        binding.unconnectedGroup.visibility = View.VISIBLE

        binding.tvTitle.text = getString(R.string.secure_your_connection_title)

        binding.tvSubtitle.text = getString(R.string.secure_your_connection_subtitle)
        binding.tvSubtitle.visibility = View.VISIBLE

        binding.connectedGroup.visibility = View.GONE

        mainHandler.removeCallbacks(counterTask)
    }


    fun doLayoutStarting(context: Context) {
        binding.controlGroup.visibility = View.GONE

        binding.ivStatus.setImageResource(R.drawable.logo_starting_25)
        binding.ivStatus.setOnClickListener {
            stopAnonAndVpn()
        }

        binding.unconnectedGroup.visibility = View.VISIBLE

        binding.tvTitle.text = context.getString(R.string.trying_to_connect_title)

        binding.tvSubtitle.visibility = View.GONE

        binding.connectedGroup.visibility = View.GONE
    }

    fun setProgress(progress: Int) {
        if (progress in 25..< 50) {
            binding.ivStatus.setImageResource(R.drawable.logo_starting_50)
        }
        else if (progress in 50 ..< 100) {
            binding.ivStatus.setImageResource(R.drawable.logo_starting_75)
        }
    }

    override fun tryConnecting() {
        startAnonAndVpn() // TODO for now just start tor and VPN, we need to decouple this down the line
    }

    override fun onExitNodeSelected(countryCode: String, displayCountryName: String) {

        //tor format expects "{" for country code
        Prefs.exitNodes = "{$countryCode}"

        sendIntentToService(
            Intent(
                requireActivity(),
                AnyoneBotService::class.java
            ).setAction(AnyoneBotConstants.CMD_SET_EXIT).putExtra("exit", countryCode)
        )

        updateExitFlag()
    }

    override fun onAppsChange() {
        sendIntentToService(AnyoneBotConstants.ACTION_RESTART_VPN)

        doLayoutOn()
    }


    /** Sends intent to service, first modifying it to indicate it is not from the system */
    private fun sendIntentToService(intent: Intent) =
        ContextCompat.startForegroundService(requireActivity(), intent.putNotSystem())

    private fun sendIntentToService(action: String) {
        sendIntentToService(Intent(requireActivity(), AnyoneBotService::class.java).apply {
            this.action = action
        })
    }

    private fun getAppIcon(id: String): ImageView? {
        val context = context ?: return null
        val name: CharSequence
        val drawable: Drawable

        try {
            val appInfo = context.packageManager.getApplicationInfo(id, 0)
            name = context.packageManager.getApplicationLabel(appInfo)
            drawable = context.packageManager.getApplicationIcon(appInfo)
        }
        catch (_: Throwable) {
            return null
        }

        val iv = ImageView(context)
        iv.id = View.generateViewId()
        iv.tag = "app_icon_${id}"
        iv.contentDescription = name
        iv.setImageDrawable(drawable)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        params.height = 80
        params.width = 80
        params.setMargins(1, 10, 1, 1)
        iv.layoutParams = params

        iv.setOnClickListener {
            val i = context.packageManager.getLaunchIntentForPackage(id)

            if (i?.resolveActivity(context.packageManager) != null) {
                context.startActivity(i)
            }
        }

        return iv
    }

    private fun updateExitFlag() {
        val firstCountryCode = Prefs.firstExitCountry
        var flag = getString(R.string.globe)

        if (firstCountryCode?.length == 2) {
            flag = Utils.convertCountryCodeToFlagEmoji(firstCountryCode)
        }

        binding.btChangeExit.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
            Utils.emojiToDrawable(context,flag), null)
    }
}
