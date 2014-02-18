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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;

import com.android.managedprovisioning.ManagedProvisioningActivity.ProvisioningState;
import com.android.managedprovisioning.Preferences;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
/**
 * This tasks
 *     - creates a secondary user
 *     - installs the mdm app that started the provisioning on that new profile
 *     - sets the mdm as the profile owner for the new profile
 *     - removes the mdm from the primary user
 */
public class CreateProfileTask extends ProvisionTask {

    private Context mContext;
    private IPackageManager mIpm;
    private UserInfo mManagedProfileUserInfo;

    public CreateProfileTask(Context context) {
        super("Create Profile");
        mContext = context;
    }

    @Override
    public void executeTask(String... params) {

        // Preferences that persist initial parameters of provisioning.
        Preferences prefs = mTaskManager.getPreferences();

        mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        String mdmPackageName = prefs.getStringProperty(Preferences.MDM_PACKAGE_KEY);
        String defaultManagedProfileName = prefs.getStringProperty(
                Preferences.DEFAULT_MANAGED_PROFILE_NAME_KEY);
        // TODO: allow sending a default bitmap for the profile image in the intent.

        // Create the new user for the managed profile
        // TODO make this a related user.
        mManagedProfileUserInfo = userManager.createUser(defaultManagedProfileName, 0);

        deleteNonRequiredAppsForManagedProfile();

        // Install the mdm for the new profile.
        // TODO: Include proper error handling for both catch blocks to report back to the mdm.
        try {
            mIpm.installExistingPackageAsUser(mdmPackageName, mManagedProfileUserInfo.id);
        } catch (RemoteException e) {
            ProvisionLogger.logd("RemoteException, installing the mobile device management application "
                    + "for the managed profile failed.");
        }


        // TODO: Set the mdm as the profile owner of the managed profile.

        // Remove the mdm from the primary user.
        try {
            mIpm.deletePackageAsUser(mdmPackageName, null, userManager.getUserHandle(), 0);
        } catch (Exception e) {
            ProvisionLogger.logd("RemoteException, removing the mobile device management application "
                    + "from the primary user failed failed.");
            e.printStackTrace();
        }

        mTaskManager.registerProvisioningState(ProvisioningState.CREATE_PROFILE, "");
        onSuccess();
    }

    public void deleteNonRequiredAppsForManagedProfile() {
        List<ApplicationInfo> allApps = null;
        try {
            allApps = mIpm.getInstalledApplications(
                    PackageManager.GET_UNINSTALLED_PACKAGES, mManagedProfileUserInfo.id).getList();
        } catch (RemoteException e) {
            ProvisionLogger.logd("RemoteException when getting the installed applications for the "
                    + "managed profile");
        }

        //TODO: Remove hardcoded list of required apps. This is just a temporary list to aid
        //development and testing.

        HashSet<String> requiredApps = new HashSet<String> (Arrays.asList(
                mContext.getResources().getStringArray(R.array.required_managedprofile_apps)));

        for (ApplicationInfo app : allApps) {
            PackageInfo packageInfo = null;
            try {
                packageInfo = mIpm.getPackageInfo(app.packageName,
                        PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_SIGNATURES,
                        mManagedProfileUserInfo.id);
            } catch (RemoteException e) {
                ProvisionLogger.logd("RemoteException when getting package info for "
                        + app.packageName + " for the managed profile");
            }

            boolean isRequired = packageInfo.requiredForAllUsers
                    || requiredApps.contains(app.packageName);

            if (!isRequired) {
                try {
                    mIpm.deletePackageAsUser(app.packageName, null, mManagedProfileUserInfo.id,
                            PackageManager.DELETE_SYSTEM_APP);
                } catch (RemoteException e) {
                    ProvisionLogger.logd("RemoteException when deleting " + app.packageName
                            + " for the managed profile");
                }
            }
        }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void hasFailed() {
    }
}
