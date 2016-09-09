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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.CopyAccountToUserTask;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;
import com.android.managedprovisioning.task.DeviceOwnerInitializeProvisioningTask;
import com.android.managedprovisioning.task.DisallowAddUserTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.SetDevicePolicyTask;

/**
 * Controller for Device Owner provisioning.
 */
public class DeviceOwnerProvisioningController extends AbstractProvisioningController {

    public DeviceOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningServiceInterface service,
            Looper looper) {
        this(context, params, userId, service,
                new AbstractProvisioningController.ProvisioningTaskHandler(looper));
    }

    @VisibleForTesting
    DeviceOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningServiceInterface service,
            Handler handler) {
        super(context, params, userId, service, handler);
    }

    protected void setUpTasks() {
        addTasks(new DeviceOwnerInitializeProvisioningTask(mContext, mParams, this));

        if (mParams.wifiInfo != null) {
            addTasks(new AddWifiNetworkTask(mContext, mParams, this));
        }

        if (mParams.deviceAdminDownloadInfo != null) {
            DownloadPackageTask downloadTask = new DownloadPackageTask(mContext, mParams, this);
            addTasks(downloadTask,
                    new InstallPackageTask(downloadTask, mContext, mParams, this));
        }

        addTasks(
                new DeleteNonRequiredAppsTask(true /* new profile */, mContext, mParams, this),
                new SetDevicePolicyTask(mContext, mParams, this),
                new DisallowAddUserTask(mContext, mParams, this)
        );

        if (mParams.accountToMigrate != null) {
            addTasks(new CopyAccountToUserTask(UserHandle.USER_SYSTEM, mContext, mParams, this));
        }
    }

    @Override
    protected int getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        if (task instanceof AddWifiNetworkTask) {
            return R.string.device_owner_error_wifi;
        } else if (task instanceof DownloadPackageTask) {
            switch(errorCode) {
                case DownloadPackageTask.ERROR_HASH_MISMATCH:
                    return R.string.device_owner_error_hash_mismatch;
                case DownloadPackageTask.ERROR_DOWNLOAD_FAILED:
                    return R.string.device_owner_error_download_failed;
                default:
                    return R.string.device_owner_error_general;
            }
        } else if (task instanceof InstallPackageTask) {
            switch (errorCode) {
                case InstallPackageTask.ERROR_PACKAGE_INVALID:
                    return R.string.device_owner_error_package_invalid;
                case InstallPackageTask.ERROR_INSTALLATION_FAILED:
                    return R.string.device_owner_error_installation_failed;
                default:
                    return R.string.device_owner_error_general;
            }
        }

        return R.string.device_owner_error_general;
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        return !((task instanceof AddWifiNetworkTask)
                || (task instanceof DeviceOwnerInitializeProvisioningTask));
    }

    @Override
    protected void performCleanup() {
        // Do nothing, because a factory reset will be triggered.
    }
}
