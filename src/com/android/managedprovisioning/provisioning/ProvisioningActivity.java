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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;

import android.Manifest.permission;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.UserHandle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.transition.TransitionActivity;
import com.android.setupwizardlib.GlifLayout;
import java.util.List;

/**
 * Progress activity shown whilst provisioning is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link ProvisioningManager}. It shows progress updates as provisioning progresses and handles
 * showing of cancel and error dialogs.</p>
 */
public class ProvisioningActivity extends AbstractProvisioningActivity {

    private static final int POLICY_COMPLIANCE_REQUEST_CODE = 1;
    private static final int TRANSITION_ACTIVITY_REQUEST_CODE = 2;
    private static final int RESULT_CODE_ADD_PERSONAL_ACCOUNT = 120;

    public ProvisioningActivity() {
        this(null, new Utils());
    }

    @VisibleForTesting
    public ProvisioningActivity(ProvisioningManager provisioningManager, Utils utils) {
        super(utils);
        mProvisioningManager = provisioningManager;
    }

    @Override
    protected ProvisioningManagerInterface getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = ProvisioningManager.getInstance(this);
        }
        return mProvisioningManager;
    }

    @Override
    public void preFinalizationCompleted() {
        if (mState == STATE_PROVISIONING_FINALIZED) {
            return;
        }

        ProvisionLogger.logi("ProvisioningActivity pre-finalization completed");

        // TODO: call this for the new flow after new NFC flow has been added
        // maybeLaunchNfcUserSetupCompleteIntent();

        if (mUtils.isAdminIntegratedFlow(mParams)) {
            showPolicyComplianceScreen();
        } else {
            finishProvisioning();
        }
        mState = STATE_PROVISIONING_FINALIZED;
    }

    private void finishProvisioning() {
        setResult(Activity.RESULT_OK);
        maybeLaunchNfcUserSetupCompleteIntent();
        finish();
    }

    private void showPolicyComplianceScreen() {
        final String adminPackage = mParams.inferDeviceAdminPackageName();
        UserHandle userHandle;
        if (mParams.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
          userHandle = mUtils.getManagedProfile(getApplicationContext());
        } else {
          userHandle = UserHandle.of(UserHandle.myUserId());
        }

        final Intent policyComplianceIntent =
            new Intent(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE);
        policyComplianceIntent.setPackage(adminPackage);
        startActivityForResultAsUser(
            policyComplianceIntent, POLICY_COMPLIANCE_REQUEST_CODE, userHandle);
    }

    boolean shouldShowTransitionScreen() {
        return mParams.isOrganizationOwnedProvisioning
                && mParams.provisioningMode == ProvisioningParams.PROVISIONING_MODE_MANAGED_PROFILE
                && mUtils.isConnectedToNetwork(getApplicationContext());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case POLICY_COMPLIANCE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    if (shouldShowTransitionScreen()) {
                        Intent intent = new Intent(this, TransitionActivity.class);
                        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mParams);
                        startActivityForResult(intent, TRANSITION_ACTIVITY_REQUEST_CODE);
                    } else {
                        setResult(Activity.RESULT_OK);
                        finish();
                    }
                } else {
                    error(/* titleId */ R.string.cant_set_up_device,
                            /* messageId */ R.string.cant_set_up_device,
                            /* resetRequired = */ true);
                }
                break;
            case TRANSITION_ACTIVITY_REQUEST_CODE:
                setResult(RESULT_CODE_ADD_PERSONAL_ACCOUNT);
                finish();
                break;
        }
    }

    private void maybeLaunchNfcUserSetupCompleteIntent() {
        if (mParams != null && mParams.isNfc) {
            // Start SetupWizard to complete the intent.
            final Intent intent = new Intent(DevicePolicyManager.ACTION_STATE_USER_SETUP_COMPLETE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final PackageManager pm = getPackageManager();
            List<ResolveInfo> ris = pm.queryIntentActivities(intent, 0);

            // Look for the first legitimate component protected by the permission
            ComponentName targetComponent = null;
            for (ResolveInfo ri : ris) {
                if (ri.activityInfo == null) {
                    continue;
                }
                if (!permission.BIND_DEVICE_ADMIN.equals(ri.activityInfo.permission)) {
                    ProvisionLogger.loge("Component " + ri.activityInfo.getComponentName()
                            + " is not protected by " + permission.BIND_DEVICE_ADMIN);
                } else if (pm.checkPermission(permission.DISPATCH_PROVISIONING_MESSAGE,
                        ri.activityInfo.packageName) != PackageManager.PERMISSION_GRANTED) {
                    ProvisionLogger.loge("Package " + ri.activityInfo.packageName
                            + " does not have " + permission.DISPATCH_PROVISIONING_MESSAGE);
                } else {
                    targetComponent = ri.activityInfo.getComponentName();
                    break;
                }
            }

            if (targetComponent == null) {
                ProvisionLogger.logw("No activity accepts intent ACTION_STATE_USER_SETUP_COMPLETE");
                return;
            }

            intent.setComponent(targetComponent);
            startActivity(intent);
            ProvisionLogger.logi("Launched ACTION_STATE_USER_SETUP_COMPLETE with component "
                    + targetComponent);
        }
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
    }

    @Override
    protected void decideCancelProvisioningDialog() {
        if (getUtils().isDeviceOwnerAction(mParams.provisioningAction)
                || mParams.isOrganizationOwnedProvisioning) {
            showCancelProvisioningDialog(/* resetRequired = */true);
        } else {
            showCancelProvisioningDialog(/* resetRequired = */false);
        }
    }

    @Override
    protected void initializeUi(ProvisioningParams params) {
        final boolean isDoProvisioning = getUtils().isDeviceOwnerAction(params.provisioningAction);
        final int headerResId = isDoProvisioning ? R.string.setup_work_device
                : R.string.setting_up_workspace;
        final int titleResId = isDoProvisioning ? R.string.setup_device_progress
                : R.string.setup_profile_progress;

        CustomizationParams customizationParams =
                CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(R.layout.progress, headerResId, customizationParams.mainColor,
                customizationParams.statusBarColor);
        setTitle(titleResId);
        GlifLayout layout = findViewById(R.id.setup_wizard_layout);

        TextView textView = layout.findViewById(R.id.description);
        ImageView imageView = layout.findViewById(R.id.animation);
        if (isDoProvisioning) {
            textView.setText(R.string.device_owner_description);
            imageView.setImageResource(R.drawable.enterprise_do_animation);
        } else {
            textView.setText(R.string.work_profile_description);
            imageView.setImageResource(R.drawable.enterprise_wp_animation);
        }
        mAnimatedVectorDrawable = (AnimatedVectorDrawable) imageView.getDrawable();
    }
}