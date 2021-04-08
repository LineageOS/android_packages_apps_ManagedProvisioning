/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;

/**
 * {@link android.app.Application} for ManagedProvisioning.
 */
public class ManagedProvisioningApplication extends android.app.Application {

    private final ThemeHelper mThemeHelper = new ThemeHelper(
            new DefaultNightModeChecker(),
            new DefaultSetupWizardBridge());

    @Override
    public void onCreate() {
        int defaultNightMode = updateDefaultNightMode();
        super.onCreate();
        logMetrics(defaultNightMode);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateDefaultNightMode();
    }

    private int updateDefaultNightMode() {
        int defaultNightMode = mThemeHelper.getDefaultNightMode(this);
        AppCompatDelegate.setDefaultNightMode(defaultNightMode);
        return defaultNightMode;
    }

    private void logMetrics(int defaultNightMode) {
        final ProvisioningAnalyticsTracker analyticsTracker =
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(this, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(this));
        analyticsTracker.logIsNightMode(defaultNightMode == MODE_NIGHT_YES);
    }
}
