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

package com.android.managedprovisioning.provisioning;

import static com.android.managedprovisioning.provisioning.Constants.ACTION_CANCEL_PROVISIONING;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_GET_PROVISIONING_STATE;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROGRESS_UPDATE;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_CANCELLED;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_ERROR;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_SUCCESS;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_START_PROVISIONING;
import static com.android.managedprovisioning.provisioning.Constants.EXTRA_FACTORY_RESET_REQUIRED;
import static com.android.managedprovisioning.provisioning.Constants.EXTRA_PROGRESS_MESSAGE_ID_KEY;
import static com.android.managedprovisioning.provisioning.Constants.EXTRA_USER_VISIBLE_ERROR_ID_KEY;

import android.app.Service;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.UserHandle;
import android.support.v4.content.LocalBroadcastManager;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.finalization.FinalizationController;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Service that runs the provisioning process.
 *
 * <p>This service is started from and sends updates to the {@link ProvisioningActivity} that
 * contains the provisioning UI.</p>
 *
 * <p>The actual execution of the various provisioning tasks is handled by the
 * {@link AbstractProvisioningController} and the main purpose of this service is to decouple the
 * task execution from the activity life-cycle.</p>
 */
public class ProvisioningService extends Service
        implements AbstractProvisioningController.ProvisioningServiceInterface {
    private ProvisioningParams mParams;

    private final Utils mUtils = new Utils();
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker =
            new ProvisioningAnalyticsTracker();

    private AbstractProvisioningController mController;
    private HandlerThread mHandlerThread;

    @Override
    public void onCreate() {
        super.onCreate();

        mHandlerThread = new HandlerThread("ProvisioningHandler");
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
                ProvisionLogger.logd("Cancelling provisioning service");
                if (mController != null) {
                    mController.cancel();
                } else {
                    ProvisionLogger.logw("Cancelling provisioning, but controller is null");
                }
                break;
            case ACTION_START_PROVISIONING:
                if (mController == null) {
                    ProvisionLogger.logd("Starting provisioning service");
                    mParams =
                            intent.getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
                    mProvisioningAnalyticsTracker.logProvisioningStarted(this, mParams);
                    mController = buildController();
                    mController.initialize();
                    mController.start();
                } else {
                    ProvisionLogger.loge("Provisioning start requested,"
                            + " but controller not null");
                    error(R.string.device_owner_error_general, false);
                }
                break;
            case ACTION_GET_PROVISIONING_STATE:
                if (mController == null) {
                    ProvisionLogger.loge("Provisioning status requested,"
                            + " but provisioning not ongoing");
                    error(R.string.device_owner_error_general, false);
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
     * This method constructs the controller used for the given type of provisioning.
     */
    private AbstractProvisioningController buildController() {
        if (mUtils.isDeviceOwnerAction(mParams.provisioningAction)) {
            return new DeviceOwnerProvisioningController(
                    this,
                    mParams,
                    UserHandle.myUserId(),
                    this,
                    mHandlerThread.getLooper());
        } else {
            return new ProfileOwnerProvisioningController(
                    this,
                    mParams,
                    UserHandle.myUserId(),
                    this,
                    mHandlerThread.getLooper());
        }
    }

    /**
     * Called when the new profile or managed user is ready for provisioning (the profile is created
     * and all the apps not needed have been deleted).
     */
    @Override
    public void provisioningComplete() {
        // Set DPM userProvisioningState appropriately and persists mParams for use during
        // FinalizationActivity if necessary.
        new FinalizationController(this).provisioningInitiallyDone(mParams);
        Intent successIntent = new Intent(ACTION_PROVISIONING_SUCCESS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(successIntent);
    }

    @Override
    public void progressUpdate(int progressMessage) {
        Intent intent = new Intent(ACTION_PROGRESS_UPDATE);
        intent.putExtra(EXTRA_PROGRESS_MESSAGE_ID_KEY, progressMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void error(int dialogMessage, boolean factoryResetRequired) {
        Intent intent = new Intent(ACTION_PROVISIONING_ERROR);
        intent.putExtra(EXTRA_USER_VISIBLE_ERROR_ID_KEY, dialogMessage);
        intent.putExtra(EXTRA_FACTORY_RESET_REQUIRED, factoryResetRequired);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void cancelled() {
        Intent cancelIntent = new Intent(ACTION_PROVISIONING_CANCELLED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(cancelIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
