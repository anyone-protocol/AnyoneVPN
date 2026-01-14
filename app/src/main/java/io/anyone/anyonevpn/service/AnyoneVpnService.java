/* Copyright (c) 2009-2011, Nathan Freitas, Orbot / The Guardian Project - https://guardianproject.info/apps/orbot */
/* See LICENSE for licensing information */

package io.anyone.anyonevpn.service;

import static io.anyone.jni.AnonService.ACTION_ERROR;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import io.anyone.anyonevpn.R;
import io.anyone.anyonevpn.service.util.CustomAnonResourceInstaller;
import io.anyone.anyonevpn.service.util.Prefs;
import io.anyone.anyonevpn.service.util.Utils;
import io.anyone.anyonevpn.service.vpn.AnyoneVpnManager;
import io.anyone.jni.AnonControlCommands;
import io.anyone.jni.AnonControlConnection;
import io.anyone.jni.AnonService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
public class AnyoneVpnService extends VpnService implements AnyoneVpnConstants {

    public final static String BINARY_ANON_VERSION = AnonService.VERSION_NAME;

    static final int NOTIFY_ID = 1;
    private static final int ERROR_NOTIFY_ID = 3;

    //these will be set dynamically due to build flavors
    private static Uri V3_HIDDEN_SERVICES_CONTENT_URI = null;//Uri.parse("content://org.torproject.android.ui.v3onionservice/v3");
    private static Uri V3_CLIENT_AUTH_URI = null;//Uri.parse("content://org.torproject.android.ui.v3onionservice.clientauth/v3auth");
    private final static String NOTIFICATION_CHANNEL_ID = "anyonevpn_channel_1";
    private static final String[] V3_HIDDEN_SERVICE_PROJECTION = new String[]{HiddenService._ID, HiddenService.NAME, HiddenService.DOMAIN, HiddenService.PORT, HiddenService.ANON_PORT, HiddenService.ENABLED, HiddenService.PATH};
    private static final String[] V3_CLIENT_AUTH_PROJECTION = new String[]{V3ClientAuth._ID, V3ClientAuth.DOMAIN, V3ClientAuth.HASH, V3ClientAuth.ENABLED};

    public static int mPortSOCKS = -1;
    public static int mPortHTTP = -1;
    public static int mPortDns = -1;
    public static int mPortTrans = -1;
    public static File appBinHome;
    public static File appCacheHome;
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    AnyoneVpnRawEventListener mRawEventListener;
    AnyoneVpnManager mVpnManager;
    Handler mHandler;
    ActionBroadcastReceiver mActionBroadcastReceiver;
    private String mCurrentStatus = STATUS_OFF;
    AnonControlConnection conn = null;
    private ServiceConnection anonServiceConnection;
    private boolean shouldUnbindAnonService;
    private NotificationManager mNotificationManager = null;
    private NotificationCompat.Builder mNotifyBuilder;
    private File mV3HiddenServicesBasePath, mV3AuthBasePath;

    public void debug(String msg) {
        Log.d(TAG, msg);

        if (Prefs.getUseDebugLogging()) {
            sendCallbackLogMessage(msg);
        }
    }

    public void logException(String msg, Exception e) {
        if (Prefs.getUseDebugLogging()) {
            Log.e(TAG, msg, e);
            var baos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(baos));

            sendCallbackLogMessage(msg + '\n' + baos);
        } else sendCallbackLogMessage(msg);

    }

    private void showConnectedToAnonNetworkNotification() {
        mNotifyBuilder.setProgress(0, 0, false);
        showToolbarNotification(getString(R.string.status_activated), NOTIFY_ID, R.drawable.ic_anyone);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        //this doesn't need to be shown to the user unless there is something to do
        debug(getString(R.string.log_notice_low_memory_warning));
    }

    private void clearNotifications() {
        if (mNotificationManager != null) mNotificationManager.cancelAll();

        if (mRawEventListener != null) mRawEventListener.getNodes().clear();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        var mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        var mChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        mChannel.setDescription(getString(R.string.app_description));
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    @SuppressLint({"NewApi", "RestrictedApi"})
    protected void showToolbarNotification(String notifyMsg, int notifyType, int icon) {
        var intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        var pendIntent = PendingIntent.getActivity(AnyoneVpnService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (mNotifyBuilder == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotifyBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setSmallIcon(R.drawable.ic_anyone).setContentIntent(pendIntent).setCategory(Notification.CATEGORY_SERVICE);
        }

        mNotifyBuilder.setOngoing(true);

        var title = getString(R.string.status_disabled);
        if (mCurrentStatus.equals(STATUS_STARTING) || notifyMsg.equals(getString(R.string.status_starting_up)))
            title = getString(R.string.status_starting_up);
        else if (mCurrentStatus.equals(STATUS_ON)) {
            title = getString(R.string.status_activated);
        }

        mNotifyBuilder.setContentTitle(title);

        mNotifyBuilder.mActions.clear(); // clear out any notification actions, if any

        if (conn != null && mCurrentStatus.equals(STATUS_ON)) { // only add new identity action when there is a connection
            var i = new Intent(this, AnyoneVpnService.class);
            i.setAction(AnonControlCommands.SIGNAL_NEWNYM);
            i.putExtra(AnyoneVpnConstants.EXTRA_NOT_SYSTEM, true);

            mNotifyBuilder.addAction(R.drawable.ic_refresh_white_24dp, getString(R.string.menu_new_identity), getServiceIntent(i));
        }
        else if (mCurrentStatus.equals(STATUS_OFF)) {
            var i = new Intent(this, AnyoneVpnService.class);
            i.setAction(ACTION_START);
            i.putExtra(AnyoneVpnConstants.EXTRA_NOT_SYSTEM, true);

            mNotifyBuilder.addAction(R.drawable.ic_anyone, getString(R.string.connect_to_anon), getServiceIntent(i));
        }

        mNotifyBuilder.setContentText(notifyMsg).setSmallIcon(icon).setTicker(notifyType != NOTIFY_ID ? notifyMsg : null);

        if (!mCurrentStatus.equals(STATUS_ON)) {
            mNotifyBuilder.setSubText(null);
        }

        if (!mCurrentStatus.equals(STATUS_STARTING)) {
            mNotifyBuilder.setProgress(0, 0, false); // removes progress bar
        }

        ServiceCompat.startForeground(this, NOTIFY_ID, mNotifyBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) {
                Log.d(TAG, "Got null onStartCommand() intent");
                return Service.START_REDELIVER_INTENT;
            }

            final boolean shouldStartVpnFromSystemIntent = !intent.getBooleanExtra(AnyoneVpnConstants.EXTRA_NOT_SYSTEM, false);

            if (mCurrentStatus.equals(STATUS_OFF))
                showToolbarNotification(getString(R.string.open_anyonevpn_to_connect_to_anon), NOTIFY_ID, R.drawable.ic_anyone);

            if (shouldStartVpnFromSystemIntent) {
                Log.d(TAG, "Starting VPN from system intent: " + intent);
                showToolbarNotification(getString(R.string.status_starting_up), NOTIFY_ID, R.drawable.ic_anyone);
                if (VpnService.prepare(this) == null) {
                    // Power-user mode doesn't matter here. If the system is starting the VPN, i.e.
                    // via always-on VPN, we need to start it regardless.
                    Prefs.setUseVpn(true);
                    mExecutor.execute(new IncomingIntentRouter(new Intent(ACTION_START)));
                    mExecutor.execute(new IncomingIntentRouter(new Intent(ACTION_START_VPN)));
                } else {
                    Log.wtf(TAG, "Could not start VPN from system because it is not prepared, which should be impossible!");
                }
            } else {
                mExecutor.execute(new IncomingIntentRouter(intent));
            }
        } catch (RuntimeException re) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }

        return Service.START_REDELIVER_INTENT;
    }

    private void showDeactivatedNotification() {
        showToolbarNotification(getString(R.string.open_anyonevpn_to_connect_to_anon), NOTIFY_ID, R.drawable.ic_anyone);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mActionBroadcastReceiver);
        } catch (IllegalArgumentException iae) {
            //not registered yet
        }
        super.onDestroy();
    }

    private void stopAnonAsync(boolean showNotification) {
        debug("stopAnon");

        if (showNotification) sendCallbackLogMessage(getString(R.string.status_shutting_down));

        stopAnon();

        //stop the foreground priority and make sure to remove the persistent notification
        stopForeground(!showNotification);

        if (showNotification) sendCallbackLogMessage(getString(R.string.status_disabled));

        mPortDns = -1;
        mPortSOCKS = -1;
        mPortHTTP = -1;
        mPortTrans = -1;

        if (!showNotification) {
            clearNotifications();
            stopSelf();
        }
    }

    private void stopAnonOnError(String message) {
        stopAnonAsync(false);
        showToolbarNotification(getString(R.string.unable_to_start_anon) + ": " + message, ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
    }

    // if someone stops during startup, we may have to wait for the conn port to be setup, so we can properly shutdown anon
    private void stopAnon() {
        if (shouldUnbindAnonService) {
            unbindService(anonServiceConnection); //unbinding from the anon service will stop anon
            shouldUnbindAnonService = false;
            conn = null;
        } else {
            sendLocalStatusOffBroadcast();
        }
    }

    private void requestAnonRereadConfig() {
        try {
            if (conn != null) {
                conn.signal(AnonControlCommands.SIGNAL_RELOAD);
            }
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    protected void logNotice(String msg) {
        if (msg != null && !msg.trim().isEmpty()) {
            if (Prefs.getUseDebugLogging()) Log.d(TAG, msg);
            sendCallbackLogMessage(msg);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onCreate() {
        super.onCreate();
        configLanguage();
        try {
            //set proper content URIs for current build flavor
            V3_HIDDEN_SERVICES_CONTENT_URI = Uri.parse("content://" + getApplicationContext().getPackageName() + ".ui.hostedservices/v3");
            V3_CLIENT_AUTH_URI = Uri.parse("content://" + getApplicationContext().getPackageName() + ".ui.hostedservices.clientauth/v3auth");

            try {
                mHandler = new Handler();

                appBinHome = getFilesDir();
                if (!appBinHome.exists()) appBinHome.mkdirs();

                appCacheHome = getDir(DIRECTORY_ANON_DATA, Application.MODE_PRIVATE);

                if (!appCacheHome.exists()) appCacheHome.mkdirs();

                mV3HiddenServicesBasePath = new File(getFilesDir().getAbsolutePath(), ANON_SERVICES_DIR);
                if (!mV3HiddenServicesBasePath.isDirectory()) mV3HiddenServicesBasePath.mkdirs();

                mV3AuthBasePath = new File(getFilesDir().getAbsolutePath(), V3_CLIENT_AUTH_DIR);
                if (!mV3AuthBasePath.isDirectory()) mV3AuthBasePath.mkdirs();

                if (mNotificationManager == null) {
                    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                }

                IntentFilter filter = new IntentFilter(CMD_ACTIVE);
                filter.addAction(ACTION_STATUS);
                filter.addAction(ACTION_ERROR);

                mActionBroadcastReceiver = new ActionBroadcastReceiver();
                ContextCompat.registerReceiver(this, mActionBroadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel();

                var hasGeoip = new File(appBinHome, GEOIP_ASSET_KEY).exists();
                var hasGeoip6 = new File(appBinHome, GEOIP6_ASSET_KEY).exists();

                // only write out geoip files if there's an app update or they don't exist
                if (!hasGeoip || !hasGeoip6 || Prefs.isGeoIpReinstallNeeded()) {
                    try {
                        Log.d(TAG, "Installing geoip files...");
                        new CustomAnonResourceInstaller(this, appBinHome).installGeoIP();
                        Prefs.setGeoIpReinstallNeeded(false);
                    } catch (IOException io) { // user has < 10MB free space on disk...
                        Log.e(TAG, "Error installing geoip files", io);
                    }
                }

                mVpnManager = new AnyoneVpnManager(this);
            } catch (Exception e) {
                Log.e(TAG, "Error setting up AnyoneVpn", e);
                logNotice(getString(R.string.couldn_t_start_anon_process_) + " " + e.getClass().getSimpleName());
            }
        }
        catch (RuntimeException re) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    private void configLanguage() {
        Configuration config = getBaseContext().getResources().getConfiguration();
        Locale locale = new Locale(Prefs.getDefaultLocale());
        Locale.setDefault(locale);
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }

    protected String getCurrentStatus() {
        return mCurrentStatus;
    }

    private File updateAnonrcCustomFile() throws IOException {
        var extraLines = new StringBuffer();

        extraLines.append("\n");
        extraLines.append("RunAsDaemon 0").append('\n');
        extraLines.append("AvoidDiskWrites 1").append('\n');

        var socksPortPref = Prefs.getProxySocksPort();
        if (socksPortPref.indexOf(':') != -1) socksPortPref = socksPortPref.split(":")[1];

        socksPortPref = checkPortOrAuto(socksPortPref);

        var httpPortPref = Prefs.getProxyHttpPort();
        if (httpPortPref.indexOf(':') != -1) httpPortPref = httpPortPref.split(":")[1];

        httpPortPref = checkPortOrAuto(httpPortPref);

        var isolate = "";
        if (Prefs.getIsolateDest()) {
            isolate += " IsolateDestAddr ";
        }
        if (Prefs.getIsolatePort()) {
            isolate += " IsolateDestPort ";
        }
        if (Prefs.getIsolateProtocol()) {
            isolate += " IsolateClientProtocol ";
        }

        var ipv6Pref = "";
        if (Prefs.getPreferIpv6()) {
            ipv6Pref += " IPv6Traffic PreferIPv6 ";
        }

        if (Prefs.getDisableIpv4()) {
            ipv6Pref += " IPv6Traffic NoIPv4Traffic ";
        }

        if (!Prefs.getOpenProxyOnAllInterfaces()) {
            extraLines.append("SOCKSPort ").append(socksPortPref).append(isolate).append(ipv6Pref).append('\n');
        } else {
            extraLines.append("SOCKSPort 0.0.0.0:").append(socksPortPref).append(ipv6Pref).append(isolate).append("\n");
            extraLines.append("SocksPolicy accept *:*").append('\n');
        }

        extraLines.append("SafeSocks 0").append('\n');
        extraLines.append("TestSocks 0").append('\n');
        extraLines.append("HTTPTunnelPort ").append(httpPortPref).append(isolate).append('\n');


        if (Prefs.getConnectionPadding()) {
            extraLines.append("ConnectionPadding 1").append('\n');
        }

        if (Prefs.getReducedConnectionPadding()) {
            extraLines.append("ReducedConnectionPadding 1").append('\n');
        }

        if (Prefs.getCircuitPadding()) {
            extraLines.append("CircuitPadding 1").append('\n');
        } else {
            extraLines.append("CircuitPadding 0").append('\n');
        }

        if (Prefs.getReducedCircuitPadding()) {
            extraLines.append("ReducedCircuitPadding 1").append('\n');
        }

        var transPort = Prefs.getAnonTransPort();
        var dnsPort = Prefs.getAnonDnsPort();

        extraLines.append("TransPort ").append(checkPortOrAuto(transPort)).append(isolate).append('\n');
        extraLines.append("DNSPort ").append(checkPortOrAuto(dnsPort)).append(isolate).append('\n');

        extraLines.append("VirtualAddrNetwork 10.192.0.0/10").append('\n');
        extraLines.append("AutomapHostsOnResolve 1").append('\n');

        extraLines.append("DormantClientTimeout 10 minutes").append('\n');
        // extraLines.append("DormantOnFirstStartup 0").append('\n');
        extraLines.append("DormantCanceledByStartup 1").append('\n');

        extraLines.append("DisableNetwork 0").append('\n');

        if (Prefs.getUseDebugLogging()) {
            extraLines.append("Log debug syslog").append('\n');
            extraLines.append("SafeLogging 0").append('\n');
        }

        extraLines = processSettingsImpl(extraLines);

        if (extraLines == null) return null;

        extraLines.append('\n');
        extraLines.append(Prefs.getCustomAnonRc()).append('\n');

        logNotice(getString(R.string.log_notice_updating_anonrc));

        debug("torrc.custom=" + extraLines);

        var fileTorRcCustom = AnonService.getAnonrc(this);
        updateAnonConfigCustom(fileTorRcCustom, extraLines.toString(), false);
        return fileTorRcCustom;
    }

    private String checkPortOrAuto(String portString) {
        if (!portString.equalsIgnoreCase("auto")) {
            var isPortUsed = true;
            var port = Integer.parseInt(portString);

            while (isPortUsed) {
                isPortUsed = Utils.isPortOpen("127.0.0.1", port, 500);

                if (isPortUsed) //the specified port is not available, so let Tor find one instead
                    port++;
            }
            return port + "";
        }

        return portString;
    }

    public void updateAnonConfigCustom(File fileTorRcCustom, String extraLines, boolean append) throws IOException {
        var ps = new PrintWriter(new FileWriter(fileTorRcCustom, append));
        ps.print(extraLines);
        ps.flush();
        ps.close();
    }

    /**
     * Send Anyone VPN's status in reply to an
     * {@link #ACTION_START} {@link Intent}, targeted only to
     * the app that sent the initial request. If the user has disabled auto-
     * starts, the reply {@code ACTION_START Intent} will include the extra
     * {@link #STATUS_STARTS_DISABLED}
     */
    private void replyWithStatus(Intent startRequest) {
        String packageName = startRequest.getStringExtra(EXTRA_PACKAGE_NAME);

        Intent reply = new Intent(ACTION_STATUS);
        reply.putExtra(EXTRA_STATUS, mCurrentStatus);
        reply.putExtra(EXTRA_SOCKS_PROXY, "socks://127.0.0.1:" + mPortSOCKS);
        reply.putExtra(EXTRA_SOCKS_PROXY_HOST, "127.0.0.1");
        reply.putExtra(EXTRA_SOCKS_PROXY_PORT, mPortSOCKS);
        reply.putExtra(EXTRA_HTTP_PROXY, "http://127.0.0.1:" + mPortHTTP);
        reply.putExtra(EXTRA_HTTP_PROXY_HOST, "127.0.0.1");
        reply.putExtra(EXTRA_HTTP_PROXY_PORT, mPortHTTP);
        reply.putExtra(EXTRA_DNS_PORT, mPortDns);

        if (packageName != null) {
            reply.setPackage(packageName);
            sendBroadcast(reply);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(reply.setAction(LOCAL_ACTION_STATUS));

        if (mPortSOCKS != -1 && mPortHTTP != -1)
            sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);
    }

    private boolean showTorServiceErrorMsg = false;

    /**
     * The entire process for starting tor and related services is run from this method.
     */
    private void startAnon() {
        try {
            if (anonServiceConnection != null && conn != null) {
                sendCallbackLogMessage(getString(R.string.log_notice_ignoring_start_request));
                showConnectedToAnonNetworkNotification();
                return;
            }

            mNotifyBuilder.setProgress(100, 0, false);
            showToolbarNotification("", NOTIFY_ID, R.drawable.ic_anyone);

            startAnonService();
            showTorServiceErrorMsg = true;

            if (Prefs.getHostHiddenServicesEnabled()) {
                try {
                    updateV3HiddenServicesNames();
                } catch (SecurityException se) {
                    logNotice(getString(R.string.log_notice_unable_to_update_onions));
                }
            }
        } catch (Exception e) {
            logException(getString(R.string.unable_to_start_anon) + " " + e.getLocalizedMessage(), e);
            stopAnonOnError(e.getLocalizedMessage());
        }
    }

    private void updateV3HiddenServicesNames() throws SecurityException {
        var contentResolver = getApplicationContext().getContentResolver();
        var hiddenServices = contentResolver.query(V3_HIDDEN_SERVICES_CONTENT_URI, null, null, null, null);
        if (hiddenServices != null) {
            try {
                while (hiddenServices.moveToNext()) {
                    var domain_index = hiddenServices.getColumnIndex(HiddenService.DOMAIN);
                    var path_index = hiddenServices.getColumnIndex(HiddenService.PATH);
                    var id_index = hiddenServices.getColumnIndex(HiddenService._ID);
                    if (domain_index < 0 || path_index < 0 || id_index < 0) continue;
                    var domain = hiddenServices.getString(domain_index);
                    if (domain == null || TextUtils.isEmpty(domain)) {
                        var path = hiddenServices.getString(path_index);
                        var v3HiddenServicesDirPath = new File(mV3HiddenServicesBasePath.getAbsolutePath(), path).getCanonicalPath();
                        var hostname = new File(v3HiddenServicesDirPath, "hostname");
                        if (hostname.exists()) {
                            int id = hiddenServices.getInt(id_index);
                            domain = Utils.readInputStreamAsString(new FileInputStream(hostname)).trim();
                            var fields = new ContentValues();
                            fields.put(HiddenService.DOMAIN, domain);
                            contentResolver.update(V3_HIDDEN_SERVICES_CONTENT_URI, fields, HiddenService._ID + "=" + id, null);
                        }
                    }
                }
                /*
                This old status hack is temporary and fixes the issue reported by syphyr at
                https://github.com/guardianproject/orbot/pull/556
                Down the line a better approach needs to happen for sending back the hidden services names updated
                status, perhaps just adding it as an extra to the normal Intent callback...
                 */
                var oldStatus = mCurrentStatus;
                var intent = new Intent(LOCAL_ACTION_V3_NAMES_UPDATED);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                mCurrentStatus = oldStatus;
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            hiddenServices.close();
        }
    }

    private synchronized void startAnonService() throws Exception {
        updateAnonConfigCustom(AnonService.getDefaultsAnonrc(this), """
                DNSPort 0
                TransPort 0
                DisableNetwork 1
                """, false);

        var fileTorrcCustom = updateAnonrcCustomFile();
        if (fileTorrcCustom == null || !fileTorrcCustom.exists() || !fileTorrcCustom.canRead()) return;

        sendCallbackLogMessage(getString(R.string.status_starting_up));

        anonServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

                //moved torService to a local variable, since we only need it once
                AnonService torService = ((AnonService.LocalBinder) iBinder).getService();

                while ((conn = torService.getAnonControlConnection()) == null) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                }

                //wait another second before we set our own event listener
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }

                mRawEventListener = new AnyoneVpnRawEventListener(AnyoneVpnService.this);

                if (conn != null) {
                    try {
                        initControlConnection();
                        if (conn == null)
                            return; // maybe there was an error setting up the control connection

                        //override the TorService event listener
                        conn.addRawEventListener(mRawEventListener);

                        logNotice(getString(R.string.log_notice_connected_to_anon_control_port));

                        //now set our own events
                        ArrayList<String> events = new ArrayList<>(Arrays.asList(AnonControlCommands.EVENT_OR_CONN_STATUS, AnonControlCommands.EVENT_CIRCUIT_STATUS, AnonControlCommands.EVENT_NOTICE_MSG, AnonControlCommands.EVENT_WARN_MSG, AnonControlCommands.EVENT_ERR_MSG, AnonControlCommands.EVENT_BANDWIDTH_USED, AnonControlCommands.EVENT_NEW_DESC, AnonControlCommands.EVENT_ADDRMAP));
                        if (Prefs.getUseDebugLogging()) {
                            events.add(AnonControlCommands.EVENT_DEBUG_MSG);
                            events.add(AnonControlCommands.EVENT_INFO_MSG);
                        }

                        if (Prefs.getUseDebugLogging())
                            events.add(AnonControlCommands.EVENT_STREAM_STATUS);

                        conn.setEvents(events);
                        logNotice(getString(R.string.log_notice_added_event_handler));
                    } catch (IOException e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                if (Prefs.getUseDebugLogging()) Log.d(TAG, "TorService: onServiceDisconnected");
                sendLocalStatusOffBroadcast();
            }

            @Override
            public void onNullBinding(ComponentName componentName) {
                Log.w(TAG, "TorService: was unable to bind: onNullBinding");
            }

            @Override
            public void onBindingDied(ComponentName componentName) {
                Log.w(TAG, "TorService: onBindingDied");
                sendLocalStatusOffBroadcast();
            }
        };

        Intent serviceIntent = new Intent(this, AnonService.class);
        if (Build.VERSION.SDK_INT < 29) {
            shouldUnbindAnonService = bindService(serviceIntent, anonServiceConnection, BIND_AUTO_CREATE);
        } else {
            shouldUnbindAnonService = bindService(serviceIntent, BIND_AUTO_CREATE, mExecutor, anonServiceConnection);
        }
    }

    private void sendLocalStatusOffBroadcast() {
        var localOffStatus = new Intent(LOCAL_ACTION_STATUS).putExtra(EXTRA_STATUS, STATUS_OFF);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localOffStatus);
    }

    protected void exec(Runnable run) {
        mExecutor.execute(run);
    }

    private void initControlConnection() {
        if (conn != null) {
            try {
                String confSocks = conn.getInfo("net/listeners/socks");
                StringTokenizer st = new StringTokenizer(confSocks, " ");
                if (confSocks.trim().isEmpty()) {
                    mPortSOCKS = 0;
                } else {
                    confSocks = st.nextToken().split(":")[1];
                    confSocks = confSocks.substring(0, confSocks.length() - 1);
                    mPortSOCKS = Integer.parseInt(confSocks);
                }
                String confHttp = conn.getInfo("net/listeners/httptunnel");
                if (confHttp.trim().isEmpty()) {
                    mPortHTTP = 0;
                } else {
                    st = new StringTokenizer(confHttp, " ");
                    confHttp = st.nextToken().split(":")[1];
                    confHttp = confHttp.substring(0, confHttp.length() - 1);
                    mPortHTTP = Integer.parseInt(confHttp);
                }
                String confDns = conn.getInfo("net/listeners/dns");
                st = new StringTokenizer(confDns, " ");
                if (st.hasMoreTokens()) {
                    confDns = st.nextToken().split(":")[1];
                    confDns = confDns.substring(0, confDns.length() - 1);
                    mPortDns = Integer.parseInt(confDns);
                    Prefs.setAnonDnsPortResolved(mPortDns);
                }

                String confTrans = conn.getInfo("net/listeners/trans");
                st = new StringTokenizer(confTrans, " ");
                if (st.hasMoreTokens()) {
                    confTrans = st.nextToken().split(":")[1];
                    confTrans = confTrans.substring(0, confTrans.length() - 1);
                    mPortTrans = Integer.parseInt(confTrans);
                }

                sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);

            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                stopAnonOnError(e.getLocalizedMessage());
                conn = null;
            } catch (NullPointerException npe) {
                Log.e(TAG, Log.getStackTraceString(npe));
                stopAnonOnError("stopping from NPE");
                conn = null;
            }
        }
    }

    public void sendSignalActive() {
        if (conn != null && mCurrentStatus.equals(STATUS_ON)) {
            try {
                conn.signal("ACTIVE");
            } catch (IOException e) {
                debug("error send active: " + e.getLocalizedMessage());
            }
        }
    }

    public void newIdentity() {
        if (conn == null) return;
        new Thread() {
            public void run() {
                try {
                    if (conn != null && mCurrentStatus.equals(STATUS_ON)) {
                        mNotifyBuilder.setSubText(null); // clear previous exit node info if present
                        showToolbarNotification(getString(R.string.newnym), NOTIFY_ID, R.drawable.ic_anyone);
                        conn.signal(AnonControlCommands.SIGNAL_NEWNYM);
                    }
                } catch (Exception ioe) {
                    debug("error requesting newnym: " + ioe.getLocalizedMessage());
                }
            }
        }.start();
    }

    protected void sendCallbackBandwidth(long lastWritten, long lastRead, long totalWritten, long totalRead) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(LOCAL_ACTION_BANDWIDTH).putExtra(LOCAL_EXTRA_TOTAL_WRITTEN, totalWritten).putExtra(LOCAL_EXTRA_TOTAL_READ, totalRead).putExtra(LOCAL_EXTRA_LAST_WRITTEN, lastWritten).putExtra(LOCAL_EXTRA_LAST_READ, lastRead));
    }

    private void sendCallbackLogMessage(final String logMessage) {
        var notificationMessage = logMessage;
        var localIntent = new Intent(LOCAL_ACTION_LOG).putExtra(LOCAL_EXTRA_LOG, logMessage);
        if (logMessage.contains(LOG_NOTICE_HEADER)) {
            notificationMessage = notificationMessage.substring(LOG_NOTICE_HEADER.length());
            if (notificationMessage.contains(LOG_NOTICE_BOOTSTRAPPED)) {
                var percent = notificationMessage.substring(LOG_NOTICE_BOOTSTRAPPED.length());
                percent = percent.substring(0, percent.indexOf('%')).trim();
                localIntent.putExtra(LOCAL_EXTRA_BOOTSTRAP_PERCENT, percent);
                mNotifyBuilder.setProgress(100, Integer.parseInt(percent), false);
                notificationMessage = notificationMessage.substring(notificationMessage.indexOf(':') + 1).trim();
            }
        }
        showToolbarNotification(notificationMessage, NOTIFY_ID, R.drawable.ic_anyone);
        mHandler.post(() -> LocalBroadcastManager.getInstance(AnyoneVpnService.this).sendBroadcast(localIntent));
    }

    private void sendCallbackPorts(int socksPort, int httpPort, int dnsPort, int transPort) {
        var intent = new Intent(LOCAL_ACTION_PORTS).putExtra(EXTRA_SOCKS_PROXY_PORT, socksPort).putExtra(EXTRA_HTTP_PROXY_PORT, httpPort).putExtra(EXTRA_DNS_PORT, dnsPort).putExtra(EXTRA_TRANS_PORT, transPort);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        if (Prefs.getUseVpn() && mVpnManager != null) mVpnManager.handleIntent(new Builder(), intent);
    }

    private StringBuffer processSettingsImpl(StringBuffer extraLines) throws IOException {
        logNotice(getString(R.string.updating_settings_in_anon_service));
        var becomeRelay = Prefs.getBeRelay();
        var ReachableAddresses = Prefs.getReachableAddresses();
        var entranceNodes = Prefs.getEntryNodes();
        var exitNodes = Prefs.getExitNodes();
        var excludeNodes = Prefs.getExcludeNodes();

        extraLines = processSettingsImplDirectPathway(extraLines);

        var fileGeoIP = new File(appBinHome, GEOIP_ASSET_KEY);
        var fileGeoIP6 = new File(appBinHome, GEOIP6_ASSET_KEY);

        if (fileGeoIP.exists()) { // only apply geoip if it exists
            extraLines.append("GeoIPFile" + ' ').append(fileGeoIP.getCanonicalPath()).append('\n');
            extraLines.append("GeoIPv6File" + ' ').append(fileGeoIP6.getCanonicalPath()).append('\n');
        }

        if (!TextUtils.isEmpty(entranceNodes))
            extraLines.append("EntryNodes ").append(entranceNodes).append('\n');

        if (!TextUtils.isEmpty(exitNodes))
            extraLines.append("ExitNodes ").append(exitNodes).append('\n');

        if (!TextUtils.isEmpty(excludeNodes))
            extraLines.append("ExcludeNodes ").append(excludeNodes).append('\n');

        extraLines.append("StrictNodes ").append(Prefs.getStrictNodes() ? "1" : "0").append('\n');

        extraLines.append("\n");

        try {
            if (ReachableAddresses) {
                var ReachableAddressesPorts = Prefs.getReachableAddressesPorts();
                extraLines.append("ReachableAddresses" + ' ').append(ReachableAddressesPorts).append('\n');
            }

        } catch (Exception e) {
            showToolbarNotification(getString(R.string.your_reachableaddresses_settings_caused_an_exception_), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
            return null;
        }

        try {
            if (becomeRelay && (!ReachableAddresses)) {
                var ORPort = Integer.parseInt(Prefs.getOrPort());
                var nickname = Prefs.getOrNickname();
                var dnsFile = writeDNSFile();

                extraLines.append("ServerDNSResolvConfFile").append(' ').append(dnsFile).append('\n'); // DNSResolv is not a typo
                extraLines.append("ORPort").append(' ').append(ORPort).append('\n');
                extraLines.append("Nickname").append(' ').append(nickname).append('\n');
                extraLines.append("ExitPolicy").append(' ').append("reject *:*").append('\n');

            }
        } catch (Exception e) {
            showToolbarNotification(getString(R.string.your_relay_settings_caused_an_exception_), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
            return null;
        }

        if (Prefs.getHostHiddenServicesEnabled()) {
            var contentResolver = getApplicationContext().getContentResolver();
            addV3HiddenServicesToAnonrc(extraLines, contentResolver);
            addV3ClientAuthToAnonrc(extraLines, contentResolver);
        }

        return extraLines;
    }

    private StringBuffer processSettingsImplDirectPathway(StringBuffer extraLines) {
        extraLines.append("UseBridges 0").append('\n');
        if (Prefs.getUseVpn()) { //set the proxy here if we aren't using a bridge
            var proxy = Prefs.getProxy();

            if (proxy != null) {
                var hostPort = proxy.getHost();
                if (proxy.getPort() >= 1 && proxy.getPort() <= 65536) hostPort += ":" + proxy.getPort();

                switch (proxy.getScheme()) {
                    case "https":
                        extraLines.append("HTTPSProxy ").append(hostPort).append("\n");

                        if (!TextUtils.isEmpty(proxy.getUserInfo())) {
                            extraLines.append("HTTPSProxyAuthenticator ").append(proxy.getUserInfo()).append("\n");
                        }

                        break;

                    case "socks4":
                        extraLines.append("Socks4Proxy ").append(hostPort).append("\n");

                        break;

                    case "socks5":
                        extraLines.append("Socks5Proxy ").append(hostPort).append("\n");

                        var userInfo = proxy.getUserInfo().split(":");

                        if (userInfo.length > 0 && !userInfo[0].isEmpty()) {
                            extraLines.append("Socks5ProxyUsername ").append(userInfo[0]).append("\n");

                            if (userInfo.length > 1 && !userInfo[1].isEmpty()) {
                                extraLines.append("Socks5ProxyPassword ").append(userInfo[1]).append("\n");
                            }
                        }

                        break;
                }
            }
        }
        return extraLines;
    }

    void showBandwidthNotification(String message, boolean isActiveTransfer) {
        if (!mCurrentStatus.equals(STATUS_ON)) return;
        var icon = R.drawable.ic_anyone;
        showToolbarNotification(message, NOTIFY_ID, icon);
    }

    public static String formatBandwidthCount(Context context, long bitsPerSecond) {
        var nf = NumberFormat.getInstance(Locale.getDefault());
        if (bitsPerSecond < 1e6)
            return nf.format(Math.round(((float) ((int) (bitsPerSecond * 10 / 1024)) / 10))) + context.getString(R.string.kibibyte_per_second);
        else
            return nf.format(Math.round(((float) ((int) (bitsPerSecond * 100 / 1024 / 1024)) / 100))) + context.getString(R.string.mebibyte_per_second);
    }

    private void addV3HiddenServicesToAnonrc(StringBuffer torrc, ContentResolver contentResolver) {
        try {
            var hiddenServices = contentResolver.query(V3_HIDDEN_SERVICES_CONTENT_URI, V3_HIDDEN_SERVICE_PROJECTION, HiddenService.ENABLED + "=1", null, null);
            if (hiddenServices != null) {
                while (hiddenServices.moveToNext()) {
                    var id_index = hiddenServices.getColumnIndex(HiddenService._ID);
                    var port_index = hiddenServices.getColumnIndex(HiddenService.PORT);
                    var anon_port_index = hiddenServices.getColumnIndex(HiddenService.ANON_PORT);
                    var path_index = hiddenServices.getColumnIndex(HiddenService.PATH);
                    var domain_index = hiddenServices.getColumnIndex(HiddenService.DOMAIN);
                    // Ensure that are have all the indexes before trying to use them
                    if (id_index < 0 || port_index < 0 || anon_port_index < 0 || path_index < 0 || domain_index < 0)
                        continue;

                    var id = hiddenServices.getInt(id_index);
                    var localPort = hiddenServices.getInt(port_index);
                    var anonPort = hiddenServices.getInt(anon_port_index);
                    var path = hiddenServices.getString(path_index);
                    var domain = hiddenServices.getString(domain_index);
                    if (path == null) {
                        path = "v3";
                        if (domain == null) path += UUID.randomUUID().toString();
                        else path += localPort;
                        var cv = new ContentValues();
                        cv.put(HiddenService.PATH, path);
                        contentResolver.update(V3_HIDDEN_SERVICES_CONTENT_URI, cv, HiddenService._ID + "=" + id, null);
                    }
                    var v3DirPath = new File(mV3HiddenServicesBasePath.getAbsolutePath(), path).getCanonicalPath();
                    torrc.append("HiddenServiceDir ").append(v3DirPath).append("\n");
                    torrc.append("HiddenServiceVersion 3").append("\n");
                    torrc.append("HiddenServicePort ").append(anonPort).append(" 127.0.0.1:").append(localPort).append("\n");
                }
                hiddenServices.close();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public static String buildV3ClientAuthFile(String domain, String keyHash) {
        return domain + ":descriptor:x25519:" + keyHash;
    }

    private void addV3ClientAuthToAnonrc(StringBuffer anonrc, ContentResolver contentResolver) {
        var v3auths = contentResolver.query(V3_CLIENT_AUTH_URI, V3_CLIENT_AUTH_PROJECTION, V3ClientAuth.ENABLED + "=1", null, null);

        if (v3auths == null) return;

        File[] files = mV3AuthBasePath.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    file.delete(); // todo the adapter should maybe just write these files and not do this in service...
                }
            }
        }

        anonrc.append("ClientOnionAuthDir " + mV3AuthBasePath.getAbsolutePath()).append('\n');

        try {
            int i = 0;
            while (v3auths.moveToNext()) {
                var domain_index = v3auths.getColumnIndex(V3ClientAuth.DOMAIN);
                var hash_index = v3auths.getColumnIndex(V3ClientAuth.HASH);
                // Ensure that are have all the indexes before trying to use them
                if (domain_index < 0 || hash_index < 0) continue;
                var domain = v3auths.getString(domain_index);
                var hash = v3auths.getString(hash_index);
                var authFile = new File(mV3AuthBasePath, (i++) + ".auth_private");
                authFile.createNewFile();
                var fos = new FileOutputStream(authFile);
                fos.write(buildV3ClientAuthFile(domain, hash).getBytes());
                fos.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "error adding v3 client auth...");
        } finally {
            v3auths.close();
        }
    }

    //using Google DNS for now as the public DNS server
    private String writeDNSFile() throws IOException {
        var file = new File(appBinHome, "resolv.conf");
        var bw = new PrintWriter(new FileWriter(file));
        bw.println("nameserver 8.8.8.8");
        bw.println("nameserver 8.8.4.4");
        bw.close();
        return file.getCanonicalPath();
    }

    @SuppressLint("NewApi")
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        switch (level) {
            case TRIM_MEMORY_BACKGROUND -> debug("trim memory requested: app in the background");
            case TRIM_MEMORY_COMPLETE -> debug("trim memory requested: cleanup all memory");
            case TRIM_MEMORY_MODERATE -> debug("trim memory requested: clean up some memory");
            case TRIM_MEMORY_RUNNING_CRITICAL -> debug("trim memory requested: memory on device is very low and critical");
            case TRIM_MEMORY_RUNNING_LOW -> debug("trim memory requested: memory on device is running low");
            case TRIM_MEMORY_RUNNING_MODERATE -> debug("trim memory requested: memory on device is moderate");
            case TRIM_MEMORY_UI_HIDDEN -> debug("trim memory requested: app is not showing UI anymore");
        }
    }

    public void setNotificationSubtext(String message) {
        if (mNotifyBuilder != null) {
            // stop showing expanded notifications if the user changed the after starting Anyone VPN
            // if (!Prefs.showExpandedNotifications()) message = null;
            mNotifyBuilder.setSubText(message);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "AnyoneVpnService: onBind");
        return super.onBind(intent); // invoking super class will call onRevoke() when appropriate
    }

    // system calls this method when VPN disconnects (either by the user or another VPN app)
    @Override
    public void onRevoke() {
        Prefs.setUseVpn(false);
        mVpnManager.handleIntent(new Builder(), new Intent(ACTION_STOP_VPN));
        // tell UI, if it's open, to update immediately (don't wait for onResume() in Activity...)
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_STOP_VPN));
    }

    private void setExitNode(String newExits) {

        if (TextUtils.isEmpty(newExits)) {
            Prefs.setExitNodes("");

            if (conn != null) {
                try {
                    var resetBuffer = new ArrayList<String>();
                    resetBuffer.add("ExitNodes");
                    resetBuffer.add("StrictNodes");
                    conn.resetConf(resetBuffer);
                    conn.setConf("DisableNetwork", "1");
                    conn.setConf("DisableNetwork", "0");

                } catch (Exception ioe) {
                    Log.e(TAG, "Connection exception occurred resetting exits", ioe);
                }
            }
        } else {

            Prefs.setExitNodes("{" + newExits + "}");

            if (conn != null) {
                try {
                    var fileGeoIP = new File(appBinHome, GEOIP_ASSET_KEY);
                    var fileGeoIP6 = new File(appBinHome, GEOIP6_ASSET_KEY);

                    conn.setConf("GeoIPFile", fileGeoIP.getCanonicalPath());
                    conn.setConf("GeoIPv6File", fileGeoIP6.getCanonicalPath());
                    conn.setConf("ExitNodes", Prefs.getExitNodes());
                    conn.setConf("StrictNodes", Prefs.getStrictNodes() ? "1" : "0");
                    conn.setConf("DisableNetwork", "1");
                    conn.setConf("DisableNetwork", "0");

                } catch (Exception ioe) {
                    Log.e(TAG, "Connection exception occurred resetting exits", ioe);
                }
            }
        }
    }

    private PendingIntent getServiceIntent(Intent i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(this, 0, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        return PendingIntent.getService(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static final class HiddenService implements BaseColumns {
        public static final String NAME = "name";
        public static final String PORT = "port";
        public static final String ANON_PORT = "anon_port";
        public static final String DOMAIN = "domain";
        public static final String ENABLED = "enabled";
        public static final String PATH = "filepath";
    }

    public static final class V3ClientAuth implements BaseColumns {
        public static final String DOMAIN = "domain";
        public static final String HASH = "hash";
        public static final String ENABLED = "enabled";
    }


    private class IncomingIntentRouter implements Runnable {
        final Intent mIntent;

        public IncomingIntentRouter(Intent intent) {
            mIntent = intent;
        }

        public void run() {
            var action = mIntent.getAction();
            if (TextUtils.isEmpty(action)) return;
            switch (action) {
                case ACTION_START -> {
                    startAnon();
                    replyWithStatus(mIntent);
                    if (Prefs.getUseVpn()) {
                        if (mVpnManager != null && (!mVpnManager.isStarted())) { // start VPN here
                            Intent vpnIntent = VpnService.prepare(AnyoneVpnService.this);
                            if (vpnIntent == null) { //then we can run the VPN
                                mVpnManager.handleIntent(new Builder(), mIntent);
                            }
                        }

                        if (mPortSOCKS != -1 && mPortHTTP != -1)
                            sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);
                    }
                }
                case ACTION_STOP -> {
                    var userIsQuitting = mIntent.getBooleanExtra(ACTION_STOP_FOREGROUND_TASK, false);
                    stopAnonAsync(!userIsQuitting);
                }
                case ACTION_UPDATE_HIDDEN_SERVICES_NAMES -> updateV3HiddenServicesNames();
                case ACTION_STOP_FOREGROUND_TASK -> stopForeground(true);
                case ACTION_START_VPN -> {
                    if (mVpnManager != null && (!mVpnManager.isStarted())) {
                        //start VPN here
                        Intent vpnIntent = VpnService.prepare(AnyoneVpnService.this);
                        if (vpnIntent == null) { //then we can run the VPN
                            mVpnManager.handleIntent(new Builder(), mIntent);
                        }
                    }
                    if (mPortSOCKS != -1 && mPortHTTP != -1)
                        sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);
                }
                case ACTION_STOP_VPN -> {
                    if (mVpnManager != null) mVpnManager.handleIntent(new Builder(), mIntent);
                }
                case ACTION_RESTART_VPN -> {
                    if (mVpnManager != null) mVpnManager.restartVPN(new Builder());
                }
                case ACTION_STATUS -> {
                    if (mCurrentStatus.equals(STATUS_OFF))
                        showToolbarNotification(getString(R.string.open_anyonevpn_to_connect_to_anon), NOTIFY_ID, R.drawable.ic_anyone);
                    replyWithStatus(mIntent);
                }
                case AnonControlCommands.SIGNAL_RELOAD -> requestAnonRereadConfig();
                case AnonControlCommands.SIGNAL_NEWNYM -> newIdentity();
                case CMD_ACTIVE -> {
                    sendSignalActive();
                    replyWithStatus(mIntent);
                }
                case CMD_SET_EXIT -> setExitNode(mIntent.getStringExtra("exit"));
                case ACTION_LOCAL_LOCALE_SET -> configLanguage();
                default -> Log.w(TAG, "unhandled AnyoneVpnService Intent: " + action);
            }
        }
    }

    private class ActionBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) return;

            switch (action) {
                case CMD_ACTIVE -> sendSignalActive();
                case ACTION_ERROR -> {
                    if (showTorServiceErrorMsg) {
                        Toast.makeText(context, getString(R.string.config_invalid), Toast.LENGTH_LONG).show();
                        showTorServiceErrorMsg = false;
                    }
                    stopAnon();
                }
                case ACTION_STATUS -> {
                    // hack for https://github.com/guardianproject/tor-android/issues/73 remove when fixed
                    var newStatus = intent.getStringExtra(EXTRA_STATUS);
                    if (mCurrentStatus.equals(STATUS_OFF) && STATUS_STOPPING.equals(newStatus))
                        break;
                    mCurrentStatus = newStatus;
                    if (STATUS_OFF.equals(mCurrentStatus)) {
                        showDeactivatedNotification();
                    }
                    sendStatusToAnyoneVpnActivity();
                }
            }
        }
    }

    private void sendStatusToAnyoneVpnActivity() {
        var localStatus = new Intent(LOCAL_ACTION_STATUS).putExtra(EXTRA_STATUS, mCurrentStatus);
        LocalBroadcastManager.getInstance(AnyoneVpnService.this).sendBroadcast(localStatus); // update the activity with what's new
    }
}
