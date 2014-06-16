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

import com.android.internal.widget.LockPatternUtils;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;

/**
 * Activity to ask for permission to activate full-filesystem encryption.
 *
 * Pressing 'restart' will leave the screen as it is and start encrypting
 */
public class EncryptDeviceActivity extends Activity {
    protected static final String EXTRA_RESUME = "com.android.managedprovisioning.RESUME";
    protected static final String EXTRA_RESUME_TARGET =
            "com.android.managedprovisioning.RESUME_TARGET";
    protected static final String TARGET_PROFILE_OWNER = "profile_owner";
    protected static final String TARGET_DEVICE_OWNER = "device_owner";

    protected static final String ENCRYPTION_ACTIVE_KEY = "encryptionActiveKey";

    // Minimum battery charge level (in percent) to launch encryption.  If the battery charge is
    // lower than this, encryption should not be activated.
    private static final int MIN_BATTERY_LEVEL = 80;

    private Button mCancelButton;
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
        mCancelButton = (Button) contentView.findViewById(R.id.cancel_button);
        mCableWarning = contentView.findViewById(R.id.warning_unplugged);
        mBatteryWarning = contentView.findViewById(R.id.warning_low_charge);
        setContentView(contentView);

        if (passwordOrPatternSet()) {
            mEncryptButton.setText(R.string.encrypt_device_launch_settings);
        } else {
            mEncryptButton.setText(R.string.encrypt_device_confirm);
        }

        mEncryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Bundle resumeInfo = getIntent().getBundleExtra(EXTRA_RESUME);
                BootReminder.setProvisioningReminder(EncryptDeviceActivity.this, resumeInfo);
                // TODO intent to settings for both cases when we have a nicer UI.
                if (passwordOrPatternSet()) {
                    // Use settings so user confirms password/pattern and its passed
                    // to encryption tool.
                    Intent intent = new Intent();
                    intent.setAction(DevicePolicyManager.ACTION_START_ENCRYPTION);
                    startActivity(intent);
                    finish();
                    // If user doesn't actually encrypt but presses back or starts
                    // flow again then we will check again if device is encrypted.
                } else {
                    // We didn't need a confirmation so just encrypt with no password.
                    if (!encryptDeviceEmptyPassword()) {
                        // Failed to encrypt
                        setResult(RESULT_CANCELED, new Intent().putExtra(ENCRYPTION_ACTIVE_KEY,
                                false));
                        finish();
                    }
                }
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
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

    private static boolean encryptDeviceEmptyPassword() {
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

    /**
     * @return true if the user has an existing password or pattern set.
     */
    private boolean passwordOrPatternSet() {
        LockPatternUtils lockPatternUtils = new LockPatternUtils(this);
        switch (lockPatternUtils.getKeyguardStoredPasswordQuality()) {
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                if (lockPatternUtils.isLockPatternEnabled()
                        && lockPatternUtils.savedPatternExists()) {
                    return true;
                }
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                if (lockPatternUtils.isLockPasswordEnabled()) {
                    return true;
                }
        }
        return false;
    }
}
