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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;

import com.android.internal.app.LocalePicker;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public class ManagedProvisioningActivity extends Activity {

    private static final String NFC_MIME_TYPE = "application/com.android.managedprovisioning";
    private static final String BUMP_LAUNCHER_ACTIVITY = ".ManagedProvisioningActivity";

    // Five minute timeout by default.
    private static final String TIMEOUT_IN_MS = "300000";

    // The packet that we receive over NFC is this serialized properties object.
    private Properties mProps;

    // Abstraction above settings for testing.
    private SettingsAdapter mSettingsAdapter;

    // TODO Make this a boolean if it turns out that we only have one or two states to be
    // tracked here. If we don't remove it, rename to make the difference between this state and the
    // task state used in the task manager more clear.
    enum State {
        BUMP_DETECTED, // We've received a NFC packet.
        MAX_VALUES
    }

    private State mState = State.BUMP_DETECTED;

    // Used to determine which state to enter.
    private static Set<State> mCompletedStates = Sets.newHashSet();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageManager pkgMgr = getPackageManager();
        pkgMgr.setComponentEnabledSetting(getComponentName(this),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        mSettingsAdapter = new SettingsAdapter(getContentResolver());

        // TODO Register status receiver for error handling only if needed.
        // (Might be possible to simplify this)

        // Sometimes ManagedProvisioning gets killed and restarted, and needs to resume
        // provisioning already in progress. Provisioning doesn't need to resume if
        // provisioning has already succeeded, in which case prefs.doesntNeedResume() returns true.

        Preferences prefs = new Preferences(this);

        // TODO Check if we need the settingsAdapter for tests.
        if (prefs.doesntNeedResume() && (mSettingsAdapter.isDeviceProvisioned())) {

            Intent intent = getIntent();
            if (intent != null) {
                processNfcPayload(intent);
                sendProvisioningError("Device bumped twice.");
            }

            ProvisionLogger.logd("Device already provisioned or running in sandbox, exiting.");
            if (savedInstanceState == null) {
                cleanupAndFinish();
            } else {
                ProvisionLogger.logd("onCreate() called, provisioning in progress. Exiting.");
                finish();
            }
            return;
        }

        setContentView(R.layout.show_progress);
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        // Check to see that the Activity started due to an Android Beam.
        // Don't restart an provisioning in progress

        // TODO also react to bluetooth
        if ((mState == State.BUMP_DETECTED) &&
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            processNfcPayload(intent);
            if (mProps != null) {
                initialize();
                completeState(State.BUMP_DETECTED);
            }
        }
    }

    private void sendProvisioningError(final String message) {
        // TODO implement error message for Programmer app.
        ProvisionLogger.logd("send provision error code: " + " " + message);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView.
     */
    void processNfcPayload(Intent intent) {

        // TODO internationalization for messages.
        ProvisionLogger.logi("Processing NFC Payload.");

        if (intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
            for (Parcelable rawMsg : intent
                    .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
                NdefMessage msg = (NdefMessage) rawMsg;
                NdefRecord firstRecord = msg.getRecords()[0];
                String mimeType = new String(firstRecord.getType());

                if (NFC_MIME_TYPE.equals(mimeType)) {
                    loadProps(new String(firstRecord.getPayload()));
                }
            }
        }
        // TODO Add bluetooth alternative to NFC.
    }

    private void loadProps(String data) {
        try {
            mProps = new Properties();
            mProps.load(new StringReader(data));
        } catch (IOException e) {
            error("Couldn't load payload", e);
        }
    }

    // TODO This initialisation should be different for BYOD (e.g don't set time zone).
    private void initialize() {
        registerErrorTimeout();
        setTimeAndTimezone();

        enableWifi();
        setLanguage();
    }

    private void registerErrorTimeout() {
        Runnable checkForDoneness = new Runnable() {
            @Override
            public void run() {
                if (mState == State.MAX_VALUES) {
                    return;
                }
                error("timeout");
            }
        };

        final Handler handler = new Handler();
        long timeout = Long.parseLong(
                mProps.getProperty(Preferences.TIMEOUT_KEY, TIMEOUT_IN_MS));
        handler.postDelayed(checkForDoneness, timeout);
    }

    private void setTimeAndTimezone() {
        String timeZoneId = mProps.getProperty(Preferences.TIME_ZONE_KEY);
        String localTimeString = mProps.getProperty(Preferences.LOCAL_TIME_KEY);
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

    private void setLanguage() {
        String locale = mProps.getProperty(Preferences.LOCALE_KEY);
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
        // TODO unregister status receiver if added
    }

    public void completeState(State completed) {
        completeState(completed, true);
    }

    private synchronized void completeState(State completed, boolean startNext) {
        mCompletedStates.add(completed);
        ProvisionLogger.logv("Completed state:" + completed);

        // Find the first state that isn't done.
        for (State state : State.values()) {
            if (!mCompletedStates.contains(state)) {
                if (mState == state) {
                    ProvisionLogger.logv("No state change");
                    return; // No state change.
                } else {
                    ProvisionLogger.logv("New State: " + state);
                    mState = state;
                    break;
                }
            }
        }

        // TODO Start the task manager to start the setup.
        // Ensure that the execute() method is called on the UI thread.
        // Message taskMsg = mHandler.obtainMessage(RUN_TASK_MSG);
        // skMsg.sendToTarget();
    }

    private void cleanupAndFinish() {
        ProvisionLogger.logd("Finishing NfcBumpActivity");
        finish();
    }

    private void error(String logMsg) {
        error(logMsg, null);
    }

    private void error(String logMsg, Throwable t) {
        Preferences prefs = new Preferences(this);
        prefs.setError(logMsg.toString());
        ProvisionLogger.loge("Error: " + logMsg, t);
        // TODO add error Dialog.
        // ErrorDialog.showError(NfcBumpActivity.this);
    }

    private ComponentName getComponentName(Context context) {
        String ourPackage = context.getPackageName();
        String bumpLauncher = ourPackage + BUMP_LAUNCHER_ACTIVITY;
        return new ComponentName(ourPackage, bumpLauncher);
    }
}
