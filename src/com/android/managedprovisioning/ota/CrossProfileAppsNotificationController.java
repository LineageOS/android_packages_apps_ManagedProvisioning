/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.managedprovisioning.ota;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.CrossProfileApps;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.provisioning.crossprofile.CrossProfileConsentActivity;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for notification which is shown on boot if the user has not given consent for cross
 * profile apps.
 */
public class CrossProfileAppsNotificationController {
    private final Context mContext;
    private final UserManager mUserManager;
    private final NotificationManager mNotificationManager;
    private final ManagedProvisioningSharedPreferences mManagedProvisioningSharedPreferences;
    private final DevicePolicyManager mDevicePolicyManager;
    private final CrossProfileApps mCrossProfileApps;

    private static final String CHANNEL_ID = "CrossProfileAppPermissions";

    public CrossProfileAppsNotificationController(Context context) {
        mContext = checkNotNull(context);
        mUserManager = mContext.getSystemService(UserManager.class);
        mManagedProvisioningSharedPreferences = new ManagedProvisioningSharedPreferences(context);
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        mCrossProfileApps = mContext.getSystemService(CrossProfileApps.class);
    }

    public void maybeShowPermissionsNotification() {
        if (!isParentProfileOfManagedProfile()) {
            return;
        }
        if (hasConsentedToAllPackages()) {
            return;
        }
        createNotificationChannel();

        Intent intent = new Intent(mContext, CrossProfileConsentActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(mContext, /* requestCode= */ 0, intent, /* flags= */ 0);

        Resources resources = mContext.getResources();

        Notification notification = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(resources.getString(R.string.cross_profile_notification_title))
                .setContentText(resources.getString(R.string.cross_profile_notification_text))
                .setSmallIcon(com.android.internal.R.drawable.ic_corp_statusbar_icon)
                .setColor(Color.argb(1, 26, 115, 232))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        mNotificationManager.notify(/* notificationId= */ 0, notification);
    }

    private boolean hasConsentedToAllPackages() {
        Set<String> consentedPackages =
                mManagedProvisioningSharedPreferences.getConsentedCrossProfilePackages();

        Set<String> unconsentedPackages = getConfigurableDefaultCrossProfilePackages();
        unconsentedPackages.removeAll(consentedPackages);

        return unconsentedPackages.isEmpty();
    }

    private Set<String> getConfigurableDefaultCrossProfilePackages() {
        Set<String> defaultPackages = mDevicePolicyManager.getDefaultCrossProfilePackages();
        return defaultPackages.stream().filter(
                mCrossProfileApps::canConfigureInteractAcrossProfiles).collect(Collectors.toSet());
    }

    private boolean isParentProfileOfManagedProfile() {
        int currentUserId = android.os.Process.myUserHandle().getIdentifier();
        for (final UserInfo userInfo : mUserManager.getProfiles(currentUserId)) {
            UserHandle userHandle = userInfo.getUserHandle();
            if (userInfo.isManagedProfile() &&
                    mUserManager.getProfileParent(userHandle).getIdentifier() == currentUserId) {
                return true;
            }
        }
        return false;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                mContext.getResources().getString(R.string.cross_profile_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);

        mNotificationManager.createNotificationChannel(channel);
    }
}
