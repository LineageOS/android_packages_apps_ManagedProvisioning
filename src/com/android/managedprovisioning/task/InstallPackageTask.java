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
 * Optionally installs device owner and device initializer packages.
 * <p>
 * Before installing it is checked whether each file at the specified paths contains the correct
 * package and admin receiver.
 * </p>
 */
public class InstallPackageTask {
    public static final int ERROR_PACKAGE_INVALID = 0;
    public static final int ERROR_INSTALLATION_FAILED = 1;

    private final Context mContext;
    private final Callback mCallback;
    private final String mDeviceAdminPackageName;
    private final String mDeviceInitializerPackageName;
    private final boolean mDownloadedAdmin;
    private final boolean mDownloadedInitializer;

    private PackageManager mPm;
    private int mPackageVerifierEnable;
    private Set<InstallInfo> mPackagesToInstall;

    public InstallPackageTask (Context context, ProvisioningParams params, Callback callback) {
        mCallback = callback;
        mContext = context;
        mDeviceAdminPackageName = params.inferDeviceAdminPackageName();
        mDeviceInitializerPackageName = params.mDeviceInitializerComponentName.getPackageName();
        mDownloadedAdmin = !TextUtils.isEmpty(params.mDeviceAdminPackageDownloadLocation);
        mDownloadedInitializer =
                !TextUtils.isEmpty(params.mDeviceInitializerPackageDownloadLocation);
        mPackagesToInstall = new HashSet<InstallInfo>();
    }

    public void run(String deviceAdminPackageLocation, String deviceInitializerPackageLocation) {
        if (mDownloadedAdmin) {
            if (!TextUtils.isEmpty(deviceAdminPackageLocation)) {
                mPackagesToInstall.add(
                        new InstallInfo(mDeviceAdminPackageName, deviceAdminPackageLocation));
            } else {
                ProvisionLogger.loge("Package Location is empty.");
                mCallback.onError(ERROR_PACKAGE_INVALID);
                return;
            }
        }
        if (mDownloadedInitializer) {
            if (!TextUtils.isEmpty(deviceInitializerPackageLocation)) {
                mPackagesToInstall.add(new InstallInfo(
                        mDeviceInitializerPackageName, deviceInitializerPackageLocation));
            } else {
                ProvisionLogger.loge("Package Location is empty.");
                mCallback.onError(ERROR_PACKAGE_INVALID);
                return;
            }
        }

        if (mPackagesToInstall.size() == 0) {
            ProvisionLogger.loge("Nothing to install");
            mCallback.onSuccess();
            return;
        }
        ProvisionLogger.logi("Installing package(s)");

        PackageInstallObserver observer = new PackageInstallObserver();
        mPm = mContext.getPackageManager();

        for (InstallInfo info : mPackagesToInstall) {
            if (packageContentIsCorrect(info.mPackageName, info.mLocation)) {
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