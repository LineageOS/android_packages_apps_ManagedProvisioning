/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.finalization;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.content.Intent.ACTION_USER_UNLOCKED;
import android.os.UserHandle;
import android.content.Context;
import android.content.Intent;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import com.android.managedprovisioning.model.ProvisioningParams;
import android.os.UserManager;
import com.android.managedprovisioning.common.Utils;
import android.content.IntentFilter;


/**
 * This class is used to make sure that we start the MDM after we shut the setup wizard down.
 * The shut down of the setup wizard is initiated in the
 * {@link com.android.managedprovisioning.provisioning.ProvisioningActivity} by calling
 * {@link DevicePolicyManager#setUserProvisioningState(int, int)}. This will cause the
 * Setup wizard to shut down and send a ACTION_PROVISIONING_FINALIZATION intent. This intent is
 * caught by this receiver instead which will send the
 * ACTION_PROFILE_PROVISIONING_COMPLETE broadcast to the MDM, which can then present it's own
 * activities.
 */
public class FinalizationActivity extends Activity {

    private boolean mIsReceiverRegistered;
    private FinalizationController mFinalizationController;

    private final BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                return;
            }
            int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, /* defaultValue= */ -1);
            UserManager userManager = getSystemService(UserManager.class);
            if (!userManager.isManagedProfile(userId)) {
                return;
            }
            unregisterUserUnlockedReceiver();
            tryFinalizeProvisioning();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFinalizationController = new FinalizationController(this);
        ProvisioningParams params = mFinalizationController.getProvisioningParams();

        registerUserUnlockedReceiver();
        UserManager userManager = getSystemService(UserManager.class);
        Utils utils = new Utils();
        if (!params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)
                || userManager.isUserUnlocked(utils.getManagedProfile(this))) {
            unregisterUserUnlockedReceiver();
            tryFinalizeProvisioning();
        }
    }

    private void tryFinalizeProvisioning() {
        mFinalizationController.provisioningFinalized();
        finish();
    }

    private void registerUserUnlockedReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USER_UNLOCKED);
        registerReceiverAsUser(
                mUserUnlockedReceiver,
                UserHandle.ALL,
                filter,
                /* broadcastPermission= */ null,
                /* scheduler= */ null);
        mIsReceiverRegistered = true;
    }

    private void unregisterUserUnlockedReceiver() {
        if (!mIsReceiverRegistered) {
            return;
        }
        unregisterReceiver(mUserUnlockedReceiver);
        mIsReceiverRegistered = false;
    }

    @Override
    public final void onDestroy() {
        unregisterUserUnlockedReceiver();
        super.onDestroy();
    }
}
