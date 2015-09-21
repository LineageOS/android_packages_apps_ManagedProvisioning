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

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.Utils;

/**
 * This tasks sets a given component as the owner of the device.
 */
public class SetDevicePolicyTask {
    public static final int ERROR_PACKAGE_NOT_INSTALLED = 0;
    public static final int ERROR_NO_RECEIVER = 1;
    public static final int ERROR_OTHER = 2;

    private final Callback mCallback;
    private final Context mContext;
    private String mAdminPackage;
    private ComponentName mAdminComponent;
    private final String mOwnerName;

    private PackageManager mPackageManager;
    private DevicePolicyManager mDevicePolicyManager;

    public SetDevicePolicyTask(Context context, String ownerName, Callback callback) {
        mCallback = callback;
        mContext = context;
        mOwnerName = ownerName;

        mPackageManager = mContext.getPackageManager();
        mDevicePolicyManager = (DevicePolicyManager) mContext.
                getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public void run(ComponentName adminComponent) {
        try {
            mAdminComponent = adminComponent;
            mAdminPackage = mAdminComponent.getPackageName();

            enableDevicePolicyApp(mAdminPackage);
            setActiveAdmin(mAdminComponent);
            setDeviceOwner(mAdminPackage, mOwnerName);
        } catch (Exception e) {
            ProvisionLogger.loge("Failure setting device owner", e);
            mCallback.onError(ERROR_OTHER);
            return;
        }

        mCallback.onSuccess();
    }

    private void enableDevicePolicyApp(String packageName) {
        int enabledSetting = mPackageManager.getApplicationEnabledSetting(packageName);
        if (enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            mPackageManager.setApplicationEnabledSetting(packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    // Device policy app may have launched ManagedProvisioning, play nice and don't
                    // kill it as a side-effect of this call.
                    PackageManager.DONT_KILL_APP);
        }
    }

    public void setActiveAdmin(ComponentName component) {
        ProvisionLogger.logd("Setting " + component + " as active admin.");
        mDevicePolicyManager.setActiveAdmin(component, true);
    }

    public void setDeviceOwner(String packageName, String owner) {
        ProvisionLogger.logd("Setting " + packageName + " as device owner " + owner + ".");
        if (!mDevicePolicyManager.isDeviceOwner(packageName)) {
            mDevicePolicyManager.setDeviceOwner(packageName, owner);
        }
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError(int errorCode);
    }
}
