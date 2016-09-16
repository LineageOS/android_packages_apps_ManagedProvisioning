/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning.task;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deletes all non-required apps.
 *
 * This task may be run when a profile (both for managed device and managed profile) is created.
 * In that case the newProfile flag should be true.
 *
 * It should also be run after a system update with newProfile false. Note that only
 * newly installed system apps will be deleted.
 */
public class DeleteNonRequiredAppsTask extends AbstractProvisioningTask {
    private final PackageManager mPm;
    private final NonRequiredAppsHelper mNonRequiredAppsHelper;

    private int mUserId;

    public DeleteNonRequiredAppsTask(
            boolean firstTimeCreation,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(
                context,
                params,
                callback,
                new NonRequiredAppsHelper(context, params, firstTimeCreation));
    }

    @VisibleForTesting
    DeleteNonRequiredAppsTask(
            Context context,
            ProvisioningParams params,
            Callback callback,
            NonRequiredAppsHelper helper) {
        super(context, params, callback);

        mPm = checkNotNull(context.getPackageManager());
        mNonRequiredAppsHelper = checkNotNull(helper);
    }

    @Override
    public void run(int userId) {
        mUserId = userId;

        if (mNonRequiredAppsHelper.leaveSystemAppsEnabled()) {
            ProvisionLogger.logd("Not deleting non-required apps.");
            success();
            return;
        }
        ProvisionLogger.logd("Deleting non required apps.");

        final Set<String> packagesToDelete = mNonRequiredAppsHelper.getNonRequiredApps(userId);
        Set<String> newSystemApps = mNonRequiredAppsHelper.getNewSystemApps(userId);
        if (newSystemApps == null) {
            error(0);
            newSystemApps = Collections.emptySet();
        }

        packagesToDelete.retainAll(newSystemApps);
        removeNonInstalledPackages(packagesToDelete);

        if (packagesToDelete.isEmpty()) {
            success();
            return;
        }

        PackageDeleteObserver packageDeleteObserver =
                new PackageDeleteObserver(packagesToDelete.size());
        for (String packageName : packagesToDelete) {
            ProvisionLogger.logd("Deleting package [" + packageName + "] as user " + mUserId);
            mPm.deletePackageAsUser(packageName, packageDeleteObserver,
                    PackageManager.DELETE_SYSTEM_APP, mUserId);
        }
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_delete_non_required_apps;
    }

    /**
     * Remove all packages from the set that are not installed.
     */
    private void removeNonInstalledPackages(Set<String> packages) {
        Set<String> toBeRemoved = new HashSet<String>();
        for (String packageName : packages) {
            try {
                PackageInfo info = mPm.getPackageInfoAsUser(packageName, 0 /* default flags */,
                        mUserId);
                if (info == null) {
                    toBeRemoved.add(packageName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                toBeRemoved.add(packageName);
            }
        }
        packages.removeAll(toBeRemoved);
    }

    /**
     * Returns if this task should be run on OTA.
     * This is indicated by the presence of the system apps file.
     */
    public static boolean shouldDeleteNonRequiredApps(Context context, int userId) {
        return NonRequiredAppsHelper.getSystemAppsFile(context, userId).exists();
    }

    /**
     * Runs the next task when all packages have been deleted or shuts down the activity if package
     * deletion fails.
     */
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        private final AtomicInteger mPackageCount = new AtomicInteger(0);

        public PackageDeleteObserver(int packageCount) {
            this.mPackageCount.set(packageCount);
        }

        @Override
        public void packageDeleted(String packageName, int returnCode) {
            if (returnCode != PackageManager.DELETE_SUCCEEDED) {
                ProvisionLogger.logw(
                        "Could not finish the provisioning: package deletion failed");
                error(0);
                return;
            }
            int currentPackageCount = mPackageCount.decrementAndGet();
            if (currentPackageCount == 0) {
                ProvisionLogger.logi("All non-required system apps with launcher icon, "
                        + "and all disallowed apps have been uninstalled.");
                success();
            }
        }
    }
}
