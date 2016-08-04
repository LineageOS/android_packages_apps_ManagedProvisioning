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

import android.app.Service;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v4.content.LocalBroadcastManager;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.AbstractProvisioningController;
import com.android.managedprovisioning.provisioning.DeviceOwnerProvisioningController;

/**
 * This service does the work for the DeviceOwnerProvisioningActivity.
 * Feedback is sent back to the activity via intents.
 *
 * <p>
 * If the corresponding activity is killed and restarted, the service is
 * called twice. The service will not start the provisioning flow a second time, but instead
 * send a status update to the activity.
 * </p>
 */
public class DeviceOwnerProvisioningService extends Service
        implements AbstractProvisioningController.ProvisioningServiceInterface {

    // Intent actions and extras for communication from DeviceOwnerProvisioningService to Activity.
    protected static final String ACTION_PROVISIONING_SUCCESS =
            "com.android.managedprovisioning.provisioning_success";
    protected static final String ACTION_PROVISIONING_ERROR =
            "com.android.managedprovisioning.error";
    protected static final String EXTRA_USER_VISIBLE_ERROR_ID_KEY =
            "UserVisibleErrorMessage-Id";
    protected static final String EXTRA_FACTORY_RESET_REQUIRED =
            "FactoryResetRequired";
    protected static final String ACTION_PROGRESS_UPDATE =
            "com.android.managedprovisioning.progress_update";
    protected static final String EXTRA_PROGRESS_MESSAGE_ID_KEY =
            "ProgressMessageId";

    private AbstractProvisioningController mController;
    private ProvisioningParams mParams;

    private final Utils mUtils = new Utils();
    private HandlerThread mHandlerThread;

    @Override
    public void onCreate() {
        mHandlerThread = new HandlerThread("DeviceOwnerProvisioningHandler");
        mHandlerThread.start();
    }

    @Override
    public void onDestroy() {
        mHandlerThread.quitSafely();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        synchronized (this) {
            if (mController == null) {
                ProvisionLogger.logd("Starting device owner provisioning");
                mParams = intent.getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
                mController = new DeviceOwnerProvisioningController(
                        this,
                        mParams,
                        UserHandle.myUserId(),
                        this,
                        mHandlerThread.getLooper());
                mController.initialize();
                mController.start();
            } else {
                mController.updateStatus();
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void error(int dialogMessage, boolean factoryResetRequired) {
        Intent intent = new Intent(ACTION_PROVISIONING_ERROR);
        intent.setClass(this, DeviceOwnerProvisioningActivity.ServiceMessageReceiver.class);
        intent.putExtra(EXTRA_USER_VISIBLE_ERROR_ID_KEY, dialogMessage);
        intent.putExtra(EXTRA_FACTORY_RESET_REQUIRED, factoryResetRequired);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void progressUpdate(int progressMessage) {
        Intent intent = new Intent(ACTION_PROGRESS_UPDATE);
        intent.setClass(this, DeviceOwnerProvisioningActivity.ServiceMessageReceiver.class);
        intent.putExtra(EXTRA_PROGRESS_MESSAGE_ID_KEY, progressMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void provisioningComplete() {
        // Copying an account needs to happen late in the provisioning process to allow the current
        // user to be started, but before we tell the MDM that provisioning succeeded.
        maybeCopyAccount();

        // Set DPM userProvisioningState appropriately and persists mParams for use during
        // FinalizationActivity if necessary.
        mUtils.markUserProvisioningStateInitiallyDone(this, mParams);

        Intent successIntent = new Intent(ACTION_PROVISIONING_SUCCESS);
        successIntent.setClass(this, DeviceOwnerProvisioningActivity.ServiceMessageReceiver.class);
        LocalBroadcastManager.getInstance(this).sendBroadcast(successIntent);
        // Wait for stopService() call from the activity.
    }

    @Override
    public void cancelled() {
        // Do nothing, as device will be factory reset
    }

    // TODO: Move this into its own task once ProfileOwnerProvisioningService is refactored.
    private void maybeCopyAccount() {
        if (!UserManager.isSplitSystemUser()) {
            // Only one user involved in this case.
            return;
        }

        mUtils.maybeCopyAccount(DeviceOwnerProvisioningService.this,
                mParams.accountToMigrate, UserHandle.SYSTEM,
                Process.myUserHandle());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
