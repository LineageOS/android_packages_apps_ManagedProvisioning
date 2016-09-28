/*
 * Copyright 2016, The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton instance that provides communications between the ongoing provisioning process and the
 * UI layer.
 */
public class ProvisioningManager implements ProvisioningControllerCallback {
    private static ProvisioningManager sInstance;

    private static final int CALLBACK_NONE = 0;
    private static final int CALLBACK_ERROR = 1;
    private static final int CALLBACK_PROGRESS = 2;
    private static final int CALLBACK_CANCELLED = 3;
    private static final int CALLBACK_TASKS_COMPLETED = 5;
    private static final int CALLBACK_PRE_FINALIZED = 4;

    private final Context mContext;
    private final ProvisioningControllerFactory mFactory;
    private final Handler mUiHandler;

    @GuardedBy("this")
    private AbstractProvisioningController mController;
    @GuardedBy("this")
    private List<ProvisioningManagerCallback> mCallbacks = new ArrayList<>();

    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private int mLastCallback = CALLBACK_NONE;
    private Pair<Integer, Boolean> mLastError;
    private int mLastProgressMsgId;

    public static ProvisioningManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ProvisioningManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private ProvisioningManager(Context context) {
        this(
                context,
                new Handler(Looper.getMainLooper()),
                new ProvisioningControllerFactory(),
                ProvisioningAnalyticsTracker.getInstance());
    }

    @VisibleForTesting
    ProvisioningManager(
            Context context,
            Handler uiHandler,
            ProvisioningControllerFactory factory,
            ProvisioningAnalyticsTracker analyticsTracker) {
        mContext = checkNotNull(context);
        mUiHandler = checkNotNull(uiHandler);
        mFactory = checkNotNull(factory);
        mProvisioningAnalyticsTracker = checkNotNull(analyticsTracker);
    }

    /**
     * Initiate a new provisioning process, unless one is already ongoing.
     *
     * @param params {@link ProvisioningParams} associated with the new provisioning process.
     */
    public void initiateProvisioning(final ProvisioningParams params) {
        synchronized (this) {
            if (mController == null) {
                mLastCallback = CALLBACK_NONE;
                ProvisionLogger.logd("Initializing provisioning process");
                mController = mFactory.createProvisioningController(mContext, params, this);
                mController.initialize();
                mProvisioningAnalyticsTracker.logProvisioningStarted(mContext, params);
                mContext.startService(new Intent(Constants.ACTION_START_PROVISIONING)
                        .setComponent(new ComponentName(mContext, ProvisioningService.class)));
            } else {
                ProvisionLogger.loge("Trying to start provisioning, but it's already running");
            }
        }
   }

    /**
     * Start the provisioning process.
     *
     * @param looper looper of a worker thread.
     */
    public void startProvisioning(Looper looper) {
        synchronized (this) {
            if (mController != null) {
                mController.start(looper);
            }
        }
    }

    /**
     * Cancel the provisioning progress.
     */
    public void cancelProvisioning() {
        synchronized (this) {
            if (mController != null) {
                mController.cancel();
            } else {
                ProvisionLogger.loge("Trying to cancel provisioning, but controller is null");
            }
        }
    }

    /**
     * Prefinalize the provisioning progress.
     *
     * <p>This is the last step that this class is concerned with.</p>
     */
    public void preFinalize() {
        synchronized (this) {
            if (mController != null) {
                mController.preFinalize();
            } else {
                ProvisionLogger.loge("Trying to pre-finalize provisioning, but controller is null");
            }
        }
    }

    /**
     * Register a listener for updates of the provisioning progress.
     *
     * <p>Registering a listener will immediately result in the last callback being sent to the
     * listener. All callbacks will occur on the UI thread.</p>
     *
     * @param callback listener to be registered.
     */
    public void registerListener(ProvisioningManagerCallback callback) {
        synchronized (this) {
            mCallbacks.add(callback);
            callLastCallbackLocked(callback);
        }
    }

    /**
     * Unregister a listener from updates of the provisioning progress.
     *
     * @param callback listener to be unregistered.
     */
    public void unregisterListener(ProvisioningManagerCallback callback) {
        synchronized (this) {
            mCallbacks.remove(callback);
        }
    }

    @Override
    public void cleanUpCompleted() {
        synchronized (this) {
            for (ProvisioningControllerCallback callback : mCallbacks) {
                mUiHandler.post(() -> callback.cleanUpCompleted());
            }
            mLastCallback = CALLBACK_CANCELLED;
            finishLocked();
        }
    }

    @Override
    public void error(int errorMessageId, boolean factoryResetRequired) {
        synchronized (this) {
            for (ProvisioningControllerCallback callback : mCallbacks) {
                mUiHandler.post(() -> callback.error(errorMessageId, factoryResetRequired));
            }
            mLastCallback = CALLBACK_ERROR;
            mLastError = Pair.create(errorMessageId, factoryResetRequired);
        }
    }

    @Override
    public void progressUpdate(int progressMsgId) {
        synchronized (this) {
            for (ProvisioningControllerCallback callback : mCallbacks) {
                mUiHandler.post(() -> callback.progressUpdate(progressMsgId));
            }
            mLastCallback = CALLBACK_PROGRESS;
            mLastProgressMsgId = progressMsgId;
        }
    }

    @Override
    public void provisioningTasksCompleted() {
        synchronized (this) {
            for (ProvisioningControllerCallback callback : mCallbacks) {
                mUiHandler.post(() -> callback.provisioningTasksCompleted());
            }
            mLastCallback = CALLBACK_TASKS_COMPLETED;
        }
    }

    @Override
    public void preFinalizationCompleted() {
        synchronized (this) {
            for (ProvisioningControllerCallback callback : mCallbacks) {
                mUiHandler.post(() -> callback.preFinalizationCompleted());
            }
            mLastCallback = CALLBACK_PRE_FINALIZED;
            finishLocked();
        }
    }

    private void callLastCallbackLocked(ProvisioningManagerCallback callback) {
        switch (mLastCallback) {
            case CALLBACK_CANCELLED:
                mUiHandler.post(() -> callback.cleanUpCompleted());
                break;
            case CALLBACK_ERROR:
                final Pair<Integer, Boolean> error = mLastError;
                mUiHandler.post(() -> callback.error(error.first, error.second));
                break;
            case CALLBACK_PROGRESS:
                final int progressMsg = mLastProgressMsgId;
                mUiHandler.post(() -> callback.progressUpdate(progressMsg));
                break;
            case CALLBACK_PRE_FINALIZED:
                mUiHandler.post(() -> callback.preFinalizationCompleted());
                break;
            case CALLBACK_TASKS_COMPLETED:
                mUiHandler.post(() -> callback.provisioningTasksCompleted());
                break;
            default:
                ProvisionLogger.logd("No previous callback");
        }
    }

    // TODO: improve life-cycle management
    public void finishLocked() {
        mController = null;
        mContext.stopService(new Intent(mContext, ProvisioningService.class));
    }
}
