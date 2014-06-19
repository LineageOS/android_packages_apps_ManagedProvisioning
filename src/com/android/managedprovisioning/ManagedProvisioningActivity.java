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

import static android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TOKEN;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

import com.android.managedprovisioning.UserConsentSaver;

import java.util.List;

/**
 * Managed provisioning sets up a separate profile on a device whose primary user is already set up.
 * The typical example is setting up a corporate profile that is controlled by their employer on a
 * users personal device to keep personal and work data separate.
 *
 * The activity handles the input validation and UI for managed profile provisioning.
 * and starts the {@link ManagedProvisioningService}, which runs through the setup steps in an
 * async task.
 */
// TODO: Proper error handling to report back to the user and potentially the mdm.
public class ManagedProvisioningActivity extends Activity {

    // TODO remove these when the new constant values are in use in all relevant places.
    protected static final String EXTRA_LEGACY_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME =
            "deviceAdminPackageName";
    protected static final String EXTRA_LEGACY_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME =
            "defaultManagedProfileName";

    protected static final int ENCRYPT_DEVICE_REQUEST_CODE = 2;

    private String mMdmPackageName;
    private int mToken;
    private BroadcastReceiver mServiceMessageReceiver;

    private View mMainTextView;
    private View mProgressView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisionLogger.logd("Managed provisioning activity ONCREATE");

        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_PROFILES)) {
            showErrorAndClose(R.string.managed_provisioning_not_supported,
                    "Exiting managed provisioning, managed profiles feature is not available");
            return;
        }

        // Setup broadcast receiver for feedback from service.
        mServiceMessageReceiver = new ServiceMessageReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ManagedProvisioningService.ACTION_PROVISIONING_SUCCESS);
        filter.addAction(ManagedProvisioningService.ACTION_PROVISIONING_ERROR);
        registerReceiver(mServiceMessageReceiver, filter);

        // Initialize member variables from the intent, stop if the intent wasn't valid.
        try {
            initialize(getIntent());
        } catch (ManagedProvisioningFailedException e) {
            showErrorAndClose(R.string.managed_provisioning_error_text, e.getMessage());
            return;
        }

        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.user_consent, null);
        mMainTextView = contentView.findViewById(R.id.main_text_container);
        mProgressView = contentView.findViewById(R.id.progress_container);
        setContentView(contentView);
        setMdmIcon(mMdmPackageName, contentView);

        // Don't continue if a managed profile already exists
        if (alreadyHasManagedProfile()) {
            showErrorAndClose(R.string.managed_profile_already_present,
                    "The device already has a managed profile, nothing to do.");
        } else {

            // If we previously received an intent confirming user consent, skip the user consent.
            // Otherwise wait for the user to consent.
            if (UserConsentSaver.hasUserConsented(this, mMdmPackageName, mToken)) {
                checkEncryptedAndStartProvisioningService();
            } else {
                Button positiveButton = (Button) contentView.findViewById(R.id.positive_button);
                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkEncryptedAndStartProvisioningService();
                    }
                });
            }
        }
    }

    class ServiceMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ManagedProvisioningService.ACTION_PROVISIONING_SUCCESS)) {
                ProvisionLogger.logd("Successfully provisioned");
                finish();
                return;
            } else if (action.equals(ManagedProvisioningService.ACTION_PROVISIONING_ERROR)) {
                String errorLogMessage = intent.getStringExtra(
                        ManagedProvisioningService.EXTRA_LOG_MESSAGE_KEY);
                ProvisionLogger.logd("Error reported: " + errorLogMessage);
                showErrorAndClose(R.string.managed_provisioning_error_text, errorLogMessage);
                return;
            }
        }
    }

    private void setMdmIcon(String packageName, View contentView) {
        if (packageName != null) {
            PackageManager pm = getPackageManager();
            try {
                ApplicationInfo ai = pm.getApplicationInfo(packageName, /* default flags */ 0);
                if (ai != null) {
                    Drawable packageIcon = pm.getApplicationIcon(packageName);
                    ImageView imageView = (ImageView) contentView.findViewById(R.id.mdm_icon_view);
                    imageView.setImageDrawable(packageIcon);

                    String appLabel = pm.getApplicationLabel(ai).toString();
                    TextView deviceManagerName = (TextView) contentView
                            .findViewById(R.id.device_manager_name);
                    deviceManagerName.setText(appLabel);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Package does not exist, ignore. Should never happen.
                ProvisionLogger.loge("Package does not exist. Should never happen.");
            }
        }
    }

    /**
     * Checks if all required provisioning parameters are provided.
     * Does not check for extras that are optional such as the email address.
     *
     * @param intent The intent that started provisioning
     */
    private void initialize(Intent intent) throws ManagedProvisioningFailedException {

        // Validate package name and check if the package is installed
        mMdmPackageName = getMdmPackageName(intent);
        if (TextUtils.isEmpty(mMdmPackageName)) {
            throw new ManagedProvisioningFailedException("Missing intent extra: "
                    + EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        } else {
            try {
                this.getPackageManager().getPackageInfo(mMdmPackageName, 0);
            } catch (NameNotFoundException e) {
                throw new ManagedProvisioningFailedException("Mdm "+ mMdmPackageName
                        + " is not installed. " + e);
            }
        }

        // Validate the provided device admin component.
        if (intent.getParcelableExtra(EXTRA_DEVICE_ADMIN) == null) {
            throw new ManagedProvisioningFailedException("Missing intent extra: "
                    + EXTRA_DEVICE_ADMIN);
        }

        // Validate the default profile name.
        if (TextUtils.isEmpty(getDefaultManagedProfileName(intent))) {
            throw new ManagedProvisioningFailedException("Missing intent extra: "
                    + EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        }

        // The token will be empty if the user has not previously consented.
        mToken = intent.getIntExtra(EXTRA_PROVISIONING_TOKEN, UserConsentSaver.NO_TOKEN_RECEIVED);
    }

    private String getMdmPackageName(Intent intent) {
        String name = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        if (TextUtils.isEmpty(name)) {
            name = intent.getStringExtra(EXTRA_LEGACY_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        }
        return name;
    }

    private String getDefaultManagedProfileName(Intent intent) {
        String name = intent.getStringExtra(EXTRA_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        if (TextUtils.isEmpty(name)) {
            name = intent.getStringExtra(EXTRA_LEGACY_PROVISIONING_DEFAULT_MANAGED_PROFILE_NAME);
        }
        return name;
    }

    @Override
    public void onBackPressed() {
        // TODO: Handle this graciously by stopping the provisioning flow and cleaning up.
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mServiceMessageReceiver);
        super.onDestroy();
    }

    /**
     * If the device is encrypted start the service which does the provisioning, otherwise ask for
     * user consent to encrypt the device.
     */
    private void checkEncryptedAndStartProvisioningService() {
        if (EncryptDeviceActivity.isDeviceEncrypted()) {
            mProgressView.setVisibility(View.VISIBLE);
            mMainTextView.setVisibility(View.GONE);

            Intent intent = new Intent(this, ManagedProvisioningService.class);
            intent.putExtras(getIntent());
            startService(intent);
        } else {
            Bundle resumeExtras = getIntent().getExtras();
            resumeExtras.putString(EncryptDeviceActivity.EXTRA_RESUME_TARGET,
                    EncryptDeviceActivity.TARGET_PROFILE_OWNER);
            Intent encryptIntent = new Intent(this, EncryptDeviceActivity.class)
                    .putExtra(EncryptDeviceActivity.EXTRA_RESUME, resumeExtras);
            startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
            // Continue in onActivityResult or after reboot.
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENCRYPT_DEVICE_REQUEST_CODE) {
            if (resultCode == RESULT_CANCELED) {
                // Move back to user consent.
                if (UserConsentSaver.hasUserConsented(this, mMdmPackageName, mToken)) {
                    checkEncryptedAndStartProvisioningService();
                }
            }
        }
    }

    public void showErrorAndClose(int resourceId, String logText) {
        ProvisionLogger.loge(logText);
        new ManagedProvisioningErrorDialog(getString(resourceId))
              .show(getFragmentManager(), "ErrorDialogFragment");
    }

    boolean alreadyHasManagedProfile() {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        List<UserInfo> profiles = userManager.getProfiles(getUserId());
        for (UserInfo userInfo : profiles) {
            if (userInfo.isManagedProfile()) return true;
        }
        return false;
    }

    /**
     * Exception thrown when the managed provisioning has failed completely.
     *
     * We're using a custom exception to avoid catching subsequent exceptions that might be
     * significant.
     */
    private class ManagedProvisioningFailedException extends Exception {
      public ManagedProvisioningFailedException(String message) {
          super(message);
      }
    }
}

