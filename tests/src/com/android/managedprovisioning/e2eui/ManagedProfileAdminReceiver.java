/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.managedprovisioning.e2eui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.util.Log;
import com.android.managedprovisioning.TestInstrumentationRunner;

import java.util.function.Supplier;

public class ManagedProfileAdminReceiver extends AbstractAdminReceiver {
    public static final ComponentName COMPONENT_NAME = new ComponentName(
            TestInstrumentationRunner.TEST_PACKAGE_NAME,
            ManagedProfileAdminReceiver.class.getName());

    public static final Intent INTENT_PROVISION_MANAGED_PROFILE = insertProvisioningExtras(
            new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                            COMPONENT_NAME)
    );

    private static final String TAG = ManagedProfileAdminReceiver.class.getSimpleName();

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        DevicePolicyManager dpm = getManager(context);
        dpm.setProfileEnabled(COMPONENT_NAME);

        boolean isProfileOwner = dpm.isProfileOwnerApp(context.getPackageName());
        Log.i(TAG, "isProfileOwner: " + isProfileOwner);

        boolean testResult = isProfileOwner && verifyProvisioningExtras(intent);
        Log.i(TAG, "testResult: " + testResult);

        sendResult(context, testResult);

        // cleanup, remove this user, kill this process.
        dpm.wipeData(0);
    }
}
