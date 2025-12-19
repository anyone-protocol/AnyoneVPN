/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anyone.anyonevpn.service.vpn;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import io.anyone.anyonevpn.R;
import io.anyone.anyonevpn.service.AnyoneVpnConstants;
import io.anyone.anyonevpn.service.AnyoneVpnService;
import io.anyone.anyonevpn.service.TProxyService;
import io.anyone.anyonevpn.service.util.Prefs;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


public class AnyoneVpnManager implements Handler.Callback, AnyoneVpnConstants {
    private static final String TAG = "AnyoneVpnManager";
    boolean isStarted = false;
    private final static String mSessionName = "AnyoneVPN";
    private ParcelFileDescriptor mInterface;
    private int mTorSocks = -1;
    private int mTorDns = -1;
    private final VpnService mService;
    private final SharedPreferences prefs;

    private FileInputStream fis;
    private DataOutputStream fos;

    private static final int DELAY_FD_LISTEN_MS = 5000;

    public AnyoneVpnManager(AnyoneVpnService service) {
        mService = service;
        prefs = Prefs.getSharedPrefs(mService.getApplicationContext());
    }

    public void handleIntent(VpnService.Builder builder, Intent intent) {
        if (intent != null) {
            var action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START_VPN, ACTION_START -> {
                        Log.d(TAG, "starting VPN");
                        isStarted = true;
                    }
                    case ACTION_STOP_VPN, ACTION_STOP -> {
                        isStarted = false;
                        Log.d(TAG, "stopping VPN");
                        stopVPN();

                        //reset ports
                        mTorSocks = -1;
                        mTorDns = -1;
                    }
                    case AnyoneVpnConstants.LOCAL_ACTION_PORTS -> {
                        Log.d(TAG, "setting VPN ports");
                        int torSocks = intent.getIntExtra(AnyoneVpnService.EXTRA_SOCKS_PROXY_PORT, -1);
//                    int torHttp = intent.getIntExtra(AnyoneVpnService.EXTRA_HTTP_PROXY_PORT,-1);
                        int torDns = intent.getIntExtra(AnyoneVpnService.EXTRA_DNS_PORT, -1);

                        //if running, we need to restart
                        if ((torSocks != -1 && torSocks != mTorSocks && torDns != -1 && torDns != mTorDns)) {

                            mTorSocks = torSocks;
                            mTorDns = torDns;

                            setupTun2Socks(builder);
                        }
                    }
                }
            }
        }
    }

    public void restartVPN(VpnService.Builder builder) {
        stopVPN();
        setupTun2Socks(builder);
    }

    private void stopVPN() {
        if (mInterface != null) {
            try {
                Log.d(TAG, "closing interface, destroying VPN interface");
                TProxyService.TProxyStopService();
                if (fis != null) {
                    fis.close();
                    fis = null;
                }

                if (fos != null) {
                    fos.close();
                    fos = null;
                }

                mInterface.close();
                mInterface = null;
            } catch (Exception | Error e) {
                Log.d(TAG, "error stopping tun2socks", e);
            }
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message message) {
        Toast.makeText(mService, message.what, Toast.LENGTH_SHORT).show();
        return true;
    }

    private synchronized void setupTun2Socks(final VpnService.Builder builder) {
        try {
            builder.setMtu(TProxyService.TUNNEL_MTU)
                    .addAddress(TProxyService.VIRTUAL_GATEWAY_IPV4, 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(TProxyService.FAKE_DNS) //just setting a value here so DNS is captured by TUN interface
                    .setSession(mService.getString(R.string.app_name))
            //handle ipv6
                    .addAddress(TProxyService.VIRTUAL_GATEWAY_IPV6, 128)
                    .addRoute("::", 0);

            doAppBasedRouting(builder);

            // https://developer.android.com/reference/android/net/VpnService.Builder#setMetered(boolean)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);

                // Explicitly allow both families, so we do not block
                // traffic for ones without DNS servers (issue 129).
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);

            }

            builder.setSession(mSessionName)
                    .setConfigureIntent(null) // previously this was set to a null member variable
                    .setBlocking(true);

            mInterface = builder.establish();

            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    startListeningToFD();
                } catch (IOException e) {
                    Log.d(TAG, "VPN tun listening has stopped", e);
                }
            }, DELAY_FD_LISTEN_MS);

        } catch (Exception e) {
            Log.d(TAG, "VPN tun setup has stopped", e);
        }
    }

    private File getHevSocksTunnelConfFile() throws IOException {
        var file = new File(mService.getCacheDir(), "tproxy.conf");
        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();

        var fos = new FileOutputStream(file, false);

        var conf = "misc:\n" +
//                "  log-file: /data/data/io.anyone.anyonevpn/cache/hev.log\n" +
                "  log-level: warn\n" +
                "  task-stack-size: " + TProxyService.TASK_SIZE + "\n" +
                "tunnel:\n" +
                "  ipv4: '" + TProxyService.VIRTUAL_GATEWAY_IPV4 + "'\n" +
                "  ipv6: '" + TProxyService.VIRTUAL_GATEWAY_IPV6 + "'\n" +
                "  mtu: " + TProxyService.TUNNEL_MTU + "\n" +
                "socks5:\n" +
                "  port: " + mTorSocks + "\n" +
                "  address: 127.0.0.1\n" +
                "  upd: 'udp'\n" +
                "mapdns:\n" +
                "  address: " + TProxyService.FAKE_DNS + "\n" +
                "  port: 53\n" +
                "  network: 240.0.0.0\n" +
                "  netmask: 240.0.0.0\n" +
                "  cache-size: 10000\n";

//        Log.d(TAG, conf);

        fos.write(conf.getBytes());
        fos.close();

        return file;
    }

    private void startListeningToFD() throws IOException, IllegalStateException {
        if (mInterface == null) return; // Prepare hasn't been called yet

        fis = new FileInputStream(mInterface.getFileDescriptor());
        fos = new DataOutputStream(new FileOutputStream(mInterface.getFileDescriptor()));

        var conf = getHevSocksTunnelConfFile();

        TProxyService.TProxyStartService(conf.getAbsolutePath(), mInterface.getFd());
    }

    private void doAppBasedRouting(VpnService.Builder builder) throws NameNotFoundException {
        var apps = AnonifiedApp.getApps(mService, prefs);
        var individualAppsWereSelected = false;
        var isLockdownMode = isVpnLockdown(mService);

        for (AnonifiedApp app : apps) {
            if (app.isTorified() && (!app.getPackageName().equals(mService.getPackageName()))) {
                if (prefs.getBoolean(app.getPackageName() + AnyoneVpnConstants.APP_TOR_KEY, true)) {
                    builder.addAllowedApplication(app.getPackageName());
                }
                individualAppsWereSelected = true;
            }
        }
        Log.i(TAG, "App based routing is enabled?=" + individualAppsWereSelected + ", isLockdownMode=" + isLockdownMode);

        if (!individualAppsWereSelected && !isLockdownMode) {
            // disallow orobt itself...
            builder.addDisallowedApplication(mService.getPackageName());

            // disallow tor apps to avoid tor over tor, Orbot doesnt need to concern itself with them
            for (String packageName : AnyoneVpnConstants.BYPASS_VPN_PACKAGES)
                builder.addDisallowedApplication(packageName);
        }
    }

    public boolean isStarted() {
        return isStarted;
    }

    private boolean isVpnLockdown(final VpnService vpn) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return vpn.isLockdownEnabled();
        } else {
            return false;
        }
    }
}
