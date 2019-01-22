/*
 * Copyright 2019, The Android Open Source Project
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

import android.content.Context;
import android.os.UserHandle;

import com.android.internal.annotations.GuardedBy;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Singleton instance that provides communications between the ongoing admin integrated flow
 * preparing process and the UI layer.
 */
// TODO(b/123288153): Rearrange provisioning activity, manager, controller classes.
class AdminIntegratedFlowPrepareManager implements ProvisioningControllerCallback,
        ProvisioningManagerInterface {

    private static AdminIntegratedFlowPrepareManager sInstance;

    private final Context mContext;
    private final ProvisioningManagerHelper mHelper;

    @GuardedBy("this")
    private AbstractProvisioningController mController;

    private AdminIntegratedFlowPrepareManager(Context context) {
        mContext = context;
        mHelper = new ProvisioningManagerHelper(context);
    }

    static AdminIntegratedFlowPrepareManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AdminIntegratedFlowPrepareManager(context.getApplicationContext());
        }
        return sInstance;
    }

    @Override
    public void maybeStartProvisioning(ProvisioningParams params) {
        synchronized (this) {
            if (mController == null) {
                mController = getController(params);
                mHelper.startNewProvisioningLocked(mController);
            } else {
                ProvisionLogger.loge("Trying to start admin integrated flow preparing, "
                        + "but it's already running");
            }
        }
    }

    @Override
    public void registerListener(ProvisioningManagerCallback callback) {
        mHelper.registerListener(callback);
    }

    @Override
    public void unregisterListener(ProvisioningManagerCallback callback) {
        mHelper.unregisterListener(callback);
    }

    @Override
    public void cancelProvisioning() {
        mHelper.cancelProvisioning(mController);
    }

    @Override
    public void provisioningTasksCompleted() {
        preFinalizationCompleted();
    }

    @Override
    public void preFinalizationCompleted() {
        synchronized (this) {
            mHelper.notifyPreFinalizationCompleted();
            clearControllerLocked();
            ProvisionLogger.logi("AdminIntegratedFlowPrepareManager pre-finalization completed");
        }
    }

    @Override
    public void cleanUpCompleted() {
        synchronized (this) {
            clearControllerLocked();
        }
    }

    @Override
    public void error(int titleId, int messageId, boolean factoryResetRequired) {
        mHelper.error(titleId, messageId, factoryResetRequired);
    }

    private AbstractProvisioningController getController(ProvisioningParams params) {
        return new AdminIntegratedFlowPrepareController(
                mContext,
                params,
                UserHandle.myUserId(),
                this);
    }

    private void clearControllerLocked() {
        mController = null;
        mHelper.clearResourcesLocked();
    }
}
