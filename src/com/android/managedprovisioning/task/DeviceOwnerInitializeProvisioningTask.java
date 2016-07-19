package com.android.managedprovisioning.task;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.internal.app.LocalePicker;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.Locale;

/**
 * Initialization of locale, timezone and activation of the CDMA phone connection by OTASP.
 */
public class DeviceOwnerInitializeProvisioningTask extends AbstractProvisioningTask {

    /**
     * Intent action to activate the CDMA phone connection by OTASP.
     * This is not necessary for a GSM phone connection, which is activated automatically.
     * String must agree with the constants in com.android.phone.InCallScreenShowActivation.
     */
    private static final String ACTION_PERFORM_CDMA_PROVISIONING =
            "com.android.phone.PERFORM_CDMA_PROVISIONING";

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

        // Start CDMA activation to enable phone calls.
        // TODO: Investigate whether we need to wait on this. Could this be moved to SUW?
        final Intent intent = new Intent(ACTION_PERFORM_CDMA_PROVISIONING);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent); // Activity will be a Nop if not a CDMA device.

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
