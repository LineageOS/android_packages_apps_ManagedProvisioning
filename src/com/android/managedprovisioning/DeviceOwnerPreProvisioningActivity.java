/*
 * Copyright 2015, The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.Utils.MdmPackageInfo;

/**
 * This activity makes necessary checks before starting {@link DeviceOwnerProvisioningActivity}.
 * It checks if the device or user is already setup. It makes sure the device is encrypted, the
 * provisioning intent extras are valid and the device is connected to a wifi network.
 */
public class DeviceOwnerPreProvisioningActivity extends SetupLayoutActivity
        implements UserConsentDialog.ConsentCallback {
    private static final boolean DEBUG = false; // To control logging.

    private static final String KEY_USER_CONSENTED = "user_consented";

    private static final int ENCRYPT_DEVICE_REQUEST_CODE = 1;
    private static final int WIFI_REQUEST_CODE = 2;
    private static final int PROVISIONING_REQUEST_CODE = 3;

    // Indicates whether user consented by clicking on positive button of consent dialog.
    private boolean mUserConsented = false;

    // Params that will be used after user consent.
    // Extracted from the starting intent.
    private ProvisioningParams mParams;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) ProvisionLogger.logd("DeviceOwnerPreProvisioningActivity ONCREATE");

        if (savedInstanceState != null) {
            mUserConsented = savedInstanceState.getBoolean(KEY_USER_CONSENTED, false);
        }

        // Setup the UI.
        initializeLayoutParams(R.layout.user_consent, R.string.setup_work_device, false);
        configureNavigationButtons(R.string.set_up, View.INVISIBLE, View.VISIBLE);

        // Check whether we have already provisioned this user.
        if (Utils.isCurrentUserOwner()) {
            int provisioned =
                    Global.getInt(getContentResolver(), Global.DEVICE_PROVISIONED, 0 /*defaults*/);
            if (provisioned != 0) {
                showErrorAndClose(R.string.device_owner_error_already_provisioned,
                        "Device already provisioned.");
                return;
            }
        } else {
             int provisioned =
                    Secure.getInt(getContentResolver(), Secure.USER_SETUP_COMPLETE, 0 /*default*/);
            if (provisioned != 0) {
                showErrorAndClose(R.string.device_owner_error_already_provisioned_user,
                        "User already provisioned.");
                return;
            }
        }

        // Parse the incoming intent.
        MessageParser parser = new MessageParser();
        try {
            mParams = parser.parseIntent(getIntent());
        } catch (Utils.IllegalProvisioningArgumentException e) {
            showErrorAndClose(R.string.device_owner_error_general,
                    "Could not read data from intent");
            return;
        }

        // Ask to encrypt the device before proceeding
        if (!(EncryptDeviceActivity.isDeviceEncrypted()
                        || SystemProperties.getBoolean("persist.sys.no_req_encrypt", false)
                        || mParams.skipEncryption)) {
            requestEncryption(parser, mParams);
            finish();
            return;
            // System will reboot. Bootreminder will restart this activity.
        }

        // Have the user pick a wifi network if necessary.
        if (!AddWifiNetworkTask.isConnectedToWifi(this) && TextUtils.isEmpty(mParams.wifiInfo.ssid)
                && !mParams.bluetoothInfo.useProxy) {
            requestWifiPick();
            return;
            // Wait for onActivityResult.
        }

        if (mUserConsented || mParams.startedByNfc || !Utils.isCurrentUserOwner()) {
            startDeviceOwnerProvisioning();
        } else {
            showStartProvisioningButton();
            TextView consentMessageTextView = (TextView) findViewById(R.id.user_consent_message);
            consentMessageTextView.setText(R.string.company_controls_device);
            TextView mdmInfoTextView = (TextView) findViewById(R.id.mdm_info_message);
            mdmInfoTextView.setText(R.string.the_following_is_your_mdm_for_device);
            setMdmInfo();
        }
    }

    private void startDeviceOwnerProvisioning() {
        Intent intent = new Intent(this, DeviceOwnerProvisioningActivity.class);
        intent.putExtra(DeviceOwnerProvisioningService.EXTRA_PROVISIONING_PARAMS, mParams);
        startActivityForResult(intent, PROVISIONING_REQUEST_CODE);
        // Set cross-fade transition animation into the interstitial progress activity.
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showErrorAndClose(int resourceId, String logText) {
                ProvisionLogger.loge(logText);
        new AlertDialog.Builder(this)
                .setTitle(R.string.provisioning_error_title)
                .setMessage(resourceId)
                .setCancelable(false)
                .setPositiveButton(R.string.device_owner_error_ok,
                        new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,int id) {
                                    // Close activity
                                    DeviceOwnerPreProvisioningActivity.this
                                            .setResult(Activity.RESULT_CANCELED);
                                    DeviceOwnerPreProvisioningActivity.this.finish();
                                }
                        })
                .show();
    }

    private void requestEncryption(MessageParser messageParser, ProvisioningParams params) {
        Intent encryptIntent = new Intent(this, EncryptDeviceActivity.class);

        Bundle resumeExtras = new Bundle();
        resumeExtras.putString(EncryptDeviceActivity.EXTRA_RESUME_TARGET,
                EncryptDeviceActivity.TARGET_DEVICE_OWNER);
        messageParser.addProvisioningParamsToBundle(resumeExtras, params);

        encryptIntent.putExtra(EncryptDeviceActivity.EXTRA_RESUME, resumeExtras);

        startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
    }

    private void requestWifiPick() {
        startActivityForResult(AddWifiNetworkTask.getWifiPickIntent(), WIFI_REQUEST_CODE);
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
                finish();
            } else if (resultCode == RESULT_OK) {
                if (DEBUG) ProvisionLogger.logd("Wifi request result is OK");
                if (!AddWifiNetworkTask.isConnectedToWifi(this)) {
                    requestWifiPick();
                }
            }
        } else if (requestCode == PROVISIONING_REQUEST_CODE) {
            setResult(resultCode);
            finish();
        }
    }

    private void setMdmInfo() {
        ImageView imageView = (ImageView) findViewById(R.id.mdm_icon_view);
        TextView deviceManagerName = (TextView) findViewById(R.id.device_manager_name);
        String packageName = mParams.inferDeviceAdminPackageName();
        MdmPackageInfo packageInfo = Utils.getMdmPackageInfo(getPackageManager(), packageName);
        if (packageInfo != null) {
            imageView.setImageDrawable(packageInfo.getPackageIcon());
            deviceManagerName.setText(packageInfo.getAppLabel());
        } else {
            // Should never happen. Package will be on the device by now.
            deviceManagerName.setText(packageName);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_USER_CONSENTED, mUserConsented);
    }

    private void showStartProvisioningButton() {
        mNextButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDialogConsent() {
        mUserConsented = true;
        startDeviceOwnerProvisioning();
    }

    @Override
    public void onDialogCancel() {
        // Do nothing.
    }

    @Override
    public void onNavigateNext() {
        // Notify the user that the admin will have full control over the device,
        // then start provisioning.
        UserConsentDialog.newInstance(UserConsentDialog.DEVICE_OWNER)
                .show(getFragmentManager(), "UserConsentDialogFragment");
    }
}