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
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.DeleteManagedProfileDialog.DeleteManagedProfileCallback;
import com.android.managedprovisioning.UserConsentDialog.ConsentCallback;
import com.android.managedprovisioning.Utils.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.Utils.MdmPackageInfo;

import static com.android.managedprovisioning.EncryptDeviceActivity.EXTRA_RESUME;
import static com.android.managedprovisioning.EncryptDeviceActivity.EXTRA_RESUME_TARGET;
import static com.android.managedprovisioning.EncryptDeviceActivity.TARGET_PROFILE_OWNER;

/**
 * The activity sets up the environment in which the {@link ProfileOwnerProvisioningActivity} can be run.
 * It makes sure the device is encrypted, the current launcher supports managed profiles, the
 * provisioning intent extras are valid, and that the already present managed profile is removed.
 */
public class ProfileOwnerPreProvisioningActivity extends SetupLayoutActivity
        implements ConsentCallback, DeleteManagedProfileCallback {

    private static final String MANAGE_USERS_PERMISSION = "android.permission.MANAGE_USERS";

    // Note: must match the constant defined in HomeSettings
    private static final String EXTRA_SUPPORT_MANAGED_PROFILES = "support_managed_profiles";

    // Aliases to start profile owner provisioning with and without MANAGE_USERS permission
    protected static final ComponentName ALIAS_CHECK_CALLER =
            new ComponentName("com.android.managedprovisioning",
                    "com.android.managedprovisioning.ProfileOwnerProvisioningActivity");

    protected static final ComponentName ALIAS_NO_CHECK_CALLER =
            new ComponentName("com.android.managedprovisioning",
                    "com.android.managedprovisioning.ProfileOwnerProvisioningActivityNoCallerCheck");

    protected static final int PROVISIONING_REQUEST_CODE = 3;
    protected static final int ENCRYPT_DEVICE_REQUEST_CODE = 2;
    protected static final int CHANGE_LAUNCHER_REQUEST_CODE = 1;

    // If dialog is null, it means it's already been shown and acted on.
    private DeleteManagedProfileDialog mDeleteDialog;

    private ProvisioningParams mParams;
    private final MessageParser mParser = new MessageParser();
    private DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDevicePolicyManager =
                    (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        initializeLayoutParams(R.layout.user_consent, R.string.setup_work_profile, false);
        configureNavigationButtons(R.string.set_up, View.INVISIBLE, View.VISIBLE);

        TextView consentMessageTextView = (TextView) findViewById(R.id.user_consent_message);
        consentMessageTextView.setText(R.string.company_controls_workspace);
        TextView mdmInfoTextView = (TextView) findViewById(R.id.mdm_info_message);
        mdmInfoTextView.setText(R.string.the_following_is_your_mdm);

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        UserInfo uInfo = userManager.getUserInfo(UserHandle.myUserId());

        if (mDevicePolicyManager.isProvisioningAllowed(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            initiateProfileOwnerProvisioning();
        }
        // Try to show an error message explaining why provisioning is not allowed.
        else if (!systemHasManagedProfileFeature()) {
            showErrorAndClose(R.string.managed_provisioning_not_supported,
                    "Exiting managed profile provisioning, "
                    + "managed profiles feature is not available");
        } else if (!uInfo.canHaveProfile()) {
            showErrorAndClose(R.string.user_cannot_have_work_profile,
                    "Exiting managed profile provisioning, calling user cannot have managed"
                    + "profiles.");
        } else if (Utils.hasDeviceOwner(this)) {
            showErrorAndClose(R.string.device_owner_exists,
                    "Exiting managed profile provisioning, a device owner exists");
        } else if (!userManager.canAddMoreManagedProfiles(UserHandle.myUserId(),
                true /* after removing one eventual existing managed profile */)) {
            showErrorAndClose(R.string.maximum_user_limit_reached,
                    "Exiting managed profile provisioning, cannot add more users.");
        } else {
            showErrorAndClose(R.string.managed_provisioning_error_text, "Managed profile"
                    + " provisioning not allowed for an unknown reason.");
        }
    }

    private void initiateProfileOwnerProvisioning() {
        // Initialize member variables from the intent, stop if the intent wasn't valid.
        try {
            initialize(getIntent(), getPackageName().equals(getCallingPackage()));
        } catch (IllegalProvisioningArgumentException e) {
            showErrorAndClose(R.string.managed_provisioning_error_text, e.getMessage());
            return;
        }

        setMdmIcon(mParams.deviceAdminPackageName);

        // If the caller started us via ALIAS_NO_CHECK_CALLER then they must have permission to
        // MANAGE_USERS since it is a restricted intent. Otherwise, check the calling package.
        boolean hasManageUsersPermission = (getComponentName().equals(ALIAS_NO_CHECK_CALLER));
        if (!hasManageUsersPermission) {
            // Calling package has to equal the requested device admin package or has to be system.
            String callingPackage = getCallingPackage();
            if (callingPackage == null) {
                showErrorAndClose(R.string.managed_provisioning_error_text,
                        "Calling package is null. " +
                        "Was startActivityForResult used to start this activity?");
                return;
            }
            if (!callingPackage.equals(mParams.deviceAdminPackageName)
                    && !packageHasManageUsersPermission(callingPackage)) {
                showErrorAndClose(R.string.managed_provisioning_error_text, "Permission denied, "
                        + "calling package tried to set a different package as profile owner. "
                        + "The system MANAGE_USERS permission is required.");
                return;
            }
        }

        // If there is already a managed profile, setup the profile deletion dialog.
        // Otherwise, check whether system has reached maximum user limit.
        int existingManagedProfileUserId = Utils.alreadyHasManagedProfile(this);
        if (existingManagedProfileUserId != -1) {
            createDeleteManagedProfileDialog(mDevicePolicyManager, existingManagedProfileUserId);
        } else {
            showStartProvisioningButton();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        setTitle(R.string.setup_profile_start_setup);
        if (Utils.alreadyHasManagedProfile(this) != -1) {
            maybeShowDeleteManagedProfileDialog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        hideDeleteManagedProfileDialog();
    }

    @Override
    protected void onDestroy() {
        mDeleteDialog = null;
        super.onDestroy();
    }

    private void showStartProvisioningButton() {
        mNextButton.setVisibility(View.VISIBLE);
    }

    private boolean packageHasManageUsersPermission(String pkg) {
        return PackageManager.PERMISSION_GRANTED == getPackageManager()
                .checkPermission(MANAGE_USERS_PERMISSION, pkg);
    }

    private boolean systemHasManagedProfileFeature() {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
    }

    private boolean currentLauncherSupportsManagedProfiles() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        PackageManager pm = getPackageManager();
        ResolveInfo launcherResolveInfo
                = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (launcherResolveInfo == null) {
            return false;
        }
        try {
            // If the user has not chosen a default launcher, then launcherResolveInfo will be
            // referring to the resolver activity. It is fine to create a managed profile in
            // this case since there will always be at least one launcher on the device that
            // supports managed profile feature.
            ApplicationInfo launcherAppInfo = getPackageManager().getApplicationInfo(
                    launcherResolveInfo.activityInfo.packageName, 0 /* default flags */);
            return versionNumberAtLeastL(launcherAppInfo.targetSdkVersion);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean versionNumberAtLeastL(int versionNumber) {
        return versionNumber >= Build.VERSION_CODES.LOLLIPOP;
    }

    private void setMdmIcon(String packageName) {
        MdmPackageInfo packageInfo = Utils.getMdmPackageInfo(getPackageManager(), packageName);
        if (packageInfo != null) {
            String appLabel = packageInfo.getAppLabel();
            ImageView imageView = (ImageView) findViewById(R.id.mdm_icon_view);
            imageView.setImageDrawable(packageInfo.getPackageIcon());
            imageView.setContentDescription(
                    getResources().getString(R.string.mdm_icon_label, appLabel));

            TextView deviceManagerName = (TextView) findViewById(R.id.device_manager_name);
            deviceManagerName.setText(appLabel);
        }
    }

    /**
     * Checks if all required provisioning parameters are provided.
     * Does not check for extras that are optional such as wifi ssid.
     * Also checks whether type of admin extras bundle (if present) is PersistableBundle.
     *
     * @param intent The intent that started provisioning
     * @param trusted Whether the intent is trusted or not.
     */
    private void initialize(Intent intent, boolean trusted)
            throws IllegalProvisioningArgumentException {
        mParams = mParser.parseNonNfcIntent(intent, trusted);

        mParams.deviceAdminComponentName = Utils.findDeviceAdmin(
                mParams.deviceAdminPackageName, mParams.deviceAdminComponentName, this);
        mParams.deviceAdminPackageName = mParams.deviceAdminComponentName.getPackageName();

    }

    /**
     * If the device is encrypted start the service which does the provisioning, otherwise ask for
     * user consent to encrypt the device.
     */
    private void checkEncryptedAndStartProvisioningService() {
        if (EncryptDeviceActivity.isDeviceEncrypted()
                || SystemProperties.getBoolean("persist.sys.no_req_encrypt", false)) {

            // Notify the user once more that the admin will have full control over the profile,
            // then start provisioning.
            UserConsentDialog.newInstance(UserConsentDialog.PROFILE_OWNER)
                    .show(getFragmentManager(), "UserConsentDialogFragment");
        } else {
            Bundle resumeExtras = new Bundle();
            resumeExtras.putString(EXTRA_RESUME_TARGET, TARGET_PROFILE_OWNER);
            mParser.addProvisioningParamsToBundle(resumeExtras, mParams);
            Intent encryptIntent = new Intent(this, EncryptDeviceActivity.class)
                    .putExtra(EXTRA_RESUME, resumeExtras);
            startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
            // Continue in onActivityResult or after reboot.
        }
    }

    @Override
    public void onDialogConsent() {
        // For accessibility purposes: we need to talk back only the title of the
        // next screen after user clicks ok.
        setTitle("");
        setupEnvironmentAndProvision();
    }

    @Override
    public void onDialogCancel() {
        // Do nothing.
    }

    @Override
    public void onRemoveProfileApproval(int existingManagedProfileUserId) {
        mDeleteDialog = null;
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        userManager.removeUser(existingManagedProfileUserId);
        showStartProvisioningButton();
    }

    @Override
    public void onRemoveProfileCancel() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private void setupEnvironmentAndProvision() {
        // Remove any pre-provisioning UI in favour of progress display
        BootReminder.cancelProvisioningReminder(this);

        // Check whether the current launcher supports managed profiles.
        if (!currentLauncherSupportsManagedProfiles()) {
            showCurrentLauncherInvalid();
        } else {
            startProfileOwnerProvisioning();
        }
    }

    private void pickLauncher() {
        Intent changeLauncherIntent = new Intent("android.settings.HOME_SETTINGS");
        changeLauncherIntent.putExtra(EXTRA_SUPPORT_MANAGED_PROFILES, true);
        startActivityForResult(changeLauncherIntent, CHANGE_LAUNCHER_REQUEST_CODE);
        // Continue in onActivityResult.
    }

    private void startProfileOwnerProvisioning() {
        Intent intent = new Intent(this, ProfileOwnerProvisioningActivity.class);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mParams);
        startActivityForResult(intent, PROVISIONING_REQUEST_CODE);
        // Set cross-fade transition animation into the interstitial progress activity.
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENCRYPT_DEVICE_REQUEST_CODE) {
            if (resultCode == RESULT_CANCELED) {
                ProvisionLogger.loge("User canceled device encryption.");
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        } else if (requestCode == CHANGE_LAUNCHER_REQUEST_CODE) {
            if (resultCode == RESULT_CANCELED) {
                showCurrentLauncherInvalid();
            } else if (resultCode == RESULT_OK) {
                startProfileOwnerProvisioning();
            }
        }
        if (requestCode == PROVISIONING_REQUEST_CODE) {
            setResult(resultCode);
            finish();
        }
    }

    private void showCurrentLauncherInvalid() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage(R.string.managed_provisioning_not_supported_by_launcher)
                .setNegativeButton(R.string.cancel_provisioning,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.dismiss();
                                setResult(Activity.RESULT_CANCELED);
                                finish();
                            }
                        })
                .setPositiveButton(R.string.pick_launcher,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,int id) {
                                pickLauncher();
                            }
                        })
                .show();
    }

    public void showErrorAndClose(int resourceId, String logText) {
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
                                ProfileOwnerPreProvisioningActivity.this.setResult(
                                        Activity.RESULT_CANCELED);
                                ProfileOwnerPreProvisioningActivity.this.finish();
                            }
                        })
                .show();
    }

    /**
     * Builds a dialog that allows the user to remove an existing managed profile.
     */
    private void createDeleteManagedProfileDialog(DevicePolicyManager dpm,
            int existingManagedProfileUserId) {
        if (mDeleteDialog != null) {
            return;
        }

        ComponentName mdmPackageName = dpm.getProfileOwnerAsUser(existingManagedProfileUserId);
        String domainName = dpm.getProfileOwnerNameAsUser(existingManagedProfileUserId);

        mDeleteDialog = DeleteManagedProfileDialog.newInstance(existingManagedProfileUserId,
                mdmPackageName, domainName);
    }

    /** Dialog gets shown unless it's already visible or has already been acted on. */
    private void maybeShowDeleteManagedProfileDialog() {
        if (mDeleteDialog != null && !mDeleteDialog.isVisible()) {
            mDeleteDialog.show(getFragmentManager(), "DeleteManagedProfileDialogFragment");
        }
    }

    private void hideDeleteManagedProfileDialog() {
        if (mDeleteDialog != null) {
            mDeleteDialog.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.work_profile_setup_later_title)
                .setMessage(R.string.work_profile_setup_later_message)
                .setCancelable(false)
                .setPositiveButton(R.string.work_profile_setup_stop,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,int id) {
                                ProfileOwnerPreProvisioningActivity.this.setResult(
                                        Activity.RESULT_CANCELED);
                                ProfileOwnerPreProvisioningActivity.this.finish();
                            }
                        })
                .setNegativeButton(R.string.work_profile_setup_continue,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                              // user chose to continue. Do nothing
                            }
                        })
                .show();
    }

    @Override
    public void onNavigateNext() {
        checkEncryptedAndStartProvisioningService();
    }
}
