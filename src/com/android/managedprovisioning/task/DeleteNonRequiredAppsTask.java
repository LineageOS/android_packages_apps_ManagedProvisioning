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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Removes all apps with a launcher that are not required.
 * There are two decendants of this class:
 * {@link DeleteNonRequiredAppsTask} for profile owner provisioning,
 * and {@link DeleteNonRequiredAppsTask} for device owner provisioning.
 * The decendents specify which apps are required.
 */
public class DeleteNonRequiredAppsTask {
    private final Callback mCallback;
    private final Context mContext;
    private final IPackageManager mIpm;
    private final String mMdmPackageName;
    private final PackageManager mPm;
    private final int mReqAppsList;
    private final int mVendorReqAppsList;
    private final int mUserId;

    public DeleteNonRequiredAppsTask(Context context, String mdmPackageName, int userId,
            int requiredAppsList, int vendorRequiredAppsList, Callback callback) {
        mCallback = callback;
        mContext = context;
        mMdmPackageName = mdmPackageName;
        mUserId = userId;
        mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        mPm = context.getPackageManager();
        mReqAppsList = requiredAppsList;
        mVendorReqAppsList = vendorRequiredAppsList;
    }

    public void run() {
        ProvisionLogger.logd("Disabling non required components.");
        List<ComponentName> toDisable = new ArrayList<ComponentName>();

        // Adding sharing via nfc and bluetooth to the list
        toDisable.add(new ComponentName("com.android.nfc", "com.android.nfc.BeamShareActivity"));
        toDisable.add(new ComponentName("com.android.bluetooth",
                "com.android.bluetooth.opp.BluetoothOppLauncherActivity"));

        // Adding the components that listen to INSTALL_SHORTCUT to the list
        Intent installShortcut = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        List<ResolveInfo> receivers = mPm.queryBroadcastReceivers(installShortcut, 0, mUserId);
        for (ResolveInfo ri : receivers) {
            // One of ri.activityInfo, ri.serviceInfo, ri.providerInfo is not null. Let's find which
            // one.
            ComponentInfo ci;
            if (ri.activityInfo != null) {
                ci = ri.activityInfo;
            } else if (ri.serviceInfo != null) {
                ci = ri.serviceInfo;
            } else {
                ci = ri.providerInfo;
            }
            toDisable.add(new ComponentName(ci.packageName, ci.name));
        }
        try {
            for (ComponentName cn : toDisable) {
                mIpm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP, mUserId);
            }
        } catch (RemoteException neverThrown) {
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }

        ProvisionLogger.logd("Deleting non required apps.");
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherActivities = mPm.queryIntentActivitiesAsUser(
                launcherIntent, PackageManager.NO_CROSS_PROFILE, mUserId);

        Set<String> requiredApps = getRequiredApps();

        Set<String> packagesToDelete = new HashSet<String>();
        for (ResolveInfo activity : launcherActivities) {
            String packageName = activity.activityInfo.packageName;
            if (!requiredApps.contains(packageName)) {
                packagesToDelete.add(packageName);
            }
        }

        PackageDeleteObserver packageDeleteObserver =
                    new PackageDeleteObserver(packagesToDelete.size());
        for (String packageName : packagesToDelete) {
            try {
                mIpm.deletePackageAsUser(packageName, packageDeleteObserver, mUserId,
                        PackageManager.DELETE_SYSTEM_APP);
            } catch (RemoteException neverThrown) {
                // Never thrown, as we are making local calls.
                ProvisionLogger.loge("This should not happen.", neverThrown);
            }
        }
    }

    protected Set<String> getRequiredApps() {
        HashSet<String> requiredApps = new HashSet<String> (Arrays.asList(
                        mContext.getResources().getStringArray(mReqAppsList)));
        requiredApps.addAll(Arrays.asList(
                        mContext.getResources().getStringArray(mVendorReqAppsList)));
        requiredApps.add(mMdmPackageName);
        return requiredApps;
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
                        "Could not finish managed profile provisioning: package deletion failed");
                mCallback.onError();
            }
            int currentPackageCount = mPackageCount.decrementAndGet();
            if (currentPackageCount == 0) {
                ProvisionLogger.logi("All non-required system apps have been uninstalled.");
                mCallback.onSuccess();
            }
        }
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
