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

package com.android.managedprovisioning.provisioning.crossprofile;

import static android.app.Activity.RESULT_OK;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.content.pm.ApplicationInfo.FLAG_TEST_ONLY;

import static com.android.managedprovisioning.model.ProvisioningParams.EXTRA_PROVISIONING_PARAMS;
import static com.android.managedprovisioning.provisioning.crossprofile.CrossProfileConsentActivity.CROSS_PROFILE_SUMMARY_META_DATA;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupdesign.GlifLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

/** Robolectric unit tests for {@link CrossProfileConsentActivity}. */
@RunWith(RobolectricTestRunner.class)
public class CrossProfileConsentActivityRoboTest {
    private final ContextWrapper mContext = ApplicationProvider.getApplicationContext();
    private final DevicePolicyManager mDevicePolicyManager =
            mContext.getSystemService(DevicePolicyManager.class);
    private final PackageManager mPackageManager = mContext.getPackageManager();
    private final CrossProfileApps mCrossProfileApps =
            mContext.getSystemService(CrossProfileApps.class);
    private final PackageInfo mTestPackageInfo =
            buildTestPackageInfo("com.android.managedprovisioning.test1", "Test1");
    private final PackageInfo mTestPackageInfo2 =
            buildTestPackageInfo("com.android.managedprovisioning.test2", "Test2");

    @Before
    public void fixRobolectricLooping() {
        // This is a known Robolectric issue: http://robolectric.org/blog/2019/06/04/paused-looper/.
        // Pause the looper here and then execute each time after setting up the activity.
        ShadowLooper.pauseMainLooper();
    }

    @Before
    public void setIcons() {
        final Drawable icon = buildTestIcon();
        shadowOf(mPackageManager).setApplicationIcon(mTestPackageInfo.packageName, icon);
        shadowOf(mPackageManager).setApplicationIcon(mTestPackageInfo2.packageName, icon);
    }

    @Test
    public void setupActivity_noDefaultCrossProfilePackages_finishesActivityWithOkResult() {
        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertActivityFinishedWithOkResult(activity);
    }

    @Test
    public void setupActivity_setsCrossProfileItemTitleToAppName() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        TextView titleView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_title);
        assertThat(titleView.getText()).isEqualTo(mTestPackageInfo.applicationInfo.name);
    }

    @Test
    public void setupActivity_secondItem_setsCrossProfileItemTitleToAppName() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        TextView titleView =
                findCrossProfileItem(activity, /* index= */ 1)
                        .findViewById(R.id.cross_profile_item_title);
        assertThat(titleView.getText()).isEqualTo(mTestPackageInfo2.applicationInfo.name);
    }

    @Test
    public void setupActivity_setsCorrectNumberOfCrossProfileItems() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(2);
    }

    @Test
    public void restartActivity_stillHasItems() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        ActivityController<CrossProfileConsentActivity> activityController =
                Robolectric.buildActivity(CrossProfileConsentActivity.class);
        activityController.setup();
        activityController.restart();
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activityController.get())).isEqualTo(2);
    }

    @Test
    public void activityConfigurationChange_stillHasItems() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        ActivityController<CrossProfileConsentActivity> activityController =
                Robolectric.buildActivity(CrossProfileConsentActivity.class);
        activityController.setup();
        activityController.configurationChange();
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activityController.get())).isEqualTo(2);
    }

    @Test
    public void recreateActivity_stillHasItems() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        Robolectric.buildActivity(CrossProfileConsentActivity.class).setup().destroy();
        ShadowLooper.idleMainLooper();
        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(2);
    }

    @Test
    public void setupActivity_noApplicationInfo_crossProfileItemExcluded() {
        mTestPackageInfo2.applicationInfo = null;
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).addPackageNoDefaults(mTestPackageInfo2);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(1);
        TextView titleView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_title);
        assertThat(titleView.getText()).isEqualTo(mTestPackageInfo.applicationInfo.name);
    }

    @Test
    public void setupActivity_packageNotADefaultCrossProfilePackage_crossProfileItemExcluded() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(1);
        TextView titleView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_title);
        assertThat(titleView.getText()).isEqualTo(mTestPackageInfo.applicationInfo.name);
    }

    @Test
    public void setupActivity_packageNotInstalled_crossProfileItemExcluded() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(1);
        TextView titleView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_title);
        assertThat(titleView.getText()).isEqualTo(mTestPackageInfo.applicationInfo.name);
    }

    @Test
    public void setupActivity_setsCrossProfileItemIconToAppIcon() {
        final Drawable icon = buildTestIcon();
        shadowOf(mPackageManager).setApplicationIcon(mTestPackageInfo.packageName, icon);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        ImageView iconView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_icon);
        assertThat(iconView.getDrawable()).isEqualTo(icon);
    }

    @Test
    public void setupActivity_setsCrossProfileItemSummaryFromMetaData() {
        final String summary = "Summary";
        mTestPackageInfo.applicationInfo.metaData = new Bundle();
        mTestPackageInfo.applicationInfo.metaData.putString(
                CROSS_PROFILE_SUMMARY_META_DATA, summary);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        final TextView summaryView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_summary);
        assertThat(summaryView.getText()).isEqualTo(summary);
    }

    @Test
    public void setupActivity_noMetaData_emptySummary() {
        mTestPackageInfo.applicationInfo.metaData = null;
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        final TextView summaryView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_summary);
        assertThat(summaryView.getText()).isEqualTo("");
    }

    @Test
    public void setupActivity_noCrossProfileMetaData_emptySummary() {
        mTestPackageInfo.applicationInfo.metaData = new Bundle();
        mTestPackageInfo.applicationInfo.metaData.putString(
                "irrelevant_key", "irrelevant_value");
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        final TextView summaryView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_summary);
        assertThat(summaryView.getText()).isEqualTo("");
    }

    @Test
    public void setupActivity_setsOnlyFinalHorizontalDividerToGoneVisibility() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        final View firstHorizontalDividerView = findHorizontalDivider(activity, /* index= */ 0);
        assertThat(firstHorizontalDividerView.getVisibility()).isNotEqualTo(View.GONE);
        final View finalHorizontalDividerView = findHorizontalDivider(activity, /* index= */ 1);
        assertThat(finalHorizontalDividerView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void buttonClick_setsInteractAcrossProfileAppOp() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();
        findToggle(activity, /* index= */ 0).setChecked(true);
        findToggle(activity, /* index= */ 1).setChecked(false);
        findButton(activity).performClick();
        ShadowLooper.idleMainLooper();

        final int mode1 = shadowOf(mCrossProfileApps)
                .getInteractAcrossProfilesAppOp(mTestPackageInfo.packageName);
        assertThat(mode1).isEqualTo(MODE_ALLOWED);
        final int mode2 = shadowOf(mCrossProfileApps)
                .getInteractAcrossProfilesAppOp(mTestPackageInfo2.packageName);
        assertThat(mode2).isEqualTo(MODE_IGNORED);
    }

    @Test
    public void buttonClick_noToggleClicks_setsInteractAcrossProfileAppOpsToEnabledByDefault() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();
        findButton(activity).performClick();
        ShadowLooper.idleMainLooper();

        final int mode = shadowOf(mCrossProfileApps)
                .getInteractAcrossProfilesAppOp(mTestPackageInfo.packageName);
        assertThat(mode).isEqualTo(MODE_ALLOWED);
    }

    @Test
    public void noButtonClick_sharedPreferenceNotSet() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(new ManagedProvisioningSharedPreferences(mContext).getCrossProfileConsentDone())
                .isFalse();
    }

    @Test
    public void buttonClick_setsSharedPreference() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();
        findButton(activity).performClick();
        ShadowLooper.idleMainLooper();

        assertThat(new ManagedProvisioningSharedPreferences(mContext).getCrossProfileConsentDone())
                .isTrue();
    }

    @Test
    public void buttonClick_finishesActivityWithOkResult() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();
        findButton(activity).performClick();
        ShadowLooper.idleMainLooper();

        assertActivityFinishedWithOkResult(activity);
    }

    @Test
    public void setupActivity_silentProvisioningParams_setsInteractAcrossProfileAppOpsToEnabled() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        final PackageInfo profileOwnerPackageInfo = setSilentProvisioningFlags(mTestPackageInfo2);
        shadowOf(mPackageManager).installPackage(profileOwnerPackageInfo);
        final Intent intent =
                buildSilentProvisioningParamsIntent(profileOwnerPackageInfo.packageName);

        Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        final int mode = shadowOf(mCrossProfileApps)
                .getInteractAcrossProfilesAppOp(mTestPackageInfo.packageName);
        assertThat(mode).isEqualTo(MODE_ALLOWED);
    }

    @Test
    public void setupActivity_silentProvisioningParams_setsSharedPreference() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        final PackageInfo profileOwnerPackageInfo = setSilentProvisioningFlags(mTestPackageInfo2);
        shadowOf(mPackageManager).installPackage(profileOwnerPackageInfo);
        final Intent intent =
                buildSilentProvisioningParamsIntent(profileOwnerPackageInfo.packageName);

        Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        assertThat(new ManagedProvisioningSharedPreferences(mContext).getCrossProfileConsentDone())
                .isTrue();
    }

    @Test
    public void setupActivity_silentProvisioningParams_finishesActivityWithOkResult() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        final PackageInfo profileOwnerPackageInfo = setSilentProvisioningFlags(mTestPackageInfo2);
        shadowOf(mPackageManager).installPackage(profileOwnerPackageInfo);
        final Intent intent =
                buildSilentProvisioningParamsIntent(profileOwnerPackageInfo.packageName);

        final CrossProfileConsentActivity activity =
                Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        assertActivityFinishedWithOkResult(activity);
    }

    private Drawable buildTestIcon() {
        return new BitmapDrawable();
    }

    private PackageInfo buildTestPackageInfo(String packageName, String appName) {
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = packageName;
        packageInfo.applicationInfo.name = appName;
        return packageInfo;
    }

    private RecyclerView findCrossProfileItems(CrossProfileConsentActivity activity) {
        return activity.findViewById(R.id.cross_profile_items);
    }

    private int findCrossProfileItemsNum(CrossProfileConsentActivity activity) {
        return findCrossProfileItems(activity).getAdapter().getItemCount();
    }

    private View findCrossProfileItem(CrossProfileConsentActivity activity) {
        return findCrossProfileItem(activity, /* index= */ 0);
    }

    private View findCrossProfileItem(CrossProfileConsentActivity activity, int index) {
        return findCrossProfileItems(activity).getLayoutManager().findViewByPosition(index);
    }

    private View findHorizontalDivider(CrossProfileConsentActivity activity, int index) {
        return findCrossProfileItem(activity, index)
                .findViewById(R.id.cross_profile_item_horizontal_divider);
    }

    private Switch findToggle(CrossProfileConsentActivity activity, int index) {
        return (Switch) findCrossProfileItem(activity, index)
                .findViewById(R.id.cross_profile_item_toggle);
    }

    private Button findButton(Activity activity) {
        final GlifLayout glifLayout = activity.findViewById(R.id.setup_wizard_layout);
        final FooterBarMixin footerBarMixin = glifLayout.getMixin(FooterBarMixin.class);
        return footerBarMixin.getPrimaryButtonView();
    }

    private void assertActivityFinishedWithOkResult(Activity activity) {
        assertThat(activity.isFinishing()).isTrue();
        assertThat(shadowOf(activity).getResultCode()).isEqualTo(RESULT_OK);
    }

    /**
     * Mutates the given package profile-owner package info to be considered suitable for silent
     * provisioning.
     */
    private PackageInfo setSilentProvisioningFlags(PackageInfo profileOwnerPackageInfo) {
        profileOwnerPackageInfo.applicationInfo.flags = FLAG_TEST_ONLY;
        return profileOwnerPackageInfo;
    }

    private Intent buildSilentProvisioningParamsIntent(String profileOwnerPackageName) {
        final ProvisioningParams provisioningParams =
                new ProvisioningParams.Builder()
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(
                                new ComponentName(profileOwnerPackageName, "NonexistentDummyClass"))
                        .build();
        return new Intent().putExtra(EXTRA_PROVISIONING_PARAMS, provisioningParams);
    }
}
