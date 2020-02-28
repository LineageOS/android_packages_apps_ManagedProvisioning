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

import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.CrossProfileApps;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowProcess;

import java.util.Collections;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class CrossProfileAppsNotificationControllerTest {

    private static final int MY_USER_ID = 0;
    private static final int OTHER_USER_ID = 5;
    private static final int OTHER_USER_UID = UserHandle.PER_USER_RANGE * OTHER_USER_ID;

    private static final Set<String> CONFIGURABLE_PACKAGES = Set.of("CONFIGURABLE_PACKAGE");
    private static final Set<String> NON_CONFIGURABLE_PACKAGES = Set.of("NON_CONFIGURABLE_PACKAGE");

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final UserManager mUserManager = mContext.getSystemService(UserManager.class);
    private final CrossProfileApps mCrossProfileApps =
            mContext.getSystemService(CrossProfileApps.class);
    private final DevicePolicyManager mDevicePolicyManager =
            mContext.getSystemService(DevicePolicyManager.class);
    private final NotificationManager mNotificationManager =
            mContext.getSystemService(NotificationManager.class);
    private final ManagedProvisioningSharedPreferences mManagedProvisioningSharedPreferences =
            new ManagedProvisioningSharedPreferences(mContext);
    private final CrossProfileAppsNotificationController mNotificationController =
            new CrossProfileAppsNotificationController(mContext);

    @Test
    public void onPrimaryProfile_noManagedProfile_doesNotShow() {
        // We default to no managed profile
        setWhitelistedPackages(Collections.emptySet());

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    @Test
    public void onPrimaryProfileWithManagedProfile_noPackagesToConsent_doesNotShow() {
        setRunningOnParentProfile();
        setWhitelistedPackages(Collections.emptySet());

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    @Test
    public void onPrimaryProfileWithManagedProfile_allPackagesConsented_doesNotShow() {
        setRunningOnParentProfile();
        setWhitelistedPackages(CONFIGURABLE_PACKAGES);
        setConfigurablePackages(CONFIGURABLE_PACKAGES);
        setConsentedPackages(CONFIGURABLE_PACKAGES);

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    @Test
    public void onPrimaryProfileWithManagedProfile_nonConfigurableWhitelistedPackages_doesNotShow() {
        setRunningOnParentProfile();
        setWhitelistedPackages(NON_CONFIGURABLE_PACKAGES);

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    @Test
    public void onPrimaryProfileWithManagedProfile_nonConsentedPackages_shows() {
        setRunningOnParentProfile();
        setWhitelistedPackages(CONFIGURABLE_PACKAGES);
        setConfigurablePackages(CONFIGURABLE_PACKAGES);

        mNotificationController.maybeShowPermissionsNotification();

        Notification notification = shadowOf(mNotificationManager).getNotification(/* id= */ 0);
        assertThat(shadowOf(notification).getContentTitle())
                .isEqualTo("Connect work & personal apps");
    }

    @Test
    public void onManagedProfile_doesNotShow() {
        setRunningOnManagedProfile();
        setWhitelistedPackages(CONFIGURABLE_PACKAGES);
        setConfigurablePackages(CONFIGURABLE_PACKAGES);

        mNotificationController.maybeShowPermissionsNotification();

        assertThat(shadowOf(mNotificationManager).getAllNotifications()).isEmpty();
    }

    private void setRunningOnParentProfile() {
        shadowOf(mUserManager).addProfile(
                MY_USER_ID, OTHER_USER_ID, /*profileName= */"otherUser", FLAG_MANAGED_PROFILE);
    }

    private void setRunningOnManagedProfile() {
        shadowOf(mUserManager).addProfile(
                MY_USER_ID, OTHER_USER_ID, /*profileName= */"otherUser", FLAG_MANAGED_PROFILE);
        ShadowProcess.setUid(OTHER_USER_UID);
    }

    private void setConsentedPackages(Set<String> packages) {
        mManagedProvisioningSharedPreferences.writeConsentedCrossProfilePackages(packages);
    }

    private void setWhitelistedPackages(Set<String> packages) {
        shadowOf(mDevicePolicyManager).setDefaultCrossProfilePackages(packages);
    }

    private void setConfigurablePackages(Set<String> packages) {
        packages.forEach(p -> shadowOf(mCrossProfileApps).addCrossProfilePackage(p));
    }
}
