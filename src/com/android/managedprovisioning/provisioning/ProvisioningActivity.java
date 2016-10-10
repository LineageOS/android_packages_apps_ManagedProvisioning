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

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.SetupLayoutActivity;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Progress activity shown whilst provisioning is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link ProvisioningManager}. It shows progress updates as provisioning progresses and handles
 * showing of cancel and error dialogs.</p>
 */
public class ProvisioningActivity extends SetupLayoutActivity
        implements SimpleDialog.SimpleDialogListener, ProvisioningManagerCallback {

    private static final String ERROR_DIALOG_OK = "ErrorDialogOk";
    private static final String ERROR_DIALOG_RESET = "ErrorDialogReset";
    private static final String CANCEL_PROVISIONING_DIALOG_OK = "CancelProvisioningDialogOk";
    private static final String CANCEL_PROVISIONING_DIALOG_RESET = "CancelProvisioningDialogReset";

    private TextView mProgressTextView;
    private ProvisioningParams mParams;

    protected ProvisioningManager getProvisioningManager() {
        return ProvisioningManager.getInstance(this);
    }

    protected Utils getUtils() {
        return mUtils;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);

        getProvisioningManager().initiateProvisioning(mParams);

        initializeUi(mParams);
        mProgressTextView = (TextView) findViewById(R.id.prog_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getProvisioningManager().registerListener(this);
    }

    @Override
    public void onPause() {
        getProvisioningManager().unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        showCancelProvisioningDialog();
    }

    @Override
    public void cleanUpCompleted() {
        onProvisioningAborted();
    }

    @Override
    public void preFinalizationCompleted() {
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    public void provisioningTasksCompleted() {
        getProvisioningManager().preFinalize();
    }

    @Override
    public void progressUpdate(int progressMessage) {
        mProgressTextView.setText(progressMessage);
        mProgressTextView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    @Override
    public void error(int dialogMessage, boolean resetRequired) {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(R.string.provisioning_error_title)
                .setMessage(dialogMessage)
                .setCancelable(false)
                .setPositiveButtonMessage(resetRequired
                        ? R.string.device_owner_error_reset : R.string.device_owner_error_ok);

        // Stop listening for further updates to avoid finishing the activity after cleanup has
        // completed
        getProvisioningManager().unregisterListener(this);
        showDialog(dialogBuilder, resetRequired ? ERROR_DIALOG_RESET : ERROR_DIALOG_OK);
    }

    private void showCancelProvisioningDialog() {
        final boolean isDoProvisioning = getUtils().isDeviceOwnerAction(mParams.provisioningAction);
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

        // Temporarily stop listening for further updates to avoid the UI changing whilst the user
        // is contemplating cancelling the progress
        getProvisioningManager().unregisterListener(this);
        showDialog(dialogBuilder, dialogTag);
    }

    private void onProvisioningAborted() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
            case CANCEL_PROVISIONING_DIALOG_RESET:
                dialog.dismiss();
                getProvisioningManager().registerListener(this);
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
                getProvisioningManager().cancelProvisioning();
                onProvisioningAborted();
                break;
            case CANCEL_PROVISIONING_DIALOG_RESET:
                getUtils().sendFactoryResetBroadcast(this, "DO provisioning cancelled by user");
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_OK:
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_RESET:
                getUtils().sendFactoryResetBroadcast(this, "Error during DO provisioning");
                onProvisioningAborted();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    private void initializeUi(ProvisioningParams params) {
        final boolean isDoProvisioning = getUtils().isDeviceOwnerAction(params.provisioningAction);
        final int headerResId = isDoProvisioning ? R.string.setup_work_device
                : R.string.setting_up_workspace;
        final int titleResId = isDoProvisioning ? R.string.setup_device_progress
                : R.string.setup_profile_progress;

        initializeLayoutParams(R.layout.progress, headerResId, true);
        setTitle(titleResId);
        maybeSetLogoAndMainColor(params.mainColor);
    }
}
