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

import android.app.AlarmManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.text.TextUtils;

import com.android.internal.app.LocalePicker;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.SetDevicePolicyTask;

import java.lang.Runnable;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This service does the work for the DeviceOwnerProvisioningActivity.
 * Feedback is sent back to the activity via intents.
 *
 * <p>
 * If the corresponding activity is killed and restarted, the service is
 * called twice. The service will not start the provisioning flow a second time, but instead
 * send a status update to the activity.
 * </p>
 */
public class DeviceOwnerProvisioningService extends Service {
    /**
     * Intent action to activate the CDMA phone connection by OTASP.
     * This is not necessary for a GSM phone connection, which is activated automatically.
     * String must agree with the constants in com.android.phone.InCallScreenShowActivation.
     */
    private static final String ACTION_PERFORM_CDMA_PROVISIONING =
            "com.android.phone.PERFORM_CDMA_PROVISIONING";

    // Intent actions for communication with DeviceOwnerProvisioningService.
    public static final String ACTION_PROVISIONING_SUCCESS =
            "com.android.managedprovisioning.provisioning_success";
    public static final String ACTION_PROVISIONING_ERROR =
            "com.android.managedprovisioning.error";
    public static final String EXTRA_PROVISIONING_ERROR_ID_KEY = "ErrorMessageId";
    public static final String ACTION_PROGRESS_UPDATE =
            "com.android.managedprovisioning.progress_update";
    public static final String EXTRA_PROGRESS_MESSAGE_ID_KEY = "ProgressMessageId";

    private AtomicBoolean mProvisioningInFlight = new AtomicBoolean(false);
    private int mLastProgressMessage;
    private int mStartIdProvisioning;

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (mProvisioningInFlight.getAndSet(true)) {
            ProvisionLogger.logd("Provisioning already in flight. Ignoring intent.");
            sendProgressUpdateToActivity();
            stopSelf(startId);
        } else {
            mStartIdProvisioning = startId;

            // Do the work on a separate thread.
            new Thread(new Runnable() {
                    public void run() {
                        // Load the ProvisioningParams (from Nfc message in Intent).
                        progressUpdate(R.string.progress_processing_nfc);
                        ProvisioningParams params;
                        NfcMessageParser parser = new NfcMessageParser();
                        try {
                            params = parser.parseNfcIntent(intent);
                            initializeProvisioningEnvironment(params);
                            startDeviceOwnerProvisioning(params);
                        } catch (NfcMessageParser.ParseException e) {
                            ProvisionLogger.loge("Could not read Nfc data from intent", e);
                            error(e.getErrorMessageId());
                            return;
                        }
                    }
                }).start();
        }
        return START_NOT_STICKY;
    }

    /**
     * This is the core method of this class. It goes through every provisioning step.
     */
    private void startDeviceOwnerProvisioning(final ProvisioningParams params) {
        ProvisionLogger.logd("Starting device owner provisioning");

        // Construct Tasks. Do not start them yet.
        final AddWifiNetworkTask addWifiNetworkTask = new AddWifiNetworkTask(this, params.mWifiSsid,
                params.mWifiHidden, params.mWifiSecurityType, params.mWifiPassword,
                params.mWifiProxyHost, params.mWifiProxyPort, params.mWifiProxyBypassHosts);
        final DownloadPackageTask downloadPackageTask = new DownloadPackageTask(this,
                params.mDownloadLocation, params.mHash);
        final InstallPackageTask installPackageTask = new InstallPackageTask(this,
                params.mDeviceAdminPackageName, params.mAdminReceiver);
        final SetDevicePolicyTask setDevicePolicyTask = new SetDevicePolicyTask(this,
                params.mDeviceAdminPackageName, params.mAdminReceiver, params.mOwner);
        final DeleteNonRequiredAppsTask deleteNonRequiredAppsTask =  new DeleteNonRequiredAppsTask(
                this, params.mDeviceAdminPackageName, 0 /* primary user's UserId */, null,
                R.array.required_apps_managed_device,
                R.array.vendor_required_apps_managed_device);

        // Set callbacks.
        addWifiNetworkTask.setCallback(new AddWifiNetworkTask.Callback() {
            @Override
            public void onSuccess() {
                if (downloadPackageTask.downloadLocationWasProvided()) {
                    progressUpdate(R.string.progress_download);
                    downloadPackageTask.run();
                } else {
                    progressUpdate(R.string.progress_set_owner);
                    setDevicePolicyTask.run();
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
                progressUpdate(R.string.progress_install);
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
                progressUpdate(R.string.progress_set_owner);
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
                deleteNonRequiredAppsTask.run();
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

        deleteNonRequiredAppsTask.setCallback(new DeleteNonRequiredAppsTask
                .Callback() {
                    public void onSuccess() {
                        // Done with provisioning. Success.
                        onProvisioningSuccess(new ComponentName(params.mDeviceAdminPackageName,
                                        params.mAdminReceiver));
                    }

                    public void onError() {
                        error(R.string.device_owner_error_general);
                    };
                }
        );


        // Start first task, which starts next task in its callback, etc.
        if (addWifiNetworkTask.wifiCredentialsWereProvided()) {
            progressUpdate(R.string.progress_connect_to_wifi);
            addWifiNetworkTask.run();
        } else {
            progressUpdate(R.string.progress_set_owner);
            setDevicePolicyTask.run();
        }
    }

    private void error(int dialogMessage) {
        ProvisionLogger.logd("Reporting Error with code " + dialogMessage);
        Intent intent = new Intent(ACTION_PROVISIONING_ERROR);
        intent.putExtra(EXTRA_PROVISIONING_ERROR_ID_KEY, dialogMessage);
        sendBroadcast(intent);
        stopSelf(mStartIdProvisioning);
    }

    private void progressUpdate(int progressMessage) {
        ProvisionLogger.logd("Reporting progress update with code " + progressMessage);
        mLastProgressMessage = progressMessage;
        sendProgressUpdateToActivity();
    }

    private void sendProgressUpdateToActivity() {
        Intent intent = new Intent(ACTION_PROGRESS_UPDATE);
        intent.putExtra(EXTRA_PROGRESS_MESSAGE_ID_KEY, mLastProgressMessage);
        sendBroadcast(intent);
    }

    private void onProvisioningSuccess(ComponentName deviceAdminComponent) {
        sendBroadcast(new Intent(ACTION_PROVISIONING_SUCCESS));

        // Skip the setup wizard.
        Global.putInt(getContentResolver(), Global.DEVICE_PROVISIONED, 1);
        Secure.putInt(getContentResolver(), Secure.USER_SETUP_COMPLETE, 1);

        Intent completeIntent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        completeIntent.setComponent(deviceAdminComponent);
        completeIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES |
            Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(completeIntent);

        stopSelf(mStartIdProvisioning);
    }

    private void initializeProvisioningEnvironment(ProvisioningParams params) {
        setTimeAndTimezone(params.mTimeZone, params.mLocalTime);
        setLocale(params.mLocale);

        // Start CDMA activation to enable phone calls.
        final Intent intent = new Intent(ACTION_PERFORM_CDMA_PROVISIONING);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ProvisionLogger.logv("Starting cdma activation activity");
        startActivity(intent); // Activity will be a Nop if not a CDMA device.
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
            // If locale is different from current locale this results in a configuration change,
            // which will trigger the restarting of the activity.
            LocalePicker.updateLocale(locale);
        } catch (Exception e) {
            ProvisionLogger.loge("Failed to set the system locale.");
            // Do not stop provisioning process, but ignore this error.
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

