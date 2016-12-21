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

package com.android.managedprovisioning.ota;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.provisioning.ProvisioningService;
import com.android.managedprovisioning.task.AbstractProvisioningTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that executes the provisioning tasks during the OTA process.
 */
public class TaskExecutor implements AbstractProvisioningTask.Callback {

    @VisibleForTesting
    static final Intent SERVICE_INTENT = new Intent().setComponent(new ComponentName(
            Globals.MANAGED_PROVISIONING_PACKAGE_NAME,
            ProvisioningService.class.getName()));

    @GuardedBy("this")
    private List<AbstractProvisioningTask> mOngoingTasks = new ArrayList<>();

    private final Context mContext;

    public TaskExecutor(Context context) {
        mContext = checkNotNull(context);
    }

    public synchronized void execute(int userId, AbstractProvisioningTask task) {
        if (mOngoingTasks.isEmpty()) {
            mContext.startService(SERVICE_INTENT);
        }

        mOngoingTasks.add(task);
        task.run(userId);
    }

    @Override
    public void onSuccess(AbstractProvisioningTask task) {
        ProvisionLogger.logd("Task ran successfully: " + task.getClass().getSimpleName());
        taskCompleted(task);
    }

    @Override
    public void onError(AbstractProvisioningTask task, int errorMsg) {
        ProvisionLogger.logd("Error running task: " + task.getClass().getSimpleName());
        taskCompleted(task);
    }

    private synchronized void taskCompleted(AbstractProvisioningTask task) {
        mOngoingTasks.remove(task);

        if (mOngoingTasks.isEmpty()) {
            mContext.stopService(SERVICE_INTENT);
        }
    }
}
