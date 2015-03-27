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

import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.Manifest.permission;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.ProvisioningParams;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Optionally installs device owner and device initializer packages. Can install a downloaded apk,
 * or install an existing package which is already installed for a different user.
 * <p>
 * Before installing from a downloaded file, each file is checked to ensure it contains the correct
 * package and admin receiver.
 * </p>
 */
public class InstallPackageTask {
    public static final int ERROR_PACKAGE_INVALID = 0;
    public static final int ERROR_INSTALLATION_FAILED = 1;
    public static final int ERROR_PACKAGE_NAME_INVALID = 2;

    private final Context mContext;
    private final Callback mCallback;
    private final String mDeviceAdminPackageName;
    private final String mDeviceInitializerPackageName;

    private PackageManager mPm;
    private int mPackageVerifierEnable;
    private Set<InstallInfo> mPackagesToInstall;

    /**
     * Create an InstallPackageTask. When run, this will attempt to install the device initializer
     * and device admin packages if they are specified in {@code params}.
     *
     * {@see #run(String, String)} for more detail on package installation.
     */
    public InstallPackageTask (Context context, ProvisioningParams params, Callback callback) {
        mCallback = callback;
        mContext = context;
        mDeviceAdminPackageName = params.inferDeviceAdminPackageName();

        if (params.mDeviceInitializerComponentName != null) {
            mDeviceInitializerPackageName = params.mDeviceInitializerComponentName.getPackageName();
        } else {
            mDeviceInitializerPackageName = null;
        }

        mPackagesToInstall = new HashSet<InstallInfo>();
        mPm = mContext.getPackageManager();
    }

    /**
     * Install the device admin and device initializer packages. Each package will be installed from
     * the given location if one is provided. If a null or empty location is provided, and the
     * package is installed for a different user, it will be enabled for the calling user. If the
     * package location is not provided and the package is not installed for any other users, this
     * task will produce an error.
     *
     * Errors will be indicated if a downloaded package is invalid, or installation fails.
     *
     * @param deviceAdminPackageLocation The file system location of a downloaded device admin
     *                                   package. If null, the package will be installed from
     *                                   another user if possible.
     * @param deviceInitializerPackageLocation The file system location of a downloaded device
     *                                         initializer package. If null, the package will be
     *                                         installed from another user if possible.
     */
    public void run(String deviceAdminPackageLocation, String deviceInitializerPackageLocation) {
        if (!TextUtils.isEmpty(mDeviceAdminPackageName)) {
            mPackagesToInstall.add(new InstallInfo(
                    mDeviceAdminPackageName, deviceAdminPackageLocation));
        }
        if (!TextUtils.isEmpty(mDeviceInitializerPackageName)) {
            mPackagesToInstall.add(new InstallInfo(
                    mDeviceInitializerPackageName, deviceInitializerPackageLocation));
        }

        if (mPackagesToInstall.size() == 0) {
            ProvisionLogger.loge("No downloaded packages to install");
            mCallback.onSuccess();
            return;
        }
        ProvisionLogger.logi("Installing package(s)");

        PackageInstallObserver observer = new PackageInstallObserver();

        for (InstallInfo info : mPackagesToInstall) {
            if (TextUtils.isEmpty(info.mLocation)) {
                installExistingPackage(info);

            } else if (packageContentIsCorrect(info.mPackageName, info.mLocation)) {
                // Temporarily turn off package verification.
                mPackageVerifierEnable = Global.getInt(mContext.getContentResolver(),
                        Global.PACKAGE_VERIFIER_ENABLE, 1);
                Global.putInt(mContext.getContentResolver(), Global.PACKAGE_VERIFIER_ENABLE, 0);

                // Allow for replacing an existing package.
                // Needed in case this task is performed multiple times.
                mPm.installPackage(Uri.parse("file://" + info.mLocation),
                        observer,
                        /* flags */ PackageManager.INSTALL_REPLACE_EXISTING,
                        mContext.getPackageName());
            } else {
                // Error should have been reported in packageContentIsCorrect().
                return;
            }
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
        for (ActivityInfo ai : pi.receivers) {
            if (!TextUtils.isEmpty(ai.permission) &&
                    ai.permission.equals(android.Manifest.permission.BIND_DEVICE_ADMIN)) {
                return true;
            }
        }
        ProvisionLogger.loge("Installed package has no admin receiver.");
        mCallback.onError(ERROR_PACKAGE_INVALID);
        return false;
    }

    private class PackageInstallObserver extends IPackageInstallObserver.Stub {
        @Override
        public void packageInstalled(String packageName, int returnCode) {
            InstallInfo installInfo = null;
            for (InstallInfo info : mPackagesToInstall) {
                if (packageName.equals(info.mPackageName)) {
                    installInfo = info;
                }
            }
            if (installInfo == null)  {
                ProvisionLogger.loge("Package doesn't have expected package name.");
                mCallback.onError(ERROR_PACKAGE_INVALID);
                return;
            }
            if (returnCode == PackageManager.INSTALL_SUCCEEDED) {
                installInfo.mDoneInstalling = true;
                ProvisionLogger.logd(
                        "Package " + installInfo.mPackageName + " is succesfully installed.");
                checkSuccess();
            } else {
                if (returnCode == PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE) {
                    installInfo.mDoneInstalling = true;
                    ProvisionLogger.logd("Current version of " + installInfo.mPackageName
                            + " higher than the version to be installed. It was not reinstalled.");
                    checkSuccess();
                } else {
                    ProvisionLogger.logd(
                            "Installing package " + installInfo.mPackageName + " failed.");
                    ProvisionLogger.logd(
                            "Errorcode returned by IPackageInstallObserver = " + returnCode);
                    mCallback.onError(ERROR_INSTALLATION_FAILED);
                }
            }
        }
    }

    /**
     * Calls the success callback once all of the packages that needed to be installed are
     * successfully installed.
     */
    private void checkSuccess() {
        for (InstallInfo info : mPackagesToInstall) {
            if (!info.mDoneInstalling) {
                return;
            }
        }
        // Set package verification flag to its original value.
        Global.putInt(mContext.getContentResolver(), Global.PACKAGE_VERIFIER_ENABLE,
                mPackageVerifierEnable);
        mCallback.onSuccess();
    }

    /**
     * Attempt to install this package from an existing package installed under a different user.
     * If this package is already installed for this user, this is a no-op. If it is not installed
     * for another user, this will produce an error.
     * @param info The package to install
     */
    private void installExistingPackage(InstallInfo info) {
        try {
            ProvisionLogger.logi("Installing existing package " + info.mPackageName);
            mPm.installExistingPackage(info.mPackageName);
            info.mDoneInstalling = true;
        } catch (PackageManager.NameNotFoundException e) {
            mCallback.onError(ERROR_PACKAGE_INVALID);
            return;
        }
        checkSuccess();
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError(int errorCode);
    }

    private static class InstallInfo {
        public String mPackageName;
        public String mLocation;
        public boolean mDoneInstalling;

        public InstallInfo(String packageName, String location) {
            mPackageName = packageName;
            mLocation = location;
        }
    }
}