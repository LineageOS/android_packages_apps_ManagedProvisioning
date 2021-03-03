/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Bundle;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;

/**
 * An activity for telling the user they can abort set-up, reset the device and return
 * it to their IT admin.
 */
public class ResetAndReturnDeviceActivity extends SetupGlifLayoutActivity {
    private FooterButton mCancelAndResetButton;
    private ProvisioningParams mParams;

    public ResetAndReturnDeviceActivity() {
        super();
    }

    @VisibleForTesting
    public ResetAndReturnDeviceActivity(
            Utils utils, SettingsFacade settingsFacade, ThemeHelper themeHelper) {
        super(utils, settingsFacade, themeHelper);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        CustomizationParams customizationParams =
                CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(R.layout.return_device_screen, null, customizationParams);

        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        layout.setIcon(getDrawable(R.drawable.ic_error_outline));
        mCancelAndResetButton = Utils.addResetButton(layout, v -> onResetButtonClicked());
    }

    private void onResetButtonClicked() {
        getUtils().factoryReset(this, "User chose to abort setup.");
        finish();
    }
}
