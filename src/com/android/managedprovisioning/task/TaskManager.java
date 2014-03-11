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
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.managedprovisioning.HandsFreeProvisioningService;
import com.android.managedprovisioning.ErrorDialog;
import com.android.managedprovisioning.Preferences;
import com.android.managedprovisioning.ProvisionLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages a set of ProvisionTask objects to be executed in order if each one
 * succeeds.  Also handles some central functionality for those tasks such as
 * status reporting and access to data passed in the intent that started provisioning.
 */
public class TaskManager {
    // Allow retries so that exponential backoff is around a minute.
    private final static int MAX_RETRIES = 6;

    public static final String PROVISIONING_STATUS_REPORT_ACTION =
            "com.android.managedprovision.PROVISIONING_STATUS";

    // The setup tasks that need to be done. They depend on the type of provisioning.
    private final ProvisionTask[] mProvisionTasks;

    private int mCurrentRetries;
    private int mCurrentTask;

    private ExecutorService mExecutor;

    private Context mContext;
    private Preferences mPreferences;

    private HandsFreeProvisioningService mService;

    private boolean mHasStarted;

    // mTaskRetryDelays[i] represents the length of the ith delay
    private int[] mTaskRetryDelays;
    private Bundle mProvisioningBundle;

    public TaskManager(Context context, int[] taskRetries, Preferences preferences,
            HandsFreeProvisioningService service) {
        mCurrentRetries = 0;
        mExecutor = Executors.newCachedThreadPool();
        mContext = context;
        mPreferences = preferences;

        // Get all tasks that need to be completed to provision the device.
        mProvisionTasks = new ProvisionTask[] {
                new AddWifiNetworkTask(),
                new ExternalSetupTask(true),
                new DevicePolicyTask(),
                new ExternalSetupTask(false),
                new SendCompleteTask()
        };

        mCurrentTask = mPreferences.getIntProperty(Preferences.TASK_STATE);
        if (mCurrentTask == -1) mCurrentTask = 0;
        for (int i = 0; i < mProvisionTasks.length; ++i) {
            mProvisionTasks[i].setManager(this, mContext, i);
        }
        mService = service;
        mHasStarted = false;
        mTaskRetryDelays = taskRetries;
    }

    public void requestRetry(int mId) {
        if (mId != mCurrentTask) {
            ProvisionLogger.logd(mProvisionTasks[mId].getName()
                    + " is not the current task, cannot retry");
            return;
        }
        if (hasRetries()) {
            ++mCurrentRetries;
            startTask();
        } else {
            mProvisionTasks[mCurrentTask].hasFailed();
            String msg = "Task: " + mProvisionTasks[mCurrentTask].getName() + " has failed.";
            mPreferences.setError(msg);
            ProvisionLogger.loge(msg);
            if (mPreferences.doesntNeedResume()) {
                // Not in connectivity task.
                if (mCurrentTask != 0) {
                    mService.failedAndRetry();
                }
            } else {
                ErrorDialog.showError(mContext);
            }
        }
    }

    private boolean hasRetries() {
        if (mTaskRetryDelays != null) {
            return mCurrentRetries < mTaskRetryDelays.length;
        }
        return mCurrentRetries < MAX_RETRIES;
    }

    public void finish() {
        ProvisionLogger.logd("HandsFreeProvisioningService - All tasks complete, shutting down");
        mPreferences.setProperty(Preferences.TASK_STATE, 0);
        mService.stop();
    }

    void completeTask(int mId) {
        if (mId != mCurrentTask) {
            ProvisionLogger.logd(mProvisionTasks[mId].getName()
                    + " is not the current state, cannot complete");
            return;
        }
        if ((mCurrentTask < mProvisionTasks.length) && (mCurrentTask >= 0)) {
            ProvisionLogger.logd("Finished Task " + mProvisionTasks[mCurrentTask].getName());
        } else {
            ProvisionLogger.logd("Other Task " + mCurrentTask);
        }
        advanceTask();
        startTask();
    }

    private void advanceTask() {
        ++mCurrentTask;
        mCurrentRetries = 0;
    }

    public boolean isStarted() {
        return mHasStarted;
    }

    public void startTask() {
        if (mCurrentTask < 0) {
            ProvisionLogger.loge("Invalid task " + mCurrentTask);
            return;
        }
        if (mCurrentTask < mProvisionTasks.length) {
            ProvisionLogger.logd("Starting Task " + mProvisionTasks[mCurrentTask].getName());
            mHasStarted = true;
            mPreferences.setProperty(Preferences.TASK_STATE, mCurrentTask);
            mProvisionTasks[mCurrentTask].start();
        } else {
            finish();
        }
    }

    ExecutorService getExecutor() {
        return mExecutor;
    }

    /**
     * This should only be called for complete failures that will stop the
     * state machine from proceeding.
     *
     * Failures that can have retry's should call ProvisionTask.fail().
     */
    void fail(String message) {
      // TODO: figure out if we do need this functionality and implement it
    }

    void registerProvisioningState(int state, String reason) {
        ProvisionLogger.logd("Registering state " + state + " with reason: " + reason);

        Intent message = new Intent(PROVISIONING_STATUS_REPORT_ACTION);
        if (state != -1) {
            message.putExtra(HandsFreeProvisioningService.PROVISIONING_STATUS_REPORT_EXTRA, state);
        }
        if (!TextUtils.isEmpty(reason)) {
            message.putExtra(HandsFreeProvisioningService.PROVISIONING_STATUS_TEXT_EXTRA, reason);
        }
        mContext.sendBroadcast(message);
    }

    Preferences getPreferences() {
        return mPreferences;
    }

    public void shutdown() {
        for (ProvisionTask task : mProvisionTasks) {
            task.shutdown();
        }
    }

    int getRetryDelay() {
        if (mTaskRetryDelays != null) {
            if (mCurrentRetries >= mTaskRetryDelays.length) {
                return 0;
            }
            return mTaskRetryDelays[mCurrentRetries];
        } else {
            return (int)Math.pow(2, mCurrentRetries);
        }
    }

    public void setOriginalProvisioningIntent(Intent intent) {
        mProvisioningBundle = intent.getExtras();
    }

    /**
     * Returns the bundle that contains the provisioning parameters.
     */
    public Bundle getProvisioningBundle() {
        return mProvisioningBundle;
    }
}
