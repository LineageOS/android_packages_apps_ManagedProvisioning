package com.android.managedprovisioning.task;

import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageDeleteObserver;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Removes all apps with a launcher that are not required.
 * There are two decendants of this class:
 * {@link DeleteNonRequiredAppsFromManagedProfileTask} for profile owner provisioning,
 * and {@link DeleteNonRequiredAppsFromManagedDeviceTask} for device owner provisioning.
 * The decendents specify which apps are required.
 */
public class DeleteNonRequiredAppsTask {
    private Callback mCallback;

    private final Context mContext;
    private final IPackageManager mIpm;
    private final String mMdmPackageName;
    private final PackageManager mPm;
    private final int mReqAppsList;
    private final int mVendorReqAppsList;
    private final int mUserId;

    public DeleteNonRequiredAppsTask(Context context, String mdmPackageName, int userId,
            Callback callback, int requiredAppsList, int vendorRequiredAppsList) {
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
                launcherIntent, 0 /* no flags */, mUserId);

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


    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /**
     * Runs the next task when all packages have been deleted or shuts down the activity if package
     * deletion fails.
     */
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        private final AtomicInteger packageCount = new AtomicInteger(0);

        public PackageDeleteObserver(int packageCount) {
            this.packageCount.set(packageCount);
        }

        @Override
        public void packageDeleted(String packageName, int returnCode) {
            if (returnCode != PackageManager.DELETE_SUCCEEDED) {
                ProvisionLogger.logw(
                        "Could not finish managed profile provisioning: package deletion failed");
                mCallback.onError();
            }
            int currentPackageCount = packageCount.decrementAndGet();
            if (currentPackageCount == 0) {
                mCallback.onSuccess();
            }
        }
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
