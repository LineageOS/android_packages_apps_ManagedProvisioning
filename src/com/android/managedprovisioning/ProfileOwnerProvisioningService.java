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
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static com.android.managedprovisioning.ProfileOwnerProvisioningActivity.ACTION_CANCEL_PROVISIONING;
import static com.android.managedprovisioning.ProfileOwnerProvisioningActivity.ACTION_GET_PROVISIONING_STATE;
import static com.android.managedprovisioning.ProfileOwnerProvisioningActivity.ACTION_START_PROVISIONING;
import static com.android.managedprovisioning.model.ProvisioningParams.EXTRA_PROVISIONING_PARAMS;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v4.content.LocalBroadcastManager;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.AbstractProvisioningController;
import com.android.managedprovisioning.provisioning.ProfileOwnerProvisioningController;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service that runs the profile owner provisioning.
 *
 * <p>This service is started from and sends updates to the
 * {@link ProfileOwnerProvisioningActivity}, which contains the provisioning UI.
 */
public class ProfileOwnerProvisioningService extends Service
        implements AbstractProvisioningController.ProvisioningServiceInterface {
    // Intent actions for communication with DeviceOwnerProvisioningService.
    public static final String ACTION_PROVISIONING_SUCCESS =
            "com.android.managedprovisioning.provisioning_success";
    public static final String ACTION_PROVISIONING_ERROR =
            "com.android.managedprovisioning.error";
    public static final String ACTION_PROVISIONING_CANCELLED =
            "com.android.managedprovisioning.cancelled";
    public static final String EXTRA_LOG_MESSAGE_KEY = "ProvisioningErrorLogMessage";

    // Maximum time we will wait for ACTION_USER_UNLOCK until we give up and continue without
    // account migration.
    private static final int USER_UNLOCKED_TIMEOUT_SECONDS = 120; // 2 minutes

    private UserInfo mManagedProfileOrUserInfo;
    private UserManager mUserManager;
    private UserUnlockedReceiver mUnlockedReceiver;

    private ProvisioningParams mParams;

    // TODO: remove this once everything is moved to separate tasks
    private boolean mProvisioningCompleted = false;

    private final Utils mUtils = new Utils();

    private AbstractProvisioningController mController;
    private HandlerThread mHandlerThread;

    @Override
    public void onCreate() {
        super.onCreate();

        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);
        mHandlerThread = new HandlerThread("DeviceOwnerProvisioningHandler");
        mHandlerThread.start();
    }

    @Override
    public void onDestroy() {
        mHandlerThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            ProvisionLogger.logw("Missing intent or action: " + intent);
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_CANCEL_PROVISIONING:
                ProvisionLogger.logd("Cancelling profile owner provisioning service");
                if (mController != null) {
                    mController.cancel();
                } else {
                    ProvisionLogger.logw("Cancelling provisioning, but controller is null");
                }
                break;
            case ACTION_START_PROVISIONING:
                if (mController == null) {
                    mParams = intent.getParcelableExtra(EXTRA_PROVISIONING_PARAMS);
                    startManagedProfileOrUserProvisioning();
                } else {
                    ProvisionLogger.loge("Provisioning start requested,"
                            + " but controller not null");
                    error("Provisioning ongoing", new Exception());
                }
                break;
            case ACTION_GET_PROVISIONING_STATE:
                if (mController == null) {
                    ProvisionLogger.loge("Provisioning status requested,"
                            + " but provisioning not ongoing");
                    error("Provisioning not ongoing", new Exception());
                } else {
                    mController.updateStatus();
                }
                break;
            default:
                ProvisionLogger.loge("Unknown intent action: " + intent.getAction());
        }
        return START_NOT_STICKY;
    }

    /**
     * This is the core method to create a managed profile or user. It goes through every
     * provisioning step.
     */
    // TODO: Consider moving this to a separate task
    private void startManagedProfileOrUserProvisioning() {

        if(isProvisioningManagedUser()) {
            ProvisionLogger.logd("Starting managed user provisioning");
            mManagedProfileOrUserInfo = mUserManager.getUserInfo(mUserManager.getUserHandle());
        } else {
            ProvisionLogger.logd("Starting managed profile provisioning");
            // Work through the provisioning steps in their corresponding order
            mManagedProfileOrUserInfo = mUserManager.createProfileForUser(
                    getString(R.string.default_managed_profile_name),
                    UserInfo.FLAG_MANAGED_PROFILE | UserInfo.FLAG_DISABLED,
                    UserHandle.myUserId());
        }

        if (mManagedProfileOrUserInfo != null) {
            mController = new ProfileOwnerProvisioningController(
                    this,
                    mParams,
                    mManagedProfileOrUserInfo.id,
                    this,
                    mHandlerThread.getLooper());
            mController.initialize();
            mController.start();
        } else {
            error("Unable to create a managed profile", new Exception());
        }
    }

    /**
     * Called when the new profile or managed user is ready for provisioning (the profile is created
     * and all the apps not needed have been deleted).
     */
    @Override
    public void provisioningComplete() {
        if (mProvisioningCompleted) {
            notifyActivityOfSuccess();
            return;
        }

        mProvisioningCompleted = true;
        if (!isProvisioningManagedUser()) {
            // TODO: Move all of the remaining logic into separate tasks
            CrossProfileIntentFiltersHelper.setFilters(
                    getPackageManager(), getUserId(), mManagedProfileOrUserInfo.id);
            if (!startManagedProfile(mManagedProfileOrUserInfo.id)) {
                error("Could not start user in background", new Exception());
            }
            // Wait for ACTION_USER_UNLOCKED to be sent before trying to migrate the account.
            // Even if no account is present, we should not send the provisioning complete broadcast
            // before the managed profile user is properly started.
            if ((mUnlockedReceiver != null) && !mUnlockedReceiver.waitForUserUnlocked()) {
                return;
            }

            // Note: account migration must happen after setting the profile owner.
            // Otherwise, there will be a time interval where some apps may think that the account
            // does not have a profile owner.
            mUtils.maybeCopyAccount(this, mParams.accountToMigrate, Process.myUserHandle(),
                    mManagedProfileOrUserInfo.getUserHandle());
        }
        notifyMdmAndCleanup();
        notifyActivityOfSuccess();
    }

    /**
     * Initialize the user that underlies the managed profile.
     * This is required so that the provisioning complete broadcast can be sent across to the
     * profile and apps can run on it.
     */
    private boolean startManagedProfile(int userId)  {
        ProvisionLogger.logd("Starting user in background");
        IActivityManager iActivityManager = ActivityManagerNative.getDefault();
        // Register a receiver for the Intent.ACTION_USER_UNLOCKED to know when the managed profile
        // has been started and unlocked.
        mUnlockedReceiver = new UserUnlockedReceiver(this, userId);
        try {
            return iActivityManager.startUserInBackground(userId);
        } catch (RemoteException neverThrown) {
            // Never thrown, as we are making local calls.
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
        return false;
    }

    /**
     * Notify the mdm that provisioning has completed. When the mdm has received the intent, stop
     * the service and notify the {@link ProfileOwnerProvisioningActivity} so that it can finish
     * itself.
     */
    // TODO: Consider moving this into FinalizationActivity
    private void notifyMdmAndCleanup() {
        // Set DPM userProvisioningState appropriately and persist mParams for use during
        // FinalizationActivity if necessary.
        mUtils.markUserProvisioningStateInitiallyDone(this, mParams);

        if (mParams.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
            // Set the user_setup_complete flag on the managed-profile as setup-wizard is never run
            // for that user. This is not relevant for other cases since
            // Utils.markUserProvisioningStateInitiallyDone() communicates provisioning state to
            // setup-wizard via DPM.setUserProvisioningState() if necessary.
            mUtils.markUserSetupComplete(this, mManagedProfileOrUserInfo.id);
        }

        // If profile owner provisioning was started after current user setup is completed, then we
        // can directly send the ACTION_PROFILE_PROVISIONING_COMPLETE broadcast to the MDM.
        // But if the provisioning was started as part of setup wizard flow, we signal setup-wizard
        // should shutdown via DPM.setUserProvisioningState(), which will result in a finalization
        // intent being sent to us once setup-wizard finishes. As part of the finalization intent
        // handling we then broadcast ACTION_PROFILE_PROVISIONING_COMPLETE.
        if (mUtils.isUserSetupCompleted(this)) {
            UserHandle managedUserHandle = new UserHandle(mManagedProfileOrUserInfo.id);

            // Use an ordered broadcast, so that we only finish when the mdm has received it.
            // Avoids a lag in the transition between provisioning and the mdm.
            BroadcastReceiver mdmReceivedSuccessReceiver = new MdmReceivedSuccessReceiver(
                    mParams.accountToMigrate, mParams.deviceAdminComponentName.getPackageName());

            Intent completeIntent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
            completeIntent.setComponent(mParams.deviceAdminComponentName);
            completeIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
                    Intent.FLAG_RECEIVER_FOREGROUND);
            if (mParams.adminExtrasBundle != null) {
                completeIntent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                        mParams.adminExtrasBundle);
            }

            sendOrderedBroadcastAsUser(completeIntent, managedUserHandle, null,
                    mdmReceivedSuccessReceiver, null, Activity.RESULT_OK, null, null);
            ProvisionLogger.logd("Provisioning complete broadcast has been sent to user "
                    + managedUserHandle.getIdentifier());
        }
    }

    @Override
    public void progressUpdate(int progressMessageId) {
        // TODO: Do something
    }

    @Override
    public void error(int errorMessageId, boolean factoryResetRequired) {
        error("Error executing provisioning controller", new Exception());
    }

    @Override
    public void cancelled() {
        notifyActivityCancelled();
    }

    /**
     * Record the fact that an error occurred, change mProvisioningStatus to
     * reflect the fact the provisioning process failed
     */
    private void error(String dialogMessage, Exception e) {
        ProvisionLogger.logw("Error occured during provisioning process: " + dialogMessage, e);
        notifyActivityError(dialogMessage);
    }

    private void notifyActivityError(String message) {
        Intent intent = new Intent(ACTION_PROVISIONING_ERROR);
        intent.putExtra(EXTRA_LOG_MESSAGE_KEY, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void notifyActivityCancelled() {
        Intent cancelIntent = new Intent(ACTION_PROVISIONING_CANCELLED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(cancelIntent);
    }

    private void notifyActivityOfSuccess() {
        Intent successIntent = new Intent(ACTION_PROVISIONING_SUCCESS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(successIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public boolean isProvisioningManagedUser() {
        return DevicePolicyManager.ACTION_PROVISION_MANAGED_USER.equals(mParams.provisioningAction);
    }

    /**
     * BroadcastReceiver that listens to {@link Intent#ACTION_USER_UNLOCKED} in order to provide
     * a blocking wait until the managed profile has been started and unlocked.
     */
    private static class UserUnlockedReceiver extends BroadcastReceiver {
        private static final IntentFilter FILTER = new IntentFilter(Intent.ACTION_USER_UNLOCKED);

        private final Semaphore semaphore = new Semaphore(0);
        private final Context mContext;
        private final int mUserId;

        UserUnlockedReceiver(Context context, int userId) {
            mContext = context;
            mUserId = userId;
            mContext.registerReceiverAsUser(this, new UserHandle(userId), FILTER, null, null);
        }

        @Override
        public void onReceive(Context context, Intent intent ) {
            if (!Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                ProvisionLogger.logw("Unexpected intent: " + intent);
                return;
            }
            if (intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL) == mUserId) {
                ProvisionLogger.logd("Received ACTION_USER_UNLOCKED for user " + mUserId);
                semaphore.release();
                mContext.unregisterReceiver(this);
            }
        }

        public boolean waitForUserUnlocked() {
            ProvisionLogger.logd("Waiting for ACTION_USER_UNLOCKED");
            try {
                return semaphore.tryAcquire(USER_UNLOCKED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                mContext.unregisterReceiver(this);
                return false;
            }
        }
    }
 }
