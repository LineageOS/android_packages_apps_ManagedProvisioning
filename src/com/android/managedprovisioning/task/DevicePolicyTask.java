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

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.android.managedprovisioning.Preferences;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.DeviceOwnerProvisioningActivity.ProvisioningState;

/**
 * Enables the specified device policy package and sets it as the device owner and as an active
 * admin.
 */
//TODO Add download location support rather than pre-installed package.
public class DevicePolicyTask extends ProvisionTask {

    private ComponentName mDeviceManagementAgentComponentName;
    private DevicePolicyManager mDevicePolicyManager;

    private String mPkg;

    public DevicePolicyTask() {
        super("Device policy task");
    }

    @Override
    public void executeTask(String... params) {
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        findDevicePolicyComponentName();

        String owner = mTaskManager.getPreferences().getStringProperty(Preferences.OWNER_KEY);
        ProvisionLogger.logi("Setting " + owner + " as device owner.");
        setAsDeviceOwner(owner);

        installDevicePolicy();

        mTaskManager.registerProvisioningState(ProvisioningState.REGISTERED_DEVICE_POLICY, "");
        onSuccess();
    }

    private void findDevicePolicyComponentName() {
        mPkg = mTaskManager.getPreferences().getStringProperty(Preferences.MDM_PACKAGE_KEY);
        String adminReceiver =
                mTaskManager.getPreferences().getStringProperty(Preferences.MDM_ADMIN_RECEIVER_KEY);
        if (!TextUtils.isEmpty(adminReceiver) && adminReceiver.startsWith(".")) {
            adminReceiver = mPkg + adminReceiver;
        }
        mDeviceManagementAgentComponentName = new ComponentName(mPkg, adminReceiver);
    }

    public void installDevicePolicy() {
        ProvisionLogger.logi("installing device policy.");
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mPkg);
        if (intent != null) {
            enableDevicePolicyApp();
            // Register the DMAgent as a device administrator
            mDevicePolicyManager.setActiveAdmin(mDeviceManagementAgentComponentName, true);
        }
    }

    private void enableDevicePolicyApp() {
        PackageManager packageManager = mContext.getPackageManager();
        int enabledSetting = packageManager.getApplicationEnabledSetting(mPkg);
        if (enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            packageManager.setApplicationEnabledSetting(mPkg,
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            0);
        }
    }

    /**
     * This should be called once before the device is provisioned.
     */
    public void setAsDeviceOwner(String owner) {
        enableDevicePolicyApp();
        if (!mDevicePolicyManager.isDeviceOwner(mPkg)) {
            mDevicePolicyManager.setDeviceOwner(mPkg, owner);
            ProvisionLogger.logi("Setting device policy as DeviceOwner");
        }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void hasFailed() {

    }
}
