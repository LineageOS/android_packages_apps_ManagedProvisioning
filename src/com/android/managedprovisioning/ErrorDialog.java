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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

/**
 * Provisioning the device failed. Show what user it was meant for, then
 * instruct the user to factory reset so we can try again.
 */
public class ErrorDialog extends DialogFragment {
    public ErrorDialog() {
    }

    public DialogInterface.OnClickListener mClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            factoryReset();
        }
    };

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // If the device hasn't been provisioned, do the reset for the user. If
        // it has, instruct the user to reset it.
        int message = isDeviceProvisioned() ? R.string.error_desc_secondary : R.string.error_desc;
        int button = isDeviceProvisioned() ? android.R.string.ok : R.string.reset_button;
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.error_title)
                .setMessage(message)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(button, mClickListener)
                .create();
    }

    private void factoryReset() {
        // Don't factory reset if the device has been provisioned.
        if (!isDeviceProvisioned()) {
            getActivity().sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));
        }
        getActivity().finish();  // Quit.
    }

    private boolean isDeviceProvisioned() {
        SettingsAdapter adapter = new SettingsAdapter(getActivity().getContentResolver());
        return adapter.isDeviceProvisioned();
    }

    /**
     * Used when there isn't an activity being shown, like in a service. This
     * launches an activity, and then shows the error dialog on top of it.
     */
    public static void showError(Context context) {
        Intent intent = new Intent(context, ErrorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
