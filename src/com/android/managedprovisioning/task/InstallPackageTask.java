/*
 * Copyright 2014, The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;

import java.io.File;

/**
 * Installs a package. Can install a downloaded apk, or install an existing package which is already
 * installed for a different user.
 * <p>
 * Before installing from a downloaded file, the file is checked to ensure it contains the correct
 * package and admin receiver.
 * </p>
 */
public class InstallPackageTask {
    public static final int ERROR_PACKAGE_INVALID = 0;
    public static final int ERROR_INSTALLATION_FAILED = 1;

    private final Context mContext;
    private final Callback mCallback;
    private final Utils mUtils;

    private PackageManager mPm;
    private boolean mInitialPackageVerifierEnabled;

    /**
     * Create an InstallPackageTask. When run, this will attempt to install the device admin package
     * if it is non-null.
     *
     * {@see #run(String, String)} for more detail on package installation.
     */
    public InstallPackageTask (Context context, Callback callback) {
        this(context, callback, new Utils());
    }

    @VisibleForTesting
    InstallPackageTask (Context context, Callback callback, Utils utils) {
        mCallback = checkNotNull(callback);
        mContext = checkNotNull(context);
        mPm = mContext.getPackageManager();
        mUtils = checkNotNull(utils);
    }

    /**
     * Installs a package. The package will be installed from the given location if one is provided.
     * If a null or empty location is provided, and the package is installed for a different user,
     * it will be enabled for the calling user. If the package location is not provided and the
     * package is not installed for any other users, this task will produce an error.
     *
     * Errors will be indicated if a downloaded package is invalid, or installation fails.
     */
    public void run(@NonNull String packageName, @Nullable String packageLocation) {

        ProvisionLogger.logi("Installing package");
        mInitialPackageVerifierEnabled = mUtils.isPackageVerifierEnabled(mContext);
        if (TextUtils.isEmpty(packageLocation)) {
            mCallback.onSuccess();
            return;
        } else if (packageContentIsCorrect(packageName, packageLocation)) {
            // Temporarily turn off package verification.
            mUtils.setPackageVerifierEnabled(mContext, false);

            // Allow for replacing an existing package.
            // Needed in case this task is performed multiple times.
            mPm.installPackage(Uri.parse("file://" + packageLocation),
                    new PackageInstallObserver(packageName, packageLocation),
                    /* flags */ PackageManager.INSTALL_REPLACE_EXISTING,
                    mContext.getPackageName());
        } else {
            // Error should have been reported in packageContentIsCorrect().
            return;
        }
    }

    private boolean packageContentIsCorrect(String packageName, String packageLocation) {
        PackageInfo pi = mPm.getPackageArchiveInfo(packageLocation, PackageManager.GET_RECEIVERS);
        if (pi == null) {
            ProvisionLogger.loge("Package could not be parsed successfully.");
            mCallback.onError(ERROR_PACKAGE_INVALID);
            return false;
        }
        if (!pi.packageName.equals(packageName)) {
            ProvisionLogger.loge("Package name in apk (" + pi.packageName
                    + ") does not match package name specified by programmer ("
                    + packageName + ").");
            mCallback.onError(ERROR_PACKAGE_INVALID);
            return false;
        }
        if (pi.receivers != null) {
            for (ActivityInfo ai : pi.receivers) {
                if (!TextUtils.isEmpty(ai.permission) &&
                        ai.permission.equals(android.Manifest.permission.BIND_DEVICE_ADMIN)) {
                    return true;
                }
            }
        }
        ProvisionLogger.loge("Installed package has no admin receiver.");
        mCallback.onError(ERROR_PACKAGE_INVALID);
        return false;
    }

    private class PackageInstallObserver extends IPackageInstallObserver.Stub {
        private final String mPackageName;
        private final String mPackageLocation;

        public PackageInstallObserver(String packageName, String packageLocation) {
            mPackageName = packageName;
            mPackageLocation = packageLocation;
        }

        @Override
        public void packageInstalled(String packageName, int returnCode) {
            mUtils.setPackageVerifierEnabled(mContext, mInitialPackageVerifierEnabled);
            if (packageName != null && !packageName.equals(mPackageName))  {
                ProvisionLogger.loge("Package doesn't have expected package name.");
                mCallback.onError(ERROR_PACKAGE_INVALID);
                return;
            }
            if (returnCode == PackageManager.INSTALL_SUCCEEDED) {
                ProvisionLogger.logd(
                        "Package " + mPackageName + " is succesfully installed.");
                mCallback.onSuccess();
            } else if (returnCode == PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE) {
                ProvisionLogger.logd("Current version of " + mPackageName
                        + " higher than the version to be installed. It was not reinstalled.");
                // If the package is already at a higher version: success.
                mCallback.onSuccess();
            } else {
                ProvisionLogger.logd(
                        "Installing package " + mPackageName + " failed.");
                ProvisionLogger.logd(
                        "Errorcode returned by IPackageInstallObserver = " + returnCode);
                mCallback.onError(ERROR_INSTALLATION_FAILED);
            }
            // remove the file containing the apk in order not to use too much space.
            new File(mPackageLocation).delete();
        }
    }

    /**
     * Calls the success callback once the package that needed to be installed is successfully
     * installed.
     */
    private void onSuccess() {
        // Set package verification flag to its original value.
        mCallback.onSuccess();
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError(int errorCode);
    }
}
