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

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_EMAIL_ADDRESS;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.managedprovisioning.task.AddWifiNetworkTask;

import java.util.Locale;

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
    private static final int WIFI_REQUEST_CODE = 2;

    private BroadcastReceiver mServiceMessageReceiver;
    private TextView mProgressTextView;
    private Dialog mDialog; // The cancel or error dialog that is currently shown.
    private boolean mDone; // Indicates whether the service has sent ACTION_PROVISIONING_SUCCESS.
    private ProvisioningParams mParams;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisionLogger.logd("Device owner provisioning activity ONCREATE");

        // Check whether we can provision.
        if (Global.getInt(getContentResolver(), Global.DEVICE_PROVISIONED, 0 /* default */) != 0) {
            ProvisionLogger.loge("Device already provisioned.");
            error(R.string.device_owner_error_already_provisioned, false /* no factory reset */);
            return;
        }
        DevicePolicyManager dpm = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm.getDeviceOwner() != null) {
            ProvisionLogger.loge("Device owner already present.");
            error(R.string.device_owner_error_already_owned, false /* no factory reset */);
            return;
        }

        // Setup the UI.
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
        LocalBroadcastManager.getInstance(this).registerReceiver(mServiceMessageReceiver, filter);

        // Parse the incoming intent.
        MessageParser parser = new MessageParser();
        try {
            mParams = parser.parseIntent(getIntent());
        } catch (MessageParser.ParseException e) {
            ProvisionLogger.loge("Could not read data from intent", e);
            error(e.getErrorMessageId(), false /* no factory reset */);
            return;
        }

        // Ask to encrypt the device before proceeding
        if (!EncryptDeviceActivity.isDeviceEncrypted()) {
            requestEncryption(parser);
            finish();
            return;
            // System will reboot. Bootreminder will restart this activity.
        }

        // Have the user pick a wifi network if necessary.
        if (!AddWifiNetworkTask.isConnectedToWifi(this) && TextUtils.isEmpty(mParams.mWifiSsid) &&
                !TextUtils.isEmpty(mParams.mDeviceAdminPackageDownloadLocation)) {
            requestWifiPick();
            return;
            // Wait for onActivityResult.
        }

        startDeviceOwnerProvisioningService();
    }

    private void startDeviceOwnerProvisioningService() {
        Intent intent = new Intent(this, DeviceOwnerProvisioningService.class);
        intent.putExtra(DeviceOwnerProvisioningService.EXTRA_PROVISIONING_PARAMS, mParams);
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
                synchronized(this) {
                    if (mDialog == null) {
                        onProvisioningSuccess();
                    } else {
                        // Postpone finishing this activity till the user has decided whether
                        // he/she wants to reset or not.
                        mDone = true;
                    }
                }
                return;
            } else if (action.equals(DeviceOwnerProvisioningService.ACTION_PROVISIONING_ERROR)) {
                int errorMessageId = intent.getIntExtra(
                        DeviceOwnerProvisioningService.EXTRA_USER_VISIBLE_ERROR_ID_KEY,
                        R.string.device_owner_error_general);

                ProvisionLogger.logd("Error reported with code "
                        + getResources().getString(errorMessageId));
                error(errorMessageId, true /* always factory reset */);
            } else if (action.equals(DeviceOwnerProvisioningService.ACTION_PROGRESS_UPDATE)) {
                int progressMessage = intent.getIntExtra(
                        DeviceOwnerProvisioningService.EXTRA_PROGRESS_MESSAGE_ID_KEY, -1);
                ProvisionLogger.logd("Progress update reported with code "
                        + getResources().getString(progressMessage));
                if (progressMessage >= 0) {
                    progressUpdate(progressMessage);
                }
            }
        }
    }

    private void onProvisioningSuccess() {
        stopService(new Intent(DeviceOwnerProvisioningActivity.this,
                        DeviceOwnerProvisioningService.class));

        // Skip the setup wizard.
        Global.putInt(getContentResolver(), Global.DEVICE_PROVISIONED, 1);
        Secure.putInt(getContentResolver(), Secure.USER_SETUP_COMPLETE, 1);

        Intent completeIntent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        completeIntent.setPackage(mParams.mDeviceAdminPackageName);
        completeIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
                Intent.FLAG_RECEIVER_FOREGROUND);
        if (mParams.mManagedDeviceEmailAddress != null) {
            completeIntent.putExtra(EXTRA_PROVISIONING_EMAIL_ADDRESS,
                    mParams.mManagedDeviceEmailAddress);
        }

        sendBroadcast(completeIntent);
        finish();
    }

    private void requestEncryption(MessageParser messageParser) {
        Intent encryptIntent = new Intent(DeviceOwnerProvisioningActivity.this,
                EncryptDeviceActivity.class);

        Bundle resumeExtras = new Bundle();
        resumeExtras.putString(EncryptDeviceActivity.EXTRA_RESUME_TARGET,
                EncryptDeviceActivity.TARGET_DEVICE_OWNER);
        messageParser.addProvisioningParamsToBundle(resumeExtras, mParams);

        encryptIntent.putExtra(EncryptDeviceActivity.EXTRA_RESUME, resumeExtras);

        startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
    }

    private void requestWifiPick() {
        startActivityForResult(AddWifiNetworkTask.getWifiPickIntent(), WIFI_REQUEST_CODE);
    }

    @Override
    public void onBackPressed() {
        showCancelResetDialog();
    }

    private void showCancelResetDialog() {
        AlertDialog.Builder alertBuilder =
                new AlertDialog.Builder(DeviceOwnerProvisioningActivity.this)
                .setTitle(R.string.device_owner_cancel_title)
                .setMessage(R.string.device_owner_cancel_message)
                .setNegativeButton(R.string.device_owner_cancel_cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.dismiss();
                                synchronized(this) {
                                    mDialog = null;
                                    if (mDone) {
                                        onProvisioningSuccess();
                                    }
                                }
                            }
                        })
                .setPositiveButton(R.string.device_owner_error_reset,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,int id) {
                                // Factory reset the device.
                                sendBroadcast(
                                        new Intent("android.intent.action.MASTER_CLEAR"));
                                stopService(new Intent(DeviceOwnerProvisioningActivity.this,
                                                DeviceOwnerProvisioningService.class));
                                finish();
                            }
                        });

        if (mDialog != null) {
            mDialog.dismiss();
        }
        mDialog = alertBuilder.create();
        mDialog.show();
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
        } else if (requestCode == WIFI_REQUEST_CODE) {
            if (resultCode == RESULT_CANCELED) {
                ProvisionLogger.loge("User canceled wifi picking.");
                stopService(new Intent(DeviceOwnerProvisioningActivity.this,
                                DeviceOwnerProvisioningService.class));
                finish();
            } else if (resultCode == RESULT_OK) {
                ProvisionLogger.logd("Wifi request result is OK");
                if (AddWifiNetworkTask.isConnectedToWifi(this)) {
                    startDeviceOwnerProvisioningService();
                } else {
                    requestWifiPick();
                }
            }
        }
    }

    private void error(int dialogMessage, boolean resetRequired) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this)
            .setTitle(R.string.device_owner_error_title)
            .setMessage(dialogMessage)
            .setCancelable(false);
        if (resetRequired) {
            alertBuilder.setPositiveButton(R.string.device_owner_error_reset,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,int id) {
                            // Factory reset the device.
                            sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));
                            stopService(new Intent(DeviceOwnerProvisioningActivity.this,
                                            DeviceOwnerProvisioningService.class));
                            finish();
                        }
                    });
        } else {
            alertBuilder.setPositiveButton(R.string.device_owner_error_ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,int id) {
                            // Close activity.
                            stopService(new Intent(DeviceOwnerProvisioningActivity.this,
                                            DeviceOwnerProvisioningService.class));
                            finish();
                        }
                    });
        }
        mDialog = alertBuilder.create();
        mDialog.show();
    }

    @Override
    public void onDestroy() {
        ProvisionLogger.logd("Device owner provisioning activity ONDESTROY");
        if (mServiceMessageReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceMessageReceiver);
            mServiceMessageReceiver = null;
        }
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onDestroy();
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

