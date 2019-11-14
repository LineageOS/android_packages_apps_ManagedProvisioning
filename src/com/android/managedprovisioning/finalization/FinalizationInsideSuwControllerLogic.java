/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static com.android.managedprovisioning.finalization.FinalizationController.PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED;
import static com.android.managedprovisioning.finalization.FinalizationController.PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED;
import static com.android.managedprovisioning.finalization.FinalizationController.ProvisioningFinalizedResult;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * This controller is used to finalize managed provisioning before the end of Setup Wizard.
 */
public class FinalizationInsideSuwControllerLogic implements FinalizationControllerLogic {
    @VisibleForTesting
    static final String EXTRA_DPC_INTENT_CATEGORY = "android.intent.extra.dpc_intent_category";

    private final Activity mActivity;
    private final Utils mUtils;
    private final PolicyComplianceUtils mPolicyComplianceUtils;

    public FinalizationInsideSuwControllerLogic(Activity activity) {
        this(activity, new Utils(), new PolicyComplianceUtils());
    }

    public FinalizationInsideSuwControllerLogic(Activity activity, Utils utils,
            PolicyComplianceUtils policyComplianceUtils) {
        mActivity = activity;
        mUtils = utils;
        mPolicyComplianceUtils = policyComplianceUtils;
    }

    @Override
    public boolean isReadyForFinalization(ProvisioningParams params) {
        // In this release, we only allow provisioning during SUW under certain conditions.  If
        // those conditions aren't met, skip finalization now, so that we do it after SUW instead.

        if (params.isOrganizationOwnedProvisioning) {
            if (!mUtils.isAdminIntegratedFlow(params)) {
                ProvisionLogger.logw("Skipping finalization during SUW for organization "
                        + "owned provisioning, which is not admin integrated flow");
                return false;
            }
        } else {
            if (!mPolicyComplianceUtils.isPolicyComplianceActivityResolvable(
                    mActivity, params, getDpcIntentCategory(), mUtils)) {
                ProvisionLogger.logw("Skipping finalization during SUW for non-organization "
                        + "owned provisioning, because DPC doesn't implement intent handler");
                return false;
            }
        }

        return true;
    }

    @Override
    public @ProvisioningFinalizedResult int notifyDpcManagedProfile(
            ProvisioningParams params, int requestCode) {
        return startPolicyComplianceActivityForResultIfResolved(params, getDpcIntentCategory(),
                requestCode);
    }

    @Override
    public @ProvisioningFinalizedResult int notifyDpcManagedDeviceOrUser(
            ProvisioningParams params, int requestCode) {
        return startPolicyComplianceActivityForResultIfResolved(params, getDpcIntentCategory(),
                requestCode);
    }

    @Override
    public boolean shouldFinalizePrimaryProfile(ProvisioningParams params) {
        return true;
    }

    private String getDpcIntentCategory() {
        final Intent activityIntent = mActivity.getIntent();
        final Bundle extras = activityIntent != null ? activityIntent.getExtras() : null;
        return extras != null && extras.containsKey(EXTRA_DPC_INTENT_CATEGORY)
                ? extras.getString(EXTRA_DPC_INTENT_CATEGORY)
                : null;
    }

    private @ProvisioningFinalizedResult int startPolicyComplianceActivityForResultIfResolved(
            ProvisioningParams params, String dpcIntentCategory, int requestCode) {
        boolean isActivityStarted =
                mPolicyComplianceUtils.startPolicyComplianceActivityForResultIfResolved(
                        mActivity, params, dpcIntentCategory, requestCode, mUtils);

        return isActivityStarted
                ? PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED
                : PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED;
    }
}
