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
package com.android.managedprovisioning.uiflows;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.view.View;
import android.widget.TextView;

import com.android.managedprovisioning.BootReminder;
import com.android.managedprovisioning.MessageParser;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.SetupLayoutActivity;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Activity to ask for permission to activate full-filesystem encryption.
 *
 * Pressing 'settings' will launch settings to prompt the user to encrypt
 * the device.
 */
public class EncryptDeviceActivity extends SetupLayoutActivity {
    private Intent mResumeIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisioningParams params = (ProvisioningParams) getIntent().getParcelableExtra(
                ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        if (params == null) {
            ProvisionLogger.loge("Missing params in EncryptDeviceActivity activity");
            finish();
        }

        if (mUtils.isProfileOwnerAction(params.provisioningAction)) {
            initializeLayoutParams(R.layout.encrypt_device, R.string.setup_work_profile, false);
            setTitle(R.string.setup_profile_encryption);
            ((TextView) findViewById(R.id.encrypt_main_text)).setText(
                    R.string.encrypt_device_text_for_profile_owner_setup);
        } else if (mUtils.isDeviceOwnerAction(params.provisioningAction)) {
            initializeLayoutParams(R.layout.encrypt_device, R.string.setup_work_device, false);
            setTitle(R.string.setup_device_encryption);
            ((TextView) findViewById(R.id.encrypt_main_text)).setText(
                    R.string.encrypt_device_text_for_device_owner_setup);
        } else {
            ProvisionLogger.loge("Unknown provisioning action: " + params.provisioningAction);
            finish();
        }

        configureNavigationButtons(R.string.encrypt_device_launch_settings,
            View.VISIBLE, View.VISIBLE);
        maybeSetLogoAndMainColor(params.mainColor);

        mResumeIntent = new MessageParser().getIntentFromProvisioningParams(params);
    }

    @Override
    public void onNavigateNext() {
        BootReminder.setProvisioningReminder(this, mResumeIntent);
        // Use settings so user confirms password/pattern and its passed
        // to encryption tool.
        Intent intent = new Intent();
        intent.setAction(DevicePolicyManager.ACTION_START_ENCRYPTION);
        startActivity(intent);
    }
}
