package io.anyone.anyonevpn

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.scottyab.rootbeer.RootBeer
import io.anyone.anyonevpn.core.LocaleHelper
import io.anyone.anyonevpn.core.putNotSystem
import io.anyone.anyonevpn.core.ui.BaseActivity
import io.anyone.anyonevpn.databinding.ActivityAnyonevpnBinding
import io.anyone.anyonevpn.service.AnyoneVpnConstants
import io.anyone.anyonevpn.service.AnyoneVpnService
import io.anyone.anyonevpn.service.util.Prefs
import io.anyone.anyonevpn.ui.LogFragment

class AnyoneVpnActivity : BaseActivity() {

    private lateinit var mBinding: ActivityAnyonevpnBinding

    private lateinit var mFragLog: LogFragment

    lateinit var fragConnect: ConnectFragment
    lateinit var fragMore: MoreFragment

    var previousReceivedTorStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /** TODO TODO TODO TODO TODO 
        Currently there are a lot of problems with landscape mode and bugs resulting from
        rotation. To this end, Anyone VPN will be locked into either portrait or landscape
        if the device is a tablet (whichever the app is set when an activity is created)
        until these things are fixed. On smaller devices it's just portrait...
         */
        val isTablet =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >=
                    Configuration.SCREENLAYOUT_SIZE_LARGE
        requestedOrientation = if (isTablet) {
            val currentOrientation = resources.configuration.orientation
            val lockedInOrientation =
                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            lockedInOrientation
        } else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT


        try {
            createAnyoneVpn()

        } catch (re: RuntimeException) {
            // Catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2:
            // Anyone VPN DoS via exported activity (High)

            //clear malicious intent
            intent = null
            finish()
        }

    }

    private fun createAnyoneVpn() {
        mBinding = ActivityAnyonevpnBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mFragLog = LogFragment()

        val navController = findNavController(R.id.nav_fragment)
        mBinding.bottomNavigation.setupWithNavController(navController)
        mBinding.bottomNavigation.selectedItemId = R.id.connectFragment

        val filter = IntentFilter().apply {
            addAction(AnyoneVpnConstants.LOCAL_ACTION_STATUS)
            addAction(AnyoneVpnConstants.LOCAL_ACTION_LOG)
            addAction(AnyoneVpnConstants.LOCAL_ACTION_PORTS)
            addAction(AnyoneVpnConstants.LOCAL_ACTION_SMART_CONNECT_EVENT)
        }

        ContextCompat.registerReceiver(this, anyoneVpnServiceBroadcastReceiver,
            filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        requestNotificationPermission()

        if (Prefs.detectRoot) {
            val rootBeer = RootBeer(this)
            if (rootBeer.isRooted) {
                //we found indication of root
                val toast = Toast.makeText(
                    applicationContext, getString(R.string.root_warning), Toast.LENGTH_LONG
                )
                toast.show()
            } else {
                //we didn't find indication of root
            }
        }

    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun requestNotificationPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                // You can use the API that requires the permission.
            }

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your
            // app.
        } else {
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied. At the
            // same time, respect the user's decision. Don't link to system
            // settings in an effort to convince the user to change their
            // decision.
        }
    }

    override fun onResume() {
        super.onResume()

        sendIntentToService(AnyoneVpnConstants.CMD_ACTIVE)
        LocaleHelper.onAttach(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(anyoneVpnServiceBroadcastReceiver)
    }


    /** Sends intent to service, first modifying it to indicate it is not from the system */
    private fun sendIntentToService(intent: Intent) =
        ContextCompat.startForegroundService(this, intent.putNotSystem())

    private fun sendIntentToService(action: String) = sendIntentToService(
        Intent(
        this,
        AnyoneVpnService::class.java
    ).apply {
        this.action = action
    })

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleHelper.onAttach(newBase))

    var allCircumventionAttemptsFailed = false

    private val anyoneVpnServiceBroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(AnyoneVpnConstants.EXTRA_STATUS)
            when (intent?.action) {
                AnyoneVpnConstants.LOCAL_ACTION_STATUS -> {
                    if (status.equals(previousReceivedTorStatus)) return
                    when (status) {
                        AnyoneVpnConstants.STATUS_OFF -> {
                            if (previousReceivedTorStatus.equals(AnyoneVpnConstants.STATUS_STARTING)) {
                                if (allCircumventionAttemptsFailed) {
                                    allCircumventionAttemptsFailed = false
                                    return
                                }
                            } else if (fragConnect.isAdded && fragConnect.context != null) {
                                fragConnect.doLayoutOff()
                            }
                        }

                        AnyoneVpnConstants.STATUS_STARTING -> if (fragConnect.isAdded && fragConnect.context != null) fragConnect.doLayoutStarting(this@AnyoneVpnActivity)
                        AnyoneVpnConstants.STATUS_ON -> if (fragConnect.isAdded && fragConnect.context != null) fragConnect.doLayoutOn()
                        AnyoneVpnConstants.STATUS_STOPPING -> {}
                    }

                    previousReceivedTorStatus = status
                }

                AnyoneVpnConstants.LOCAL_ACTION_LOG -> {
                    intent.getStringExtra(AnyoneVpnConstants.LOCAL_EXTRA_BOOTSTRAP_PERCENT)?.let {
                        fragConnect.setProgress(Integer.parseInt(it))
                    }
                    intent.getStringExtra(AnyoneVpnConstants.LOCAL_EXTRA_LOG)?.let {
                        mFragLog.appendLog(it)
                    }
                }

                AnyoneVpnConstants.LOCAL_ACTION_PORTS -> {
                    val socks = intent.getIntExtra(AnyoneVpnConstants.EXTRA_SOCKS_PROXY_PORT, -1)
                    val http = intent.getIntExtra(AnyoneVpnConstants.EXTRA_HTTP_PROXY_PORT, -1)
                    if (http > 0 && socks > 0) fragMore.setPorts(http, socks)
                }

                else -> {}
            }
        }
    }

    fun showLog() {
        if (!mFragLog.isAdded) {
            mFragLog.show(supportFragmentManager, AnyoneVpnActivity::class.java.simpleName)
        }
    }
}
