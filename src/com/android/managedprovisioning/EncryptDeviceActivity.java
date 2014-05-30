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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.os.BatteryManager;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.RemoteException;
import android.os.ServiceManager;

/**
 * Activity to ask for permission to activate full-filesystem encryption.
 *
 * Pressing 'restart' will leave the screen as it is and start encrypting
 */
public class EncryptDeviceActivity extends Activity {
    protected static final String EXTRA_RESUME = "com.android.managedprovisioning.RESUME";
    protected static final String ENCRYPTION_ACTIVE_KEY = "encryptionActiveKey";

    // Minimum battery charge level (in percent) to launch encryption.  If the battery charge is
    // lower than this, encryption should not be activated.
    private static final int MIN_BATTERY_LEVEL = 80;

    private Button mEncryptButton;
    private View mCableWarning;
    private View mBatteryWarning;
    private IntentFilter mIntentFilter;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                final int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                final int invalidCharger = intent.getIntExtra(
                    BatteryManager.EXTRA_INVALID_CHARGER, 0);

                final boolean levelOk = (level >= MIN_BATTERY_LEVEL);
                final boolean pluggedOk =
                        ((plugged & BatteryManager.BATTERY_PLUGGED_ANY) != 0) &&
                        (invalidCharger == 0);

                final boolean encryptOk = (levelOk && pluggedOk);
                mEncryptButton.setEnabled(encryptOk);
                mCableWarning.setVisibility(pluggedOk ? View.GONE : View.VISIBLE);
                mBatteryWarning.setVisibility(levelOk ? View.GONE : View.VISIBLE);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View contentView = getLayoutInflater().inflate(R.layout.encrypt_device, null);
        mEncryptButton = (Button) contentView.findViewById(R.id.accept_button);
        mCableWarning = contentView.findViewById(R.id.warning_unplugged);
        mBatteryWarning = contentView.findViewById(R.id.warning_low_charge);
        setContentView(contentView);

        mEncryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Bundle resumeInfo = getIntent().getBundleExtra(EXTRA_RESUME);
                BootReminder.setProvisioningReminder(EncryptDeviceActivity.this, resumeInfo);

                if (!encryptDevice()) {
                    setResult(RESULT_CANCELED, new Intent().putExtra(ENCRYPTION_ACTIVE_KEY, false));
                    finish();
                }
            }
        });

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(mIntentReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mIntentReceiver);
    }

    public static boolean isDeviceEncrypted() {
        IMountService mountService = IMountService.Stub.asInterface(
                ServiceManager.getService("mount"));

        try {
            return (mountService.getEncryptionState() == IMountService.ENCRYPTION_STATE_OK);
        } catch (RemoteException e) {
            return false;
        }
    }

    private static boolean encryptDevice() {
        IMountService mountService = IMountService.Stub.asInterface(
                ServiceManager.getService("mount"));

        try {
            int err = mountService.encryptStorage(StorageManager.CRYPT_TYPE_DEFAULT, "");
            if (err != 0) {
                throw new Exception("Error code " + err);
            }
        } catch (Exception e) {
            ProvisionLogger.loge("Unable to enable encryption: ", e);
            return false;
        }

        return true;
    }
}
