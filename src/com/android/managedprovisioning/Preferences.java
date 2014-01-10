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

package com.android.managedprovisioning;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferences for NfcProvision.
 */
// TODO When common provisioning code is moved, double check that there are no
// unused keys and access methods here.
public class Preferences {
    private static final String PREF_FILE = "prefs";

    static final String NFC_BUMP_PACKET = "nfcBumpPacket";

    // Keys for the properties in the packet.
    static final String TIMEOUT_KEY = "timeout";
    static final String TIME_ZONE_KEY = "timeZone";
    static final String LOCAL_TIME_KEY = "localTime";
    static final String LOCALE_KEY = "locale";
    static final String BLUETOOTH_RETRY_KEY = "bluetoothRetries";
    static final String TASK_RETRY_KEY = "taskRetries";
    static final String BLUETOOTH_UUID_KEY = "uuid";
    static final String BLUETOOTH_MAC_KEY = "macAddress";

    public static final String OWNER_INFO_KEY = "ownerInfo";
    public static final String CLOSE_BLUETOOTH_KEY = "closeBluetooth";
    public static final String WIFI_SSID_KEY = "wifiSsid";
    public static final String WIFI_HIDDEN_KEY = "wifiHidden";
    public static final String WIFI_SECURITY_TYPE_KEY = "wifiSecurityType";
    public static final String WIFI_PASSWORD_KEY = "wifiPassword";
    public static final String WIFI_PROXY_HOST_KEY = "wifiProxyHost";
    public static final String WIFI_PROXY_PORT_STRING_KEY = "wifiProxyPort";
    public static final String WIFI_PROXY_PORT_INT_KEY = "wifiProxyPort";
    public static final String WIFI_PROXY_BYPASS_KEY = "wifiProxyBypassHosts";

    // Where to report this device's android ID.
    static final String RPC_URL_KEY = "rpcUrl";

    public static String[] propertiesToStore = {
            RPC_URL_KEY, BLUETOOTH_MAC_KEY, BLUETOOTH_UUID_KEY, OWNER_INFO_KEY,
            WIFI_SSID_KEY, WIFI_PASSWORD_KEY, WIFI_SECURITY_TYPE_KEY, WIFI_PROXY_BYPASS_KEY,
            WIFI_PROXY_HOST_KEY, BLUETOOTH_RETRY_KEY, TASK_RETRY_KEY, CLOSE_BLUETOOTH_KEY
    };

    private static final String DEVICE_POLICY_REGISTERD_KEY = "devicePolicyRegistered";
    private static final String ERROR_KEY = "error";
    private static final String DOESNT_NEED_RESUME_KEY = "doesntNeedResume";

    public static final String TASK_STATE = "taskState";

    public static final String BLUETOOTH_ONLY = "keyBluetoothOnly";

    private final Context mContext;

    public Preferences(Context context) {
        mContext = context;
    }

    private SharedPreferences getPrefs() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return prefs;
    }

    public String getRpc() {
        return getPrefs().getString(RPC_URL_KEY, null);
    }

    public void setRpc(String rpcUrl) {
        getPrefs().edit()
                .putString(RPC_URL_KEY, rpcUrl)
                .commit();
    }

    /**
     * Returns true if we have registered the device policy.
     */
    public boolean isDevicePolicyRegistered() {
        return getPrefs().getBoolean(DEVICE_POLICY_REGISTERD_KEY, false);
    }

    public void setDevicePolicyRegistered(boolean started) {
        getPrefs().edit()
                .putBoolean(DEVICE_POLICY_REGISTERD_KEY, started)
                .commit();
    }

    public boolean isConfigureUserComplete(String account) {
        return account == null ? false : getBooleanProperty(account);
    }

    public void setConfigureUserComplete(String account, boolean complete) {
        if (account != null) setProperty(account, complete);
    }

    public boolean doesntNeedResume() {
        return getPrefs().getBoolean(DOESNT_NEED_RESUME_KEY, false);
    }

    public void setDoesntNeedResume(boolean b) {
        getPrefs().edit()
                .putBoolean(DOESNT_NEED_RESUME_KEY, b)
                .commit();
    }

    public String getError() {
        return getPrefs().getString(ERROR_KEY, null);
    }

    public void setError(String error) {
        getPrefs().edit()
                .putString(ERROR_KEY, error)
                .commit();
    }

    public void setProperty(String key, String value) {
        getPrefs().edit()
                .putString(key, value)
                .commit();
    }

    public void setProperty(String key, int value) {
        getPrefs().edit()
                .putInt(key, value)
                .commit();
    }

    public void setProperty(String key, boolean value) {
        getPrefs().edit()
                .putBoolean(key, value)
                .commit();
    }

    public String getStringProperty(String key) {
        return getPrefs().getString(key, null);
    }

    public int getIntProperty(String key) {
        return getPrefs().getInt(key, -1);
    }

    public boolean getBooleanProperty(String key) {
        return getPrefs().getBoolean(key, false);
    }
}