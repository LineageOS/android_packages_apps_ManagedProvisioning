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

package com.android.managedprovisioning.task;

import static android.provider.Settings.Secure.MANAGED_PROFILE_CONTACT_REMOTE_SEARCH;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import com.android.managedprovisioning.CrossProfileIntentFiltersHelper;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

public class ManagedProfileSettingsTask extends AbstractProvisioningTask {

    public ManagedProfileSettingsTask(
            Context context,
            ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);
    }

    @Override
    public void run(int userId) {
        // Turn on managed profile contacts remote search.
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                MANAGED_PROFILE_CONTACT_REMOTE_SEARCH,
                1, userId);

        // Disable managed profile wallpaper access
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        um.setUserRestriction(UserManager.DISALLOW_WALLPAPER, true, UserHandle.of(userId));

        // Set the main color of managed provisioning from the provisioning params
        if (mProvisioningParams.mainColor != null) {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            dpm.setOrganizationColorForUser(mProvisioningParams.mainColor, userId);
        }

        CrossProfileIntentFiltersHelper.setFilters(
                mContext.getPackageManager(), UserHandle.myUserId(), userId);

        // always mark managed profile setup as completed
        markUserSetupComplete(userId);

        success();
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_finishing_touches;
    }

    /**
     * Sets user setup complete on a given user.
     *
     * <p>This will set USER_SETUP_COMPLETE to 1 on the given user.
     */
    private void markUserSetupComplete(int userId) {
        ProvisionLogger.logd("Setting USER_SETUP_COMPLETE to 1 for user " + userId);
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 1, userId);
    }
}
