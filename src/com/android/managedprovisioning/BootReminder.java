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

import android.os.Bundle;

/**
 * Class to handle showing a prompt to continue provisioning after the device is rebooted for
 * setting up encryption
 */
public class BootReminder extends BroadcastReceiver {
    private static final int NOTIFY_ID = 1;

    /*
     * Profile owner parameters that are stored in the IntentStore for resuming provisioning.
     */
    private static final String PROFILE_OWNER_PREFERENCES_NAME =
            "profile-owner-provisioning-resume";

    private static final String[] PROFILE_OWNER_STRING_EXTRAS = {
        // Key for the default name of the managed profile
        EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME,
        // Key for the device admin package name
        EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME
    };

    private static final String[] PROFILE_OWNER_COMPONENT_EXTRAS = {
        // Key for the device admin component that started provisioning
        EXTRA_DEVICE_ADMIN
    };

    private static final ComponentName PROFILE_OWNER_INTENT_TARGET =
            new ComponentName("com.android.managedprovisioning",
                    "com.android.managedprovisioning.ManagedProvisioningActivity");

    /*
     * Device owner parameters that are stored in the IntentStore for resuming provisioning.
     */
    private static final String DEVICE_OWNER_PREFERENCES_NAME =
            "device-owner-provisioning-resume";

    private static final String[] DEVICE_OWNER_STRING_EXTRAS = {
        // Key for the string storing the properties from the intent that started the provisioning
        MessageParser.EXTRA_PROVISIONING_PROPERTIES
    };

    private static final String[] DEVICE_OWNER_COMPONENT_EXTRAS = {
        // No ComponentNames are persisted in the device owner case.
    };

    private static final ComponentName DEVICE_OWNER_INTENT_TARGET =
            new ComponentName("com.android.managedprovisioning",
                    "com.android.managedprovisioning.DeviceOwnerProvisioningActivity");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (android.content.Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            // Resume profile owner provisioning if applicable.
            IntentStore profileOwnerIntentStore = getProfileOwnerIntentStore(context);
            final Intent resumeProfileOwnerPrvIntent = profileOwnerIntentStore.load();
            if (resumeProfileOwnerPrvIntent != null ) {
                // Show reminder notification and then forget about it for next boot
                setNotification(context, resumeProfileOwnerPrvIntent);
                profileOwnerIntentStore.clear();
            }

            // Resume device owner provisioning if applicable.
            IntentStore deviceOwnerIntentStore = getDeviceOwnerIntentStore(context);
            Intent resumeDeviceOwnerPrvIntent = deviceOwnerIntentStore.load();
            if (resumeDeviceOwnerPrvIntent != null) {
                deviceOwnerIntentStore.clear();
                resumeDeviceOwnerPrvIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(resumeDeviceOwnerPrvIntent);
            }
        }
    }

    /**
     * Schedule a provisioning reminder notification for the next reboot.
     *
     * {@code extras} should be a Bundle containing the
     * {@link EncryptDeviceActivity.EXTRA_RESUME_TARGET}.
     * This field has only two supported values:
     *
     * <p>
     * In case of TARGET_PROFILE_OWNER {@code extras} should further contain values for at least the
     * following set of keys:
     * <ul>
     * <li>{@link EXTRA_DEVICE_ADMIN}, the {@link ComponentName} for the admin receiver to
     *     set up.</li>
     * <li>{@link EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME}, a {@link String} giving a
     *     default name to suggest to the user for the new managed profile.</li>
     * <li>{@link EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME}, a {@link String} specifying the
     *     package to set as profile owner.</li>
     * </ul></p>
     *
     * <p>
     * In case of TARGET_DEVICE_OWNER {@code extras} should further contain values for at least the
     * following set of keys:
     * <ul>
     * <li>{@link MessageParser.EXTRA_PROVISIONING_PROPERTIES}, a {@link String} storing the
     *     serialized {@link Properties} that contains all key value pairs specified in
     *     {@link MessageParser} that were used to initiate this provisioning flow.</li>
     * </ul></p>
     *
     * <p>
     * These fields will be persisted and restored to the provisioner after rebooting. Any other
     * key/value pairs will be ignored.</p>
     */
    public static void setProvisioningReminder(Context context, Bundle extras) {
        IntentStore intentStore;
        String resumeTarget = extras.getString(EncryptDeviceActivity.EXTRA_RESUME_TARGET, null);
        if (resumeTarget == null) {
            ProvisionLogger.loge("Resume target not specify. Missing EXTRA_RESUME_TARGET.");
            return;
        }
        if (resumeTarget.equals(EncryptDeviceActivity.TARGET_PROFILE_OWNER)) {
            intentStore = getProfileOwnerIntentStore(context);
        } else if (resumeTarget.equals(EncryptDeviceActivity.TARGET_DEVICE_OWNER)) {
            intentStore = getDeviceOwnerIntentStore(context);
        } else {
            ProvisionLogger.loge("Unknown resume target for bootreminder.");
            return;
        }
        intentStore.save(extras);
    }

    private static IntentStore getProfileOwnerIntentStore(Context context) {
        return new IntentStore(context,
                PROFILE_OWNER_STRING_EXTRAS,
                PROFILE_OWNER_COMPONENT_EXTRAS,
                PROFILE_OWNER_INTENT_TARGET,
                PROFILE_OWNER_PREFERENCES_NAME);
    }

    private static IntentStore getDeviceOwnerIntentStore(Context context) {
        return new IntentStore(context,
                DEVICE_OWNER_STRING_EXTRAS,
                DEVICE_OWNER_COMPONENT_EXTRAS,
                DEVICE_OWNER_INTENT_TARGET,
                DEVICE_OWNER_PREFERENCES_NAME);
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
