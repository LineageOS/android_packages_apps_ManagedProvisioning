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

import static com.google.common.truth.Truth.assertThat;
import static android.content.pm.UserInfo.FLAG_MANAGED_PROFILE;

import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CrossProfileAppsNotificationControllerTest {

    private static final int MY_USER_ID = 0;
    private static final int OTHER_USER_ID = 5;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final UserManager mUserManager = mContext.getSystemService(UserManager.class);
    private final NotificationManager mNotificationManager =
            mContext.getSystemService(NotificationManager.class);
    private final ManagedProvisioningSharedPreferences mManagedProvisioningSharedPreferences =
            new ManagedProvisioningSharedPreferences(mContext);

    private final CrossProfileAppsNotificationController mNotificationController =
            new CrossProfileAppsNotificationController(mContext);

    @Test
    public void onPrimaryProfile_noManagedProfile_doesNotShow() {
        // We default to no managed profile
        setConsentDone();

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    @Test
    public void onPrimaryProfileWithManagedProfile_consentDone_doesNotShow() {
        setRunningOnParentProfile();
        setConsentNotDone();

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    @Test
    public void onManagedProfile_doesNotShow() {
        setRunningOnManagedProfile();
        setConsentNotDone();

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    @Test
    public void onPrimaryProfileWithManagedProfile_consentNotDone_shows() {
         setRunningOnParentProfile();
         setConsentDone();

        mNotificationController.maybeShowPermissionsNotification();

        Notification notification = shadowOf(mNotificationManager).getNotification(/* id= */ 0);
        assertThat(shadowOf(notification).getContentTitle()).isEqualTo("Connect work & personal apps");

    }

    private void setRunningOnParentProfile() {
        shadowOf(mUserManager)
                .addProfile(MY_USER_ID, OTHER_USER_ID, "otherUser", FLAG_MANAGED_PROFILE);
    }

    private void setRunningOnManagedProfile() {
        shadowOf(mUserManager).addProfile(MY_USER_ID, MY_USER_ID, "myUser", FLAG_MANAGED_PROFILE);
    }

    private void setConsentNotDone() {
        mManagedProvisioningSharedPreferences.writeCrossProfileConsentDone(true);
    }

    private void setConsentDone() {
        mManagedProvisioningSharedPreferences.writeCrossProfileConsentDone(false);
    }
}
