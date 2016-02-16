/*
 * Copyright 2014, The Android Open Source Project
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
package com.android.managedprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.managedprovisioning.common.Utils;

/**
 * Class that handles the resuming process that takes place after a reboot during the provisioning
 * process. The reboot could be an unexpected reboot or a reboot during the encryption process.
 */
public class BootReminder extends BroadcastReceiver {
    private static final int NOTIFY_ID = 1;

    static final ComponentName PROFILE_OWNER_INTENT_TARGET =
            ProfileOwnerPreProvisioningActivity.ALIAS_NO_CHECK_CALLER;

    static final ComponentName DEVICE_OWNER_INTENT_TARGET =
            new ComponentName("com.android.managedprovisioning",
                    "com.android.managedprovisioning.DeviceOwnerPreProvisioningActivity");

    private static final String BOOT_REMINDER_INTENT_STORE_NAME = "boot-reminder";

    private final Utils mUtils = new Utils();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (android.content.Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            IntentStore intentStore = getIntentStore(context);
            Intent loadedIntent = intentStore.load();
            intentStore.clear();

            if (loadedIntent != null) {
                String action = loadedIntent.getAction();

                if (mUtils.isProfileOwnerAction(action)) {
                    if (!EncryptDeviceActivity.isPhysicalDeviceEncrypted()) {
                        ProvisionLogger.loge("Device is not encrypted after provisioning with"
                                + " action " + action + " but it should");
                        return;
                    }
                    if (mUtils.isUserSetupCompleted(context)) {
                        setNotification(context, loadedIntent);
                    } else {
                        TrampolineActivity.startActivity(context, loadedIntent);
                    }
                } else if (mUtils.isDeviceOwnerAction(action)) {
                    TrampolineActivity.startActivity(context, loadedIntent);
                } else {
                    ProvisionLogger.loge("Unknown intent action loaded from the intent store: "
                            + action);
                }
            }
        }
    }

    /**
     * Schedule a provisioning reminder notification for the next reboot.
     * The intent passed in argument will be restarted after the next reboot.
     */
    public static void setProvisioningReminder(Context context, Intent intent) {
        getIntentStore(context).save(intent);
    }

    /**
     * Cancel all active provisioning reminders.
     */
    public static void cancelProvisioningReminder(Context context) {
        getIntentStore(context).clear();
        setNotification(context, null);
    }

    /** Create and show the provisioning reminder notification. */
    private static void setNotification(Context context, Intent intent) {
        final NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (intent == null) {
            notificationManager.cancel(NOTIFY_ID);
            return;
        }
        final Intent trampolineIntent = TrampolineActivity.createIntent(context, intent);
        final PendingIntent resumePendingIntent = PendingIntent.getActivity(
                context, 0, trampolineIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        final Notification.Builder notify = new Notification.Builder(context)
                .setContentIntent(resumePendingIntent)
                .setContentTitle(context.getString(R.string.continue_provisioning_notify_title))
                .setContentText(context.getString(R.string.continue_provisioning_notify_text))
                .setSmallIcon(com.android.internal.R.drawable.ic_corp_statusbar_icon)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(context.getResources().getColor(
                        com.android.internal.R.color.system_notification_accent_color))
                .setAutoCancel(true);
        notificationManager.notify(NOTIFY_ID, notify.build());
    }

    private static IntentStore getIntentStore(Context context) {
        return new IntentStore(context, BOOT_REMINDER_INTENT_STORE_NAME);
    }
}
