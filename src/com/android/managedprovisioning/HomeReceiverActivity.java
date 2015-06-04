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

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.android.managedprovisioning.Utils.IllegalProvisioningArgumentException;

/*
 * This class is used to make sure that we start the mdm after we shut the Setup wizard down.
 * The shut down of the Setup wizard is initiated in the DeviceOwnerProvisioningActivity by setting
 * Global.DEVICE_PROVISIONED. This will cause the Setup wizard to shut down and send a HOME intent.
 * Instead of opening the home screen we want to open the mdm, so the HOME intent is caught by this
 * activity instead, which notifies the DeviceOwnerProvisioningService to send the
 * ACTION_PROFILE_PROVISIONING_COMPLETE to the mdm, which will then open up.
 */

public class HomeReceiverActivity extends Activity {
    @Override
   public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            finalizeProvisioning();
            stopService(new Intent(this, DeviceOwnerProvisioningService.class));
        } finally {
            // Disable the HomeReceiverActivity. Make sure this is always called to prevent an
            // infinite loop of HomeReceiverActivity capturing HOME intent in case something fails.
            disableComponent(new ComponentName(this, HomeReceiverActivity.class));
        }
        finish();
    }

    private void finalizeProvisioning() {
        IntentStore store = BootReminder.getDeviceOwnerFinalizingIntentStore(this);
        Intent intent = store.load();
        if (intent == null) {
            ProvisionLogger.loge("Fail to retrieve ProvisioningParams from intent store.");
            return;
        }
        store.clear();
        ProvisioningParams mParams;
        try {
            MessageParser parser = new MessageParser();
            mParams = parser.parseNonNfcIntent(intent);
        } catch (IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failed to parse provisioning intent", e);
            return;
        }

        // Disable the Device Initializer component, if it exists, in case it did not do so itself.
        if(mParams.deviceInitializerComponentName != null) {
            DevicePolicyManager devicePolicyManager =
                    (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            devicePolicyManager.removeActiveAdmin(mParams.deviceInitializerComponentName);
            disableComponent(mParams.deviceInitializerComponentName);
        }

        // Finalizing provisioning: send complete intent to mdm.
        Intent result = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        try {
            result.setComponent(mParams.inferDeviceAdminComponentName(this));
        } catch (Utils.IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failed to infer the device admin component name", e);
            return;
        }
        result.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
                Intent.FLAG_RECEIVER_FOREGROUND);
        if (mParams.adminExtrasBundle != null) {
            result.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                    mParams.adminExtrasBundle);
        }
        sendBroadcast(result);
    }

    private void disableComponent(ComponentName component) {
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
