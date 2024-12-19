package io.anyone.anyonebot

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.scottyab.rootbeer.RootBeer
import io.anyone.anyonebot.core.LocaleHelper
import io.anyone.anyonebot.core.putNotSystem
import io.anyone.anyonebot.core.ui.BaseActivity
import io.anyone.anyonebot.service.AnyoneBotConstants
import io.anyone.anyonebot.service.util.Prefs
import io.anyone.anyonebot.ui.LogFragment
import io.anyone.anyonebot.service.AnyoneBotService

class AnyoneBotActivity : BaseActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    private lateinit var fragLog: LogFragment
    lateinit var fragConnect: ConnectFragment
    lateinit var fragMore: MoreFragment

    var previousReceivedTorStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /** TODO TODO TODO TODO TODO 
        Currently there are a lot of problems wiht landscape mode and bugs resulting from
        rotation. To this end, AnyoneBot will be locked into either portrait or landscape
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
            createAnyoneBot()

        } catch (re: RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: AnyoneBot DoS via exported activity (High)

            //clear malicious intent
            intent = null
            finish()
        }

    }

    private fun createAnyoneBot() {
        setContentView(R.layout.activity_anyonebot)

        fragLog = LogFragment()

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        val navController = findNavController(R.id.nav_fragment)
        bottomNavigationView.setupWithNavController(navController)
        bottomNavigationView.selectedItemId = R.id.connectFragment

        with(LocalBroadcastManager.getInstance(this)) {
            registerReceiver(
                anyoneBotServiceBroadcastReceiver, IntentFilter(AnyoneBotConstants.LOCAL_ACTION_STATUS)
            )
            registerReceiver(
                anyoneBotServiceBroadcastReceiver, IntentFilter(AnyoneBotConstants.LOCAL_ACTION_LOG)
            )
            registerReceiver(
                anyoneBotServiceBroadcastReceiver, IntentFilter(AnyoneBotConstants.LOCAL_ACTION_PORTS)
            )
            registerReceiver(
                anyoneBotServiceBroadcastReceiver,
                IntentFilter(AnyoneBotConstants.LOCAL_ACTION_SMART_CONNECT_EVENT)
            )
        }

        requestNotificationPermission()

        if (Prefs.detectRoot()) {
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
                requestPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
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
        sendIntentToService(AnyoneBotConstants.CMD_ACTIVE)
        LocaleHelper.onAttach(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(anyoneBotServiceBroadcastReceiver)
    }


    /** Sends intent to service, first modifying it to indicate it is not from the system */
    private fun sendIntentToService(intent: Intent) =
        ContextCompat.startForegroundService(this, intent.putNotSystem())

    private fun sendIntentToService(action: String) = sendIntentToService(
        Intent(
        this,
        AnyoneBotService::class.java
    ).apply {
        this.action = action
    })

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VPN && resultCode == RESULT_OK) {
            fragConnect.startAnonAndVpn()
        }
    }

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleHelper.onAttach(newBase))

    var allCircumventionAttemptsFailed = false

    private val anyoneBotServiceBroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(AnyoneBotConstants.EXTRA_STATUS)
            when (intent?.action) {
                AnyoneBotConstants.LOCAL_ACTION_STATUS -> {
                    if (status.equals(previousReceivedTorStatus)) return
                    when (status) {
                        AnyoneBotConstants.STATUS_OFF -> {
                            if (previousReceivedTorStatus.equals(AnyoneBotConstants.STATUS_STARTING)) {
                                if (allCircumventionAttemptsFailed) {
                                    allCircumventionAttemptsFailed = false
                                    return
                                }
                            } else if (fragConnect.isAdded && fragConnect.context != null) {
                                fragConnect.doLayoutOff()
                            }
                        }

                        AnyoneBotConstants.STATUS_STARTING -> if (fragConnect.isAdded && fragConnect.context != null) fragConnect.doLayoutStarting(this@AnyoneBotActivity)
                        AnyoneBotConstants.STATUS_ON -> if (fragConnect.isAdded && fragConnect.context != null) fragConnect.doLayoutOn()
                        AnyoneBotConstants.STATUS_STOPPING -> {}
                    }

                    previousReceivedTorStatus = status
                }

                AnyoneBotConstants.LOCAL_ACTION_LOG -> {
                    intent.getStringExtra(AnyoneBotConstants.LOCAL_EXTRA_BOOTSTRAP_PERCENT)?.let {
                        fragConnect.setProgress(Integer.parseInt(it))
                    }
                    intent.getStringExtra(AnyoneBotConstants.LOCAL_EXTRA_LOG)?.let {
                        fragLog.appendLog(it)
                    }
                }

                AnyoneBotConstants.LOCAL_ACTION_PORTS -> {
                    val socks = intent.getIntExtra(AnyoneBotConstants.EXTRA_SOCKS_PROXY_PORT, -1)
                    val http = intent.getIntExtra(AnyoneBotConstants.EXTRA_HTTP_PROXY_PORT, -1)
                    if (http > 0 && socks > 0) fragMore.setPorts(http, socks)
                }

                else -> {}
            }
        }
    }

    companion object {
        const val REQUEST_CODE_VPN = 1234
        const val REQUEST_CODE_SETTINGS = 2345
        const val REQUEST_VPN_APP_SELECT = 2432
    }

    fun showLog() {
        if (!fragLog.isAdded) {
            fragLog.show(supportFragmentManager, AnyoneBotActivity::class.java.simpleName)
        }
    }
}
