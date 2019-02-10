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

package com.android.managedprovisioning.provisioning;

import android.app.Activity;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.setupwizardlib.GlifLayout;

/**
 * Progress activity shown whilst network setup, downloading, verifying and installing the
 * admin package is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link AdminIntegratedFlowPrepareManager}. It shows progress updates as provisioning
 * progresses and handles showing of cancel and error dialogs.</p>
 */
public class AdminIntegratedFlowPrepareActivity extends AbstractProvisioningActivity {

    public AdminIntegratedFlowPrepareActivity() {
        this(new Utils());
    }

    @VisibleForTesting
    protected AdminIntegratedFlowPrepareActivity(Utils utils) {
        super(utils);
    }

    @Override
    protected ProvisioningManagerInterface getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = AdminIntegratedFlowPrepareManager.getInstance(this);
        }
        return mProvisioningManager;
    }

    @Override
    public void preFinalizationCompleted() {
        ProvisionLogger.logi("AdminIntegratedFlowPrepareActivity pre-finalization completed");
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    protected void decideCancelProvisioningDialog() {
        showCancelProvisioningDialog(/* resetRequired = */true);
    }

    @Override
    protected void initializeUi(ProvisioningParams params) {
        final int headerResId = R.string.setup_provisioning_header;
        final int titleResId = R.string.setup_device_progress;

        CustomizationParams customizationParams =
                CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(R.layout.prepare_progress, headerResId,
                customizationParams.mainColor, customizationParams.statusBarColor);
        setTitle(titleResId);
        GlifLayout layout = findViewById(R.id.setup_wizard_layout);

        TextView textView = layout.findViewById(R.id.description);
        ImageView imageView = layout.findViewById(R.id.animation);

        textView.setText(R.string.setup_provisioning_header_description);
        imageView.setImageResource(R.drawable.enterprise_do_animation);

        mAnimatedVectorDrawable = (AnimatedVectorDrawable) imageView.getDrawable();
    }
}