package com.android.managedprovisioning.task;

import android.app.AlarmManager;
import android.content.Context;

import com.android.internal.app.LocalePicker;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.Locale;

/**
 * Initialization of locale and timezone.
 */
public class DeviceOwnerInitializeProvisioningTask extends AbstractProvisioningTask {

    public DeviceOwnerInitializeProvisioningTask(Context context, ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_initialize;
    }

    @Override
    public void run(int userId) {
        setTimeAndTimezone(mProvisioningParams.timeZone, mProvisioningParams.localTime);
        setLocale(mProvisioningParams.locale);

        success();
    }

    private void setTimeAndTimezone(String timeZone, long localTime) {
        try {
            final AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
            if (timeZone != null) {
                alarmManager.setTimeZone(timeZone);
            }
            if (localTime > 0) {
                alarmManager.setTime(localTime);
            }
        } catch (Exception e) {
            ProvisionLogger.loge("Alarm manager failed to set the system time/timezone.", e);
            // Do not stop provisioning process, but ignore this error.
        }
    }

    private void setLocale(Locale locale) {
        if (locale == null || locale.equals(Locale.getDefault())) {
            return;
        }
        try {
            // If locale is different from current locale this results in a configuration change,
            // which will trigger the restarting of the activity.
            LocalePicker.updateLocale(locale);
        } catch (Exception e) {
            ProvisionLogger.loge("Failed to set the system locale.", e);
            // Do not stop provisioning process, but ignore this error.
        }
    }
}
