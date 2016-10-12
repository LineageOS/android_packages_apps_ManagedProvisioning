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

import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_ACTION;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_DPC_INSTALLED_BY_PACKAGE;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_DPC_PACKAGE_NAME;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_EXTRA;

import android.content.Context;
import android.content.Intent;

import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.List;

/**
 * Utility class to log metrics.
 */
public class ProvisioningAnalyticsTracker {
    private static final ProvisioningAnalyticsTracker sInstance =
            new ProvisioningAnalyticsTracker();

    private final MetricsLoggerWrapper mMetricsLoggerWrapper = new MetricsLoggerWrapper();

    public static ProvisioningAnalyticsTracker getInstance() {
        return sInstance;
    }

    private ProvisioningAnalyticsTracker() {
        // Disables instantiation. Use getInstance() instead.
    }

    /**
     * Logs some metrics when the provisioning starts.
     *
     * @param context Context passed to MetricsLogger
     * @param params Provisioning params
     */
    public void logProvisioningStarted(Context context, ProvisioningParams params) {
        logDpcPackageInformation(context, params.inferDeviceAdminPackageName());
        logNetworkType(context);
        logProvisioningAction(context, params.provisioningAction);
    }

    /**
     * Logs all the provisionign extras passed by the dpc.
     *
     * @param context Context passed to MetricsLogger
     * @param intent Intent that started provisioning
     */
    public void logProvisioningExtras(Context context, Intent intent) {
        final List<String> provisioningExtras = AnalyticsUtils.getAllProvisioningExtras(intent);
        for (String extra : provisioningExtras) {
            mMetricsLoggerWrapper.logAction(context, PROVISIONING_EXTRA, extra);
        }
    }

    /**
     * Logs package information of the dpc.
     *
     * @param context Context passed to MetricsLogger
     * @param dpcPackageName Package name of the dpc
     */
    private void logDpcPackageInformation(Context context, String dpcPackageName) {
        // Logs package name of the dpc.
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_DPC_PACKAGE_NAME, dpcPackageName);

        // Logs package name of the package which installed dpc.
        final String dpcInstallerPackage =
                AnalyticsUtils.getInstallerPackageName(context, dpcPackageName);
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_DPC_INSTALLED_BY_PACKAGE,
                dpcInstallerPackage);
    }

    /**
     * Logs the network type to which the device is connected.
     *
     * @param context Context passed to MetricsLogger
     */
    private void logNetworkType(Context context) {
        NetworkTypeLogger networkTypeLogger = new NetworkTypeLogger(context);
        networkTypeLogger.log();
    }

    /**
     * Logs the provisioning action.
     *
     * @param context Context passed to MetricsLogger
     * @param provisioningAction Action that triggered provisioning
     */
    private void logProvisioningAction(Context context, String provisioningAction) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_ACTION, provisioningAction);
    }
}
