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
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings.Global;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

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
public class DeviceOwnerProvisioningActivity extends Activity {
    private static final int ENCRYPT_DEVICE_REQUEST_CODE = 1;

    private BroadcastReceiver mServiceMessageReceiver;
    private TextView mProgressTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisionLogger.logd("Device owner provisioning activity ONCREATE");

        // Check whether we can provision.
        if (Global.getInt(getContentResolver(), Global.DEVICE_PROVISIONED, /* default */ 0) != 0) {
            ProvisionLogger.loge("Device already provisioned.");
            error(R.string.device_owner_error_already_provisioned);
            return;
        }

        DevicePolicyManager dpm = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm.getDeviceOwner() != null) {
            ProvisionLogger.loge("Device owner already present.");
            error(R.string.device_owner_error_already_owned);
            return;
        }

        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.progress, null);
        setContentView(contentView);
        mProgressTextView = (TextView) findViewById(R.id.prog_text);

        // Setup broadcast receiver for feedback from service.
        mServiceMessageReceiver = new ServiceMessageReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DeviceOwnerProvisioningService.ACTION_PROVISIONING_SUCCESS);
        filter.addAction(DeviceOwnerProvisioningService.ACTION_PROVISIONING_ERROR);
        filter.addAction(DeviceOwnerProvisioningService.ACTION_PROGRESS_UPDATE);
        filter.addAction(DeviceOwnerProvisioningService.ACTION_REQUEST_ENCRYPTION);
        registerReceiver(mServiceMessageReceiver, filter);

        // Start service.
        Intent intent = new Intent(this, DeviceOwnerProvisioningService.class);
        intent.putExtras(getIntent());
        startService(intent);
    }

    class ServiceMessageReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (action.equals(DeviceOwnerProvisioningService.ACTION_PROVISIONING_SUCCESS)) {
                ProvisionLogger.logd("Successfully provisioned");
                finish();
                return;
            } else if (action.equals(DeviceOwnerProvisioningService.ACTION_PROVISIONING_ERROR)) {
                int errorCode = intent.getIntExtra(
                        DeviceOwnerProvisioningService.EXTRA_USER_VISIBLE_ERROR_ID_KEY, -1);
                ProvisionLogger.logd("Error reported with code "
                        + getResources().getString(errorCode));
                if (errorCode < 0) {
                    error(R.string.device_owner_error_general);
                    return;
                } else {
                    error(errorCode);
                }
            } else if (action.equals(DeviceOwnerProvisioningService.ACTION_PROGRESS_UPDATE)) {
                int progressMessage = intent.getIntExtra(
                        DeviceOwnerProvisioningService.EXTRA_PROGRESS_MESSAGE_ID_KEY, -1);
                ProvisionLogger.logd("Progress update reported with code "
                        + getResources().getString(progressMessage));
                if (progressMessage >= 0) {
                    progressUpdate(progressMessage);
                }
            } else if (action.equals(DeviceOwnerProvisioningService.ACTION_REQUEST_ENCRYPTION)) {
                ProvisionLogger.logd("Received request to encrypt device.");
                Intent encryptIntent = new Intent(DeviceOwnerProvisioningActivity.this,
                        EncryptDeviceActivity.class);
                encryptIntent.putExtras(intent);
                startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // TODO: Handle this graciously by stopping the provisioning flow and cleaning up.
    }

    @Override
    public void onDestroy() {
        ProvisionLogger.logd("Device owner provisioning activity ONDESTROY");
        unregisterReceiver(mServiceMessageReceiver);
        super.onDestroy();
    }

    private void progressUpdate(int progressMessage) {
        mProgressTextView.setText(progressMessage);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENCRYPT_DEVICE_REQUEST_CODE) {
            if (resultCode == RESULT_CANCELED) {
                ProvisionLogger.loge("User canceled device encryption.");
                finish();
            }
        }
    }

    private void error(int dialogMessage) {
        AlertDialog dlg = new AlertDialog.Builder(this)
            .setTitle(R.string.device_owner_error_title)
            .setMessage(dialogMessage)
            .setCancelable(false)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,int id) {
                    // Close activity
                    finish();
                }
            })
            .create();
        dlg.show();
    }

    @Override
    protected void onRestart() {
        ProvisionLogger.logd("Device owner provisioning activity ONRESTART");
        super.onRestart();
    }

    @Override
    protected void onResume() {
        ProvisionLogger.logd("Device owner provisioning activity ONRESUME");
        super.onResume();
    }

    @Override
    protected void onPause() {
        ProvisionLogger.logd("Device owner provisioning activity ONPAUSE");
        super.onPause();
    }

    @Override
    protected void onStop() {
        ProvisionLogger.logd("Device owner provisioning activity ONSTOP");
        super.onStop();
    }
}

