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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.app.LocalePicker;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.SetDevicePolicyTask;

import java.lang.Runnable;
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

        // Load the ProvisioningParams (from Nfc message in Intent).
        ProvisioningParams params;
        NfcMessageParser parser = new NfcMessageParser();
        Intent bumpData = getIntent();
        try {
            params = parser.parseNfcIntent(bumpData);
        } catch (NfcMessageParser.NfcParseException e) {
            ProvisionLogger.loge("Could not read Nfc data from intent", e);
            error(R.string.device_owner_error_nfc_parse_fail);
            return;
        }

        initializeProvisioningEnvironment(params);

        // TODO: update UI
        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.progress_device_owner, null);
        setContentView(contentView);

        startDeviceOwnerProvisioning(params);
    }

    /**
     * This is the core method of this class. It goes through every provisioning step.
     */
    private void startDeviceOwnerProvisioning(ProvisioningParams params) {
        ProvisionLogger.logd("Starting device owner provisioning");

        // Construct Tasks. Do not start them yet.
        final AddWifiNetworkTask addWifiNetworkTask = new AddWifiNetworkTask(this, params.mWifiSsid,
                params.mWifiHidden, params.mWifiSecurityType, params.mWifiPassword,
                params.mWifiProxyHost, params.mWifiProxyPort, params.mWifiProxyBypassHosts);
        final DownloadPackageTask downloadPackageTask = new DownloadPackageTask(this,
                params.mDownloadLocation, params.mHash);
        final InstallPackageTask installPackageTask = new InstallPackageTask(this,
                params.mMdmPackageName, params.mMdmAdminReceiver);
        final SetDevicePolicyTask setDevicePolicyTask = new SetDevicePolicyTask(this,
                params.mMdmPackageName, params.mMdmAdminReceiver, params.mOwner);

        // Set callbacks.
        addWifiNetworkTask.setCallback(new AddWifiNetworkTask.Callback() {
            @Override
            public void onSuccess() {
                if (downloadPackageTask.downloadLocationWasProvided()) {
                    downloadPackageTask.run();
                } else {
                    // TODO: replace success by starting the next task.
                    onProvisioningSuccess();
                }
            }

            @Override
            public void onError(){
                error(R.string.device_owner_error_wifi);
            }
        });

        downloadPackageTask.setCallback(new DownloadPackageTask.Callback() {
            @Override
            public void onSuccess() {
                String downloadLocation = downloadPackageTask.getDownloadedPackageLocation();
                Runnable cleanupRunnable = downloadPackageTask.getCleanUpDownloadRunnable();
                installPackageTask.run(downloadLocation, cleanupRunnable);
            }

            @Override
            public void onError(int errorCode) {
                switch(errorCode) {
                    case DownloadPackageTask.ERROR_HASH_MISMATCH:
                        error(R.string.device_owner_error_hash_mismatch);
                        break;
                    case DownloadPackageTask.ERROR_DOWNLOAD_FAILED:
                        error(R.string.device_owner_error_download_failed);
                        break;
                    default:
                        error(R.string.device_owner_error_general);
                        break;
                }
            }
        });

        installPackageTask.setCallback(new InstallPackageTask.Callback() {
            @Override
            public void onSuccess() {
                setDevicePolicyTask.run();
            }

            @Override
            public void onError(int errorCode) {
                switch(errorCode) {
                    case InstallPackageTask.ERROR_PACKAGE_INVALID:
                        error(R.string.device_owner_error_package_invalid);
                        break;
                    case InstallPackageTask.ERROR_INSTALLATION_FAILED:
                        error(R.string.device_owner_error_installation_failed);
                        break;
                    default:
                        error(R.string.device_owner_error_general);
                        break;
                }
            }
        });

        setDevicePolicyTask.setCallback(new SetDevicePolicyTask.Callback() {
            public void onSuccess() {
                // Done with provisioning. Success.
                onProvisioningSuccess();
            }
            public void onError(int errorCode) {
                switch(errorCode) {
                    case SetDevicePolicyTask.ERROR_PACKAGE_NOT_INSTALLED:
                        error(R.string.device_owner_error_package_not_installed);
                        break;
                    default:
                        error(R.string.device_owner_error_general);
                        break;
                }
            }
        });


        // Start first task, which starts next task in its callback, etc.
        if (addWifiNetworkTask.wifiCredentialsWereProvided()) {
            addWifiNetworkTask.run();
        } else {
            setDevicePolicyTask.run();
        }
    }

    public void onProvisioningSuccess() {

        // This package is no longer needed by the system, so disable it.
        PackageManager pkgMgr = getPackageManager();
        pkgMgr.setComponentEnabledSetting(
                new ComponentName(getPackageName(), getClass().getName()),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        finish();
    }

    private void initializeProvisioningEnvironment(ProvisioningParams params) {
        setTimeAndTimezone(params.mTimeZone, params.mLocalTime);
        setLocale(params.mLocale);
    }

    private void setTimeAndTimezone(String timeZone, Long localTime) {
        try {
            final AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (timeZone != null) {
                ProvisionLogger.logd("Setting time zone to " + timeZone);
                am.setTimeZone(timeZone);
            }
            if (localTime != null) {
                ProvisionLogger.logd("Setting time to " + localTime);
                am.setTime(localTime);
            }
        } catch (Exception e) {
            ProvisionLogger.loge("Alarm manager failed to set the system time/timezone.");
            // Do not stop provisioning process, but ignore this error.
        }
    }

    private void setLocale(Locale locale) {
        if (locale == null || locale.equals(Locale.getDefault())) {
            return;
        }
        try {
            ProvisionLogger.logd("Setting locale to " + locale);
            LocalePicker.updateLocale(locale);
        } catch (Exception e) {
            ProvisionLogger.loge("Failed to set the system locale.");
            // Do not stop provisioning process, but ignore this error.
        }
    }

    @Override
    public void onBackPressed() {
        // TODO: Handle this graciously by stopping the provisioning flow and cleaning up.
    }

    public void error(int dialogMessage) {
        AlertDialog dlg = new AlertDialog.Builder(this)
            .setTitle(R.string.device_owner_error_title)
            .setMessage(dialogMessage)
            .setCancelable(false)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    // Close activity
                    finish();
                }
            })
            .create();
        dlg.show();
    }
}

