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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;
import com.android.managedprovisioning.task.DisableBluetoothSharingTask;
import com.android.managedprovisioning.task.DisableInstallShortcutListenersTask;
import com.android.managedprovisioning.task.InstallExistingPackageTask;
import com.android.managedprovisioning.task.ManagedProfileSettingsTask;
import com.android.managedprovisioning.task.SetDevicePolicyTask;

/**
 * Controller for Profile Owner provisioning.
 */
// TODO: Consider splitting this controller into one for managed profile and one for user owner
public class ProfileOwnerProvisioningController extends AbstractProvisioningController {

    public ProfileOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningServiceInterface service,
            Looper looper) {
        super(context, params, userId, service, looper);
    }

    @VisibleForTesting
    ProfileOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningServiceInterface service,
            Handler handler) {
        super(context, params, userId, service, handler);
    }

    protected void setUpTasks() {
        addTasks(
                new DeleteNonRequiredAppsTask(true /* new profile */, mContext, mParams, this),
                new InstallExistingPackageTask(mContext, mParams, this),
                new SetDevicePolicyTask(mContext, mParams, this));

        if (ACTION_PROVISION_MANAGED_PROFILE.equals(mParams.provisioningAction)) {
            addTasks(
                    new DisableBluetoothSharingTask(mContext, mParams, this),
                    new ManagedProfileSettingsTask(mContext, mParams, this),
                    new DisableInstallShortcutListenersTask(mContext, mParams, this));
        }
    }

    protected void performCleanup() {
        if (ACTION_PROVISION_MANAGED_PROFILE.equals(mParams.provisioningAction)) {
            ProvisionLogger.logd("Removing managed profile");
            UserManager um = mContext.getSystemService(UserManager.class);
            um.removeUser(mUserId);
        }
    }

    @Override
    protected int getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        return R.string.managed_provisioning_error_text;
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        return false;
    }
}
