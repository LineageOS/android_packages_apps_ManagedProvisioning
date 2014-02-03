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

import android.app.Activity;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;

import com.android.internal.app.LocalePicker;

import java.io.IOException;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;

/**
 * Handles intents initiating the provisioning process then launches the ConfigureUserActivity
 * as needed to handle the provisioning flow.
 *
 * Provisioning types that are supported are:
 * - Managed profile: A device that already has a user, but needs to be set up for a
 *   secondary usage purpose (e.g using your personal device as a corporate device).
 * - Device owner: a new device is set up for a single use case (e.g. a tablet with restricted
 *   usage options, which a company wants to provide for clients
 */
public class ManagedProvisioningActivity extends Activity {

    public static final String PACKAGE_NAME = "com.android.managedprovisioning";

    // TODO: Put this action somewhere public.
    private static final String ACTION_PROVISION_MANAGED_PROFILE
        = "android.managedprovisioning.PROVISION_MANAGED_PROFILE";

    private static final String NFC_MIME_TYPE = "application/com.android.managedprovisioning";
    private static final String BUMP_LAUNCHER_ACTIVITY = ".ManagedProvisioningActivity";

    // Five minute timeout by default.
    private static final String TIMEOUT_IN_MS = "300000";

    private boolean mHasLaunchedConfiguration = false;

    // Abstraction above settings for testing.
    private SettingsAdapter mSettingsAdapter;

    private BroadcastReceiver mStatusReceiver;
    private Handler mHandler;
    private Runnable mTimeoutRunnable;
    
    private boolean mIsDeviceOwner;

    /**
     * States that provisioning can have completed. Not all states are reached for each of
     * the provisioning types.
     */
    public static class ProvisioningState {
        public static final int CONNECTED_NETWORK = 0; // Device owner only
        public static final int CREATE_PROFILE = 1; // Managed profile only
        public static final int REGISTERED_DEVICE_POLICY = 1;
        public static final int SETUP_COMPLETE = 2;
        public static final int UPDATE = 3;
        public static final int ERROR = 4;
    }

    // Catches updates in provisioning process, watching for errors or completion.
    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ProvisionLogger.logd("Received broadcast: " + intent.getAction());

            if (ConfigureUserService.PROVISIONING_STATUS_REPORT_ACTION.
                    equals(intent.getAction())) {
                int state = intent.
                        getIntExtra(ConfigureUserService.PROVISIONING_STATUS_REPORT_EXTRA, -1);
                String stateText = intent.
                        getStringExtra(ConfigureUserService.PROVISIONING_STATUS_TEXT_EXTRA);
                ProvisionLogger.logd("Received state broadcast: " + state);

                if (state != -1) {
                    switch (state) {
                        case ProvisioningState.SETUP_COMPLETE:
                            cleanupAndFinish();
                            break;
                        case ProvisioningState.ERROR:
                            error(stateText);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO Find better location/flow for disabling the NFC receiver during provisioning.
        // Re-enabling currently takes place in the ConfigureUserService.
        PackageManager pkgMgr = getPackageManager();
        pkgMgr.setComponentEnabledSetting(getComponentName(this),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        mSettingsAdapter = new SettingsAdapter(getContentResolver());

        if (mStatusReceiver == null) {
            mStatusReceiver = new StatusReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConfigureUserService.PROVISIONING_STATUS_REPORT_ACTION);
            registerReceiver(mStatusReceiver, filter);
        }

        // Sometimes ManagedProvisioning gets killed and restarted, and needs to resume
        // provisioning already in progress. Provisioning doesn't need to resume if
        // provisioning has already succeeded, in which case prefs.doesntNeedResume() returns true.

        Preferences prefs = new Preferences(this);

        // TODO Check if we need the settingsAdapter for tests.
        if (prefs.doesntNeedResume() && (mSettingsAdapter.isDeviceProvisioned())) {
            // TODO Add double bump checking/handling.

            if ((savedInstanceState == null) || prefs.doesntNeedResume()) {
                finish();
            }
        }

        setContentView(R.layout.show_progress);
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();

        // Build the provisioning intent from either the NFC properties or managed profile intent.
        Intent provisioningIntent = null;
        if (!mHasLaunchedConfiguration) {
            if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {

              // NFC provisioning for Device Owner flow.
              mIsDeviceOwner = true;
              provisioningIntent = processNfcPayload(intent);
            } else if (ACTION_PROVISION_MANAGED_PROFILE.equals(intent.getAction())) {

                // Programmatic intent for managed profile flow.
                // Add a flag so the ConfigureUserService knows this is the incoming intent.

                // TODO: Uncomment this once the managed profile flow is in place. We can't trigger
                // this activity yet since it'll make it possible to set device owner from an intent
                // which we don't want.
                mIsDeviceOwner = false;
                provisioningIntent = new Intent(intent);
                provisioningIntent.putExtra(ConfigureUserService.ORIGINAL_INTENT_KEY, true);
            }

            if (provisioningIntent != null) {

                // TODO: Validate incoming intent.

                // Launch ConfigureUserActivity.
                provisioningIntent.putExtra(Preferences.IS_DEVICE_OWNER_KEY, mIsDeviceOwner);
                provisioningIntent.setClass(getApplicationContext(), ConfigureUserActivity.class);
                initialize(provisioningIntent);

                startActivity(provisioningIntent);
                mHasLaunchedConfiguration = true;
            } else {
                ProvisionLogger.logd("Unknown provisioning intent, exiting.");
                cleanupAndFinish();
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and returns it in the form of intent extras.
     */
    private Intent processNfcPayload(Intent intent) {
        ProvisionLogger.logi("Processing NFC Payload.");

        if (intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
            for (Parcelable rawMsg : intent
                    .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
                NdefMessage msg = (NdefMessage) rawMsg;
                NdefRecord firstRecord = msg.getRecords()[0];
                String mimeType = new String(firstRecord.getType());

                if (NFC_MIME_TYPE.equals(mimeType)) {
                    return parseNfcProperties(new String(firstRecord.getPayload()));
                }
            }
        }
        return null;
    }

    private Intent parseNfcProperties(String data) {
        try {
            Properties props = new Properties();
            props.load(new StringReader(data));

            // Copy properties to intent.
            Intent provisioningIntent = new Intent();
            Enumeration<Object> propertyNames = props.keys();
            while (propertyNames.hasMoreElements()) {
                String propName = (String) propertyNames.nextElement();
                provisioningIntent.putExtra(propName, props.getProperty(propName));
            }
            provisioningIntent.putExtra(ConfigureUserService.ORIGINAL_INTENT_KEY, true);
            return provisioningIntent;
        } catch (IOException e) {
            error("Couldn't load payload", e);
            return null;
        }
    }

    // TODO Initialization should be different for managed profile flow (e.g don't set time zone).
    private void initialize(Intent provisioningIntent) {
        registerErrorTimeout(provisioningIntent);
        setTimeAndTimezone(provisioningIntent);

        enableWifi();
        setLanguage(provisioningIntent);
    }

    private void registerErrorTimeout(Intent provisioningIntent) {
        mTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                error("timeout");
            }
        };

        mHandler = new Handler();
        long timeout = Long.parseLong(getStringExtra(provisioningIntent,
                Preferences.TIMEOUT_KEY, TIMEOUT_IN_MS));
        mHandler.postDelayed(mTimeoutRunnable, timeout);
    }

    private void setTimeAndTimezone(Intent provisioningIntent) {
        String timeZoneId = provisioningIntent.getStringExtra(Preferences.TIME_ZONE_KEY);
        String localTimeString = provisioningIntent.getStringExtra(Preferences.LOCAL_TIME_KEY);
        Long localTime = localTimeString != null ? Long.valueOf(localTimeString) : null;

        try {
            final AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (timeZoneId != null) am.setTimeZone(timeZoneId);
            if (localTime != null) am.setTime(localTime);
        } catch (Exception e) {
            ProvisionLogger.loge("Failed to get alarm manager to set the system time/timezone.");
        }
    }

    private void enableWifi() {
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            int wifiState = wm.getWifiState();
            boolean wifiOn = wifiState == WifiManager.WIFI_STATE_ENABLED;
            if (!wifiOn) {
                if (!wm.setWifiEnabled(true)) {
                    error("Unable to enable WiFi");
                }
            }
        }
    }

    private void setLanguage(Intent provisioningIntent) {
        String locale = provisioningIntent.getStringExtra(Preferences.LOCALE_KEY);
        ProvisionLogger.logd("About to set locale: " + locale);
        if (locale == null || locale.equals(Locale.getDefault().toString())) {
            return;
        }

        try {
            if (locale.length() == 5) {
                Locale l = new Locale(locale.substring(0, 2), locale.substring(3, 5));
                LocalePicker.updateLocale(l);
            }
        } catch (Exception e) {
            ProvisionLogger.loge("failed to set the system locale");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mStatusReceiver != null) {
            unregisterReceiver(mStatusReceiver);
            mStatusReceiver = null;
        }
    }

    private void cleanupAndFinish() {
        ProvisionLogger.logd("Finishing NfcBumpActivity");

        if (mHandler != null) {
            mHandler.removeCallbacks(mTimeoutRunnable);
        }
        finish();
    }

    private void error(String logMsg) {
        error(logMsg, null);
    }

    private void error(String logMsg, Throwable t) {
        Preferences prefs = new Preferences(this);
        prefs.setError(logMsg.toString());
        ProvisionLogger.loge("Error: " + logMsg, t);
        ErrorDialog.showError(ManagedProvisioningActivity.this);
    }

    public static ComponentName getComponentName(Context context) {
        String ourPackage = context.getPackageName();
        String bumpLauncher = ourPackage + BUMP_LAUNCHER_ACTIVITY;
        return new ComponentName(ourPackage, bumpLauncher);
    }

    private String getStringExtra(Intent intent, String key, String defaultValue) {
        String rtn = intent.getStringExtra(key);
        return (rtn != null) ? rtn : defaultValue;
    }
}
