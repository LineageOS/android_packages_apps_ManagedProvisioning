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

import static com.android.managedprovisioning.UserConsentActivity.USER_CONSENT_KEY;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckedTextView;

import com.android.internal.app.LocalePicker;

import java.io.IOException;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Handles intents initiating the provisioning process then launches the DeviceProvisioningService
 * to start the provisioning tasks.
 *
 * Provisioning types that are supported are:
 * - Managed profile: A device that already has a user, but needs to be set up for a
 *   secondary usage purpose (e.g using your personal device as a corporate device).
 * - Device owner: a new device is set up for a single use case (e.g. a tablet with restricted
 *   usage options, which a company wants to provide for clients.
 */
public class DeviceOwnerProvisioningActivity extends Activity {

    public static final String PACKAGE_NAME = "com.android.managedprovisioning";

    private static final String NFC_MIME_TYPE = "application/com.android.managedprovisioning";
    private static final String BUMP_LAUNCHER_ACTIVITY = ".DeviceOwnerProvisioningActivity";

    // Five minute timeout by default.
    private static final String TIMEOUT_IN_MS = "300000";

    // Base string for keys that are used to save UI state in onSaveInstanceState.
    private static final String BASE_STATE_KEY = "stateKey";

    private BroadcastReceiver mStatusReceiver;
    private Handler mHandler;
    private Runnable mTimeoutRunnable;

    private boolean mHasLaunchedConfiguration = false;
    private Preferences mPrefs;

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

    /**
     * The UI shows a bunch of checkboxes that roughly correspond to states.
     * If completing a state should flip a checkbox, it is registered here.
     */
    private static Map<Integer, Integer> mStateToCheckbox = new HashMap<Integer, Integer>();

    static {
        mStateToCheckbox.put(ProvisioningState.CONNECTED_NETWORK, R.id.connecting_wifi);
        mStateToCheckbox.put(ProvisioningState.CREATE_PROFILE, R.id.creating_profile);
        mStateToCheckbox.put(ProvisioningState.REGISTERED_DEVICE_POLICY, R.id.device_policy);
        mStateToCheckbox.put(ProvisioningState.SETUP_COMPLETE, R.id.setup_complete);
    }

    // Catches updates in provisioning process, watching for errors or completion.
    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ProvisionLogger.logd("Received broadcast: " + intent.getAction());

            if (DeviceProvisioningService.PROVISIONING_STATUS_REPORT_ACTION.
                    equals(intent.getAction())) {
                int state = intent.
                        getIntExtra(DeviceProvisioningService.PROVISIONING_STATUS_REPORT_EXTRA, -1);
                String stateText = intent.
                        getStringExtra(DeviceProvisioningService.PROVISIONING_STATUS_TEXT_EXTRA);
                ProvisionLogger.logd("Received state broadcast: " + state);

                if (state != -1) {
                    ProvisionLogger.logd("Received state broadcast: " + state);
                    if (mStateToCheckbox.containsKey(state)) {
                        completeCheckbox(state);
                    }

                    switch (state) {
                        case ProvisioningState.SETUP_COMPLETE:
                            sendBroadcast(new Intent(ManagedProvisioningActivity.PROVISIONING_COMPLETE_ACTION));
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
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisionLogger.logd("DeviceOwnerProvisioning started");
        // TODO Find better location/flow for disabling the NFC receiver during provisioning.
        // Re-enabling currently takes place in the DeviceProvisioningService.
        // We need to reassess the whole of enabling/disabling and not provisioning twice
        PackageManager pkgMgr = getPackageManager();
        pkgMgr.setComponentEnabledSetting(getComponentName(this),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        SettingsAdapter settingsAdapter = new SettingsAdapter(getContentResolver());

        if (mStatusReceiver == null) {
            mStatusReceiver = new StatusReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(DeviceProvisioningService.PROVISIONING_STATUS_REPORT_ACTION);
            registerReceiver(mStatusReceiver, filter);
        }

        mPrefs = new Preferences(this);

        // Avoid that provisioning is done twice.
        // Sometimes ManagedProvisioning gets killed and restarted, and needs to resume
        // provisioning already in progress. Provisioning doesn't need to resume if
        // provisioning has already succeeded, in which case prefs.doesntNeedResume() returns true.
        // TODO Check if we need the settingsAdapter for tests.
        // TODO Add double bump checking/handling.
        if (mPrefs.doesntNeedResume() && (settingsAdapter.isDeviceProvisioned())) {
            // TODO: Display message to the user.
            ProvisionLogger.logi("Device already provisioned. Stopping.");
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.show_progress, null);
        setContentView(contentView);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mHasLaunchedConfiguration) {
            startDeviceOwnerProvisioning();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Iterator<Integer> stateIterator = mStateToCheckbox.keySet().iterator();
        while (stateIterator.hasNext()) {
            int state = stateIterator.next();
            CheckedTextView cb = (CheckedTextView) findViewById(mStateToCheckbox.get(state));
            if (cb != null) {
                savedInstanceState.putBoolean(BASE_STATE_KEY + state, cb.isChecked());
            }
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        Iterator<Integer> stateIterator = mStateToCheckbox.keySet().iterator();
        while (stateIterator.hasNext()) {
            int state = stateIterator.next();
            CheckedTextView cb = (CheckedTextView) findViewById(mStateToCheckbox.get(state));
            if (cb != null) {
                cb.setChecked(savedInstanceState.getBoolean(BASE_STATE_KEY + state));
            }
        }
    }

    @Override
    public void onBackPressed() {
        // TODO: Handle this graciously by stopping the provisioning flow and cleaning up.
    }

    /**
     *  Build the provisioning intent from the NFC properties and start the device owner
     *  provisioning.
     */
    private void startDeviceOwnerProvisioning() {
        Intent intent = getIntent();
        Intent provisioningIntent = processNfcPayload(intent);

        if (provisioningIntent != null) {
            if (isProvisioningIntentValid(provisioningIntent)) {
                initializePreferences(provisioningIntent);
            } else {
                cleanupAndFinish();
            }

            // Launch DeviceProvisioningService.
            provisioningIntent.setClass(getApplicationContext(), DeviceProvisioningService.class);
            initialize(provisioningIntent);

            ProvisionLogger.logd("Starting DeviceProvisioningService");
            startService(provisioningIntent);
            mHasLaunchedConfiguration = true;
        } else {
            ProvisionLogger.logd("Unknown provisioning intent, exiting.");
            cleanupAndFinish();
        }
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
            provisioningIntent.putExtra(DeviceProvisioningService.ORIGINAL_INTENT_KEY, true);
            return provisioningIntent;
        } catch (IOException e) {
            error("Couldn't load payload", e);
            return null;
        }
    }

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

    /**
     * Checks that the required preferences in the incoming intent are valid.
     */
    private boolean isProvisioningIntentValid(Intent intent) {

      // Needed in {@code CreateProfileTask}
      if (!checkIntentHasExtra(intent, Preferences.MDM_PACKAGE_KEY)) {
        return false;
      }
      String mdmPackageName = intent.getStringExtra(Preferences.MDM_PACKAGE_KEY);
      // Check if the package is installed
      try {
        this.getPackageManager().getPackageInfo(mdmPackageName, 0);
      } catch (NameNotFoundException e) {
          ProvisionLogger.loge("Mdm "+ mdmPackageName + " is not installed.", e);
          return false;
      }

      // TODO: Validate incoming preferences required by the other tasks.
      return true;
    }

    /**
     * Sets preferences that are shared and persisted between activities and services.
     *
     * TODO: Refactor Preferences so the state created by this method is clear.
     */
    private void initializePreferences(Intent intent) {

        // Copy most values directly from bump packet to preferences.
        for (String propertyName : Preferences.propertiesToStore) {
            mPrefs.setProperty(propertyName, intent.getStringExtra(propertyName));
        }

        if (mPrefs.getStringProperty(Preferences.WIFI_SSID_KEY) != null) {
            String hiddenString = intent.getStringExtra(Preferences.WIFI_HIDDEN_KEY);
            mPrefs.setProperty(Preferences.WIFI_HIDDEN_KEY, Boolean.parseBoolean(hiddenString));

            if (mPrefs.getStringProperty(Preferences.WIFI_PROXY_HOST_KEY) != null) {

                String proxyPortStr = intent.getStringExtra(Preferences.WIFI_PROXY_PORT_STRING_KEY);
                try {
                    if (proxyPortStr != null) {
                        mPrefs.setProperty(Preferences.WIFI_PROXY_PORT_INT_KEY,
                                Integer.valueOf(proxyPortStr));
                    }
                } catch (NumberFormatException e) {
                    ProvisionLogger.loge("Proxy port " + proxyPortStr
                            + " could not be parsed as a number.");
                    mPrefs.setProperty(Preferences.WIFI_PROXY_HOST_KEY, null);
                }
            }
        }
    }

    protected void completeCheckbox(int state) {
        ProvisionLogger.logd("Setting checkbox for state " + state);
        Integer id = mStateToCheckbox.get(state);
        if (id != null) {
            CheckedTextView check = (CheckedTextView) findViewById(id);
            if (check != null) {
                check.setChecked(true);
            }
        }
    }

    private void cleanupAndFinish() {
        ProvisionLogger.logd("Finishing DeviceOwnerProvisioningActivity");

        if (mHandler != null) {
            mHandler.removeCallbacks(mTimeoutRunnable);
        }

        PackageManager pkgMgr = getPackageManager();
        pkgMgr.setComponentEnabledSetting(
                DeviceOwnerProvisioningActivity.getComponentName(this),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        finish();
    }

    private void error(String logMsg) {
        error(logMsg, null);
    }

    private void error(String logMsg, Throwable t) {
        mPrefs.setError(logMsg.toString());
        ProvisionLogger.loge("Error: " + logMsg, t);
        ErrorDialog.showError(DeviceOwnerProvisioningActivity.this);
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

    private boolean checkIntentHasExtra(Intent intent, String name) {
        if (!intent.hasExtra(name)) {
          ProvisionLogger.loge("Intent missing extra: " + name);
          return false;
        }
        return true;
    }
}
