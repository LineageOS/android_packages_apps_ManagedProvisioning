package com.android.managedprovisioning.task;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Task to install an existing package on a given user.
 */
public class InstallExistingPackageTask extends AbstractProvisioningTask {

    public InstallExistingPackageTask(
            Context context,
            ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);
    }

    public int getStatusMsgId() {
        return R.string.progress_install;
    }

    @Override
    public void run(int userId) {
        PackageManager pm = mContext.getPackageManager();
        try {
            int status = pm.installExistingPackageAsUser(
                    mProvisioningParams.deviceAdminComponentName.getPackageName(), userId);
            if (status == PackageManager.INSTALL_SUCCEEDED) {
                success();
            } else {
                ProvisionLogger.loge("Install failed, result code = " + status);
                error(0);
            }
        } catch (PackageManager.NameNotFoundException e) {
            error(0);
        }

    }
}
