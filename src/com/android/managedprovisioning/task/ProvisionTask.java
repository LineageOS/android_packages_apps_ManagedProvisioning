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
import android.text.TextUtils;

import com.android.managedprovisioning.ProvisionLogger;

/**
 * Base class for any required task as a part of provisioning.  It allows the task to fail
 * or throw any exception and will be tried with either exponential or bump-specified retry
 * delays.
 */
public abstract class ProvisionTask implements Runnable {
    protected String mTaskName;
    protected TaskManager mTaskManager;
    protected Context mContext;
    protected String[] mArguments;
    private boolean mHasSentSuccess;
    private int mId;

    protected String mLastFailure;

    public ProvisionTask(String name) {
        mTaskName = name;
        mHasSentSuccess = false;
    }

    public String getName() {
        return mTaskName;
    }

    /**
     * Call this when task is successful.
     */
    public void onSuccess() {
        if (!mHasSentSuccess) {
            mTaskManager.completeTask(mId);
            mHasSentSuccess = true;
        } else {
            ProvisionLogger.logd(mTaskName + " tried to send success more than once");
        }
    }

    /**
     * Call this when a task has failed and needs to be retried.
     * @param string
     */
    public void onFailure(String reason) {
        mLastFailure = reason;
        int retryDelay = mTaskManager.getRetryDelay();
        ProvisionLogger.logd(mTaskName + " Requesting retry in " + retryDelay + " seconds");
        onFailure(retryDelay * 1000);
    }

    /**
     * Call this when a task has failed and needs to be retried in millis ms later.
     * @param millis Number of millis to delay.
     */
    private void onFailure(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mTaskManager.requestRetry(mId);
    }

    @Override
    public void run() {
        try {
            executeTask(mArguments);
        } catch (Exception e) {
            ProvisionLogger.logw("Found exception", e);
            onFailure(e + "");
        }
    }

    public void start() {
        mTaskManager.getExecutor().execute(this);
    }

    public void setManager(TaskManager taskManager, Context context, int id) {
        mTaskManager = taskManager;
        mContext = context;
        mId = id;
    }

    protected boolean checkPref(String pref) {
        String prefVal = mTaskManager.getPreferences().getStringProperty(pref);
        return !TextUtils.isEmpty(prefVal) && Boolean.parseBoolean(prefVal);
    }

    public abstract void executeTask(String... params);

    public abstract void shutdown();

    public abstract void hasFailed();
}
