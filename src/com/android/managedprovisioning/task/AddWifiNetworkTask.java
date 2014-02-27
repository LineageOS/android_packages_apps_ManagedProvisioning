/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning.task;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.android.managedprovisioning.NetworkMonitor;
import com.android.managedprovisioning.Preferences;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.WifiConfig;
import com.android.managedprovisioning.DeviceOwnerProvisioningActivity.ProvisioningState;

/**
 * Adds a wifi network to system if one is specified in the bump.
 */
public class AddWifiNetworkTask extends ProvisionTask implements NetworkMonitor.Callback {

    private String mSsid;

    private WifiConfig mWifiConfig;
    private String mWifiSsid;

    private WifiManager mWifiManager;

    public AddWifiNetworkTask() {
        super("Wifi task");
    }

    @Override
    public void setManager(TaskManager taskManager, Context context, int id) {
        super.setManager(taskManager, context, id);
    }

    @Override
    public void executeTask(String... params) {
        // The WiFi state-machine starts off in the "Disconnected" state. This means that
        // it may not attempt new WiFi connections if we add new networks.
        reconnectWifi();

        Preferences mPrefs = mTaskManager.getPreferences();
        mSsid = mPrefs.getStringProperty(Preferences.WIFI_SSID_KEY);
        if (!TextUtils.isEmpty(mSsid)) {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            mWifiConfig = new WifiConfig(mWifiManager);
            new NetworkMonitor(mContext, this);
            addWifiNetwork();
        } else {
            onSuccess();
        }
    }

    private boolean reconnectWifi() {
        ProvisionLogger.logd("Attempting to reconnect wifi");
        WifiManager wm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        return wm.reconnect();
    }

    protected void addWifiNetwork() {
        Preferences mPrefs = mTaskManager.getPreferences();
        mWifiSsid = mPrefs.getStringProperty(Preferences.WIFI_SSID_KEY);
        if (mWifiSsid == null) {
            ProvisionLogger.logd("Tried to add wifi network with null SSID");
            return;
        }

        boolean hidden = mPrefs.getBooleanProperty(Preferences.WIFI_HIDDEN_KEY);
        String password = mPrefs.getStringProperty(Preferences.WIFI_PASSWORD_KEY);
        String security = mPrefs.getStringProperty(Preferences.WIFI_SECURITY_TYPE_KEY);
        String proxyHost = mPrefs.getStringProperty(Preferences.WIFI_PROXY_HOST_KEY);
        String proxyBypassHosts = mPrefs.getStringProperty(Preferences.WIFI_PROXY_BYPASS_KEY);
        int proxyPort = mPrefs.getIntProperty(Preferences.WIFI_PROXY_PORT_INT_KEY);

        int netId = mWifiConfig.addNetwork(
                mWifiSsid, hidden, security, password, proxyHost, proxyPort, proxyBypassHosts);

        if (netId == -1) {
            mTaskManager.registerProvisioningState(
                    ProvisioningState.ERROR, "Failed to save network.");
            mTaskManager.fail("Failed to save network.");
            return;
        } else {
            WifiManager wifiManager =
                    (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

            if (!wifiManager.reconnect()) {
                onFailure("Unable to connect to wifi");
            }

        }
    }

    @Override
    // This will be called from NetworkMonitor if in Wifi mode.
    public void onNetworkConnected() {
        if (NetworkMonitor.isConnectedToWifi(mContext) &&
                mWifiManager.getConnectionInfo().getSSID().equals(mWifiSsid)) {
            ProvisionLogger.logd("Connected to the correct network");
            mTaskManager.registerProvisioningState(ProvisioningState.CONNECTED_NETWORK, "");
            onSuccess();
            return;
        }
    }

    @Override
    public void onNetworkDisconnected() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void hasFailed() {
        mTaskManager.registerProvisioningState(ProvisioningState.ERROR, mLastFailure);
    }

}
