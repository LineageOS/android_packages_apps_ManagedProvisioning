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
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.Manifest.permission.BIND_DEVICE_ADMIN;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.View;

import com.android.managedprovisioning.CrossProfileIntentFiltersHelper;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;
import com.android.managedprovisioning.task.DisableBluetoothSharingTask;
import com.android.managedprovisioning.task.DisableInstallShortcutListenersTask;

import java.io.IOException;

/**
 * Service that runs the profile owner provisioning.
 *
 * <p>This service is started from and sends updates to the {@link ProfileOwnerProvisioningActivity},
 * which contains the provisioning UI.
 */
public class ProfileOwnerProvisioningService extends Service {
    // Intent actions for communication with DeviceOwnerProvisioningService.
    public static final String ACTION_PROVISIONING_SUCCESS =
            "com.android.managedprovisioning.provisioning_success";
    public static final String ACTION_PROVISIONING_ERROR =
            "com.android.managedprovisioning.error";
    public static final String ACTION_PROVISIONING_CANCELLED =
            "com.android.managedprovisioning.cancelled";
    public static final String EXTRA_LOG_MESSAGE_KEY = "ProvisioingErrorLogMessage";

    private String mMdmPackageName;
    private ComponentName mActiveAdminComponentName;

    // PersistableBundle extra received in starting intent.
    // Should be passed through to device management application when provisioning is complete.
    private PersistableBundle mAdminExtrasBundle;
    private Account mAccountToMigrate;

    private IPackageManager mIpm;
    private UserInfo mManagedProfileUserInfo;
    private AccountManager mAccountManager;
    private UserManager mUserManager;

    private int mStartIdProvisioning;
    private AsyncTask<Intent, Void, Void> runnerTask;

    // MessageId of the last error message.
    private String mLastErrorMessage = null;

    private boolean mDone = false;
    private boolean mCancelInFuture = false;

    private class RunnerTask extends AsyncTask<Intent, Void, Void> {
        @Override
        protected Void doInBackground(Intent ... intents) {
            initialize(intents[0]);
            startManagedProfileProvisioning();
            return null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        mAccountManager = (AccountManager) getSystemService(Context.ACCOUNT_SERVICE);
        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);

        runnerTask = new RunnerTask();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (ProfileOwnerProvisioningActivity.ACTION_CANCEL_PROVISIONING.equals(intent.getAction())) {
            ProvisionLogger.logd("Cancelling profile owner provisioning service");
            cancelProvisioning();
            return START_NOT_STICKY;
        }

        ProvisionLogger.logd("Starting profile owner provisioning service");

        try {
            runnerTask.execute(intent);
        } catch (IllegalStateException e) {
            // runnerTask is either in progress, or finished.
            ProvisionLogger.logd(
                    "ProfileOwnerProvisioningService: Provisioning already started, "
                    + "second provisioning intent not being processed, only reporting status.");
            reportStatus();
        }
        return START_NOT_STICKY;
    }

    private void reportStatus() {
        if (mLastErrorMessage != null) {
            sendError();
        }
        synchronized (this) {
            if (mDone) {
                finishProvisioning();
            }
        }
    }

    private void cancelProvisioning() {
        synchronized (this) {
            if (!mDone) {
                mCancelInFuture = true;
                return;
            }
            cleanup();
        }
    }

    private void initialize(Intent intent) {
        mMdmPackageName = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        mAccountToMigrate = (Account) intent.getParcelableExtra(
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE);
        if (mAccountToMigrate != null) {
            ProvisionLogger.logi("Migrating account to managed profile");
        }

        // Cast is guaranteed by check in Activity.
        mAdminExtrasBundle  = (PersistableBundle) intent.getParcelableExtra(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);

        mActiveAdminComponentName = getAdminReceiverComponent(mMdmPackageName);
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
                        ai.permission.equals(BIND_DEVICE_ADMIN)) {
                    adminReceiverComponent = new ComponentName(packageName, ai.name);

                }
            }
        } catch (NameNotFoundException e) {
            error("Error: The provided mobile device management package does not define a device"
                    + "admin receiver component in its manifest.");
        }
        return adminReceiverComponent;
    }

    /**
     * This is the core method of this class. It goes through every provisioning step.
     */
    private void startManagedProfileProvisioning() {

        ProvisionLogger.logd("Starting managed profile provisioning");

        // Work through the provisioning steps in their corresponding order
        createProfile(getString(R.string.default_managed_profile_name));
        if (mManagedProfileUserInfo != null) {

            final DeleteNonRequiredAppsTask deleteNonRequiredAppsTask;
            final DisableInstallShortcutListenersTask disableInstallShortcutListenersTask;
            final DisableBluetoothSharingTask disableBluetoothSharingTask;

            disableInstallShortcutListenersTask = new DisableInstallShortcutListenersTask(this,
                    mManagedProfileUserInfo.id);
            disableBluetoothSharingTask = new DisableBluetoothSharingTask(
                    mManagedProfileUserInfo.id);
            deleteNonRequiredAppsTask = new DeleteNonRequiredAppsTask(this,
                    mMdmPackageName, R.array.required_apps_managed_profile,
                    R.array.vendor_required_apps_managed_profile,
                    R.array.packages_to_delete_new_managed_profile,
                    true /* creating new profile */, mManagedProfileUserInfo.id,
                    false /* delete non-required system apps */,
                    new DeleteNonRequiredAppsTask.Callback() {

                        @Override
                        public void onSuccess() {
                            disableBluetoothSharingTask.run();
                            disableInstallShortcutListenersTask.run();
                            setUpProfileAndFinish();
                        }

                        @Override
                        public void onError() {
                            error("Delete non required apps task failed.");
                        }
                    });

            deleteNonRequiredAppsTask.run();
        }
    }

    /**
     * Called when the new profile is ready for provisioning (the profile is created and all the
     * apps not needed have been deleted).
     */
    private void setUpProfileAndFinish() {
        installMdmOnManagedProfile();
        setMdmAsActiveAdmin();
        setMdmAsManagedProfileOwner();
        CrossProfileIntentFiltersHelper.setFilters(
                getPackageManager(), getUserId(), mManagedProfileUserInfo.id);

        if (!startManagedProfile(mManagedProfileUserInfo.id)) {
            error("Could not start user in background");
            return;
        }
        copyAccount(mAccountToMigrate);
        synchronized (this) {
            mDone = true;
            if (mCancelInFuture) {
                cleanup();
            } else {
                // Notify activity of success.
                finishProvisioning();
            }
        }
    }

    /**
     * Initialize the user that underlies the managed profile.
     * This is required so that the provisioning complete broadcast can be sent across to the
     * profile and apps can run on it.
     */
    private boolean startManagedProfile(int userId)  {
        ProvisionLogger.logd("Starting user in background");
        IActivityManager iActivityManager = ActivityManagerNative.getDefault();
        try {
            return iActivityManager.startUserInBackground(userId);
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
        return false;
    }

    private void notifyActivityOfSuccess() {
        Intent successIntent = new Intent(ACTION_PROVISIONING_SUCCESS);
        LocalBroadcastManager.getInstance(ProfileOwnerProvisioningService.this)
                .sendBroadcast(successIntent);
    }

    private void finishProvisioning() {
        notifyMdmAndCleanup();
        notifyActivityOfSuccess();
    }

    /**
     * Notify the mdm that provisioning has completed. When the mdm has received the intent, stop
     * the service and notify the {@link ProfileOwnerProvisioningActivity} so that it can finish
     * itself.
     */
    private void notifyMdmAndCleanup() {

        Settings.Secure.putIntForUser(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE,
                1 /* true- > setup complete */, mManagedProfileUserInfo.id);

        UserHandle userHandle = new UserHandle(mManagedProfileUserInfo.id);

        // Use an ordered broadcast, so that we only finish when the mdm has received it.
        // Avoids a lag in the transition between provisioning and the mdm.
        BroadcastReceiver mdmReceivedSuccessReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ProvisionLogger.logd("ACTION_PROFILE_PROVISIONING_COMPLETE broadcast received by"
                        + " mdm");

                final Intent primaryProfileSuccessIntent = new Intent(
                        DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED);
                primaryProfileSuccessIntent.setPackage(mMdmPackageName);
                primaryProfileSuccessIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
                        Intent.FLAG_RECEIVER_FOREGROUND);
                // Now cleanup the primary profile if necessary
                if (mAccountToMigrate != null) {
                    primaryProfileSuccessIntent.putExtra(
                            DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE,
                            mAccountToMigrate);
                    finishAccountMigration(primaryProfileSuccessIntent);
                    // Note that we currently do not check if account migration worked
                } else {
                    sendBroadcast(primaryProfileSuccessIntent);
                    stopSelf();
                }
            }
        };

        // Compose the intent that will be fired by the activity.
        Intent completeIntent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        completeIntent.setComponent(mActiveAdminComponentName);
        completeIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
                Intent.FLAG_RECEIVER_FOREGROUND);
        if (mAdminExtrasBundle != null) {
            completeIntent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                    mAdminExtrasBundle);
        }
        sendOrderedBroadcastAsUser(completeIntent, userHandle, null,
                mdmReceivedSuccessReceiver, null, Activity.RESULT_OK, null, null);
        ProvisionLogger.logd("Provisioning complete broadcast has been sent to user "
                + userHandle.getIdentifier());
    }

    private void finishAccountMigration(final Intent primaryProfileSuccessIntent) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                removeAccount(mAccountToMigrate);
                sendBroadcast(primaryProfileSuccessIntent);
                stopSelf();
                return null;
            }
        }.execute();
    }

    private void removeAccount(Account account) {
        try {
            AccountManagerFuture<Bundle> bundle = mAccountManager.removeAccount(account,
                   null, null /* callback */, null /* handler */);
            // Block to get the result of the removeAccount operation
            if (bundle.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)) {
                ProvisionLogger.logw("Account removed from the primary user.");
            } else {
                Intent removeIntent = (Intent) bundle.getResult().getParcelable(
                        AccountManager.KEY_INTENT);
                if (removeIntent != null) {
                    ProvisionLogger.logi("Starting activity to remove user");
                    startActivity(removeIntent);
                } else {
                    ProvisionLogger.logw("Could not remove account from the primary user.");
                }
            }
        } catch (OperationCanceledException | AuthenticatorException | IOException e) {
            ProvisionLogger.logw("Exception removing account from the primary user.", e);
        }
    }

    private void copyAccount(Account account) {
        if (account == null) {
            ProvisionLogger.logd("No account to migrate to the managed profile.");
            return;
        }
        ProvisionLogger.logd("Attempting to copy account to user " + mManagedProfileUserInfo.id);
        try {
            if (mAccountManager.copyAccountToUser(account, mManagedProfileUserInfo.getUserHandle(),
                    /* callback= */ null, /* handler= */ null).getResult()) {
                ProvisionLogger.logi("Copied account to user " + mManagedProfileUserInfo.id);
            } else {
                ProvisionLogger.loge("Could not copy account to user "
                        + mManagedProfileUserInfo.id);
            }
        } catch (OperationCanceledException | AuthenticatorException | IOException e) {
            ProvisionLogger.logw("Exception copying account to user " + mManagedProfileUserInfo.id,
                    e);
        }
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
        if (!dpm.setProfileOwner(mActiveAdminComponentName, mMdmPackageName,
                mManagedProfileUserInfo.id)) {
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

    private void error(String dialogMessage) {
        mLastErrorMessage = dialogMessage;
        sendError();
    }

    private void sendError() {
        Intent intent = new Intent(ACTION_PROVISIONING_ERROR);
        intent.putExtra(EXTRA_LOG_MESSAGE_KEY, mLastErrorMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
        Intent cancelIntent = new Intent(ACTION_PROVISIONING_CANCELLED);
        LocalBroadcastManager.getInstance(ProfileOwnerProvisioningService.this)
                .sendBroadcast(cancelIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
