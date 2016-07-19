/*
 * Copyright 2014, The Android Open Source Project
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

import android.app.Activity;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.ArrayList;

/**
 * This activity starts device owner provisioning:
 * It downloads a mobile device management application(mdm) from a given url and installs it,
 * or a given mdm is already present on the device. The mdm is set as the owner of the device so
 * that it has full control over the device:
 * TODO: put link here with documentation on how a device owner has control over the device
 * The mdm can then execute further setup steps.
 *
 * <p>
 * An example use case might be when a company wants to set up a device for a single use case
 * (such as giving instructions).
 * </p>
 *
 * <p>
 * Provisioning is triggered by a programmer device that sends required provisioning parameters via
 * nfc. For an example of a programmer app see:
 * com.example.android.apis.app.DeviceProvisioningProgrammerSample.
 * </p>
 *
 * <p>
 * In the unlikely case that this activity is killed the whole provisioning process so far is
 * repeated. We made sure that all tasks can be done twice without causing any problems.
 * </p>
 */
public class DeviceOwnerProvisioningActivity extends SetupLayoutActivity
        implements SimpleDialog.SimpleDialogListener {
    private static final String KEY_PENDING_INTENTS = "pending_intents";
    private static final String DEVICE_OWNER_CANCEL_RESET_DIALOG = "DeviceOwnerCancelResetDialog";
    private static final String DEVICE_OWNER_ERROR_DIALOG_OK = "DeviceOwnerErrorDialogOk";
    private static final String DEVICE_OWNER_ERROR_DIALOG_RESET = "DeviceOwnerErrorDialogReset";

    private BroadcastReceiver mServiceMessageReceiver;
    private TextView mProgressTextView;

    // List of intents received while cancel dialog is shown.
    private ArrayList<Intent> mPendingProvisioningIntents = new ArrayList<Intent>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mPendingProvisioningIntents = savedInstanceState
                    .getParcelableArrayList(KEY_PENDING_INTENTS);
        }

        // Setup the UI.
        initializeLayoutParams(R.layout.progress, R.string.setup_work_device, true);
        setTitle(R.string.setup_device_progress);
        mProgressTextView = (TextView) findViewById(R.id.prog_text);

        // Setup broadcast receiver for feedback from service.
        mServiceMessageReceiver = new ServiceMessageReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DeviceOwnerProvisioningService.ACTION_PROVISIONING_SUCCESS);
        filter.addAction(DeviceOwnerProvisioningService.ACTION_PROVISIONING_ERROR);
        filter.addAction(DeviceOwnerProvisioningService.ACTION_PROGRESS_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mServiceMessageReceiver, filter);

        // Load the ProvisioningParams (from message in Intent).
        final ProvisioningParams params = getIntent().getParcelableExtra(
                ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        if (params != null) {
            maybeSetLogoAndMainColor(params.mainColor);
        }
        startDeviceOwnerProvisioningService();
    }

    private void startDeviceOwnerProvisioningService() {
        Intent intent = new Intent(this, DeviceOwnerProvisioningService.class);
        intent.putExtras(getIntent());
        startService(intent);
    }

    class ServiceMessageReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (cancelDialogShown()) {
                // Postpone handling the intent.
                mPendingProvisioningIntents.add(intent);
                return;
            }
            handleProvisioningIntent(intent);
        }
    }

    private boolean cancelDialogShown() {
        return getFragmentManager().findFragmentByTag(DEVICE_OWNER_CANCEL_RESET_DIALOG) != null;
    }

    private void handleProvisioningIntent(Intent intent) {
        String action = intent.getAction();
        if (action.equals(DeviceOwnerProvisioningService.ACTION_PROVISIONING_SUCCESS)) {
            onProvisioningSuccess();
        } else if (action.equals(DeviceOwnerProvisioningService.ACTION_PROVISIONING_ERROR)) {
            int errorMessageId = intent.getIntExtra(
                    DeviceOwnerProvisioningService.EXTRA_USER_VISIBLE_ERROR_ID_KEY,
                    R.string.device_owner_error_general);
            boolean factoryResetRequired = intent.getBooleanExtra(
                    DeviceOwnerProvisioningService.EXTRA_FACTORY_RESET_REQUIRED,
                    true);

            error(errorMessageId, factoryResetRequired);
        } else if (action.equals(DeviceOwnerProvisioningService.ACTION_PROGRESS_UPDATE)) {
            int progressMessage = intent.getIntExtra(
                    DeviceOwnerProvisioningService.EXTRA_PROGRESS_MESSAGE_ID_KEY, -1);
            if (progressMessage >= 0) {
                progressUpdate(progressMessage);
            }
        }
    }

    private void onProvisioningSuccess() {
        closeActivity(Activity.RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        showCancelResetDialog();
    }

    private void showCancelResetDialog() {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setCancelable(false)
                .setMessage(R.string.device_owner_cancel_message)
                .setNegativeButtonMessage(R.string.device_owner_cancel_cancel)
                .setPositiveButtonMessage(R.string.device_owner_error_reset);
        showDialog(dialogBuilder, DEVICE_OWNER_CANCEL_RESET_DIALOG);
    }

    private void handlePendingIntents() {
        for (Intent intent : mPendingProvisioningIntents) {
            handleProvisioningIntent(intent);
        }
        mPendingProvisioningIntents.clear();
    }

    private void progressUpdate(int progressMessage) {
        mProgressTextView.setText(progressMessage);
        mProgressTextView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void error(int dialogMessage, boolean resetRequired) {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(R.string.provisioning_error_title)
                .setMessage(dialogMessage)
                .setCancelable(false)
                .setPositiveButtonMessage(resetRequired
                        ? R.string.device_owner_error_reset : R.string.device_owner_error_ok);

        showDialog(dialogBuilder, resetRequired
                ? DEVICE_OWNER_ERROR_DIALOG_RESET : DEVICE_OWNER_ERROR_DIALOG_OK);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(KEY_PENDING_INTENTS, mPendingProvisioningIntents);
    }

    @Override
    public void onDestroy() {
        if (mServiceMessageReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceMessageReceiver);
            mServiceMessageReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch(dialog.getTag()) {
            case DEVICE_OWNER_CANCEL_RESET_DIALOG:
                dialog.dismiss();
                handlePendingIntents();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        dialog.dismiss();
        switch(dialog.getTag()) {
            case DEVICE_OWNER_CANCEL_RESET_DIALOG:
                factoryResetDevice("DeviceOwnerProvisioningActivity.showCancelResetDialog()");
                break;
            case DEVICE_OWNER_ERROR_DIALOG_RESET:
                factoryResetDevice("DeviceOwnerProvisioningActivity.error()");
                closeActivity(RESULT_CANCELED);
                break;
            case DEVICE_OWNER_ERROR_DIALOG_OK:
                closeActivity(RESULT_CANCELED);
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    private void factoryResetDevice(String reason) {
        mUtils.sendFactoryResetBroadcast(this, reason);
    }

    private void closeActivity(int resultCode) {
        stopService(new Intent(this, DeviceOwnerProvisioningService.class));
        setResult(resultCode);
        finish();
    }
}
