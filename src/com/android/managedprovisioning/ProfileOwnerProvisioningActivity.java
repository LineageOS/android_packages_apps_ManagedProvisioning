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

import static com.android.managedprovisioning.provisioning.Constants.ACTION_CANCEL_PROVISIONING;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_GET_PROVISIONING_STATE;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_CANCELLED;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_ERROR;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_SUCCESS;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_START_PROVISIONING;
import static com.android.managedprovisioning.provisioning.Constants.EXTRA_USER_VISIBLE_ERROR_ID_KEY;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.SimpleProgressDialog;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.ProvisioningService;

/**
 * Profile owner provisioning sets up a separate profile on a device whose primary user is already
 * set up or being set up.
 *
 * <p>
 * The typical example is setting up a corporate profile that is controlled by their employer on a
 * users personal device to keep personal and work data separate.
 *
 * <p>
 * The activity handles the UI for managed profile provisioning and starts the
 * {@link ProvisioningService}, which runs through the various setup tasks.
 */
public class ProfileOwnerProvisioningActivity extends SetupLayoutActivity
        implements SimpleDialog.SimpleDialogListener {
    private static final String PROFILE_OWNER_ERROR_DIALOG =
            "ProfileOwnerErrorDialog";
    private static final String PROFILE_OWNER_CANCEL_PROGRESS_DIALOG =
            "ProfileOwnerCancelProgressDialog";
    private static final String PROFILE_OWNER_CANCEL_PROVISIONING_DIALOG =
            "ProfileOwnerCancelProvisioningDialog";

    private static final IntentFilter SERVICE_COMMS_INTENT_FILTER;
    static {
        SERVICE_COMMS_INTENT_FILTER = new IntentFilter();
        SERVICE_COMMS_INTENT_FILTER.addAction(ACTION_PROVISIONING_SUCCESS);
        SERVICE_COMMS_INTENT_FILTER.addAction(ACTION_PROVISIONING_ERROR);
        SERVICE_COMMS_INTENT_FILTER.addAction(ACTION_PROVISIONING_CANCELLED);
    }

    private static final String KEY_PROVISIONING_STARTED = "provisioning_started";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProvisionLogger.logd("Profile owner provisioning activity ONCREATE");

        boolean provisioningStarted = false;
        if (savedInstanceState != null) {
            provisioningStarted = savedInstanceState.getBoolean(KEY_PROVISIONING_STARTED, false);
        }

        if (!provisioningStarted) {
            Intent intent = new Intent(ACTION_START_PROVISIONING)
                    .setComponent(new ComponentName(this, ProvisioningService.class))
                    .putExtras(getIntent());
            startService(intent);
        }

        initializeLayoutParams(R.layout.progress, R.string.setting_up_workspace, true);
        setTitle(R.string.setup_profile_progress);

        final ProvisioningParams params = getIntent().getParcelableExtra(
                ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        if (params != null) {
            maybeSetLogoAndMainColor(params.mainColor);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_PROVISIONING_STARTED, true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Setup broadcast receiver for feedback from service.
        LocalBroadcastManager.getInstance(this).registerReceiver(mServiceMessageReceiver,
                SERVICE_COMMS_INTENT_FILTER);

        Intent intent = new Intent(ACTION_GET_PROVISIONING_STATE)
                .setComponent(new ComponentName(this, ProvisioningService.class));
        startService(intent);
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceMessageReceiver);
        super.onPause();
    }


    private final BroadcastReceiver mServiceMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleProvisioningResult(intent);
        }
    };

    private void handleProvisioningResult(Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case ACTION_PROVISIONING_SUCCESS:
                ProvisionLogger.logd("Successfully provisioned."
                        + "Finishing ProfileOwnerProvisioningActivity");
                onProvisioningSuccess();
                break;
            case ACTION_PROVISIONING_ERROR:
                int errorMessageId = intent.getIntExtra(
                        EXTRA_USER_VISIBLE_ERROR_ID_KEY,
                        R.string.managed_provisioning_error_text);
                error(errorMessageId);
                break;
            case ACTION_PROVISIONING_CANCELLED:
                onProvisioningAborted();
                break;
            default:
                ProvisionLogger.logi("Unhandled intent action: " + action);
        }
    }

    @Override
    public void onBackPressed() {
        if (!cancelProgressDialogShown()) {
            showCancelProvisioningDialog();
        }
    }

    private void cancelProvisioning() {
        Intent intent = new Intent(ACTION_CANCEL_PROVISIONING)
                .setComponent(new ComponentName(this, ProvisioningService.class));
        startService(intent);
    }

    private void showCancelProvisioningDialog() {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setCancelable(false)
                .setMessage(R.string.profile_owner_cancel_message)
                .setNegativeButtonMessage(R.string.profile_owner_cancel_cancel)
                .setPositiveButtonMessage(R.string.profile_owner_cancel_ok);
        showDialog(dialogBuilder, PROFILE_OWNER_CANCEL_PROVISIONING_DIALOG);
    }

    private void showCancelProgressDialog() {
        SimpleProgressDialog.Builder dialog = new SimpleProgressDialog.Builder()
                .setMessage(R.string.profile_owner_cancelling)
                .setCancelable(false)
                .setCanceledOnTouchOutside(false);
        showDialog(dialog, PROFILE_OWNER_CANCEL_PROGRESS_DIALOG);
    }

    private boolean cancelProgressDialogShown() {
        return getFragmentManager().findFragmentByTag(PROFILE_OWNER_CANCEL_PROGRESS_DIALOG) != null;
    }

    public void error(int resourceId) {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(R.string.provisioning_error_title)
                .setMessage(resourceId)
                .setCancelable(false)
                .setPositiveButtonMessage(R.string.device_owner_error_ok);
        showDialog(dialogBuilder, PROFILE_OWNER_ERROR_DIALOG);
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case PROFILE_OWNER_CANCEL_PROVISIONING_DIALOG:
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case PROFILE_OWNER_CANCEL_PROVISIONING_DIALOG:
                cancelProvisioning();
                showCancelProgressDialog();
                break;
            case PROFILE_OWNER_ERROR_DIALOG:
                onProvisioningAborted();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    /**
     * Finish activity and stop service.
     */
    private void onProvisioningSuccess() {
        stopService(new Intent(this, ProvisioningService.class));
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void onProvisioningAborted() {
        stopService(new Intent(this, ProvisioningService.class));
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
