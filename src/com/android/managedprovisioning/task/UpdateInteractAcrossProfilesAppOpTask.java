/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.content.pm.CrossProfileApps;
import android.util.ArraySet;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.interactacrossprofiles.CrossProfileAppsSnapshot;

import java.util.Set;

/**
 * Task which resets the {@code INTERACT_ACROSS_USERS} app op when the OEM whitelist is changed.
 */
public class UpdateInteractAcrossProfilesAppOpTask extends AbstractProvisioningTask {

    private final CrossProfileAppsSnapshot mCrossProfileAppsSnapshot;
    private final CrossProfileApps mCrossProfileApps;
    private final ManagedProvisioningSharedPreferences mManagedProvisioningSharedPreferences;

    public UpdateInteractAcrossProfilesAppOpTask(Context context,
            ProvisioningParams provisioningParams,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker) {
        super(context, provisioningParams, callback, provisioningAnalyticsTracker);
        mCrossProfileAppsSnapshot = new CrossProfileAppsSnapshot(context);
        mCrossProfileApps = context.getSystemService(CrossProfileApps.class);
        mManagedProvisioningSharedPreferences = new ManagedProvisioningSharedPreferences(context);
    }

    @Override
    public void run(int userId) {
        Set<String> previousCrossProfileApps =
                mCrossProfileAppsSnapshot.hasSnapshot(userId) ?
                        mCrossProfileAppsSnapshot.getSnapshot(userId) :
                        new ArraySet<>();
        mCrossProfileAppsSnapshot.takeNewSnapshot(userId);
        Set<String> currentCrossProfileApps = mCrossProfileAppsSnapshot.getSnapshot(userId);

        if (previousCrossProfileApps.isEmpty()) {
            return;
        }

        updateAfterOtaChanges(previousCrossProfileApps, currentCrossProfileApps);
    }

    private void updateAfterOtaChanges(
            Set<String> previousCrossProfileApps, Set<String> currentCrossProfileApps) {
        mCrossProfileApps.resetInteractAcrossProfilesAppOps(
                previousCrossProfileApps, currentCrossProfileApps);
    }

    @Override
    public int getStatusMsgId() {
        return 0;
    }
}
