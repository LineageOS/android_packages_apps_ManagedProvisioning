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
import static android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static com.android.managedprovisioning.UserConsentActivity.USER_CONSENT_KEY;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;



import com.android.managedprovisioning.task.DeleteNonRequiredAppsFromManagedProfileTask;

import java.util.List;

/**
 * Handles managed profile provisioning: A device that already has a user, but needs to be set up
 * for a secondary usage purpose (e.g using your personal device as a corporate device).
 */
// TODO: Proper error handling to report back to the user and potentially the mdm.
public class ManagedProvisioningActivity extends Activity {

    private static final int USER_CONSENT_REQUEST_CODE = 1;
    private static final int ENCRYPT_DEVICE_REQUEST_CODE = 2;

    private String mMdmPackageName;
    private ComponentName mActiveAdminComponentName;
    private String mDefaultManagedProfileName;

    private IPackageManager mIpm;
    private UserInfo mManagedProfileUserInfo;
    private UserManager mUserManager;

    private Boolean userConsented;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisionLogger.logd("Managed provisioning activity ONCREATE");

        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MANAGEDPROFILES)) {
            showErrorAndClose(R.string.managed_provisioning_not_supported,
                    "Exiting managed provisioning, managed profiles feature is not available");
            return;
        }

        // Initialize member variables from the intent, stop if the intent wasn't valid.
        try {
            initialize(getIntent());
        } catch (ManagedProvisioningFailedException e) {
            showErrorAndClose(R.string.managed_provisioning_error_text, e.getMessage());
            return;
        }

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
            showErrorAndClose(R.string.managed_profile_already_present,
                    "The device already has a managed profile, nothing to do.");
        }
    }

    private void initialize(Intent intent)
            throws ManagedProvisioningFailedException {
        mMdmPackageName = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        // Validate package name
        if (TextUtils.isEmpty(mMdmPackageName)) {
            throw new ManagedProvisioningFailedException("Missing intent extra: "
                    + EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        } else {
            // Check if the package is installed
            try {
                this.getPackageManager().getPackageInfo(mMdmPackageName, 0);
            } catch (NameNotFoundException e) {
                throw new ManagedProvisioningFailedException("Mdm "+ mMdmPackageName
                        + " is not installed. " + e);
            }
        }

        mActiveAdminComponentName = intent.getParcelableExtra(EXTRA_DEVICE_ADMIN);
        if (mActiveAdminComponentName == null) {
            throw new ManagedProvisioningFailedException("Missing intent extra: "
                    + EXTRA_DEVICE_ADMIN);
        }

        mDefaultManagedProfileName = getIntent()
                .getStringExtra(EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        // Validate profile name
        if (TextUtils.isEmpty(mDefaultManagedProfileName)) {
            throw new ManagedProvisioningFailedException("Missing intent extra: "
                    + EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        }
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
                if (!userConsented) {
                    ProvisionLogger.logd("User did not consent to profile creation, "
                            + "cancelling provisioing");
                    finish();
                    return;
                }

                // Ask to encrypt the device before proceeding
                if (!EncryptDeviceActivity.isDeviceEncrypted()) {
                    Intent encryptIntent = new Intent(this, EncryptDeviceActivity.class)
                            .putExtra(EncryptDeviceActivity.EXTRA_RESUME, getIntent().getExtras());
                    startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
                    return;
                }

                startManagedProfileProvisioning();
            }
            if (resultCode == RESULT_CANCELED) {
                ProvisionLogger.logd("User consent cancelled.");
                finish();
            }
        } else if (requestCode == ENCRYPT_DEVICE_REQUEST_CODE) {
            if (resultCode == RESULT_CANCELED) {
                // Move back to user consent screen
                Intent userConsentIntent = new Intent(this, UserConsentActivity.class);
                startActivityForResult(userConsentIntent, USER_CONSENT_REQUEST_CODE);
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
            DeleteNonRequiredAppsFromManagedProfileTask deleteTask =
                    new DeleteNonRequiredAppsFromManagedProfileTask(this,
                            mMdmPackageName, mManagedProfileUserInfo,
                            new DeleteNonRequiredAppsFromManagedProfileTask.Callback() {

                                @Override
                                public void onSuccess() {
                                    setUpProfileAndFinish();
                                }

                                @Override
                                public void onError() {
                                    cleanup();
                                    showErrorAndClose(R.string.managed_provisioning_error_text,
                                            "Delete non required apps task failed.");
                                }});
            deleteTask.run();
        } catch (ManagedProvisioningFailedException e) {
            cleanup();
            showErrorAndClose(R.string.managed_provisioning_error_text,
                    "Could not finish managed profile provisioning: " + e.getMessage());
        }
    }

    /**
     * Called when the new profile is ready for provisioning (the profile is created and all the
     * apps not needed have been deleted).
     */
    private void setUpProfileAndFinish() {
        try {
            installMdmOnManagedProfile();
            setMdmAsActiveAdmin();
            setMdmAsManagedProfileOwner();
            startManagedProfile();
            removeMdmFromPrimaryUser();
            forwardIntentsToPrimaryUser();
            sendProvisioningCompleteToManagedProfile(this);
            ProvisionLogger.logd("Finishing managed profile provisioning.");
            finish();
        } catch (ManagedProvisioningFailedException e) {
            cleanup();
            showErrorAndClose(R.string.managed_provisioning_error_text,
                    "Could not finish managed profile provisioning: " + e.getMessage());
        }
    }

    private void createProfile(String profileName) throws ManagedProvisioningFailedException {

        ProvisionLogger.logd("Creating managed profile with name " + profileName);

        mManagedProfileUserInfo = mUserManager.createProfileForUser(profileName,
                UserInfo.FLAG_MANAGED_PROFILE | UserInfo.FLAG_DISABLED,
                Process.myUserHandle().getIdentifier());

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

        ProvisionLogger.logd("Setting package " + mMdmPackageName + " as managed profile owner.");

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (!dpm.setProfileOwner(
                mMdmPackageName, mDefaultManagedProfileName, mManagedProfileUserInfo.id)) {
            ProvisionLogger.logw("Could not set profile owner.");
            throw new ManagedProvisioningFailedException("Could not set profile owner.");
        }
    }

    private void setMdmAsActiveAdmin() {

        ProvisionLogger.logd("Setting package " + mMdmPackageName + " as active admin.");

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        dpm.setActiveAdmin(mActiveAdminComponentName, true /* refreshing*/,
                mManagedProfileUserInfo.id);
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

    public void showErrorAndClose(int resourceId, String logText) {
        ProvisionLogger.loge(logText);
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
        completeIntent.setComponent(mActiveAdminComponentName);
        completeIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
            Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcastAsUser(completeIntent, userHandle);

        ProvisionLogger.logd("Provisioning complete broadcast has been sent to user "
            + userHandle.getIdentifier());
      }

    private void forwardIntentsToPrimaryUser() {
        ProvisionLogger.logd("Setting forwarding intent filters");
        PackageManager pm = getPackageManager();

        IntentFilter mimeTypeTelephony = new IntentFilter();
        mimeTypeTelephony.addAction("android.intent.action.DIAL");
        mimeTypeTelephony.addCategory("android.intent.category.DEFAULT");
        mimeTypeTelephony.addCategory("android.intent.category.BROWSABLE");
        try {
            mimeTypeTelephony.addDataType("vnd.android.cursor.item/phone");
            mimeTypeTelephony.addDataType("vnd.android.cursor.item/person");
            mimeTypeTelephony.addDataType("vnd.android.cursor.dir/calls");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //will not happen
        }
        pm.addForwardingIntentFilter(mimeTypeTelephony, false /*non-removable*/,
                mManagedProfileUserInfo.id, UserHandle.USER_OWNER);

        IntentFilter callDial = new IntentFilter();
        callDial.addAction("android.intent.action.DIAL");
        callDial.addAction("android.intent.action.CALL");
        callDial.addAction("android.intent.action.VIEW");
        callDial.addCategory("android.intent.category.DEFAULT");
        callDial.addCategory("android.intent.category.BROWSABLE");
        callDial.addDataScheme("tel");
        callDial.addDataScheme("voicemail");
        callDial.addDataScheme("sip");
        callDial.addDataScheme("tel");
        pm.addForwardingIntentFilter(callDial, false /*non-removable*/, mManagedProfileUserInfo.id,
                UserHandle.USER_OWNER);

        IntentFilter callDialNoData = new IntentFilter();
        callDialNoData.addAction("android.intent.action.DIAL");
        callDialNoData.addAction("android.intent.action.CALL");
        callDialNoData.addAction("android.intent.action.CALL_BUTTON");
        callDialNoData.addCategory("android.intent.category.DEFAULT");
        callDialNoData.addCategory("android.intent.category.BROWSABLE");
        pm.addForwardingIntentFilter(callDialNoData, false /*non-removable*/,
                mManagedProfileUserInfo.id, UserHandle.USER_OWNER);

        IntentFilter smsMms = new IntentFilter();
        smsMms.addAction("android.intent.action.VIEW");
        smsMms.addAction("android.intent.action.SENDTO");
        smsMms.addCategory("android.intent.category.DEFAULT");
        smsMms.addCategory("android.intent.category.BROWSABLE");
        smsMms.addDataScheme("sms");
        smsMms.addDataScheme("smsto");
        smsMms.addDataScheme("mms");
        smsMms.addDataScheme("mmsto");
        pm.addForwardingIntentFilter(smsMms, false /*non-removable*/, mManagedProfileUserInfo.id,
                UserHandle.USER_OWNER);

        IntentFilter setPassword = new IntentFilter();
        setPassword.addAction("android.app.action.SET_NEW_PASSWORD");
        setPassword.addCategory("android.intent.category.DEFAULT");
        pm.addForwardingIntentFilter(setPassword, false /*non-removable*/,
                mManagedProfileUserInfo.id, UserHandle.USER_OWNER);
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
}

