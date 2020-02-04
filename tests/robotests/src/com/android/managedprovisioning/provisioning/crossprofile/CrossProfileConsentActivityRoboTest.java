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

import static com.android.managedprovisioning.provisioning.crossprofile.CrossProfileConsentActivity.CROSS_PROFILE_SUMMARY_META_DATA;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.admin.DevicePolicyManager;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.R;

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
    }

    @Test
    public void setupActivity_noDefaultCrossProfilePackages_noCrossProfileItems() {
        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        assertThat(findCrossProfileItemsNum(activity)).isEqualTo(0);
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

//    @Test
//    public void setupActivity_setsCrossProfileItemIconToAppIcon() {
//        final Drawable icon = buildTestIcon();
//        shadowOf(mPackageManager).setApplicationIcon(mTestPackageInfo.packageName, icon);
//        shadowOf(mDevicePolicyManager)
//                .addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
//        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
//
//        CrossProfileConsentActivity activity =
//                Robolectric.setupActivity(CrossProfileConsentActivity.class);
//        ShadowLooper.idleMainLooper();
//
//        ImageView iconView =
//                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_icon);
//        assertThat(iconView.getDrawable()).isEqualTo(icon);
//        // TODO(http://b/148769796). getPackageManager().getApplicationIcon() from the production
//        // activity code is not calling through to the ShadowApplicationPackageManager. Tried
//        // setting directly on the activity's package manager and also tried to use
//        // getApplicationContext() in the activity class with no luck.
//    }

    @Test
    public void setupActivity_setsCrossProfileItemSummaryFromMetaData() {
        String summary = "Summary";
        mTestPackageInfo.applicationInfo.metaData = new Bundle();
        mTestPackageInfo.applicationInfo.metaData.putString(
                CROSS_PROFILE_SUMMARY_META_DATA, summary);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        TextView summaryView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_summary);
        assertThat(summaryView.getText()).isEqualTo(summary);
    }

    @Test
    public void setupActivity_noMetaData_emptySummary() {
        mTestPackageInfo.applicationInfo.metaData = null;
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        TextView summaryView =
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

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        TextView summaryView =
                findCrossProfileItem(activity).findViewById(R.id.cross_profile_item_summary);
        assertThat(summaryView.getText()).isEqualTo("");
    }

    @Test
    public void setupActivity_setsOnlyFinalHorizontalDividerToGoneVisibility() {
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo.packageName);
        shadowOf(mDevicePolicyManager).addDefaultCrossProfilePackage(mTestPackageInfo2.packageName);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo);
        shadowOf(mPackageManager).installPackage(mTestPackageInfo2);

        CrossProfileConsentActivity activity =
                Robolectric.setupActivity(CrossProfileConsentActivity.class);
        ShadowLooper.idleMainLooper();

        View firstHorizontalDividerView =
                findCrossProfileItem(activity, /* index= */ 0)
                        .findViewById(R.id.cross_profile_item_horizontal_divider);
        assertThat(firstHorizontalDividerView.getVisibility()).isNotEqualTo(View.GONE);
        View finalHorizontalDividerView =
                findCrossProfileItem(activity, /* index= */ 1)
                        .findViewById(R.id.cross_profile_item_horizontal_divider);
        assertThat(finalHorizontalDividerView.getVisibility()).isEqualTo(View.GONE);
    }

    private Drawable buildTestIcon() {
        return new BitmapDrawable();
    }

    private PackageInfo buildTestPackageInfo(String packageName, String appName) {
        PackageInfo packageInfo = new PackageInfo();
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
}
