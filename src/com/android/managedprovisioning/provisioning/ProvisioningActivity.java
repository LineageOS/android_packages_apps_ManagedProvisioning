/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static com.android.managedprovisioning.provisioning.Constants.ACTION_CANCEL_PROVISIONING;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_GET_PROVISIONING_STATE;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROGRESS_UPDATE;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_CANCELLED;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_ERROR;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_PROVISIONING_SUCCESS;
import static com.android.managedprovisioning.provisioning.Constants.ACTION_START_PROVISIONING;
import static com.android.managedprovisioning.provisioning.Constants.EXTRA_FACTORY_RESET_REQUIRED;
import static com.android.managedprovisioning.provisioning.Constants.EXTRA_PROGRESS_MESSAGE_ID_KEY;
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
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.SetupLayoutActivity;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.SimpleProgressDialog;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.ArrayList;

/**
 * Progress activity shown whilst provisioning is ongoing.
 *
 * <p>This activity communicates with the {@link ProvisioningService} via local broadcasts. It shows
 * progress updates as provisioning progresses and handles showing of cancel and error dialogs.</p>
 */
public class ProvisioningActivity extends SetupLayoutActivity
        implements SimpleDialog.SimpleDialogListener {

    private static final String ERROR_DIALOG_OK = "ErrorDialogOk";
    private static final String ERROR_DIALOG_RESET = "ErrorDialogReset";
    private static final String CANCEL_PROGRESS_DIALOG = "CancelProgressDialog";
    private static final String CANCEL_PROVISIONING_DIALOG_OK = "CancelProvisioningDialogOk";
    private static final String CANCEL_PROVISIONING_DIALOG_RESET = "CancelProvisioningDialogReset";

    private static final String KEY_PROVISIONING_STARTED = "provisioning_started";
    private static final String KEY_PENDING_INTENTS = "pending_intents";

    private static final IntentFilter SERVICE_COMMS_INTENT_FILTER;
    static {
        SERVICE_COMMS_INTENT_FILTER = new IntentFilter();
        SERVICE_COMMS_INTENT_FILTER.addAction(ACTION_PROVISIONING_SUCCESS);
        SERVICE_COMMS_INTENT_FILTER.addAction(ACTION_PROVISIONING_ERROR);
        SERVICE_COMMS_INTENT_FILTER.addAction(ACTION_PROVISIONING_CANCELLED);
        SERVICE_COMMS_INTENT_FILTER.addAction(ACTION_PROGRESS_UPDATE);
    }
    private TextView mProgressTextView;
    private ProvisioningParams mParams;

    // List of intents received while cancel dialog is shown.
    private ArrayList<Intent> mPendingProvisioningIntents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProvisionLogger.logd("ProvisioningActivity ONCREATE");

        boolean provisioningStarted = false;
        if (savedInstanceState != null) {
            provisioningStarted = savedInstanceState.getBoolean(KEY_PROVISIONING_STARTED, false);
            mPendingProvisioningIntents = savedInstanceState
                    .getParcelableArrayList(KEY_PENDING_INTENTS);
        }

        if (!provisioningStarted) {
            Intent intent = new Intent(ACTION_START_PROVISIONING)
                    .setComponent(new ComponentName(this, ProvisioningService.class))
                    .putExtras(getIntent());
            startService(intent);
        }

        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        initializeUi();

        mProgressTextView = (TextView) findViewById(R.id.prog_text);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_PROVISIONING_STARTED, true);
        outState.putParcelableArrayList(KEY_PENDING_INTENTS, mPendingProvisioningIntents);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ProvisionLogger.logd("On resume");

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

    @Override
    public void onBackPressed() {
        if (!cancelProgressDialogShown()) {
            showCancelProvisioningDialog();
        }
    }

    private final BroadcastReceiver mServiceMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (cancelDialogShown()) {
                // Postpone handling the intent.
                mPendingProvisioningIntents.add(intent);
                return;
            }
            handleProvisioningIntent(intent);
        }
    };

    private void handleProvisioningIntent(Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case ACTION_PROVISIONING_SUCCESS:
                onProvisioningSuccess();
                break;
            case ACTION_PROVISIONING_ERROR:
                int errorMessageId = intent.getIntExtra(
                        EXTRA_USER_VISIBLE_ERROR_ID_KEY,
                        R.string.device_owner_error_general);
                boolean factoryResetRequired = intent.getBooleanExtra(
                        EXTRA_FACTORY_RESET_REQUIRED,
                        true);
                error(errorMessageId, factoryResetRequired);
                break;
            case ACTION_PROGRESS_UPDATE:
                int progressMessage = intent.getIntExtra(EXTRA_PROGRESS_MESSAGE_ID_KEY, -1);
                if (progressMessage >= 0) {
                    progressUpdate(progressMessage);
                }
                break;
            case ACTION_PROVISIONING_CANCELLED:
                onProvisioningAborted();
                break;
            default:
                ProvisionLogger.logi("Unhandled intent action: " + action);
        }
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

        showDialog(dialogBuilder, resetRequired ? ERROR_DIALOG_RESET : ERROR_DIALOG_OK);
    }

    private void showCancelProgressDialog() {
        SimpleProgressDialog.Builder dialog = new SimpleProgressDialog.Builder()
                .setMessage(R.string.profile_owner_cancelling)
                .setCancelable(false)
                .setCanceledOnTouchOutside(false);
        showDialog(dialog, CANCEL_PROGRESS_DIALOG);
    }

    protected void showCancelProvisioningDialog() {
        final boolean isDoProvisioning = mUtils.isDeviceOwnerAction(mParams.provisioningAction);
        final String dialogTag = isDoProvisioning ? CANCEL_PROVISIONING_DIALOG_RESET
                : CANCEL_PROVISIONING_DIALOG_OK;
        final int positiveResId = isDoProvisioning ? R.string.device_owner_error_reset
                : R.string.profile_owner_cancel_ok;
        final int negativeResId = isDoProvisioning ? R.string.device_owner_cancel_cancel
                : R.string.profile_owner_cancel_cancel;
        final int dialogMsgResId = isDoProvisioning ? R.string.device_owner_cancel_message
                : R.string.profile_owner_cancel_message;

        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setCancelable(false)
                .setMessage(dialogMsgResId)
                .setNegativeButtonMessage(negativeResId)
                .setPositiveButtonMessage(positiveResId);
        showDialog(dialogBuilder, dialogTag);
    }

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

    private void cancelProvisioning() {
        Intent intent = new Intent(ACTION_CANCEL_PROVISIONING)
                .setComponent(new ComponentName(this, ProvisioningService.class));
        startService(intent);
    }

    private boolean cancelDialogShown() {
        return getFragmentManager().findFragmentByTag(CANCEL_PROVISIONING_DIALOG_OK) != null
                || getFragmentManager().findFragmentByTag(CANCEL_PROVISIONING_DIALOG_RESET) != null;
    }

    private boolean cancelProgressDialogShown() {
        return getFragmentManager().findFragmentByTag(CANCEL_PROGRESS_DIALOG) != null;
    }

     @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
            case CANCEL_PROVISIONING_DIALOG_RESET:
                dialog.dismiss();
                handlePendingIntents();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
                dialog.dismiss();
                cancelProvisioning();
                showCancelProgressDialog();
                break;
            case CANCEL_PROVISIONING_DIALOG_RESET:
                mUtils.sendFactoryResetBroadcast(this, "DO provisioning cancelled by user");
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_OK:
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_RESET:
                mUtils.sendFactoryResetBroadcast(this, "Error during DO provisioning");
                onProvisioningAborted();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    private void handlePendingIntents() {
        mPendingProvisioningIntents.stream().forEach(intent -> handleProvisioningIntent(intent));
        mPendingProvisioningIntents.clear();
    }

    private void initializeUi() {
        final boolean isDoProvisioning = mUtils.isDeviceOwnerAction(mParams.provisioningAction);
        final int headerResId = isDoProvisioning ? R.string.setup_work_device
                : R.string.setting_up_workspace;
        final int titleResId = isDoProvisioning ? R.string.setup_device_progress
                : R.string.setup_profile_progress;

        initializeLayoutParams(R.layout.progress, headerResId, true);
        setTitle(titleResId);
        if (mParams != null) {
            maybeSetLogoAndMainColor(mParams.mainColor);
        }
    }
}
