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
package com.android.managedprovisioning.task;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.android.managedprovisioning.Preferences;
import com.android.managedprovisioning.ProvisionLogger;

/**
 * Launches an activity on a bump-specified package that must be privileged.
 * Then waits for a response broadcast indicating the external setup has completed.
 */
public class ExternalSetupTask extends ProvisionTask {
    public static final String EXTERNAL_PROVISION_COMPLETE =
            "android.intent.action.EXTERNAL_PROVISION_COMPLETE";
    public static final String EXTERNAL_PROVISION_ACTION =
            "android.intent.action.EXTRA_PROVISION";
    public static final String IS_PRE_MDM_KEY = "isPreMdmKey";

    public static final String SUCCESS_KEY = "success";
    public static final String FAILURE_REASON_KEY = "failureReason";

    // This task can trigger an external setup either before or after the MDM has been registered.
    // This flag controls indicates whether this task is going to be triggered before or after
    // the MDM registration.
    private final boolean mIsPreMdm;

    private BroadcastReceiver mReceiver;

    public ExternalSetupTask(boolean isPreMdm) {
        super("External Provision Task");
        mIsPreMdm = isPreMdm;
    }

    @Override
    public void executeTask(String... params) {
        String pkg = mTaskManager.getPreferences().getStringProperty(
                Preferences.EXTERNAL_PROVISION_PKG);

        if (TextUtils.isEmpty(pkg)) {
            ProvisionLogger.logd("No action, skipping external setup");
            success();
        } else {
            // TODO Add timeout for external task.
            registerReceiver();
            sendBroadcast(pkg);
        }
    }

    private void sendBroadcast(String pkg) {
        ProvisionLogger.logd("Starting activity " + pkg);
        boolean isInstalled;
        try {
            // TODO Verify this is privileged app.
            mContext.getPackageManager().getApplicationInfo(pkg, 0);
            isInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            isInstalled = false;
        }
        if (isInstalled) {
            Intent intent = new Intent(EXTERNAL_PROVISION_ACTION);
            intent.setPackage(pkg);

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(IS_PRE_MDM_KEY, mIsPreMdm);
            intent.putExtras(mTaskManager.getBumpBundle());

            mContext.startActivity(intent);
        } else {
            ProvisionLogger.logd("Cannot find package " + pkg);
        }
    }

    private void registerReceiver() {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterReceiver();
                boolean success = intent.getBooleanExtra(SUCCESS_KEY, false);
                ProvisionLogger.logd("Got response " + success);
                if (success) {
                    success();
                } else {
                    String reason = intent.getStringExtra(FAILURE_REASON_KEY);
                    if (reason == null) {
                        reason = "";
                    }
                    failure(reason);
                }
            }
        };
        mContext.registerReceiver(mReceiver,
                new IntentFilter(EXTERNAL_PROVISION_COMPLETE));
    }

    private void unregisterReceiver() {
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void hasFailed() {

    }

}
