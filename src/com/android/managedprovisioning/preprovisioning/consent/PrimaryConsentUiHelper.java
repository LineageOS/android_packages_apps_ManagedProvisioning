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
package com.android.managedprovisioning.preprovisioning.consent;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import com.android.managedprovisioning.common.TouchTargetEnforcer;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.preprovisioning.PreProvisioningController.UiParams;
import com.android.setupwizardlib.GlifLayout;
import java.util.List;

/**
 * Implements functionality for the consent screen.
 */
class PrimaryConsentUiHelper implements ConsentUiHelper {
    private final Activity mActivity;
    private final TouchTargetEnforcer mTouchTargetEnforcer;
    private final ConsentUiHelperCallback mCallback;
    private final Utils mUtils;
    private @Nullable RepeatingVectorAnimation mRepeatingVectorAnimation;

    PrimaryConsentUiHelper(Activity activity, ConsentUiHelperCallback callback, Utils utils) {
        mActivity = activity;
        mCallback = callback;
        mTouchTargetEnforcer =
            new TouchTargetEnforcer(activity.getResources().getDisplayMetrics().density);
        mUtils = utils;
    }

    @Override
    public void onStart() {
        if (mRepeatingVectorAnimation != null) {
            mRepeatingVectorAnimation.start();
        }
    }

    @Override
    public void onStop() {
        if (mRepeatingVectorAnimation != null) {
            mRepeatingVectorAnimation.stop();
        }
    }

    @Override
    public void initiateUi(UiParams uiParams) {
        int titleResId = 0;
        int headerResId = 0;
        if (mUtils.isProfileOwnerAction(uiParams.provisioningAction)) {
            titleResId = R.string.setup_profile;
            headerResId = R.string.work_profile_provisioning_accept_header;
        } else if (mUtils.isDeviceOwnerAction(uiParams.provisioningAction)) {
            titleResId = R.string.setup_device;
            headerResId = R.string.fully_managed_device_provisioning_accept_header;
        }

        final CustomizationParams customization = uiParams.customization;
        mCallback.initializeLayoutParams(
                R.layout.intro,
                headerResId,
                customization.mainColor,
                customization.statusBarColor);

        setupAnimation();
        setupAcceptAndContinueButton(customization, uiParams.isSilentProvisioning);

        // set the activity title
        mActivity.setTitle(titleResId);

        // set up terms headers
        setupViewTermsButton(uiParams.viewTermsIntent, uiParams.disclaimerHeadings);
    }

    private void setupAnimation() {
        final GlifLayout layout = mActivity.findViewById(R.id.setup_wizard_layout);
        final ImageView imageView = layout.findViewById(R.id.animation);
        final AnimatedVectorDrawable animatedVectorDrawable =
                (AnimatedVectorDrawable) imageView.getDrawable();
        mRepeatingVectorAnimation = new RepeatingVectorAnimation(animatedVectorDrawable);
        mRepeatingVectorAnimation.start();
    }

    private void setupAcceptAndContinueButton(CustomizationParams customization,
            boolean isSilentProvisioning) {
        Button nextButton = mActivity.findViewById(R.id.next_button);
        nextButton.setOnClickListener(v -> onNextButtonClicked());
        nextButton.setBackgroundTintList(ColorStateList.valueOf(customization.mainColor));
        if (mUtils.isBrightColor(customization.mainColor)) {
            nextButton.setTextColor(mActivity.getColor(R.color.gray_button_text));
        }
        if (isSilentProvisioning) {
            onNextButtonClicked();
        }
    }

    private void onNextButtonClicked() {
        ProvisionLogger.logi("Next button (next_button) is clicked.");
        mCallback.nextAfterUserConsent();
    }

    private void setupViewTermsButton(Intent showTermsIntent,
            List<String> disclaimerHeadings) {
        final View viewTermsButton = mActivity.findViewById(R.id.show_terms_button);
        if (disclaimerHeadings.isEmpty()) {
            viewTermsButton.setVisibility(INVISIBLE);
        } else {
            viewTermsButton.setVisibility(VISIBLE);
            viewTermsButton.setOnClickListener(v -> mActivity.startActivity(showTermsIntent));
            mTouchTargetEnforcer.enforce(viewTermsButton, (View) viewTermsButton.getParent());
        }
    }
}
