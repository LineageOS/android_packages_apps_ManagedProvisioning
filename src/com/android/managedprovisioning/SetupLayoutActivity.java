/*
 * Copyright 2015, The Android Open Source Project
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
import android.graphics.drawable.Drawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.android.setupwizardlib.GlifLayout;
import com.android.setupwizardlib.view.NavigationBar;
import com.android.setupwizardlib.view.NavigationBar.NavigationBarListener;

/**
 * Base class for setting up the layout.
 */
public abstract class SetupLayoutActivity extends Activity {

    protected void initializeLayoutParams(int layoutResourceId, int headerResourceId) {
        setContentView(layoutResourceId);
        GlifLayout layout = (GlifLayout) findViewById(R.id.setup_wizard_layout);
        layout.setHeaderText(headerResourceId);

    }

    protected void showProgressBar() {
        GlifLayout layout = (GlifLayout) findViewById(R.id.setup_wizard_layout);
        layout.setProgressBarShown(true);
    }

    protected void maybeSetLogoAndStatusBarColor(ProvisioningParams params) {
        if (params != null) {
            // This code to colorize the status bar is just temporary.
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(params.mainColor);
        }
        GlifLayout layout = (GlifLayout) findViewById(R.id.setup_wizard_layout);
        Drawable logo = LogoUtils.getOrganisationLogo(this);
        // TODO: if the dpc hasn't specified a logo: use a default one.
        if (logo != null) {
            layout.setIcon(logo);
        }
    }
}
