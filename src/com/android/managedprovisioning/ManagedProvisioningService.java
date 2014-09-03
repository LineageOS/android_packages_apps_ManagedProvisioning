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

package com.android.managedprovisioning;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_EMAIL_ADDRESS;
import static com.android.managedprovisioning.ManagedProvisioningActivity.EXTRA_LEGACY_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME;
import static com.android.managedprovisioning.ManagedProvisioningActivity.EXTRA_LEGACY_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import com.android.managedprovisioning.CrossProfileIntentFiltersHelper;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;

/**
 * Service that runs the managed provisioning.
 *
 * <p>This service is started from and sends updates to the {@link ManagedProvisioningActivity},
 * which contains the provisioning UI.
 */
public class ManagedProvisioningService extends Service {

    // Intent actions for communication with DeviceOwnerProvisioningService.
    public static final String ACTION_PROVISIONING_SUCCESS =
            "com.android.managedprovisioning.provisioning_success";
    public static final String ACTION_PROVISIONING_ERROR =
            "com.android.managedprovisioning.error";
    public static final String EXTRA_LOG_MESSAGE_KEY = "ProvisioingErrorLogMessage";

    private String mMdmPackageName;
    private ComponentName mActiveAdminComponentName;
    private String mDefaultManagedProfileName;
    private String mManagedProfileEmailAddress;

    private IPackageManager mIpm;
    private UserInfo mManagedProfileUserInfo;
    private UserManager mUserManager;

    private int mStartIdProvisioning;

    @Override
    public void onCreate() {
        super.onCreate();

        ProvisionLogger.logd("Managed provisioning service ONCREATE");

        mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        ProvisionLogger.logd("Starting managed provisioning service");
        mStartIdProvisioning = startId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                initialize(intent);
                startManagedProfileProvisioning();
            }
        }).start();
        return START_NOT_STICKY;
}

    private void initialize(Intent intent) {
        mMdmPackageName = getMdmPackageName(intent);
        mManagedProfileEmailAddress =
                intent.getStringExtra(EXTRA_PROVISIONING_EMAIL_ADDRESS);

        mActiveAdminComponentName = getAdminReceiverComponent(mMdmPackageName);
        mDefaultManagedProfileName = getDefaultManagedProfileName(intent);
    }

    /**
     * Find the Device admin receiver component from the manifest.
     */
    private ComponentName getAdminReceiverComponent(String packageName) {
        ComponentName adminReceiverComponent = null;

        try {
            PackageInfo pi = getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_RECEIVERS);
            for (ActivityInfo ai : pi.receivers) {
                if (!TextUtils.isEmpty(ai.permission) &&
                        ai.permission.equals(android.Manifest.permission.BIND_DEVICE_ADMIN)) {
                    adminReceiverComponent = new ComponentName(packageName, ai.name);

                }
            }
        } catch (NameNotFoundException e) {
            error("Error: The provided mobile device management package does not define a device"
                    + "admin receiver component in its manifest.");
        }
        return adminReceiverComponent;
    }

    private String getDefaultManagedProfileName(Intent intent) {
        String name = intent.getStringExtra(EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        if (TextUtils.isEmpty(name)) {
            name = intent.getStringExtra(EXTRA_LEGACY_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        }
        if (TextUtils.isEmpty(name)) {
            name = getString(R.string.default_managed_profile_name);
        }
        return name;
    }

    private String getMdmPackageName(Intent intent) {
        String name = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        if (TextUtils.isEmpty(name)) {
            name = intent.getStringExtra(EXTRA_LEGACY_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        }
        return name;
    }

    /**
     * This is the core method of this class. It goes through every provisioning step.
     */
    private void startManagedProfileProvisioning() {

        ProvisionLogger.logd("Starting managed profile provisioning");

        // Work through the provisioning steps in their corresponding order
        createProfile(mDefaultManagedProfileName);
        new DeleteNonRequiredAppsTask(this,
                mMdmPackageName, mManagedProfileUserInfo.id,
                R.array.required_apps_managed_profile,
                R.array.vendor_required_apps_managed_profile,
                true /* Disable Sharing via Nfc and Blueetoth */,
                true /* Disable INSTALL_SHORTCUT listeners */,
                new DeleteNonRequiredAppsTask.Callback() {

                    @Override
                    public void onSuccess() {
                        setUpProfileAndFinish();
                    }

                    @Override
                    public void onError() {
                        error("Delete non required apps task failed.");
                    }
                }).run();
    }

    /**
     * Called when the new profile is ready for provisioning (the profile is created and all the
     * apps not needed have been deleted).
     */
    private void setUpProfileAndFinish() {
            installMdmOnManagedProfile();
            setMdmAsActiveAdmin();
            setMdmAsManagedProfileOwner();
            startManagedProfile();
            CrossProfileIntentFiltersHelper.setFilters(
                    getPackageManager(), getUserId(), mManagedProfileUserInfo.id);
            onProvisioningSuccess(mActiveAdminComponentName);
    }

    private void createProfile(String profileName) {

        ProvisionLogger.logd("Creating managed profile with name " + profileName);

        mManagedProfileUserInfo = mUserManager.createProfileForUser(profileName,
                UserInfo.FLAG_MANAGED_PROFILE | UserInfo.FLAG_DISABLED,
                Process.myUserHandle().getIdentifier());

        if (mManagedProfileUserInfo == null) {
            if (UserManager.getMaxSupportedUsers() == mUserManager.getUserCount()) {
                error("Profile creation failed, maximum number of users reached.");
            } else {
                error("Couldn't create profile. Reason unknown.");
            }
        }
    }

    /**
     * Initializes the user that underlies the managed profile.
     * This is required so that the provisioning complete broadcast can be sent across to the
     * profile and apps can run on it.
     */
    private void startManagedProfile()  {
        ProvisionLogger.logd("Starting user in background");
        IActivityManager iActivityManager = ActivityManagerNative.getDefault();
        try {
            boolean success = iActivityManager.startUserInBackground(mManagedProfileUserInfo.id);
            if (!success) {
               error("Could not start user in background");
            }
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
    }

    private void installMdmOnManagedProfile() {

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
                  error("Could not install mobile device management app on managed "
                          + "profile because the user is restricted");
              case PackageManager.INSTALL_FAILED_INVALID_URI:
                  // Should not happen because we already checked
                  error("Could not install mobile device management app on managed "
                          + "profile because the package could not be found");
              default:
                  error("Could not install mobile device management app on managed "
                          + "profile. Unknown status: " + status);
            }
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
    }

    private void setMdmAsManagedProfileOwner() {

        ProvisionLogger.logd("Setting package " + mMdmPackageName + " as managed profile owner.");

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (!dpm.setProfileOwner(mActiveAdminComponentName,
                mDefaultManagedProfileName, mManagedProfileUserInfo.id)) {
            ProvisionLogger.logw("Could not set profile owner.");
            error("Could not set profile owner.");
        }
    }

    private void setMdmAsActiveAdmin() {

        ProvisionLogger.logd("Setting package " + mMdmPackageName + " as active admin.");

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        dpm.setActiveAdmin(mActiveAdminComponentName, true /* refreshing*/,
                mManagedProfileUserInfo.id);
    }

    private void sendProvisioningCompleteToManagedProfile(Context context) {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        UserHandle userHandle = userManager.getUserForSerialNumber(
                mManagedProfileUserInfo.serialNumber);

        Intent completeIntent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        completeIntent.setComponent(mActiveAdminComponentName);
        completeIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
            Intent.FLAG_RECEIVER_FOREGROUND);
        if (mManagedProfileEmailAddress != null) {
            completeIntent.putExtra(EXTRA_PROVISIONING_EMAIL_ADDRESS, mManagedProfileEmailAddress);
        }
        context.sendBroadcastAsUser(completeIntent, userHandle);

        ProvisionLogger.logd("Provisioning complete broadcast has been sent to user "
            + userHandle.getIdentifier());
      }


    /**
     * Notify the calling activity and the mdm that provisioning has completed.
     *
     * @param deviceAdminComponent The component of the mdm that will be notified.
     */
    private void onProvisioningSuccess(ComponentName deviceAdminComponent) {
        Settings.Secure.putIntForUser(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE,
                1, mManagedProfileUserInfo.id);

        Intent successIntent = new Intent(ACTION_PROVISIONING_SUCCESS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(successIntent);

        sendProvisioningCompleteToManagedProfile(this);
        stopSelf(mStartIdProvisioning);
    }

    private void error(String logMessage) {
        Intent intent = new Intent(ACTION_PROVISIONING_ERROR);
        intent.setClass(this, ManagedProvisioningActivity.ServiceMessageReceiver.class);
        intent.putExtra(EXTRA_LOG_MESSAGE_KEY, logMessage);
        sendBroadcast(intent);
        cleanup();
        stopSelf(mStartIdProvisioning);
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
