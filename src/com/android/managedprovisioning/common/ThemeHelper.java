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

package com.android.managedprovisioning.common;

import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import static com.android.managedprovisioning.provisioning.Constants.FLAG_ENABLE_LIGHT_DARK_MODE;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.webkit.WebSettings;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.android.managedprovisioning.R;

import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.util.ThemeResolver;

import java.util.Objects;

/**
 * Helper with utility methods to manage the ManagedProvisioning theme and night mode.
 */
public class ThemeHelper {
    private static final String SYSTEM_PROPERTY_SETUPWIZARD_THEME =
            SystemProperties.get("setupwizard.theme");

    private final NightModeChecker mNightModeChecker;
    private final SetupWizardBridge mSetupWizardBridge;

    public ThemeHelper(NightModeChecker nightModeChecker, SetupWizardBridge setupWizardBridge) {
        mNightModeChecker = nightModeChecker;
        mSetupWizardBridge = setupWizardBridge;
    }

    /**
     * Infers the correct theme resource id.
     */
    public int inferThemeResId(Context context, Intent intent) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(intent);
        String themeName = getDefaultThemeName(intent);
        int defaultTheme = mSetupWizardBridge.isSetupWizardDayNightEnabled(context)
                ? R.style.SudThemeGlifV3_DayNight
                : R.style.SudThemeGlifV3_Light;
        return mSetupWizardBridge
                .resolveTheme(defaultTheme, themeName, shouldSuppressDayNight(context));
    }

    /**
     * Returns the appropriate day or night mode, depending on the setup wizard flags.
     *
     * @return {@link AppCompatDelegate#MODE_NIGHT_YES} or {@link AppCompatDelegate#MODE_NIGHT_NO}
     */
    public int getDefaultNightMode(Context context) {
        Objects.requireNonNull(context);
        if (shouldSuppressDayNight(context)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (isSystemNightMode(context)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_NO;
    }

    /**
     * Forces the web pages shown by the {@link android.webkit.WebView} which has the
     * supplied {@code webSettings} to have the appropriate day/night mode depending
     * on the app theme.
     */
    public void applyWebSettingsDayNight(Context context, WebSettings webSettings) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(webSettings);
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            return;
        }
        WebSettingsCompat.setForceDark(webSettings, getForceDarkMode(context));
    }

    private int getForceDarkMode(Context context) {
        if (getDefaultNightMode(context) == AppCompatDelegate.MODE_NIGHT_YES) {
            return WebSettingsCompat.FORCE_DARK_ON;
        } else {
            return WebSettingsCompat.FORCE_DARK_OFF;
        }
    }

    private boolean shouldSuppressDayNight(Context context) {
        if (!FLAG_ENABLE_LIGHT_DARK_MODE) {
            return true;
        }
        return !mSetupWizardBridge.isSetupWizardDayNightEnabled(context);
    }

    private boolean isSystemNightMode(Context context) {
        return mNightModeChecker.isSystemNightMode(context);
    }

    private String getDefaultThemeName(Intent intent) {
        String theme = intent.getStringExtra(WizardManagerHelper.EXTRA_THEME);
        if (TextUtils.isEmpty(theme)) {
            theme = mSetupWizardBridge.getSystemPropertySetupWizardTheme();
        }
        if (TextUtils.isEmpty(theme)) {
            theme = com.google.android.setupdesign.util.ThemeHelper.THEME_GLIF_LIGHT;
        }
        return theme;
    }

    interface SetupWizardBridge {
        boolean isSetupWizardDayNightEnabled(Context context);

        String getSystemPropertySetupWizardTheme();

        int resolveTheme(int defaultTheme, String themeName, boolean suppressDayNight);
    }

    interface NightModeChecker {
        boolean isSystemNightMode(Context context);
    }

    /**
     * Default implementation of {@link NightModeChecker}.
     */
    public static class DefaultNightModeChecker implements NightModeChecker {
        @Override
        public boolean isSystemNightMode(Context context) {
            return (context.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK)
                    == UI_MODE_NIGHT_YES;
        }
    }

    /**
     * Default implementation of {@link SetupWizardBridge}.
     */
    public static class DefaultSetupWizardBridge implements SetupWizardBridge {
        @Override
        public boolean isSetupWizardDayNightEnabled(Context context) {
            return com.google.android.setupdesign.util.ThemeHelper
                    .isSetupWizardDayNightEnabled(context);
        }

        @Override
        public String getSystemPropertySetupWizardTheme() {
            return SYSTEM_PROPERTY_SETUPWIZARD_THEME;
        }

        @Override
        public int resolveTheme(int defaultTheme, String themeName, boolean suppressDayNight) {
            ThemeResolver themeResolver = new ThemeResolver.Builder(ThemeResolver.getDefault())
                    .setDefaultTheme(defaultTheme)
                    .setUseDayNight(true)
                    .build();
            return themeResolver.resolve(
                    themeName,
                    suppressDayNight);
        }
    }
}
