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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_ACTION;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_CANCELLED;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_DPC_INSTALLED_BY_PACKAGE;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_DPC_PACKAGE_NAME;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_ENTRY_POINT_NFC;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_ENTRY_POINT_TRUSTED_SOURCE;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_EXTRA;

import android.annotation.IntDef;
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

    // Only add to the end of the list. Do not change or rearrange these values, that will break
    // historical data. Do not use negative numbers or zero, logger only handles positive
    // integers.
    public static final int CANCELLED_BEFORE_PROVISIONING = 1;
    public static final int CANCELLED_DURING_PROVISIONING = 2;

    @IntDef({
        CANCELLED_BEFORE_PROVISIONING,
        CANCELLED_DURING_PROVISIONING})
    public @interface CancelState {}

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
     * Logs some metrics when the preprovisioning starts.
     *
     * @param context Context passed to MetricsLogger
     * @param intent Intent that started provisioning
     */
    public void logPreProvisioningStarted(Context context, Intent intent) {
        logProvisioningExtras(context, intent);
        maybeLogEntryPoint(context, intent);
    }

    /**
     * Logs when provisioning is cancelled.
     *
     * @param context Context passed to MetricsLogger
     * @param cancelState State when provisioning was cancelled
     */
    public void logProvisioningCancelled(Context context, @CancelState int cancelState) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_CANCELLED, cancelState);
    }

    /**
     * Logs all the provisioning extras passed by the dpc.
     *
     * @param context Context passed to MetricsLogger
     * @param intent Intent that started provisioning
     */
    private void logProvisioningExtras(Context context, Intent intent) {
        final List<String> provisioningExtras = AnalyticsUtils.getAllProvisioningExtras(intent);
        for (String extra : provisioningExtras) {
            mMetricsLoggerWrapper.logAction(context, PROVISIONING_EXTRA, extra);
        }
    }

    /**
     * Logs some entry points to provisioning.
     *
     * @param context Context passed to MetricsLogger
     * @param intent Intent that started provisioning
     */
    private void maybeLogEntryPoint(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case ACTION_NDEF_DISCOVERED:
                mMetricsLoggerWrapper.logAction(context, PROVISIONING_ENTRY_POINT_NFC);
                break;
            case ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE:
                mMetricsLoggerWrapper.logAction(context, PROVISIONING_ENTRY_POINT_TRUSTED_SOURCE);
                break;
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
