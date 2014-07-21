package com.android.managedprovisioning.task;

import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        for (String packageName : packagesToDelete) {
            try {
                mIpm.setApplicationHiddenSettingAsUser(packageName, true, mUserId);
            } catch (RemoteException neverThrown) {
                // Never thrown, as we are making local calls.
                ProvisionLogger.loge("This should not happen.", neverThrown);
                mCallback.onError();
            }
        }

        mCallback.onSuccess();
    }

    protected Set<String> getRequiredApps() {
        HashSet<String> requiredApps = new HashSet<String> (Arrays.asList(
                        mContext.getResources().getStringArray(mReqAppsList)));
        requiredApps.addAll(Arrays.asList(
                        mContext.getResources().getStringArray(mVendorReqAppsList)));
        requiredApps.add(mMdmPackageName);
        return requiredApps;
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
