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
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static android.stats.devicepolicy.DevicePolicyEnums.PROVISIONING_MANAGED_PROFILE_ON_FULLY_MANAGED_DEVICE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ACTION;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_CANCELLED;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_COPY_ACCOUNT_STATUS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_DPC_INSTALLED_BY_PACKAGE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_DPC_PACKAGE_NAME;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ENTRY_POINT_NFC;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ENTRY_POINT_QR_CODE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ENTRY_POINT_TRUSTED_SOURCE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_ERROR;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_EXTRA;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_SESSION_COMPLETED;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_SESSION_STARTED;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TERMS_COUNT;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TERMS_READ;

import android.annotation.IntDef;
import android.app.admin.DevicePolicyEventLogger;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.stats.devicepolicy.DevicePolicyEnums;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
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

    // Only add to the end of the list. Do not change or rearrange these values, that will break
    // historical data. Do not use negative numbers or zero, logger only handles positive
    // integers.
    public static final int COPY_ACCOUNT_SUCCEEDED = 1;
    public static final int COPY_ACCOUNT_FAILED = 2;
    public static final int COPY_ACCOUNT_TIMED_OUT = 3;
    public static final int COPY_ACCOUNT_EXCEPTION = 4;

    @IntDef({
        COPY_ACCOUNT_SUCCEEDED,
        COPY_ACCOUNT_FAILED,
        COPY_ACCOUNT_TIMED_OUT,
        COPY_ACCOUNT_EXCEPTION})
    public @interface CopyAccountStatus {}

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
        maybeLogManagedProfileOnFullyManagedDevice(context, params.provisioningAction);
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
     * Logs status of copy account to user task.
     *
     * @param context Context passed to MetricsLogger
     * @param status Status of copy account to user task
     */
    public void logCopyAccountStatus(Context context, @CopyAccountStatus int status) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_COPY_ACCOUNT_STATUS, status);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_COPY_ACCOUNT_STATUS)
                .setInt(status)
                .write();
    }

    /**
     * Logs when provisioning is cancelled.
     *
     * @param context Context passed to MetricsLogger
     * @param cancelState State when provisioning was cancelled
     */
    public void logProvisioningCancelled(Context context, @CancelState int cancelState) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_CANCELLED, cancelState);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_CANCELLED)
                .setInt(cancelState)
                .write();
    }

    /**
     * Logs error during provisioning tasks.
     *
     * @param context Context passed to MetricsLogger
     * @param task Provisioning task which threw error
     * @param errorCode Code indicating the type of error that happened.
     */
    public void logProvisioningError(Context context, AbstractProvisioningTask task,
            int errorCode) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_ERROR,
                AnalyticsUtils.getErrorString(task, errorCode));
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_ERROR)
                .setStrings(AnalyticsUtils.getErrorString(task, errorCode))
                .write();
    }

    /**
     * Logs error code, when provisioning is not allowed.
     *
     * @param context Context passed to MetricsLogger
     * @param provisioningErrorCode Code indicating why provisioning is not allowed.
     */
    public void logProvisioningNotAllowed(Context context, int provisioningErrorCode) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_ERROR, provisioningErrorCode);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_ERROR)
                .setStrings(String.valueOf(provisioningErrorCode))
                .write();
    }

    /**
     * logs when a provisioning session has started.
     *
     * @param context Context passed to MetricsLogger
     */
    public void logProvisioningSessionStarted(Context context) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_SESSION_STARTED);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_SESSION_STARTED)
                .write();
    }

    /**
     * logs when a provisioning session has completed.
     *
     * @param context Context passed to MetricsLogger
     */
    public void logProvisioningSessionCompleted(Context context) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_SESSION_COMPLETED);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_SESSION_COMPLETED)
                .write();
    }

    /**
     * logs number of terms displayed on the terms screen.
     *
     * @param context Context passed to MetricsLogger
     * @param count Number of terms displayed
     */
    public void logNumberOfTermsDisplayed(Context context, int count) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_TERMS_COUNT, count);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_TERMS_COUNT)
                .write();
    }

    /**
     * logs number of terms read on the terms screen.
     *
     * @param context Context passed to MetricsLogger
     * @param count Number of terms read
     */
    public void logNumberOfTermsRead(Context context, int count) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_TERMS_READ, count);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_TERMS_READ)
                .setInt(count)
                .write();
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
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_EXTRAS)
                .setStrings(provisioningExtras.toArray(new String[0]))
                .write();
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
                DevicePolicyEventLogger
                        .createEvent(DevicePolicyEnums.PROVISIONING_ENTRY_POINT_NFC)
                        .write();
                break;
            case ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE:
                logProvisionedFromTrustedSource(context, intent);
                break;
        }
    }

    private void logProvisionedFromTrustedSource(Context context, Intent intent) {
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_ENTRY_POINT_TRUSTED_SOURCE);
        final int provisioningTrigger = intent.getIntExtra(EXTRA_PROVISIONING_TRIGGER,
                PROVISIONING_TRIGGER_UNSPECIFIED);
        if (provisioningTrigger == PROVISIONING_TRIGGER_QR_CODE) {
            mMetricsLoggerWrapper.logAction(context, PROVISIONING_ENTRY_POINT_QR_CODE);
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_ENTRY_POINT_TRUSTED_SOURCE)
                .setInt(provisioningTrigger)
                .write();
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
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_DPC_PACKAGE_NAME)
                .setStrings(dpcPackageName)
                .write();

        // Logs package name of the package which installed dpc.
        final String dpcInstallerPackage =
                AnalyticsUtils.getInstallerPackageName(context, dpcPackageName);
        mMetricsLoggerWrapper.logAction(context, PROVISIONING_DPC_INSTALLED_BY_PACKAGE,
                dpcInstallerPackage);
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_DPC_INSTALLED_BY_PACKAGE)
                .setStrings(dpcInstallerPackage)
                .write();
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
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.PROVISIONING_ACTION)
                .setStrings(provisioningAction)
                .write();
    }

    private void maybeLogManagedProfileOnFullyManagedDevice(Context context,
            String provisioningAction) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        final ComponentName currentDeviceOwner = dpm.getDeviceOwnerComponentOnAnyUser();
        if (currentDeviceOwner != null
                && ACTION_PROVISION_MANAGED_PROFILE.equals(provisioningAction)) {
            DevicePolicyEventLogger
                    .createEvent(PROVISIONING_MANAGED_PROFILE_ON_FULLY_MANAGED_DEVICE)
                    .write();
        }
    }
}