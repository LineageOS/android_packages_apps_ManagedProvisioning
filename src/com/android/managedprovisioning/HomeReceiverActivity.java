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
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;

import android.accounts.Account;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.managedprovisioning.Utils.IllegalProvisioningArgumentException;

/*
 * This class is used to make sure that we start the mdm after we shut the Setup wizard down.
 * The shut down of the Setup wizard is initiated in the DeviceOwnerProvisioningActivity or
 * ProfileOwnerProvisioningActivity by setting Global.DEVICE_PROVISIONED. This will cause the
 * Setup wizard to shut down and send a HOME intent. Instead of opening the home screen we want
 * to open the mdm, so the HOME intent is caught by this activity instead which will send the
 * ACTION_PROFILE_PROVISIONING_COMPLETE to the mdm, which will then open up.
 */

public class HomeReceiverActivity extends Activity {

    private ProvisioningParams mParams;

    private static final String HOME_RECEIVER_INTENT_STORE_NAME = "home-receiver";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            finalizeProvisioning();
        } finally {
            // Disable the HomeReceiverActivity. Make sure this is always called to prevent an
            // infinite loop of HomeReceiverActivity capturing HOME intent in case something fails.
            disableComponent(getMyComponent(this));
        }
        finish();
    }

    public static void setReminder(ProvisioningParams params, Context context) {
        Intent intent = new MessageParser().getIntentFromProvisioningParams(params);
        getIntentStore(context).save(intent);

        // Enable the HomeReceiverActivity, since the ProfileOwnerProvisioningActivity will
        // shutdown the Setup wizard soon, which will result in a home intent that should be
        // caught by the HomeReceiverActivity.
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(getMyComponent(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    private void finalizeProvisioning() {
        IntentStore intentStore = getIntentStore(this);
        Intent intent = intentStore.load();
        if (intent == null) {
            ProvisionLogger.loge("Fail to retrieve ProvisioningParams from intent store.");
            return;
        }
        intentStore.clear();
        mParams = loadProvisioningParamsFromIntent(intent);
        if (mParams != null) {
            String action = intent.getAction();
            Intent provisioningCompleteIntent = getProvisioningCompleteIntent();
            if (provisioningCompleteIntent == null) {
                return;
            }
            if (action.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
                // For the managed profile owner case, we need to send the provisioning complete
                // intent to the mdm. Once it has been received, we'll send
                // ACTION_MANAGED_PROFILE_PROVISIONED in the parent.
                finalizeManagedProfileOwnerProvisioning(provisioningCompleteIntent);
            } else {
                // For managed user and device owner, we just need to send the provisioning complete
                // intent to the mdm.
                sendBroadcast(provisioningCompleteIntent);
            }
        }
    }

    private void finalizeManagedProfileOwnerProvisioning(Intent provisioningCompleteIntent) {
        UserHandle managedUserHandle = Utils.getManagedProfile(this);
        if (managedUserHandle == null) {
            ProvisionLogger.loge("Failed to retrieve the userHandle of the managed profile.");
            return;
        }
        BroadcastReceiver mdmReceivedSuccessReceiver = new MdmReceivedSuccessReceiver(
                mParams.accountToMigrate, mParams.deviceAdminPackageName);

        sendOrderedBroadcastAsUser(provisioningCompleteIntent, managedUserHandle, null,
                mdmReceivedSuccessReceiver, null, Activity.RESULT_OK, null, null);
    }

    private Intent getProvisioningCompleteIntent() {
        Intent intent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        try {
            intent.setComponent(mParams.inferDeviceAdminComponentName(this));
        } catch (Utils.IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failed to infer the device admin component name", e);
            return null;
        }
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
        if (mParams.adminExtrasBundle != null) {
            intent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, mParams.adminExtrasBundle);
        }
        return intent;
    }

    private ProvisioningParams loadProvisioningParamsFromIntent(Intent intent) {
        ProvisioningParams params = null;
        try {
            params = (new MessageParser()).parseNonNfcIntent(intent, this, true /* trusted */);
        } catch (IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failed to parse provisioning intent", e);
        }
        return params;
    }

    private void disableComponent(ComponentName component) {
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private static ComponentName getMyComponent(Context context) {
        return new ComponentName(context, HomeReceiverActivity.class);
    }

    private static IntentStore getIntentStore(Context context) {
        return new IntentStore(context, HOME_RECEIVER_INTENT_STORE_NAME);
    }
}
