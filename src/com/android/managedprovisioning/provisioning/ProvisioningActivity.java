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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import static com.google.android.setupdesign.util.ThemeHelper.shouldApplyExtendedPartnerConfig;

import static java.util.Objects.requireNonNull;

import android.Manifest.permission;
import android.annotation.IntDef;
import android.annotation.StringRes;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.finalization.PreFinalizationController;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.AnimationComponents;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationCallback;

import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.ContentStyler;
import com.google.android.setupdesign.util.DescriptionStyler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Progress activity shown whilst provisioning is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link ProvisioningManager}. It shows progress updates as provisioning progresses and handles
 * showing of cancel and error dialogs.</p>
 */
public class ProvisioningActivity extends AbstractProvisioningActivity
        implements TransitionAnimationCallback {
    private static final int RESULT_CODE_COMPLETE_DEVICE_FINANCE = 121;
    /*
     * Returned after the work profile has been completed. Note this is before launching the DPC.
     */
    @VisibleForTesting
    static final int RESULT_CODE_WORK_PROFILE_CREATED = 122;
    /*
     * Returned after the device owner has been set. Note this is before launching the DPC.
     */
    @VisibleForTesting
    static final int RESULT_CODE_DEVICE_OWNER_SET = 123;

    static final int PROVISIONING_MODE_WORK_PROFILE = 1;
    static final int PROVISIONING_MODE_FULLY_MANAGED_DEVICE = 2;
    static final int PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE = 3;
    static final int PROVISIONING_MODE_FINANCED_DEVICE = 4;
    static final int PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE = 5;
    private CustomizationParams mCustomizationParams;

    @IntDef(prefix = { "PROVISIONING_MODE_" }, value = {
        PROVISIONING_MODE_WORK_PROFILE,
        PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE,
        PROVISIONING_MODE_FINANCED_DEVICE,
        PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ProvisioningMode {}

    private static final Map<Integer, Integer> PROVISIONING_MODE_TO_PROGRESS_LABEL =
            Collections.unmodifiableMap(new HashMap<Integer, Integer>() {{
                put(PROVISIONING_MODE_WORK_PROFILE,
                        R.string.work_profile_provisioning_progress_label);
                put(PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                        R.string.fully_managed_device_provisioning_progress_label);
                put(PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE,
                        R.string.fully_managed_device_provisioning_progress_label);
                put(PROVISIONING_MODE_FINANCED_DEVICE, R.string.just_a_sec);
                put(PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE,
                        R.string.work_profile_provisioning_progress_label);
            }});

    private TransitionAnimationHelper mTransitionAnimationHelper;
    private UserProvisioningStateHelper mUserProvisioningStateHelper;
    private PolicyComplianceUtils mPolicyComplianceUtils;
    private ProvisioningManager mProvisioningManager;

    public ProvisioningActivity() {
        this(
                /* provisioningManager */ null, // defined in getProvisioningManager()
                new Utils(),
                /* userProvisioningStateHelper */ null, // defined in onCreate()
                new PolicyComplianceUtils(),
                new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    @VisibleForTesting
    public ProvisioningActivity(ProvisioningManager provisioningManager,
            Utils utils,
            UserProvisioningStateHelper userProvisioningStateHelper,
            PolicyComplianceUtils policyComplianceUtils,
            SettingsFacade settingsFacade,
            ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
        mProvisioningManager = provisioningManager;
        mUserProvisioningStateHelper = userProvisioningStateHelper;
        mPolicyComplianceUtils = checkNotNull(policyComplianceUtils);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // assign this Activity as the view store owner to access saved state and receive updates
        getProvisioningManager().setViewModelStoreOwner(this);

        if (mUserProvisioningStateHelper == null) {
            mUserProvisioningStateHelper = new UserProvisioningStateHelper(this);
        }

        if (mState == STATE_PROVISIONING_FINALIZED) {
            updateProvisioningFinalizedScreen();
        }
    }

    @Override
    protected ProvisioningManager getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = ProvisioningManager.getInstance(this);
        }
        return mProvisioningManager;
    }

    @VisibleForTesting
    protected void setProvisioningManager(ProvisioningManager provisioningManager) {
        mProvisioningManager = requireNonNull(provisioningManager);
    }

    @Override
    public void preFinalizationCompleted() {
        if (mState == STATE_PROVISIONING_COMPLETED || mState == STATE_PROVISIONING_FINALIZED) {
            return;
        }

        if (!validatePolicyComplianceExists()) {
            ProvisionLogger.loge("POLICY_COMPLIANCE handler not implemented by the admin app.");
            error(R.string.cant_set_up_device,
                    R.string.contact_your_admin_for_help,
                    /* resetRequired */ mParams.isOrganizationOwnedProvisioning);
            return;
        }

        ProvisionLogger.logi("ProvisioningActivity pre-finalization completed");

        // TODO(183094412): Decouple state from AbstractProvisioningActivity
        mState = STATE_PROVISIONING_COMPLETED;

        if (shouldSkipEducationScreens() || mTransitionAnimationHelper.areAllTransitionsShown()) {
            updateProvisioningFinalizedScreen();
        }
    }

    // Enforces DPCs to implement the POLICY_COMPLIANCE handler for NFC and financed device
    // provisioning, since we no longer set up the DPC on setup wizard's exit procedure.
    // No need to verify it for the other flows, as that was already done earlier.
    // TODO(b/177849035): Remove NFC and financed device-specific logic
    private boolean validatePolicyComplianceExists() {
        if (!mParams.isNfc && !mUtils.isFinancedDeviceAction(mParams.provisioningAction)) {
            return true;
        }
        return mPolicyComplianceUtils.isPolicyComplianceActivityResolvableForUser(
                this, mParams, mUtils, UserHandle.SYSTEM);
    }

    private void updateProvisioningFinalizedScreen() {
        if (!shouldSkipEducationScreens()) {
            final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
            getProvisioningProgressLabelContainer().setVisibility(View.GONE);
            Utils.addNextButton(layout, v -> onNextButtonClicked());
            //TODO(b/181323689): Add tests to ProvisioningActivityTest that the button is not
            // shown for non-DO provisioning flows.
            if (mUtils.isDeviceOwnerAction(mParams.provisioningAction)) {
                Utils.addAbortAndResetButton(layout, v -> onAbortButtonClicked());
            }
        }

        if (shouldSkipEducationScreens()) {
            onNextButtonClicked();
        }

        // TODO(183094412): Decouple state from AbstractProvisioningActivity
        mState = STATE_PROVISIONING_FINALIZED;
    }

    @VisibleForTesting
    protected void onNextButtonClicked() {
        markDeviceManagementEstablishedAndFinish();
    }

    @VisibleForTesting
    protected void onAbortButtonClicked() {
        final Intent intent = new Intent(this, ResetAndReturnDeviceActivity.class);
        WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mParams);
        getTransitionHelper().startActivityWithTransition(this, intent);
    }

    private void finishActivity() {
        if (mParams.provisioningAction.equals(ACTION_PROVISION_FINANCED_DEVICE)) {
            setResult(RESULT_CODE_COMPLETE_DEVICE_FINANCE);
        } else {
            setResult(Activity.RESULT_OK);
        }
        maybeLaunchNfcUserSetupCompleteIntent();
        getTransitionHelper().finishActivity(this);
    }

    private void markDeviceManagementEstablishedAndFinish() {
        new PreFinalizationController(this, mUserProvisioningStateHelper)
                .deviceManagementEstablished(mParams);
        if (mParams.flowType == ProvisioningParams.FLOW_TYPE_ADMIN_INTEGRATED) {
            if (mUtils.isProfileOwnerAction(mParams.provisioningAction)) {
                setResult(RESULT_CODE_WORK_PROFILE_CREATED);
            } else if (mUtils.isDeviceOwnerAction(mParams.provisioningAction)) {
                setResult(RESULT_CODE_DEVICE_OWNER_SET);
            } else if (mUtils.isFinancedDeviceAction(mParams.provisioningAction)) {
                setResult(RESULT_CODE_COMPLETE_DEVICE_FINANCE);
            }
            getTransitionHelper().finishActivity(this);
        } else {
            finishActivity();
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
            getTransitionHelper().startActivityWithTransition(this, intent);
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
        if ((mState == STATE_PROVISIONING_COMPLETED || mState == STATE_PROVISIONING_FINALIZED)
                && !mParams.isOrganizationOwnedProvisioning) {
            return;
        }

        if (getUtils().isDeviceOwnerAction(mParams.provisioningAction)
                || mParams.isOrganizationOwnedProvisioning) {
            showCancelProvisioningDialog(/* resetRequired = */true);
        } else {
            showCancelProvisioningDialog(/* resetRequired = */false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!shouldSkipEducationScreens()) {
            startTransitionAnimation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!shouldSkipEducationScreens()) {
            endTransitionAnimation();
        }
        // remove this Activity as the view store owner to avoid memory leaks
        if (isFinishing()) {
            getProvisioningManager().clearViewModelStoreOwner();
        }
    }

    @Override
    public void onAllTransitionsShown() {
        if (mState == STATE_PROVISIONING_COMPLETED) {
            updateProvisioningFinalizedScreen();
        }
    }

    @Override
    public void onTransitionStart(int screenIndex, AnimatedVectorDrawable currentAnimation) {
        getProvisioningManager().setCurrentTransitionAnimation(screenIndex);
    }

    @Override
    protected void initializeUi(ProvisioningParams params) {
        boolean isPoProvisioning = mUtils.isProfileOwnerAction(params.provisioningAction);
        int titleResId =
            isPoProvisioning ? R.string.setup_profile_progress : R.string.setup_device_progress;
        int layoutResId = shouldSkipEducationScreens()
                ? R.layout.empty_loading_layout
                : R.layout.provisioning_progress;

        mCustomizationParams = CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(layoutResId, /* headerResourceId= */ null,
                mCustomizationParams);
        setTitle(titleResId);

        GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        setupEducationViews(layout);
        if (mUtils.isFinancedDeviceAction(params.provisioningAction)) {
            // make the icon invisible
            layout.findViewById(R.id.sud_layout_icon).setVisibility(View.INVISIBLE);
        }
    }

    private void setupEducationViews(GlifLayout layout) {
        final int progressLabelResId =
                PROVISIONING_MODE_TO_PROGRESS_LABEL.get(getProvisioningMode());
        addProvisioningProgressLabel();
        if (shouldSkipEducationScreens()) {
            final TextView header = layout.findViewById(R.id.suc_layout_title);
            header.setText(progressLabelResId);
            getProvisioningProgressLabelContainer().setVisibility(View.GONE);
        } else {
            setupProgressLabel(progressLabelResId);
        }
    }

    private void addProvisioningProgressLabel() {
        final LinearLayout parent = (LinearLayout) findViewById(R.id.suc_layout_footer).getParent();
        getLayoutInflater().inflate(R.layout.label_provisioning_progress, parent, true);
    }

    private ViewGroup getProvisioningProgressLabelContainer() {
        final LinearLayout parent = (LinearLayout) findViewById(R.id.suc_layout_footer).getParent();
        return parent.findViewById(R.id.provisioning_progress_container);
    }

    /**
     * Returns the relevant progress label and takes care of visibilities to show the correct one.
     */
    private void setupProgressLabel(@StringRes int progressLabelResId) {
        TextView progressLabel = getRelevantProgressLabel();
        DescriptionStyler.applyPartnerCustomizationHeavyStyle(progressLabel);
        progressLabel.setTextColor(
                shouldApplyExtendedPartnerConfig(this)
                        ? mUtils.getTextSecondaryColor(this)
                        : mUtils.getAccentColor(this));
        progressLabel.setText(progressLabelResId);
        int sidePadding = (int) ContentStyler.getPartnerContentMarginStart(this);
        progressLabel.setPadding(sidePadding, /* top= */ 0, sidePadding, /* bottom= */ 0);
        getProvisioningProgressLabelContainer().setVisibility(View.VISIBLE);
    }

    private TextView getRelevantProgressLabel() {
        ViewGroup parent = (ViewGroup) findViewById(R.id.suc_layout_footer).getParent();
        TextView provisioningProgressLabel = parent.findViewById(R.id.provisioning_progress);
        if (provisioningProgressLabel != null) {
            return provisioningProgressLabel;
        }
        TextView leftProgress = parent.findViewById(R.id.provisioning_progress_left);
        TextView rightProgress = parent.findViewById(R.id.provisioning_progress_right);
        if (getResources().getBoolean(R.bool.show_progress_label_on_left_side)) {
            leftProgress.setVisibility(View.VISIBLE);
            rightProgress.setVisibility(View.INVISIBLE);
            return leftProgress;
        }
        leftProgress.setVisibility(View.INVISIBLE);
        rightProgress.setVisibility(View.VISIBLE);
        return rightProgress;
    }

    private void setupTransitionAnimationHelper(GlifLayout layout) {
        final TextView header = layout.findViewById(R.id.suc_layout_title);
        final TextView description = layout.findViewById(R.id.sud_layout_subtitle);
        final ViewGroup item1 = layout.findViewById(R.id.item1);
        final ViewGroup item2 = layout.findViewById(R.id.item2);
        final ImageView drawable = layout.findViewById(R.id.animation);
        final ViewGroup drawableContainer = layout.findViewById(R.id.animation_container);
        final int provisioningMode = getProvisioningMode();
        final AnimationComponents animationComponents =
                new AnimationComponents(
                        header, description, item1, item2, drawable, drawableContainer);
        mTransitionAnimationHelper = new TransitionAnimationHelper(provisioningMode,
                /* adminCanGrantSensorsPermissions= */ !mParams.deviceOwnerPermissionGrantOptOut,
                animationComponents,
                this,
                getProvisioningManager().getCurrentTransitionAnimation());
    }

    private @ProvisioningMode int getProvisioningMode() {
        int provisioningMode = 0;
        final boolean isProfileOwnerAction =
                mUtils.isProfileOwnerAction(mParams.provisioningAction);
        if (isProfileOwnerAction) {
            if (getSystemService(DevicePolicyManager.class).isDeviceManaged()) {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE;
            } else if (mParams.isOrganizationOwnedProvisioning) {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE;
            } else {
                provisioningMode = PROVISIONING_MODE_WORK_PROFILE;
            }
        } else if (mUtils.isDeviceOwnerAction(mParams.provisioningAction)) {
            provisioningMode = PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
        } else if (mUtils.isFinancedDeviceAction(mParams.provisioningAction)) {
            provisioningMode = PROVISIONING_MODE_FINANCED_DEVICE;
        }
        return provisioningMode;
    }

    private void startTransitionAnimation() {
        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        setupTransitionAnimationHelper(layout);
        mTransitionAnimationHelper.start();
    }

    private void endTransitionAnimation() {
        mTransitionAnimationHelper.clean();
        mTransitionAnimationHelper = null;
    }

    private boolean shouldSkipEducationScreens() {
        return mParams.skipEducationScreens
                || getProvisioningMode() == PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE
                || getProvisioningMode() == PROVISIONING_MODE_FINANCED_DEVICE;
    }

    private ProvisioningAnalyticsTracker getProvisioningAnalyticsTracker() {
        return new ProvisioningAnalyticsTracker(
                MetricsWriterFactory.getMetricsWriter(this, new SettingsFacade()),
                new ManagedProvisioningSharedPreferences(this));
    }
}
