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
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;

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
    public static final String EXTRA_USER_VISIBLE_ERROR_ID_KEY = "UserVisibleErrorMessage-Id";
    public static final String ACTION_PROGRESS_UPDATE =
            "com.android.managedprovisioning.progress_update";
    public static final String EXTRA_PROGRESS_MESSAGE_ID_KEY = "ProgressMessageId";
    public static final String ACTION_REQUEST_ENCRYPTION =
            "com.android.managedprovisioning.request_encryption";

    private AtomicBoolean mProvisioningInFlight = new AtomicBoolean(false);
    private int mLastProgressMessage;
    private int mStartIdProvisioning;

    // Provisioning tasks.
    private AddWifiNetworkTask mAddWifiNetworkTask;
    private DownloadPackageTask mDownloadPackageTask;
    private InstallPackageTask mInstallPackageTask;
    private SetDevicePolicyTask mSetDevicePolicyTask;
    private DeleteNonRequiredAppsTask mDeleteNonRequiredAppsTask;

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (mProvisioningInFlight.getAndSet(true)) {
            ProvisionLogger.logd("Provisioning already in flight. Ignoring intent.");
            sendProgressUpdateToActivity();
            stopSelf(startId);
        } else {
            progressUpdate(R.string.progress_data_process);

            mStartIdProvisioning = startId;

            // Load the ProvisioningParams (from message in Intent).
            MessageParser parser = new MessageParser();
            final ProvisioningParams params;
            try {
                params = parser.parseIntent(intent);
            } catch (MessageParser.ParseException e) {
                ProvisionLogger.loge("Could not read data from intent", e);
                error(e.getErrorMessageId());
                return START_NOT_STICKY;
            }

            // Ask to encrypt the device before proceeding
            if (!EncryptDeviceActivity.isDeviceEncrypted()) {
                requestEncryption(parser.getCachedProvisioningProperties());
                stopSelf(startId);
                return START_NOT_STICKY;
            }

            // Do the work on a separate thread.
            new Thread(new Runnable() {
                    @Override
                    public void run() {
                        initializeProvisioningEnvironment(params);
                        startDeviceOwnerProvisioning(params);
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
        mAddWifiNetworkTask = new AddWifiNetworkTask(this, params.mWifiSsid,
                params.mWifiHidden, params.mWifiSecurityType, params.mWifiPassword,
                params.mWifiProxyHost, params.mWifiProxyPort, params.mWifiProxyBypassHosts,
                new AddWifiNetworkTask.Callback() {
                        @Override
                        public void onSuccess() {
                            if (mDownloadPackageTask.downloadLocationWasProvided()) {
                                progressUpdate(R.string.progress_download);
                                mDownloadPackageTask.run();
                            } else {
                                progressUpdate(R.string.progress_set_owner);
                                mSetDevicePolicyTask.run();
                            }
                        }

                        @Override
                        public void onError(){
                            error(R.string.device_owner_error_wifi);
                        }
                });

        mDownloadPackageTask = new DownloadPackageTask(this,
                params.mDownloadLocation, params.mHash,
                new DownloadPackageTask.Callback() {
                        @Override
                        public void onSuccess() {
                            String downloadLocation =
                                    mDownloadPackageTask.getDownloadedPackageLocation();
                            Runnable cleanupRunnable =
                                    mDownloadPackageTask.getCleanUpDownloadRunnable();
                            progressUpdate(R.string.progress_install);
                            mInstallPackageTask.run(downloadLocation, cleanupRunnable);
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

        mInstallPackageTask = new InstallPackageTask(this,
                params.mDeviceAdminPackageName, params.mAdminReceiver,
                new InstallPackageTask.Callback() {
                    @Override
                    public void onSuccess() {
                        progressUpdate(R.string.progress_set_owner);
                        mSetDevicePolicyTask.run();
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

        mSetDevicePolicyTask = new SetDevicePolicyTask(this,
                params.mDeviceAdminPackageName, params.mAdminReceiver, params.mOwner,
                new SetDevicePolicyTask.Callback() {
                    @Override
                    public void onSuccess() {
                        mDeleteNonRequiredAppsTask.run();
                    }

                    @Override
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

        mDeleteNonRequiredAppsTask =  new DeleteNonRequiredAppsTask(
                this, params.mDeviceAdminPackageName, 0 /* primary user's UserId */,
                R.array.required_apps_managed_device, R.array.vendor_required_apps_managed_device,
                new DeleteNonRequiredAppsTask.Callback() {
                    public void onSuccess() {
                        // Done with provisioning. Success.
                        onProvisioningSuccess(new ComponentName(params.mDeviceAdminPackageName,
                                        params.mAdminReceiver));
                    }

                    @Override
                    public void onError() {
                        error(R.string.device_owner_error_general);
                    };
                });

        // Start first task, which starts next task in its callback, etc.
        if (mAddWifiNetworkTask.wifiCredentialsWereProvided()) {
            progressUpdate(R.string.progress_connect_to_wifi);
            mAddWifiNetworkTask.run();
        } else {
            progressUpdate(R.string.progress_set_owner);
            mSetDevicePolicyTask.run();
        }
    }

    private void error(int dialogMessage) {

        ProvisionLogger.logd("Reporting Error: " + getResources().getString(dialogMessage));

        Intent intent = new Intent(ACTION_PROVISIONING_ERROR);
        intent.setClass(this, DeviceOwnerProvisioningActivity.ServiceMessageReceiver.class);
        intent.putExtra(EXTRA_USER_VISIBLE_ERROR_ID_KEY, dialogMessage);
        sendBroadcast(intent);

        stopSelf(mStartIdProvisioning);
    }

    private void progressUpdate(int progressMessage) {
        ProvisionLogger.logd("Reporting progress update: "
                + getResources().getString(progressMessage));
        mLastProgressMessage = progressMessage;
        sendProgressUpdateToActivity();
    }

    private void sendProgressUpdateToActivity() {
        Intent intent = new Intent(ACTION_PROGRESS_UPDATE);
        intent.putExtra(EXTRA_PROGRESS_MESSAGE_ID_KEY, mLastProgressMessage);
        intent.setClass(this, DeviceOwnerProvisioningActivity.ServiceMessageReceiver.class);
        sendBroadcast(intent);
    }

    private void requestEncryption(String propertiesForResume) {
        Bundle resumeExtras = new Bundle();
        resumeExtras.putString(EncryptDeviceActivity.EXTRA_RESUME_TARGET,
                EncryptDeviceActivity.TARGET_DEVICE_OWNER);
        resumeExtras.putString(MessageParser.EXTRA_PROVISIONING_PROPERTIES,
                propertiesForResume);
        Intent intent = new Intent(ACTION_REQUEST_ENCRYPTION);
        intent.putExtra(EncryptDeviceActivity.EXTRA_RESUME, resumeExtras);
        sendBroadcast(intent);
    }

    private void onProvisioningSuccess(ComponentName deviceAdminComponent) {
        Intent successIntent = new Intent(ACTION_PROVISIONING_SUCCESS);
        successIntent.setClass(this, DeviceOwnerProvisioningActivity.ServiceMessageReceiver.class);
        sendBroadcast(successIntent);

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
    public void onDestroy () {
        if (mAddWifiNetworkTask != null) {
            mAddWifiNetworkTask.unRegister();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

