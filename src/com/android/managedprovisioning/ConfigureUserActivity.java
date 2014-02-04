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

package com.android.managedprovisioning;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckedTextView;

import com.android.managedprovisioning.ManagedProvisioningActivity.ProvisioningState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This activity launches ConfigureUserService. It gets called from the ManagedProvisioningActivity.
 */
// TODO: Merge this activity with the ManagedProvisioningActivity.
public class ConfigureUserActivity extends Activity {
    private static final String STATE_KEY = "stateKey";

    private StatusReceiver mStatusReceiver;

    private BroadcastReceiver mProvisioningDoneReceiver;

    // The UI shows a bunch of checkboxes that roughly correspond to states.
    // If completing a state should flip a checkbox, it is registered here.
    private static Map<Integer, Integer> mStateToCheckbox = new HashMap<Integer, Integer>();

    static {
        mStateToCheckbox.put(ProvisioningState.CONNECTED_NETWORK, R.id.connecting_wifi);
        mStateToCheckbox.put(ProvisioningState.CREATE_PROFILE, R.id.creating_profile);
        mStateToCheckbox.put(ProvisioningState.REGISTERED_DEVICE_POLICY, R.id.device_policy);
        mStateToCheckbox.put(ProvisioningState.SETUP_COMPLETE, R.id.setup_complete);
    }

    // Catches updates to the provisioning process sent out by various ProvisionTasks.
    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConfigureUserService.PROVISIONING_STATUS_REPORT_ACTION.
                    equals(intent.getAction())) {
                int state = intent.
                        getIntExtra(ConfigureUserService.PROVISIONING_STATUS_REPORT_EXTRA, -1);
                if (state != -1) {
                    ProvisionLogger.logd("Received state broadcast: " + state);

                    if (mStateToCheckbox.containsKey(state)) {
                        completeCheckbox(state);
                    }
                }
            }
        }
    };

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

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Iterator<Integer> stateIterator = mStateToCheckbox.keySet().iterator();
        while (stateIterator.hasNext()) {
            int state = stateIterator.next();
            CheckedTextView cb = (CheckedTextView) findViewById(mStateToCheckbox.get(state));
            if (cb != null) {
                savedInstanceState.putBoolean(STATE_KEY + state, cb.isChecked());
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
                cb.setChecked(savedInstanceState.getBoolean(STATE_KEY + state));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisionLogger.logd("Creating ConfigureUserActivity()");
        Intent intent = getIntent();

        if (mStatusReceiver == null) {
            mStatusReceiver = new StatusReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConfigureUserService.PROVISIONING_STATUS_REPORT_ACTION);
            registerReceiver(mStatusReceiver, filter);
        }

        setPreferences(intent);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.show_progress, null);
        setContentView(contentView);

        /*
         * This waits for the PROVISIONING_COMPLETE_ACTION, which indicates that we have finished
         * setting up this user.
         */

        mProvisioningDoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ProvisionLogger.logv("Setup done.");
                mProvisioningDoneReceiver = null;
                unregisterReceiver(this);
                finish();
            }
        };

        registerReceiver(mProvisioningDoneReceiver,
                new IntentFilter(ConfigureUserService.PROVISIONING_COMPLETE_ACTION));

        // Starting service to persist beyond activity and manage the tasks required for
        // provisioning.
        Intent serviceIntent = new Intent(this, ConfigureUserService.class);
        serviceIntent.putExtras(getIntent().getExtras());

        startService(serviceIntent);
    }

    @Override
    public void onBackPressed() {
        // TODO: Handle this graciously by stopping the provisioning flow and cleaning up.
    }

    /**
     * Sets preferences that are shared and persisted between activities and services.
     *
     * TODO: Refactor Preferences so the state created by this method is clear.
     */
    private void setPreferences(Intent intent) {
        Preferences prefs = new Preferences(this);
        // Copy most values directly from bump packet to preferences.
        for (String propertyName : Preferences.propertiesToStore) {
            prefs.setProperty(propertyName, intent.getStringExtra(propertyName));
        }

        prefs.setProperty(Preferences.IS_DEVICE_OWNER_KEY,
                intent.getBooleanExtra(Preferences.IS_DEVICE_OWNER_KEY, true));

        if (prefs.getStringProperty(Preferences.WIFI_SSID_KEY) != null) {
            String hiddenString = intent.getStringExtra(Preferences.WIFI_HIDDEN_KEY);
            prefs.setProperty(Preferences.WIFI_HIDDEN_KEY, Boolean.parseBoolean(hiddenString));

            if (prefs.getStringProperty(Preferences.WIFI_PROXY_HOST_KEY) != null) {

                String proxyPortStr = intent.getStringExtra(Preferences.WIFI_PROXY_PORT_STRING_KEY);
                try {
                    if (proxyPortStr != null) {
                        prefs.setProperty(Preferences.WIFI_PROXY_PORT_INT_KEY,
                                Integer.valueOf(proxyPortStr));
                    }
                } catch (NumberFormatException e) {
                    ProvisionLogger.loge("Proxy port " + proxyPortStr
                            + " could not be parsed as a number.");
                    prefs.setProperty(Preferences.WIFI_PROXY_HOST_KEY, null);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ProvisionLogger.logi("ConfigureUserActivity.onDestroy()");
        if (mProvisioningDoneReceiver != null) {
            unregisterReceiver(mProvisioningDoneReceiver);
        }

        if (mStatusReceiver != null) {
            unregisterReceiver(mStatusReceiver);
        }
    }
}
