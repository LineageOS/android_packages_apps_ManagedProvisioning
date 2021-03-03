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
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupdesign.GlifLayout;

/**
 * This is the first activity the user will see during financed device provisioning.
 */
public final class FinancedDeviceLandingActivity extends SetupGlifLayoutActivity {

    private final AccessibilityContextMenuMaker mContextMenuMaker;

    public FinancedDeviceLandingActivity() {
        this(new Utils(), /* contextMenuMaker= */null, new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
    }

    @VisibleForTesting
    FinancedDeviceLandingActivity(Utils utils, AccessibilityContextMenuMaker contextMenuMaker,
            SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
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
        setContentView(R.layout.financed_device_landing_screen);

        GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        final String headerString = getString(R.string.financed_device_screen_header,
                params.organizationName);
        layout.setHeaderText(headerString);
        layout.setIcon(getDrawable(R.drawable.ic_info_outline_24px));
        Utils.addAcceptAndContinueButton(layout, v -> onNextButtonClicked());

        final TextView headerTextView = findViewById(R.id.creditor_capabilities_header);
        final String creditorHeaderString = getString(R.string.creditor_capabilities_header,
                params.organizationName);
        headerTextView.setText(creditorHeaderString);

        final TextView installCreditorAppTextView = findViewById(R.id.install_creditor_app);
        final String installCreditorAppString = getString(R.string.install_creditor_app_description,
                params.organizationName);
        installCreditorAppTextView.setText(installCreditorAppString);
    }

    private void onNextButtonClicked() {
        setResult(RESULT_OK);
        finish();
    }
}
