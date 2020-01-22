/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.managedprovisioning.provisioning;

import android.os.Bundle;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ClickableSpanFactory;

import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.google.android.setupdesign.GlifLayout;

/**
 * This is the first activity the user will see during financed device provisioning.
 */
public final class FinancedDeviceLandingActivity extends SetupGlifLayoutActivity {

    private final AccessibilityContextMenuMaker mContextMenuMaker;

    public FinancedDeviceLandingActivity() {
        this(new Utils(), /* contextMenuMaker= */null);
    }

    @VisibleForTesting
    FinancedDeviceLandingActivity(Utils utils, AccessibilityContextMenuMaker contextMenuMaker) {
        super(utils);
        mContextMenuMaker = contextMenuMaker != null
                ? contextMenuMaker
                : new AccessibilityContextMenuMaker(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ProvisioningParams params = getIntent()
                .getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        initializeUi(params);
    }

    private void initializeUi(ProvisioningParams params) {
        final int headerResId = R.string.financed_device_screen_header;
        // TODO: b/147399319, update title string
        final int titleResId = R.string.setup_device_progress;

        // TODO: b/147812990, update UI here
        final CustomizationParams customizationParams =
                CustomizationParams.createInstance(params, this, mUtils);
        initializeLayoutParams(R.layout.landing_screen, headerResId, customizationParams);
        setTitle(titleResId);

        handleSupportUrl(customizationParams);
        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        Utils.addNextButton(layout, v -> onNextButtonClicked());
    }

    private void onNextButtonClicked() {
        setResult(RESULT_OK);
        finish();
    }

    private void handleSupportUrl(CustomizationParams customizationParams) {
        final TextView info = findViewById(R.id.provider_info);
        final String deviceProvider = customizationParams.orgName;
        final String contactDeviceProvider =
                getString(R.string.contact_device_provider, deviceProvider);
        final ClickableSpanFactory clickableSpanFactory =
                new ClickableSpanFactory(getColor(R.color.blue_text));
        mUtils.handleSupportUrl(this, customizationParams, clickableSpanFactory,
                mContextMenuMaker, info, deviceProvider, contactDeviceProvider);
    }
}
