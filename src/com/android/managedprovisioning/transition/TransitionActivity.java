/*
 * Copyright 2019, The Android Open Source Project
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
package com.android.managedprovisioning.transition;

import android.os.Bundle;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Activity which informs the user that they are about to set up their personal profile.
 */
public class TransitionActivity extends SetupGlifLayoutActivity {

    private ProvisioningParams mParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        if (mParams == null) {
            ProvisionLogger.loge("Missing params in TransitionActivity activity");
            finish();
            return;
        }
        initializeUi();
    }

    private void initializeUi() {
        CustomizationParams customizationParams =
            CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(
                R.layout.transition_screen, R.string.now_lets_set_up_everything_else,
                customizationParams.mainColor, customizationParams.statusBarColor);
        setTitle(R.string.set_up_everything_else);
        findViewById(R.id.next_button).setOnClickListener(v -> finish());
    }

    @Override
    public void onBackPressed() {}
}
