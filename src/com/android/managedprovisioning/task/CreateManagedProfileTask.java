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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.Set;

/**
 * Task to create a managed profile.
 */
public class CreateManagedProfileTask extends AbstractProvisioningTask {

    private int mProfileUserId;
    private final NonRequiredAppsHelper mNonRequiredAppsHelper;
    private final UserManager mUserManager;

    public CreateManagedProfileTask(Context context,
            ProvisioningParams params, NonRequiredAppsHelper helper, Callback callback) {
        this(context, params, helper, callback,
                context.getSystemService(UserManager.class));
    }

    @VisibleForTesting
    CreateManagedProfileTask(Context context, ProvisioningParams params,
            NonRequiredAppsHelper helper, Callback callback, UserManager userManager) {
        super(context, params, callback);
        mNonRequiredAppsHelper = checkNotNull(helper);
        mUserManager = checkNotNull(userManager);
    }

    @Override
    public void run(int userId) {
        final Set<String> nonRequiredApps = mNonRequiredAppsHelper.getNonRequiredApps(userId);
        UserInfo userInfo = mUserManager.createProfileForUserEvenWhenDisallowed(
                mContext.getString(R.string.default_managed_profile_name),
                UserInfo.FLAG_MANAGED_PROFILE | UserInfo.FLAG_DISABLED,
                userId, nonRequiredApps.toArray(new String[nonRequiredApps.size()]));
        if (userInfo == null) {
            error(0);
            return;
        }
        mProfileUserId = userInfo.id;
        // When OTA occurs, we need to compute the non-required apps and delete them. And for
        // that we need to know the set of system apps prior to OTA. So, save the current system
        // apps to a file.
        mNonRequiredAppsHelper.writeCurrentSystemAppsIfNeeded(mProfileUserId);
        success();
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_initialize;
    }

    public int getProfileUserId() {
        return mProfileUserId;
    }
}
