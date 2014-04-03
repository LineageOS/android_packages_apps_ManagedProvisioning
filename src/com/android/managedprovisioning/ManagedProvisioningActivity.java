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

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static com.android.managedprovisioning.UserConsentActivity.USER_CONSENT_KEY;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles managed profile provisioning: A device that already has a user, but needs to be set up
 * for a secondary usage purpose (e.g using your personal device as a corporate device).
 */
// TODO: Proper error handling to report back to the user and potentially the mdm.
public class ManagedProvisioningActivity extends Activity {

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
          showErrorAndClose(R.string.managed_provisioning_error_text);
          return;
        }

        mMdmPackageName = getIntent().getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        mDefaultManagedProfileName = getIntent().getStringExtra(
                EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);

        // TODO: update UI
        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.progress_profile_owner, null);
        setContentView(contentView);

        mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);

        if (!alreadyHasManagedProfile()) {
            // Ask for user consent.
            Intent userConsentIntent = new Intent(this, UserConsentActivity.class);
            startActivityForResult(userConsentIntent, USER_CONSENT_REQUEST_CODE);
            // Wait for user consent, in onActivityResult
        } else {
            ProvisionLogger.loge("The device already has a managed profile, nothing to do.");
            showErrorAndClose(R.string.managed_profile_already_present);
        }
    }

    private boolean isIntentValid(Intent intent) {
        String mdmPackageName = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        // Validate package name
        if (TextUtils.isEmpty(mdmPackageName)) {
            ProvisionLogger.loge("Missing intent extra: "
                    + EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
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
                .getStringExtra(EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        // Validate profile name
        if (TextUtils.isEmpty(defaultManagedProfileName)) {
            ProvisionLogger.loge("Missing intent extra: "
                    + EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
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

        try {
            createProfile(mDefaultManagedProfileName);
            deleteNonRequiredAppsForManagedProfile(new Runnable() {
                    public void run() {
                        setUpProfileAndFinish();
                    }});
        } catch (ManagedProvisioningFailedException e) {
            ProvisionLogger.logw(
                    "Could not finish managed profile provisioning: " + e.getMessage());
            cleanup();
            showErrorAndClose(R.string.managed_provisioning_error_text);
        }
    }

    /**
     * Called when the new profile is ready for provisioning (the profile is created and all the
     * apps not needed have been deleted).
     */
    private void setUpProfileAndFinish() {
        try {
            installMdmOnManagedProfile();
            setMdmAsManagedProfileOwner();
            startManagedProfile();
            removeMdmFromPrimaryUser();
            sendProvisioningCompleteToManagedProfile(this);
            ProvisionLogger.logd("Finishing managed profile provisioning.");
            finish();
        } catch (ManagedProvisioningFailedException e) {
            ProvisionLogger.logw(
                    "Could not finish managed profile provisioning: " + e.getMessage());
            cleanup();
            showErrorAndClose(R.string.managed_provisioning_error_text);
        }
    }

    private void createProfile(String profileName) throws ManagedProvisioningFailedException {

        ProvisionLogger.logd("Creating managed profile with name " + profileName);

        mManagedProfileUserInfo = mUserManager.createProfileForUser(profileName,
                UserInfo.FLAG_MANAGED_PROFILE, ActivityManager.getCurrentUser());

        if (mManagedProfileUserInfo == null) {
            if (UserManager.getMaxSupportedUsers() == mUserManager.getUserCount()) {
                throw new ManagedProvisioningFailedException(
                        "Profile creation failed, maximum number of users reached.");
            } else {
                throw new ManagedProvisioningFailedException(
                        "Couldn't create profile. Reason unknown.");
            }
        }
    }

    /**
     * Performs cleanup of the device on failure.
     */
    private void cleanup() {
        // The only cleanup we need to do is remove the profile we created.
        if (mManagedProfileUserInfo != null) {
            ProvisionLogger.logd("Removing managed profile");
            mUserManager.removeUser(mManagedProfileUserInfo.id);
        }
    }

    /**
     * Initializes the user that underlies the managed profile.
     * This is required so that the provisioning complete broadcast can be sent across to the
     * profile and apps can run on it.
     */
    private void startManagedProfile() throws ManagedProvisioningFailedException {
        ProvisionLogger.logd("Starting user in background");
        IActivityManager iActivityManager = ActivityManagerNative.getDefault();
        try {
            boolean success = iActivityManager.startUserInBackground(mManagedProfileUserInfo.id);
            if (!success) {
                throw new ManagedProvisioningFailedException("Could not start user in background");
            }
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
    }

    /**
     * Removes all apps that are not marked as required for a managed profile. This includes UI
     * components such as the launcher.
     */
    public void deleteNonRequiredAppsForManagedProfile(Runnable nextTask)
            throws ManagedProvisioningFailedException {

        ProvisionLogger.logd("Deleting non required apps from managed profile.");

        List<PackageInfo> allPackages = null;
        try {
            allPackages = mIpm.getInstalledPackages(PackageManager.GET_SIGNATURES,
                        mManagedProfileUserInfo.id).getList();
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
        //TODO: Remove hardcoded list of required apps. This is just a temporary list to aid
        // development and testing.

        HashSet<String> requiredApps = new HashSet<String> (Arrays.asList(
                getResources().getStringArray(R.array.required_managedprofile_apps)));
        requiredApps.add(mMdmPackageName);
        requiredApps.addAll(getImePackages());
        requiredApps.addAll(getAccessibilityPackages());

        Set<PackageInfo> packagesToDelete = new HashSet<PackageInfo>();
        for (PackageInfo packageInfo : allPackages) {
            // TODO: Remove check for requiredForAllUsers once that flag has been fully deprecated.
            boolean isRequired = requiredApps.contains(packageInfo.packageName)
                    || (packageInfo.requiredForProfile & PackageInfo.MANAGED_PROFILE) != 0
                    || (packageInfo.requiredForProfile == 0 && packageInfo.requiredForAllUsers);

            if (!isRequired) {
                packagesToDelete.add(packageInfo);
            }
        }
        PackageDeleteObserver packageDeleteObserver =
                    new PackageDeleteObserver(packagesToDelete.size(), nextTask);
        for (PackageInfo packageInfo : packagesToDelete) {
            try {
                mIpm.deletePackageAsUser(packageInfo.packageName, packageDeleteObserver,
                        mManagedProfileUserInfo.id, PackageManager.DELETE_SYSTEM_APP);
            } catch (RemoteException neverThrown) {
                // Never thrown, as we are making local calls.
                ProvisionLogger.loge("This should not happen.", neverThrown);
            }
        }
    }

    private List<String> getImePackages() {
        ArrayList<String> imePackages = new ArrayList<String>();
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        List<InputMethodInfo> imis = imm.getInputMethodList();
        for (InputMethodInfo imi : imis) {
            if (isSystemPackage(imi.getPackageName())) {
                imePackages.add(imi.getPackageName());
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
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
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

    private void installMdmOnManagedProfile() throws ManagedProvisioningFailedException {

        ProvisionLogger.logd("Installing mobile device management app " + mMdmPackageName +
              " on managed profile");

        try {
            int status = mIpm.installExistingPackageAsUser(
                mMdmPackageName, mManagedProfileUserInfo.id);
            switch (status) {
              case PackageManager.INSTALL_SUCCEEDED:
                  return;
              case PackageManager.INSTALL_FAILED_USER_RESTRICTED:
                  // Should not happen because we're not installing a restricted user
                  throw new ManagedProvisioningFailedException(
                          "Could not install mobile device management app on managed profile " +
                          "because the user is restricted");
              case PackageManager.INSTALL_FAILED_INVALID_URI:
                  // Should not happen because we already checked
                  throw new ManagedProvisioningFailedException(
                          "Could not install mobile device management app on managed profile " +
                          "because the package could not be found");
              default:
                  throw new ManagedProvisioningFailedException(
                          "Could not install mobile device management app on managed profile. " +
                          "Unknown status: " + status);
            }
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
    }

    private void setMdmAsManagedProfileOwner() throws ManagedProvisioningFailedException {

        ProvisionLogger.logd("Setting package as managed profile owner: " + mMdmPackageName);

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (!dpm.setProfileOwner(
                mMdmPackageName, mDefaultManagedProfileName, mManagedProfileUserInfo.id)) {
            ProvisionLogger.logw("Could not set profile owner.");
            throw new ManagedProvisioningFailedException("Could not set profile owner.");
        }
    }

    private void removeMdmFromPrimaryUser() {

        ProvisionLogger.logd("Removing: " + mMdmPackageName + " from primary user.");

        try {
            mIpm.deletePackageAsUser(mMdmPackageName, null, mUserManager.getUserHandle(), 0);
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
          ProvisionLogger.loge("This should not happen.", neverThrown);
        }
    }

    public void showErrorAndClose(int resourceId) {
      new ManagedProvisioningErrorDialog(getString(resourceId))
              .show(getFragmentManager(), "ErrorDialogFragment");
    }

    boolean alreadyHasManagedProfile() {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        List<UserInfo> profiles = userManager.getProfiles(getUserId());
        for (UserInfo userInfo : profiles) {
            if (userInfo.isManagedProfile()) return true;
        }
        return false;
    }

    private void sendProvisioningCompleteToManagedProfile(Context context) {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        UserHandle userHandle = userManager.getUserForSerialNumber(
                mManagedProfileUserInfo.serialNumber);

        Intent completeIntent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        completeIntent.setPackage(mMdmPackageName);
        completeIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
            Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcastAsUser(completeIntent, userHandle);

        ProvisionLogger.logd("Provisioning complete broadcast has been sent to user "
            + userHandle.getIdentifier());
      }

    /**
     * Exception thrown when the managed provisioning has failed completely.
     *
     * Note: We're using a custom exception to avoid catching subsequent exceptions that might be
     * significant.
     */
    private class ManagedProvisioningFailedException extends Exception {
      public ManagedProvisioningFailedException(String message) {
          super(message);
      }
    }

    /**
     * Runs the next task when all packages have been deleted or shuts down the activity if package
     * deletion fails.
     */
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        private final AtomicInteger packageCount = new AtomicInteger(0);
        private final Runnable nextTask;

        public PackageDeleteObserver(int packageCount, Runnable nextTask) {
            this.nextTask = nextTask;
            this.packageCount.set(packageCount);
        }

        @Override
        public void packageDeleted(String packageName, int returnCode) {
            if (returnCode != PackageManager.DELETE_SUCCEEDED) {
                ProvisionLogger.logw(
                        "Could not finish managed profile provisioning: package deletion failed");
                cleanup();
                showErrorAndClose(R.string.managed_provisioning_error_text);
            }
            int currentPackageCount = packageCount.decrementAndGet();
            if (currentPackageCount == 0) {
                nextTask.run();
            }
        }
    }
}

