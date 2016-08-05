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

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Task to create a managed profile.
 */
public class CreateManagedProfileTask extends AbstractProvisioningTask {

    private int mProfileUserId;

    public CreateManagedProfileTask(Context context, ProvisioningParams params, Callback callback) {
        super(context, params, callback);
    }

    @Override
    public void run(int userId) {
        UserManager um = mContext.getSystemService(UserManager.class);
        UserInfo userInfo = um.createProfileForUser(
                mContext.getString(R.string.default_managed_profile_name),
                UserInfo.FLAG_MANAGED_PROFILE | UserInfo.FLAG_DISABLED,
                userId);
        if (userInfo == null) {
            error(0);
            return;
        }
        mProfileUserId = userInfo.id;
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
