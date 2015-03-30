/*
 * Copyright 2015, The Android Open Source Project
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

package com.android.managedprovisioning.task;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.service.persistentdata.PersistentDataBlockManager;

import java.util.concurrent.atomic.AtomicBoolean;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.ProvisioningParams;
import com.android.managedprovisioning.Utils;

/**
 * Check and optionally attempt to wipe factory reset protection.
 */
public class WipeResetProtectionTask {
    private static final int TIMEOUT_MS = 60000;

    /**
     * Action set on the pending intent. A broadcast with this action will be sent with the
     * result of the FRP wipe request.
     */
    private static final String ACTION_WIPE_RESULT =
            "com.android.managedprovisioning.task.action.WIPE_RESULT";

    private final Context mContext;
    private final Callback mCallback;

    private final PersistentDataBlockManager mDataBlockManager;

    private final Handler mHandler;
    private boolean mTaskDone;

    private PendingIntent mPendingIntent;
    private BroadcastReceiver mBroadcastReceiver;

    /** Used to wipe partition. */
    private final Bundle mChallengeData;

    /**
     * @param context used to register receivers and get system services
     * @param params holds FRP unlock data
     * @param callback called when this task finishes
     */
    public WipeResetProtectionTask(Context context, ProvisioningParams params, Callback callback) {
        mContext = context;
        if (params.mFrpChallengeBundle == null) {
            mChallengeData = new Bundle();
        } else {
            mChallengeData = new Bundle(params.mFrpChallengeBundle);
        }
        mDataBlockManager = (PersistentDataBlockManager) mContext.getSystemService(
                Context.PERSISTENT_DATA_BLOCK_SERVICE);
        mCallback = callback;
        // Get looper.
        HandlerThread thread = new HandlerThread("Timeout thread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper looper = thread.getLooper();
        mHandler = new Handler(looper);
    }

    public void run() {
        if (!Utils.isCurrentUserOwner()) {
            ProvisionLogger.logd("Reset protection check skipped on secondary users.");
            mCallback.onSuccess();
            return;
        }
        if (mDataBlockManager == null) {
            ProvisionLogger.logd("Reset protection not supported.");
            mCallback.onSuccess();
            return;
        }
        ProvisionLogger.logd("Data block size: " + mDataBlockManager.getDataBlockSize());
        if (mDataBlockManager.getDataBlockSize() == 0) {
            ProvisionLogger.logd("Data block empty");
            mCallback.onSuccess();
            return;
        }
        // Setup broadcast receiver
        mBroadcastReceiver = createBroadcastReceiver();
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(ACTION_WIPE_RESULT),
                Manifest.permission.ACCESS_PDB_STATE, mHandler);
        // Create Pending Intent
        Intent intent = new Intent(ACTION_WIPE_RESULT);
        intent.setPackage(mContext.getPackageName());
        mPendingIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                Intent.FLAG_RECEIVER_FOREGROUND);
        // Attempt to wipe FRP challenge
        mDataBlockManager.wipeIfAllowed(mChallengeData, mPendingIntent);
        // Set timeout handler
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mTaskDone) return;
                mTaskDone = true;
                ProvisionLogger.loge("FRP wipe timed out.");
                cleanUp();
                mPendingIntent.cancel();
                mCallback.onError();
            }
        }, TIMEOUT_MS);
    }

    /**
     * Create a broadcast receiver to receive updates about the factory reset protection wipe
     * request.
     */
    private BroadcastReceiver createBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(ACTION_WIPE_RESULT)) {
                    int resultCode = getResultCode();
                    if (mTaskDone) {
                        ProvisionLogger.logd("Broadcast received but task already done.");
                        return;
                    }
                    mTaskDone = true;
                    cleanUp();
                    ProvisionLogger.logd("FRP result code: " + resultCode);
                    if (resultCode == PersistentDataBlockManager.STATUS_SUCCESS) {
                        mCallback.onSuccess();
                    } else {
                        mCallback.onError();
                    }
                }
            }
        };
    }

    /**
     * Unregister broadcast receiver.
     */
    public void cleanUp() {
        if (mBroadcastReceiver != null) {
            mContext.unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
