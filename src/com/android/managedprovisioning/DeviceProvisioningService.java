/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;

import com.android.managedprovisioning.task.TaskManager;

/**
 * Service that allows long running operations when configuring the user.  This out lives
 * the activities associated with provisioning, e.g. when external setup is triggered.
 */
public class DeviceProvisioningService extends Service  {

    public static final String PROVISIONING_STATUS_REPORT_ACTION =
            "com.android.managedprovision.PROVISIONING_STATUS";

    public static final String PROVISIONING_STATUS_REPORT_EXTRA = "statusReport";
    public static final String PROVISIONING_STATUS_TEXT_EXTRA = "statusText";

    public static final String ORIGINAL_INTENT_KEY = "originalProvisioningIntent";

    // 1 Minute.
    private static final long RETRY_TIME_MS = 60000;

    private Preferences mPrefs;

    private TaskManager mTaskManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = new Preferences(this);

        int[] taskRetries = null;
        if (!TextUtils.isEmpty(mPrefs.getStringProperty(Preferences.TASK_RETRY_KEY))) {
            String[] split = mPrefs.getStringProperty(Preferences.TASK_RETRY_KEY).split(",");
            taskRetries = new int[split.length];
            for (int i = 0; i < split.length; ++i) {
                taskRetries[i] = Integer.parseInt(split[i]);
            }
        }

        mTaskManager = new TaskManager(this, taskRetries, mPrefs, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // This key indicates the intent that started the provisioning and contains all initial
        // parameters. We pass it to the external setup if the initial intent indicates that there
        // is one by containing Preferences.EXTERNAL_PROVISION_PKG.
        if (intent.getBooleanExtra(ORIGINAL_INTENT_KEY, false)) {
            mTaskManager.setOriginalProvisioningIntent(intent);
        }

        if (!mTaskManager.isStarted()) {
            mTaskManager.startTask();
        }

        // If we get killed. Try again.
        return START_REDELIVER_INTENT;
    }

    /**
     * Called in absolute failure case and sets an alarm to retry later.
     *
     * TODO Given the fewer failure scenarios of the generalized provisioning process
     * consider removing this.
     */
    public void failedAndRetry() {
        Intent intent = new Intent(this, DeviceProvisioningService.class);
        PendingIntent pintent = PendingIntent.getService(this, 0, intent, 0);
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long timeTillTrigger = RETRY_TIME_MS + SystemClock.elapsedRealtime();
        alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeTillTrigger, pintent);
        ProvisionLogger.logd("Failed but not during provisioning - Scheduling retry in 1 min");
        stopSelf();
    }

    /**
     * Called by the TaskManager when it wants to shut down the DeviceProvisioningService because it
     * is done.
     */
    public void stop() {
        PackageManager pkgMgr = getPackageManager();
        pkgMgr.setComponentEnabledSetting(
                ManagedProvisioningActivity.getComponentName(this),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTaskManager.shutdown();
    }

}
