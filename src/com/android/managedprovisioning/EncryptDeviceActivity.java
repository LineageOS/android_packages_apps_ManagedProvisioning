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

import com.android.setupwizard.navigationbar.SetupWizardNavBar;
import com.android.setupwizard.navigationbar.SetupWizardNavBar.NavigationBarListener;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.RemoteException;
import android.view.View;

/**
 * Activity to ask for permission to activate full-filesystem encryption.
 *
 * Pressing 'settings' will launch settings to prompt the user to encrypt
 * the device.
 */
public class EncryptDeviceActivity extends Activity implements NavigationBarListener {
    protected static final String EXTRA_RESUME = "com.android.managedprovisioning.RESUME";
    protected static final String EXTRA_RESUME_TARGET =
            "com.android.managedprovisioning.RESUME_TARGET";
    protected static final String TARGET_PROFILE_OWNER = "profile_owner";
    protected static final String TARGET_DEVICE_OWNER = "device_owner";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View contentView = getLayoutInflater().inflate(R.layout.encrypt_device, null);
        setContentView(contentView);
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

    @Override
    public void onNavigationBarCreated(SetupWizardNavBar bar) {
        bar.getNextButton().setText(R.string.encrypt_device_launch_settings);
    }

    @Override
    public void onNavigateBack() {
        onBackPressed();
    }

    @Override
    public void onNavigateNext() {
        final Bundle resumeInfo = getIntent().getBundleExtra(EXTRA_RESUME);
        BootReminder.setProvisioningReminder(EncryptDeviceActivity.this, resumeInfo);
        // Use settings so user confirms password/pattern and its passed
        // to encryption tool.
        Intent intent = new Intent();
        intent.setAction(DevicePolicyManager.ACTION_START_ENCRYPTION);
        startActivity(intent);
    }
}
