/*
 * Copyright 2019, The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;

import com.android.managedprovisioning.common.Utils;

/**
 * A helper class for the provisioning operation in primary profile after PO provisioning is done,
 * including removing the migrated account from primary profile, and sending
 * ACTION_MANAGED_PROFILE_PROVISIONED broadcast to the DPC in the primary profile.
 */
class PrimaryProfileFinalizationHelper {

    private final Account mMigratedAccount;
    private final String mMdmPackageName;
    private final boolean mKeepAccountMigrated;
    private final Utils mUtils;
    private final UserHandle mManagedUserHandle;
    private final boolean mIsAdminIntegratedFlow;

    PrimaryProfileFinalizationHelper(Account migratedAccount, boolean keepAccountMigrated,
        UserHandle managedUserHandle, String mdmPackageName, Utils utils,
        boolean isAdminIntegratedFlow) {
        mMigratedAccount = migratedAccount;
        mKeepAccountMigrated = keepAccountMigrated;
        mMdmPackageName = checkNotNull(mdmPackageName);
        mManagedUserHandle = checkNotNull(managedUserHandle);
        mUtils = checkNotNull(utils);
        mIsAdminIntegratedFlow = isAdminIntegratedFlow;
    }

    void finalizeProvisioningInPrimaryProfile(Context context,
            DpcReceivedSuccessReceiver.Callback callback) {
        // Now cleanup the primary profile if necessary
        if (mMigratedAccount != null) {
            finishAccountMigration(context, callback);
            // Note that we currently do not check if account migration worked
        } else {
            handleFinalization(context, callback);
        }
    }

    private void handleFinalization(Context context, DpcReceivedSuccessReceiver.Callback callback) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        devicePolicyManager.finalizeWorkProfileProvisioning(
                mManagedUserHandle, mMigratedAccount);
        if (callback != null) {
            callback.cleanup();
        }
    }

    private void finishAccountMigration(final Context context,
            DpcReceivedSuccessReceiver.Callback callback) {
        if (!mKeepAccountMigrated) {
            mUtils.removeAccountAsync(context, mMigratedAccount, () -> {
                handleFinalization(context, callback);
            });
        } else {
            handleFinalization(context, callback);
        }
    }
}
