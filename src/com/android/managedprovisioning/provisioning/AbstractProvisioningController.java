/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller that manages the provisioning process. It controls the order of provisioning tasks,
 * reacts to errors and user cancellation.
 */
public abstract class AbstractProvisioningController implements AbstractProvisioningTask.Callback {

    @VisibleForTesting
    static final int MSG_RUN_TASK = 1;

    protected final Context mContext;
    protected final ProvisioningParams mParams;
    protected int mUserId;

    private final ProvisioningServiceInterface mService;
    private final Handler mHandler;

    private static final int STATUS_NOT_STARTED = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_DONE = 2;
    private static final int STATUS_ERROR = 3;
    private static final int STATUS_CANCELLING = 4;
    private static final int STATUS_CANCELLED = 5;

    private int mStatus = STATUS_NOT_STARTED;
    private Pair<Integer, Boolean> mError;
    private List<AbstractProvisioningTask> mTasks = new ArrayList<>();

    protected int mCurrentTaskIndex;

    public AbstractProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningServiceInterface service,
            Handler handler) {
        mContext = checkNotNull(context);
        mParams = checkNotNull(params);
        mUserId = userId;
        mService = checkNotNull(service);
        mHandler = checkNotNull(handler);
    }

    /**
     * Initialize the provisioning controller. This will load the necessary provisioning tasks.
     */
    public void initialize() {
        setUpTasks();
    }

    protected void addTasks(AbstractProvisioningTask... tasks) {
        for (AbstractProvisioningTask task : tasks) {
            mTasks.add(task);
        }
    }

    protected abstract void setUpTasks();
    protected abstract void performCleanup();
    protected abstract int getErrorMsgId(AbstractProvisioningTask task, int errorCode);
    protected abstract boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode);

    /**
     * Start the provisioning process. The tasks loaded in {@link #initialize()} will be processed
     * one by one and the respective callbacks will be given to the UI.
     */
    public void start() {
        if (mStatus != STATUS_NOT_STARTED) {
            return;
        }

        mStatus = STATUS_RUNNING;
        runTask(0);
    }

    /**
     * Invoke a callback to the service about the current status of proceedings.
     */
    public void updateStatus() {
        switch (mStatus) {
            case STATUS_NOT_STARTED: {
                start();
                break;
            }
            case STATUS_RUNNING: {
                updateProgress();
                break;
            }
            case STATUS_ERROR: {
                mService.error(mError.first, mError.second);
                break;
            }
            case STATUS_CANCELLING: {
                // No callback, wait for cancelling to complete
                break;
            }
            case STATUS_CANCELLED: {
                mService.cancelled();
                break;
            }
            case STATUS_DONE: {
                mService.provisioningComplete();
                break;
            }
        }
    }

    /**
     * Cancel the provisioning progress. When the cancellation is complete, the
     * {@link ProvisioningServiceInterface#cancelled()} callback will be given.
     */
    public void cancel() {
        if (mStatus != STATUS_RUNNING) {
            return;
        }

        ProvisionLogger.logd("ProvisioningController: cancelled");
        mStatus = STATUS_CANCELLING;
        cleanup(STATUS_CANCELLED);
    }

    private void runTask(int index) {
        Message msg = mHandler.obtainMessage(MSG_RUN_TASK, mUserId, 0 /* arg2 not used */,
                mTasks.get(index));
        mHandler.sendMessage(msg);
        updateProgress();
    }

    private void updateProgress() {
        AbstractProvisioningTask task = mTasks.get(mCurrentTaskIndex);

        if (task != null) {
            mService.progressUpdate(task.getStatusMsgId());
        }
    }

    private void provisioningComplete() {
        mStatus = STATUS_DONE;
        mCurrentTaskIndex = -1;
        mService.provisioningComplete();
    }

    @Override
    public void onSuccess(AbstractProvisioningTask task) {
        if (mStatus != STATUS_RUNNING) {
            return;
        }

        mCurrentTaskIndex++;
        if (mCurrentTaskIndex == mTasks.size()) {
            provisioningComplete();
        } else {
            runTask(mCurrentTaskIndex);
        }
    }

    @Override
    public void onError(AbstractProvisioningTask task, int errorCode) {
        mError = Pair.create(
                getErrorMsgId(task, errorCode),
                getRequireFactoryReset(task, errorCode));
        mStatus = STATUS_ERROR;
        cleanup(STATUS_ERROR);
        mService.error(mError.first, mError.second);
    }

    private void cleanup(final int newStatus) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                performCleanup();
                mStatus = newStatus;
                mService.cancelled();
            }
        });
    }

    /**
     * Handler that runs the provisioning tasks.
     *
     * <p>We're using a {@link HandlerThread} for all the provisioning tasks in order to not
     * block the UI thread.</p>
     */
    protected static class ProvisioningTaskHandler extends Handler {
        public ProvisioningTaskHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN_TASK) {
                AbstractProvisioningTask task = (AbstractProvisioningTask) msg.obj;
                ProvisionLogger.logd("Running task: " + task.getClass().getSimpleName());
                task.run(msg.arg1);
            } else {
                ProvisionLogger.loge("Unknown message: " + msg.what);
            }
        }
    }

    /**
     * Interface for communication with the provisioning service and in result with the UI.
     */
    public interface ProvisioningServiceInterface {
        /**
         * Method called when the provisioning process was successfully cancelled.
         */
        void cancelled();

        /**
         * Method called when an error was encountered during the provisioning process.
         *
         * @param errorMessageId resource id of the error message to be displayed to the user.
         * @param factoryResetRequired indicating whether a factory reset is necessary.
         */
        void error(int errorMessageId, boolean factoryResetRequired);

        /**
         * Method called to indicate a progress update in the provisioning process.
         *
         * @param progressMessageId resource id of the progress message to be displayed to the user.
         */
        void progressUpdate(int progressMessageId);

        /**
         * Method called to indicate that the provisioning process has successfully completed.
         */
        void provisioningComplete();
    }
}
