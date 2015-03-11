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

package com.android.managedprovisioning.task;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;

import com.android.managedprovisioning.ProvisionLogger;

public class SetDevicePolicyTask {
    public static final int ERROR_PACKAGE_NOT_INSTALLED = 0;
    public static final int ERROR_NO_RECEIVER = 1;
    public static final int ERROR_OTHER = 2;

    private final Callback mCallback;
    private final Context mContext;
    private String mAdminPackage;
    private ComponentName mAdminComponent;
    private final String mOwner;

    private PackageManager mPackageManager;
    private DevicePolicyManager mDevicePolicyManager;

    public SetDevicePolicyTask(Context context, String owner, Callback callback) {
        mCallback = callback;
        mContext = context;
        mOwner = owner;
        mPackageManager = mContext.getPackageManager();
        mDevicePolicyManager = (DevicePolicyManager) mContext.
                getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public void run(ComponentName adminComponent) {
        mAdminComponent = adminComponent;
        mAdminPackage = mAdminComponent.getPackageName();
        enableDevicePolicyApp();
        setActiveAdmin();
        setDeviceOwner();
        mCallback.onSuccess();
    }

    private void enableDevicePolicyApp() {
        int enabledSetting = mPackageManager
                .getApplicationEnabledSetting(mAdminPackage);
        if (enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            mPackageManager.setApplicationEnabledSetting(mAdminPackage,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0);
        }
    }

    public void setActiveAdmin() {
        ProvisionLogger.logd("Setting " + mAdminComponent + " as active admin.");
        mDevicePolicyManager.setActiveAdmin(mAdminComponent, true);
    }

    public void setDeviceOwner() {
        ProvisionLogger.logd("Setting " + mAdminPackage + " as device owner " + mOwner + ".");
        if (!mDevicePolicyManager.isDeviceOwner(mAdminPackage)) {
            mDevicePolicyManager.setDeviceOwner(mAdminPackage, mOwner);
        }
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError(int errorCode);
    }
}
