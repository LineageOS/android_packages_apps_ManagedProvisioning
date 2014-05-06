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

import static android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.os.Bundle;

/**
 * Class to handle showing a prompt to continue provisioning after the device is rebooted for
 * setting up encryption
 */
public class BootReminder extends BroadcastReceiver {
    private static final int NOTIFY_ID = 1;

    private static final String PREFERENCES_NAME = "managed-provisioning-resume";
    private static final String RESUME_DATA_SETTING = "resume-uri";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (android.content.Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            final IntentStore store = new IntentStore(context);
            final Intent resumeIntent = store.load();

            if (resumeIntent != null ) {
                // Show reminder notification and then forget about it for next boot
                setNotification(context, resumeIntent);
                store.clear();
            }
        }
    }

    /**
     * Schedule a provisioning reminder notification for the next reboot.
     *
     * {@code extras} should be a Bundle containing values for at least the following set of keys:
     * <ul>
     * <li>{@link EXTRA_DEVICE_ADMIN}, the {@link ComponentName} for the admin receiver to
     *     set up.</li>
     * <li>{@link EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME}, a {@link String} giving a
     *     default name to suggest to the user for the new managed profile.</li>
     * <li>{@link EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME}, a {@link String} specifying the
     *     package to set as profile owner.</li>
     *
     * These fields will be persisted and restored to the provisioner after rebooting. Any other
     * key/value pairs will be ignored.
     */
    public static void setProvisioningReminder(Context context, Bundle extras) {
        new IntentStore(context).save(extras);
    }

    /** Helper class to load/save resume information from Intents into a SharedPreferences */
    private static class IntentStore {
        private SharedPreferences mPrefs;
        private Context mContext;

        private static final String[] stringKeys = {
            EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME,
            EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME
        };
        private static final String[] componentKeys = {
            EXTRA_DEVICE_ADMIN
        };

        public IntentStore(Context context) {
            mContext = context;
            mPrefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }

        public void clear() {
            mPrefs.edit().clear().commit();
        }

        public void save(Bundle data){
            SharedPreferences.Editor editor = mPrefs.edit();

            editor.clear();
            for (String stringKey : stringKeys) {
                editor.putString(stringKey, data.getString(stringKey));
            }
            for (String componentKey : componentKeys) {
                editor.putString(componentKey, ((ComponentName)
                        data.getParcelable(componentKey)).flattenToShortString());
            }
            editor.commit();
        }

        public Intent load() {
            Intent result = new Intent(mContext, ManagedProvisioningActivity.class);

            for (String key : stringKeys) {
                String value = mPrefs.getString(key, null);
                if (value == null) {
                    return null;
                }
                result.putExtra(key, value);
            }
            for (String key : componentKeys) {
                String value = mPrefs.getString(key, null);
                if (value == null) {
                    return null;
                }
                result.putExtra(key, ComponentName.unflattenFromString(value));
            }

            return result;
        }
    }

    /** Create and show the provisioning reminder notification. */
    private static void setNotification(Context context, Intent intent) {
        final NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        final PendingIntent resumePendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final Notification.Builder notify = new Notification.Builder(context)
                .setContentIntent(resumePendingIntent)
                .setContentTitle(context.getString(R.string.continue_provisioning_notify_title))
                .setContentText(context.getString(R.string.continue_provisioning_notify_text))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFY_ID, notify.build());
    }
}
