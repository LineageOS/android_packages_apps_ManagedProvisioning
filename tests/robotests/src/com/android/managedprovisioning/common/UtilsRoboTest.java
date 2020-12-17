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

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.ACTION_GET_PROVISIONING_MODE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UtilsRoboTest {
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final String TEST_MDM_ADMIN_RECEIVER = TEST_MDM_PACKAGE_NAME + ".AdminReceiver";
    private static final ComponentName TEST_MDM_ADMIN = new ComponentName(TEST_MDM_PACKAGE_NAME,
            TEST_MDM_ADMIN_RECEIVER);
    private static final ProvisioningParams PARAMS_ORG_OWNED = ProvisioningParams.Builder.builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(TEST_MDM_ADMIN)
            .setIsOrganizationOwnedProvisioning(true)
            .build();
    private static final ProvisioningParams PARAMS_NON_ORG_OWNED = ProvisioningParams.Builder.builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(TEST_MDM_ADMIN)
            .setIsOrganizationOwnedProvisioning(false)
            .build();
    private static final ProvisioningParams PARAMS_NFC = ProvisioningParams.Builder.builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(TEST_MDM_ADMIN)
            .setIsOrganizationOwnedProvisioning(true)
            .setIsNfc(true)
            .build();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private Utils mUtils = new Utils();
    private PolicyComplianceUtils mPolicyComplianceUtils = new PolicyComplianceUtils();
    private GetProvisioningModeUtils mGetProvisioningModeUtils = new GetProvisioningModeUtils();
    private ActivityManager mActivityManager = mContext.getSystemService(ActivityManager.class);

    @Test
    public void shouldPerformAdminIntegratedFlow_allConditionsMet_returnsTrue() {
        Intent policyComplianceIntent = getPolicyComplianceIntent();
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(policyComplianceIntent, info);
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(getProvisioningModeIntent, info);
        shadowOf(mActivityManager).setIsLowRamDevice(false);

        assertThat(mUtils.shouldPerformAdminIntegratedFlow(mContext, PARAMS_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isTrue();
    }

    @Test
    public void shouldPerformAdminIntegratedFlow_noPolicyComplianceScreen_returnsFalse() {
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(getProvisioningModeIntent, info);
        shadowOf(mActivityManager).setIsLowRamDevice(false);

        assertThat(mUtils.shouldPerformAdminIntegratedFlow(mContext, PARAMS_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isFalse();
    }

    @Test
    public void shouldPerformAdminIntegratedFlow_noGetProvisioningModeScreen_returnsFalse() {
        Intent policyComplianceIntent = getPolicyComplianceIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(policyComplianceIntent, info);
        shadowOf(mActivityManager).setIsLowRamDevice(false);

        assertThat(mUtils.shouldPerformAdminIntegratedFlow(mContext, PARAMS_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isFalse();
    }

    @Test
    public void shouldPerformAdminIntegratedFlow_notOrganizationOwned_returnsFalse() {
        Intent policyComplianceIntent = getPolicyComplianceIntent();
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(policyComplianceIntent, info);
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(getProvisioningModeIntent, info);
        shadowOf(mActivityManager).setIsLowRamDevice(false);

        assertThat(mUtils.shouldPerformAdminIntegratedFlow(mContext, PARAMS_NON_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isFalse();
    }

    @Test
    public void shouldPerformAdminIntegratedFlow_nfcProvisioning_returnsFalse() {
        Intent policyComplianceIntent = getPolicyComplianceIntent();
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(policyComplianceIntent, info);
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(getProvisioningModeIntent, info);
        shadowOf(mActivityManager).setIsLowRamDevice(false);

        assertThat(mUtils.shouldPerformAdminIntegratedFlow(mContext, PARAMS_NFC,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isFalse();
    }

    @Test
    public void shouldPerformAdminIntegratedFlow_lowRamDevice_returnsFalse() {
        Intent policyComplianceIntent = getPolicyComplianceIntent();
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(policyComplianceIntent, info);
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(getProvisioningModeIntent, info);
        shadowOf(mActivityManager).setIsLowRamDevice(true);

        assertThat(mUtils.shouldPerformAdminIntegratedFlow(mContext, PARAMS_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isFalse();
    }

    private Intent getGetProvisioningModeIntent() {
        final Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(TEST_MDM_PACKAGE_NAME);
        return intentGetMode;
    }

    private Intent getPolicyComplianceIntent() {
        Intent policyComplianceIntent =
                new Intent(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE);
        policyComplianceIntent.setPackage(TEST_MDM_PACKAGE_NAME);
        return policyComplianceIntent;
    }

    private ResolveInfo createFakeResolveInfo() {
        ResolveInfo info = new ResolveInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = TEST_MDM_PACKAGE_NAME;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;
        activityInfo.name = "SomeClassName";
        info.activityInfo = activityInfo;
        return info;
    }
}
