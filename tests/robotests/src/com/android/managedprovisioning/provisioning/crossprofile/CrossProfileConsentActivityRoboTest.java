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
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
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
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Set;

/** Robolectric unit tests for {@link CrossProfileConsentActivity}. */
@RunWith(RobolectricTestRunner.class)
public class CrossProfileConsentActivityRoboTest {
    private final ContextWrapper mContext = ApplicationProvider.getApplicationContext();
    private final DevicePolicyManager mDevicePolicyManager =
            mContext.getSystemService(DevicePolicyManager.class);
    private final PackageManager mPackageManager = mContext.getPackageManager();
    private final CrossProfileApps mCrossProfileApps =
            mContext.getSystemService(CrossProfileApps.class);
    private final ManagedProvisioningSharedPreferences mManagedProvisioningSharedPreferences =
            new ManagedProvisioningSharedPreferences(mContext);
    private final PackageInfo mTestPackageInfo =
            buildTestPackageInfo("com.android.managedprovisioning.test1", "Test1");
    private final PackageInfo mTestPackageInfo2 =
            buildTestPackageInfo("com.android.managedprovisioning.test2", "Test2");
    private final PackageInfo mProfileOwnerPackageInfo =
            buildTestPackageInfo("com.android.managedprovisioning.profileowner", "ProfileOwner");


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
    public void setupActivity_noDefaultCrossProfilePackages_setsNoPackagesSharedPreference() {
        Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(mManagedProvisioningSharedPreferences.getConsentedCrossProfilePackages())
                .isEmpty();
    }

    @Test
    public void setupActivity_setsCrossProfileItemTitleToAppName() {
        installDefaultCrossProfilePackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        TextView titleView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_title);
        assertThat(titleView.getText()).isEqualTo(mTestPackageInfo.applicationInfo.name);
    }

    @Test
    public void setupActivity_secondItem_setsCrossProfileItemTitleToAppName() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);

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
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(2);
    }

    @Test
    public void restartActivity_stillHasItems() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);

        ActivityController<CrossProfileConsentActivity> activityController =
                Robolectric.buildActivity(CrossProfileConsentActivity.class);
        activityController.setup();
        activityController.restart();
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activityController.get())).isEqualTo(2);
    }

    @Test
    public void activityConfigurationChange_stillHasItems() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);

        ActivityController<CrossProfileConsentActivity> activityController =
                Robolectric.buildActivity(CrossProfileConsentActivity.class);
        activityController.setup();
        activityController.configurationChange();
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activityController.get())).isEqualTo(2);
    }

    @Test
    public void recreateActivity_stillHasItems() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);

        Robolectric.buildActivity(CrossProfileConsentActivity.class).setup().destroy();
        ShadowLooper.idleMainLooper();
        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(2);
    }

    @Test
    public void setupActivity_noApplicationInfo_crossProfileItemExcluded() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mCrossProfileApps).addCrossProfilePackage(mTestPackageInfo.packageName);
        mTestPackageInfo2.applicationInfo = null;
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).addPackageNoDefaults(mTestPackageInfo2);
        shadowOf(mCrossProfileApps).addCrossProfilePackage(mTestPackageInfo2.packageName);

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
        shadowOf(mCrossProfileApps).addCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);
        shadowOf(mCrossProfileApps).addCrossProfilePackage(mTestPackageInfo2.packageName);

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
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mCrossProfileApps).addCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mCrossProfileApps).addCrossProfilePackage(mTestPackageInfo2.packageName);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(1);
        TextView titleView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_title);
        assertThat(titleView.getText()).isEqualTo(mTestPackageInfo.applicationInfo.name);
    }

    @Test
    public void setupActivity_packageNotCrossProfileConfigurable_crossProfileItemExcluded() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mCrossProfileApps).addCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
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
    public void setupActivity_packagePreviouslyConsented_crossProfileItemExcluded() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);
        mManagedProvisioningSharedPreferences.writeConsentedCrossProfilePackages(
                Set.of(mTestPackageInfo2.packageName));

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
        installDefaultCrossProfilePackage(mTestPackageInfo);

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
        installDefaultCrossProfilePackage(mTestPackageInfo);

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
        installDefaultCrossProfilePackage(mTestPackageInfo);

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
        installDefaultCrossProfilePackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        final TextView summaryView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_summary);
        assertThat(summaryView.getText()).isEqualTo("");
    }

    @Test
    public void setupActivity_setsOnlyFinalHorizontalDividerToGoneVisibility() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);

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
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);

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
        installDefaultCrossProfilePackage(mTestPackageInfo);

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
    public void noButtonClick_noPackagesInSharedPreference() {
        installDefaultCrossProfilePackage(mTestPackageInfo);

        Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(mManagedProvisioningSharedPreferences.getConsentedCrossProfilePackages())
                .isEmpty();
    }

    @Test
    public void buttonClick_setsConsentedPackagesInSharedPreference() {
        installDefaultCrossProfilePackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();
        findButton(activity).performClick();
        ShadowLooper.idleMainLooper();

        Set<String> consentedPackages =
                mManagedProvisioningSharedPreferences.getConsentedCrossProfilePackages();
        assertThat(consentedPackages).hasSize(1);
        assertThat(consentedPackages.iterator().next()).isEqualTo(mTestPackageInfo.packageName);
    }

    @Test
    public void buttonClick_packagePreviouslyConsented_setsAllConsentedPackagesInSharedPreference() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        installDefaultCrossProfilePackage(mTestPackageInfo2);
        mManagedProvisioningSharedPreferences.writeConsentedCrossProfilePackages(
                Set.of(mTestPackageInfo2.packageName));

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();
        findButton(activity).performClick();
        ShadowLooper.idleMainLooper();

        Set<String> consentedPackages =
                mManagedProvisioningSharedPreferences.getConsentedCrossProfilePackages();
        assertThat(consentedPackages).hasSize(2);
        assertThat(consentedPackages).contains(mTestPackageInfo.packageName);
        assertThat(consentedPackages).contains(mTestPackageInfo2.packageName);
    }

    @Test
    public void buttonClick_finishesActivityWithOkResult() {
        installDefaultCrossProfilePackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();
        findButton(activity).performClick();
        ShadowLooper.idleMainLooper();

        assertActivityFinishedWithOkResult(activity);
    }

    @Test
    public void setupActivity_silentProvisioningParams_setsInteractAcrossProfileAppOpsToEnabled() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        setSilentProvisioningFlags(mProfileOwnerPackageInfo);
        shadowOf(mPackageManager).installPackage(mProfileOwnerPackageInfo);
        final Intent intent = buildProvisioningParamsIntent(mProfileOwnerPackageInfo.packageName);

        Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        final int mode = shadowOf(mCrossProfileApps)
                .getInteractAcrossProfilesAppOp(mTestPackageInfo.packageName);
        assertThat(mode).isEqualTo(MODE_ALLOWED);
    }

    @Test
    public void setupActivity_silentProvisioningParams_setsConsentedPackagesInSharedPreference() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        setSilentProvisioningFlags(mProfileOwnerPackageInfo);
        shadowOf(mPackageManager).installPackage(mProfileOwnerPackageInfo);
        final Intent intent = buildProvisioningParamsIntent(mProfileOwnerPackageInfo.packageName);

        Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        Set<String> consentedPackages =
                mManagedProvisioningSharedPreferences.getConsentedCrossProfilePackages();
        assertThat(consentedPackages).hasSize(1);
        assertThat(consentedPackages.iterator().next()).isEqualTo(mTestPackageInfo.packageName);
    }

    @Test
    public void setupActivity_silentProvisioningParams_finishesActivityWithOkResult() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        setSilentProvisioningFlags(mProfileOwnerPackageInfo);
        shadowOf(mPackageManager).installPackage(mProfileOwnerPackageInfo);
        final Intent intent = buildProvisioningParamsIntent(mProfileOwnerPackageInfo.packageName);

        final CrossProfileConsentActivity activity =
                Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        assertActivityFinishedWithOkResult(activity);
    }

    @Test
    public void setupActivity_duringProvisioning_normalPhoneDimensions_locksToPortrait() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mProfileOwnerPackageInfo);
        final Intent intent = buildProvisioningParamsIntent(mProfileOwnerPackageInfo.packageName);

        final CrossProfileConsentActivity activity =
                Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        assertThat(activity.getRequestedOrientation()).isEqualTo(SCREEN_ORIENTATION_PORTRAIT);
    }

    @Test
    public void setupActivity_notDuringProvisioning_normalPhoneDimensions_doesNotLockToPortrait() {
        installDefaultCrossProfilePackage(mTestPackageInfo);

        final CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        assertThat(activity.getRequestedOrientation()).isEqualTo(SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Test
    @Config(qualifiers ="sw600dp")
    public void setupActivity_duringProvisioning_sw600dp_doesNotLockToPortrait() {
        installDefaultCrossProfilePackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mProfileOwnerPackageInfo);
        final Intent intent = buildProvisioningParamsIntent(mProfileOwnerPackageInfo.packageName);

        final CrossProfileConsentActivity activity =
                Robolectric.buildActivity(CrossProfileConsentActivity.class, intent).setup().get();
        ShadowLooper.idleMainLooper();

        assertThat(activity.getRequestedOrientation()).isEqualTo(SCREEN_ORIENTATION_UNSPECIFIED);
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

    private void installDefaultCrossProfilePackage(PackageInfo packageInfo) {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(packageInfo.packageName);
        shadowOf(mPackageManager).installPackage(packageInfo);
        shadowOf(mCrossProfileApps).addCrossProfilePackage(packageInfo.packageName);
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
    private void setSilentProvisioningFlags(PackageInfo profileOwnerPackageInfo) {
        profileOwnerPackageInfo.applicationInfo.flags = FLAG_TEST_ONLY;
    }

    private Intent buildProvisioningParamsIntent(String profileOwnerPackageName) {
        final ProvisioningParams provisioningParams =
                new ProvisioningParams.Builder()
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(
                                new ComponentName(profileOwnerPackageName, "NonexistentDummyClass"))
                        .build();
        return new Intent().putExtra(EXTRA_PROVISIONING_PARAMS, provisioningParams);
    }
}
