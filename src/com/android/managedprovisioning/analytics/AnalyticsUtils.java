/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.analytics;

import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.Nullable;

import com.android.managedprovisioning.ProvisionLogger;

/**
 * Class containing various auxiliary methods used by provisioning analytics tracker.
 */
public class AnalyticsUtils {

    public AnalyticsUtils() {}

    /**
     * Returns package name of the installer package, null if package is not present on the device
     * and empty string if installer package is not present on the device.
     *
     * @param context Context used to get package manager
     * @param packageName Package name of the installed package
     */
    @Nullable
    public static String getInstallerPackageName(Context context, String packageName) {
        try {
            return context.getPackageManager().getInstallerPackageName(packageName);
        } catch (IllegalArgumentException e) {
            ProvisionLogger.loge(packageName + " is not installed.", e);
            return null;
        }
    }

    /**
     * Returns elapsed real time.
     */
    public Long elapsedRealTime() {
        return SystemClock.elapsedRealtime();
    }
}
