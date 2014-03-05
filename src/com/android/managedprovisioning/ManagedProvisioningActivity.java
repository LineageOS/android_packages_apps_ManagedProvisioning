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

package com.android.managedprovisioning;

import static com.android.managedprovisioning.UserConsentActivity.USER_CONSENT_KEY;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.managedprovisioning.ManagedProvisioningErrorDialog;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Handles managed profile provisioning: A device that already has a user, but needs to be set up
 * for a secondary usage purpose (e.g using your personal device as a corporate device).
 */
// TODO: Proper error handling to report back to the user and potentially the mdm.
public class ManagedProvisioningActivity extends Activity {

    // TODO: Put actions and extra keys somewhere externally visible
    //       and update places that refer to this intent with @link.
    private static final String ACTION_PROVISION_MANAGED_PROFILE
        = "android.managedprovisioning.ACTION_PROVISION_MANAGED_PROFILE";
    public static final String MDM_PACKAGE_EXTRA = "mdmPackageName";
    // Used to set the name of the profile and for batching of applications.
    public static final String DEFAULT_MANAGED_PROFILE_NAME_EXTRA = "defaultManagedProfileName";
    public static final String ACTION_PROVISIONING_COMPLETE =
            "android.managedprovision.ACTION_PROVISIONING_COMPLETE";

    private static final int USER_CONSENT_REQUEST_CODE = 1;

    private String mMdmPackageName;
    private String mDefaultManagedProfileName;

    private IPackageManager mIpm;
    private UserInfo mManagedProfileUserInfo;
    private UserManager mUserManager;

    private Boolean userConsented;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisionLogger.logd("Managed provisioning activity ONCREATE");

        if (!isIntentValid(getIntent())) {
          showErrorAndClose();
          return;
        }
        // TODO: Check that no managed profile exists yet.

        mMdmPackageName = getIntent().getStringExtra(MDM_PACKAGE_EXTRA);
        mDefaultManagedProfileName = getIntent().getStringExtra(DEFAULT_MANAGED_PROFILE_NAME_EXTRA);

        // TODO: update UI
        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.progress_profile_owner, null);
        setContentView(contentView);

        mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);

        // Ask for user consent.
        Intent userConsentIntent = new Intent(this, UserConsentActivity.class);
        startActivityForResult(userConsentIntent, USER_CONSENT_REQUEST_CODE);

        // Wait for user consent, in onActivityResult
    }

    private boolean isIntentValid(Intent intent) {
      String mdmPackageName = getIntent().getStringExtra(MDM_PACKAGE_EXTRA);
      // Validate package name
      if (TextUtils.isEmpty(mdmPackageName)) {
        ProvisionLogger.loge("Missing intent extra: " + MDM_PACKAGE_EXTRA);
        return false;
      } else {
        // Check if the package is installed
        try {
          this.getPackageManager().getPackageInfo(mdmPackageName, 0);
        } catch (NameNotFoundException e) {
            ProvisionLogger.loge("Mdm "+ mdmPackageName + " is not installed.", e);
            return false;
        }
      }

      String defaultManagedProfileName = getIntent()
              .getStringExtra(DEFAULT_MANAGED_PROFILE_NAME_EXTRA);
      // Validate profile name
      if (TextUtils.isEmpty(defaultManagedProfileName)) {
        ProvisionLogger.loge("Missing intent extra: " + DEFAULT_MANAGED_PROFILE_NAME_EXTRA);
        return false;
      }
      return true;
    }

    @Override
    public void onBackPressed() {
        // TODO: Handle this graciously by stopping the provisioning flow and cleaning up.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Wait for the user to consent before starting managed profile provisioning.
        if (requestCode == USER_CONSENT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                userConsented = data.getBooleanExtra(USER_CONSENT_KEY, false);

                // Only start provisioning if the user has consented.
                if (userConsented) {
                    startManagedProfileProvisioning();
                } else {
                    ProvisionLogger.logd("User did not consent to profile creation, "
                            + "cancelling provisioing");
                    finish();
                }
            }
            if (resultCode == RESULT_CANCELED) {
                ProvisionLogger.logd("User consent cancelled.");
                finish();
            }
        }
    }

    /**
     * This is the core method of this class. It goes through every provisioning step.
     */
    private void startManagedProfileProvisioning() {

        ProvisionLogger.logd("Starting managed profile provisioning");
        // Work through the provisioning steps in their corresponding order
        boolean success = createProfile(mDefaultManagedProfileName)
            && deleteNonRequiredAppsForManagedProfile()
            && installMdmOnManagedProfile()
            && setMdmAsManagedProfileOwner()
            && removeMdmFromPrimaryUser();

        if (success) {
          ProvisionLogger.logd("Finishing managed profile provisioning.");
          finish();
        } else {
          ProvisionLogger.logw("Could not finish managed profile provisioning.");
          showErrorAndClose();
        }
    }

    private boolean createProfile(String profileName) {

        ProvisionLogger.logd("Creating managed profile with name " + profileName);

        mManagedProfileUserInfo = mUserManager.createRelatedUser(profileName,
                UserInfo.FLAG_MANAGED_PROFILE, ActivityManager.getCurrentUser());

        if (mManagedProfileUserInfo == null) {
            if (UserManager.getMaxSupportedUsers() == mUserManager.getUserCount()) {
                ProvisionLogger.logw("User creation failed, maximum number of users reached.");
                return false;
            } else {
                ProvisionLogger.logw("Couldn't create related user. Reason unknown.");
                return false;
            }
        }
        return true;
    }

    /**
     * Removes all apps that are not marked as required for a managed profile. This includes UI
     * components such as the launcher.
     */
    public boolean deleteNonRequiredAppsForManagedProfile() {

        ProvisionLogger.logd("Deleting non required apps from managed profile.");

        List<ApplicationInfo> allApps = null;
        try {
            allApps = mIpm.getInstalledApplications(
                    PackageManager.GET_UNINSTALLED_PACKAGES, mManagedProfileUserInfo.id).getList();
        } catch (RemoteException e) {
            ProvisionLogger.logw("RemoteException when getting the installed applications for the "
                    + "managed profile");
            return false;
        }

        //TODO: Remove hardcoded list of required apps. This is just a temporary list to aid
        // development and testing.

        HashSet<String> requiredApps = new HashSet<String> (Arrays.asList(
                getResources().getStringArray(R.array.required_managedprofile_apps)));
        requiredApps.addAll(getImePackages());
        requiredApps.addAll(getAccessibilityPackages());

        for (ApplicationInfo app : allApps) {
            PackageInfo packageInfo = null;
            try {
                packageInfo = mIpm.getPackageInfo(app.packageName,
                        PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_SIGNATURES,
                        mManagedProfileUserInfo.id);
            } catch (RemoteException e) {
                ProvisionLogger.logw("RemoteException when getting package info for "
                        + app.packageName + " for the managed profile");
                // TODO: We should probably stop here, but some apps are better than no apps for now
            }

            // TODO: Remove check for requiredForAllUsers once that flag has been fully deprecated.
            boolean isRequired = requiredApps.contains(app.packageName)
                    || packageInfo.requiredForAllUsers
                    || (packageInfo.requiredForProfile & PackageInfo.MANAGED_PROFILE) != 0;

            if (!isRequired) {
                try {
                    mIpm.deletePackageAsUser(app.packageName, null, mManagedProfileUserInfo.id,
                            PackageManager.DELETE_SYSTEM_APP);
                } catch (RemoteException e) {
                    ProvisionLogger.logw("RemoteException when deleting " + app.packageName
                            + " for the managed profile");
                    return false;
                }
            }
        }
        return true;
    }

    private List<String> getImePackages() {
        ArrayList<String> imePackages = new ArrayList<String>();
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        List<InputMethodInfo> imis = imm.getInputMethodList();
        for (InputMethodInfo imi : imis) {
            try {
                if (imi.isDefault(this) && isSystemPackage(imi.getPackageName())) {
                    imePackages.add(imi.getPackageName());
                }
            } catch (NotFoundException rnfe) {
                // No default IME available
            }
        }
        return imePackages;
    }

    private boolean isSystemPackage(String packageName) {
        try {
            final PackageInfo pi = mIpm.getPackageInfo(packageName, 0, mManagedProfileUserInfo.id);
            if (pi.applicationInfo == null) return false;
            final int flags = pi.applicationInfo.flags;
            if ((flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                return true;
            }
        } catch (RemoteException e) {
            ProvisionLogger.logw("RemoteException when getting package info for "
                + packageName + " for the managed profile", e);
        }
        return false;
    }

    private List<String> getAccessibilityPackages() {
        ArrayList<String> accessibilityPackages = new ArrayList<String>();
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> asis = am.getInstalledAccessibilityServiceList();
        for (AccessibilityServiceInfo asi : asis) {
            String packageName = asi.getResolveInfo().serviceInfo.packageName;
            if (isSystemPackage(packageName)) {
                accessibilityPackages.add(packageName);
            }
        }
        return accessibilityPackages;
    }

    private boolean installMdmOnManagedProfile() {

        ProvisionLogger.logd("Installing mdm on managed profile: " + mMdmPackageName);

        try {
            int status = mIpm.installExistingPackageAsUser(
                mMdmPackageName, mManagedProfileUserInfo.id);
            switch (status) {
              case PackageManager.INSTALL_SUCCEEDED:
                  return true;
              case PackageManager.INSTALL_FAILED_USER_RESTRICTED:
                  // Should not happen because we're not installing a restricted user
                  ProvisionLogger.logw("Could not install mdm on managed profile because the " +
                            "user is restricted");
                  return false;
              case PackageManager.INSTALL_FAILED_INVALID_URI:
                  // Should not happen because we already checked
                  ProvisionLogger.logw("Could not install mdm on managed profile because the " +
                            "package could not be found");
                  return false;
              default:
                  ProvisionLogger.logw("Could not install mdm on managed profile. Unknown status: "
                          + status);
                  return false;
            }
        } catch (RemoteException e) {
            ProvisionLogger.logw("RemoteException, installing the mobile device management application "
                    + "for the managed profile failed.");
            return false;
        }
    }

    private boolean setMdmAsManagedProfileOwner() {

        ProvisionLogger.logd("Setting package as managed profile owner: " + mMdmPackageName);

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm.setProfileOwner(
            mMdmPackageName, mDefaultManagedProfileName, mManagedProfileUserInfo.id)) {
          return true;
        }
        ProvisionLogger.logw("Could not set profile owner.");
        return false;
    }

    private boolean removeMdmFromPrimaryUser (){

        ProvisionLogger.logd("Removing mdm: " + mMdmPackageName + " from primary user.");

        try {
            mIpm.deletePackageAsUser(mMdmPackageName, null, mUserManager.getUserHandle(), 0);
        } catch (Exception e) {
            ProvisionLogger.logw("RemoteException, removing the mobile device management application "
                    + "from the primary user failed.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void showErrorAndClose() {
        new ManagedProvisioningErrorDialog().show(getFragmentManager(), "ErrorDialogFragment");
    }
}

