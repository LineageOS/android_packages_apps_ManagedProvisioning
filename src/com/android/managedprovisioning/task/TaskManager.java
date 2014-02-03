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

import com.android.managedprovisioning.ConfigureUserService;
import com.android.managedprovisioning.ErrorDialog;
import com.android.managedprovisioning.ManagedProvisioningActivity;
import com.android.managedprovisioning.Preferences;
import com.android.managedprovisioning.ProvisionLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages a set of ProvisionTask objects to be executed in order if each one
 * succeeds.  Also handles some central functionality for those tasks such as
 * status reporting and access to data passed in the bump.
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

    public interface CompletionListener {
        public void allTasksCompleted(boolean successful, String message);
    }

    private CompletionListener mCompletionListener;

    private Context mContext;
    private Preferences mPreferences;

    private ConfigureUserService mService;

    private boolean mHasStarted;

    private int[] mTaskRetries;
    private Bundle mBumpBundle;

    public TaskManager(Context context, int[] taskRetries, Preferences preferences,
            ConfigureUserService service) {
        mCurrentRetries = 0;
        mExecutor = Executors.newCachedThreadPool();
        mContext = context;
        mPreferences = preferences;

        // Get the setup tasks for the type of provisioning that we are running.
        boolean isDeviceOwner = mPreferences.getBooleanProperty(Preferences.IS_DEVICE_OWNER_KEY);
        mProvisionTasks = isDeviceOwner
                ? getDeviceOwnerProvisioningTasks() : getSecondaryProfileProvisioningTasks();

        mCurrentTask = mPreferences.getIntProperty(Preferences.TASK_STATE);
        if (mCurrentTask == -1) mCurrentTask = 0;
        for (int i = 0; i < mProvisionTasks.length; ++i) {
            mProvisionTasks[i].setManager(this, mContext, i);
        }
        mService = service;
        mHasStarted = false;
        mTaskRetries = taskRetries;
    }

    public ProvisionTask getTask(int id) {
        return mProvisionTasks[id];
    }

    public void setCompleteListener(CompletionListener listener) {
        mCompletionListener = listener;
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
        if (mTaskRetries != null) {
            return mCurrentRetries < mTaskRetries.length;
        }
        return mCurrentRetries < MAX_RETRIES;
    }

    public void finish() {
        ProvisionLogger.logd("ConfigureUserService - All tasks complete, shutting down");
        mPreferences.setProperty(Preferences.TASK_STATE, mProvisionTasks.length);
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
        if (mCurrentTask == mProvisionTasks.length) {
            if (mCompletionListener != null) {
                mCompletionListener.allTasksCompleted(true, "");
            }
        }
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
        if (mCompletionListener != null) {
            mCompletionListener.allTasksCompleted(false, message);
        }
    }

    void registerProvisioningState(int state, String reason) {
        ProvisionLogger.logd("Registering state " + state + " with reason: " + reason);

        Intent message = new Intent(PROVISIONING_STATUS_REPORT_ACTION);
        if (state != -1) {
            message.putExtra(ConfigureUserService.PROVISIONING_STATUS_REPORT_EXTRA, state);
        }
        if (!TextUtils.isEmpty(reason)) {
            message.putExtra(ConfigureUserService.PROVISIONING_STATUS_TEXT_EXTRA, reason);
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
        if (mTaskRetries != null) {
            if (mCurrentRetries >= mTaskRetries.length) {
                return 0;
            }
            return mTaskRetries[mCurrentRetries];
        } else {
            return (int)Math.pow(2, mCurrentRetries);
        }
    }

    public void setBumpIntent(Intent intent) {
        mBumpBundle = intent.getExtras();
    }

    /**
     * Returns the bundle that contains the bump parameters.
     */
    public Bundle getBumpBundle() {
        return mBumpBundle;
    }

    private ProvisionTask[] getDeviceOwnerProvisioningTasks() {
        return new ProvisionTask[] {
                new AddWifiNetworkTask(),
                new ExternalSetupTask(true),
                new DevicePolicyTask(),
                new ExternalSetupTask(false),
                new SendCompleteTask()
        };
    }

    private ProvisionTask[] getSecondaryProfileProvisioningTasks() {
        return new ProvisionTask[] {
                new CreateProfileTask(),
                new SendCompleteTask()
        };
    }
}
