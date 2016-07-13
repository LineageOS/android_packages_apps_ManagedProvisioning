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

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.UserHandle;

public abstract class AbstractAdminReceiver extends DeviceAdminReceiver {

    private static final String EXTRAS_BUNDLE_TEST_KEY = "extras_bundle_test_key";

    protected static Intent insertProvisioningExtras(Intent intent) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(EXTRAS_BUNDLE_TEST_KEY, true);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, bundle);
        return intent;
    }

    protected static boolean verifyProvisioningExtras(Intent intent) {
        PersistableBundle persistableBundle = intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        return persistableBundle != null && persistableBundle.getBoolean(EXTRAS_BUNDLE_TEST_KEY);
    }

    protected static void sendResult(Context context, boolean result) {
        Intent resultBroadcast = new Intent(ProvisioningResultListener.ACTION_PROVISION_RESULT);
        resultBroadcast.putExtra(ProvisioningResultListener.EXTRA_RESULT, result);
        context.sendBroadcastAsUser(resultBroadcast, UserHandle.SYSTEM);
    }
}
