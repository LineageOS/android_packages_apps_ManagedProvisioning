/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.android.managedprovisioning.NetworkMonitor;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.WifiConfig;

/**
 * Adds a wifi network to system.
 */
public class AddWifiNetworkTask implements NetworkMonitor.Callback {
    private final Context mContext;
    private final String mSsid;
    private final boolean mHidden;
    private final String mSecurityType;
    private final String mPassword;
    private final String mProxyHost;
    private final int mProxyPort;
    private final String mProxyBypassHosts;
    private final Callback mCallback;

    private WifiManager mWifiManager;
    private NetworkMonitor mNetworkMonitor;

    public AddWifiNetworkTask(Context context, String ssid, boolean hidden, String securityType,
            String password, String proxyHost, int proxyPort, String proxyBypassHosts,
            Callback callback) {
        mCallback = callback;
        mContext = context;
        mSsid = ssid;
        mHidden = hidden;
        mSecurityType = securityType;
        mPassword = password;
        mProxyHost = proxyHost;
        mProxyPort = proxyPort;
        mProxyBypassHosts = proxyBypassHosts;
        mWifiManager  = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    public boolean wifiCredentialsWereProvided() {
        return !TextUtils.isEmpty(mSsid);
    }

    public void run() {
        if (!enableWifi()) {
            ProvisionLogger.loge("Failed to enable wifi");
            mCallback.onError();
            return;
        }

        WifiConfig wifiConfig = new WifiConfig(mWifiManager);
        mNetworkMonitor = new NetworkMonitor(mContext, this);

        int netId = wifiConfig.addNetwork(mSsid, mHidden, mSecurityType, mPassword, mProxyHost,
                mProxyPort, mProxyBypassHosts);

        if (netId == -1) {
            ProvisionLogger.loge("Failed to save network.");
            mCallback.onError();
            return;
        } else {
            if (!mWifiManager.reconnect()) {
                ProvisionLogger.loge("Unable to connect to wifi");
                mCallback.onError();
                return;
            }
        }

        // NetworkMonitor will call onNetworkConnected if in Wifi mode.
    }

    private boolean enableWifi() {
        if (mWifiManager != null) {
            int wifiState = mWifiManager.getWifiState();
            boolean wifiOn = wifiState == WifiManager.WIFI_STATE_ENABLED;
            if (!wifiOn) {
                if (!mWifiManager.setWifiEnabled(true)) {
                    return false;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public void onNetworkConnected() {
        if (NetworkMonitor.isConnectedToWifi(mContext) &&
                mWifiManager.getConnectionInfo().getSSID().equals(mSsid)) {
            ProvisionLogger.logd("Connected to the correct network");
            mNetworkMonitor.close();
            mNetworkMonitor = null;
            mCallback.onSuccess();
        }
    }

    public void cleanUp() {
        if (mNetworkMonitor != null) {
            mNetworkMonitor.close();
            mNetworkMonitor = null;
        }
    }

    @Override
    public void onNetworkDisconnected() {

    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
