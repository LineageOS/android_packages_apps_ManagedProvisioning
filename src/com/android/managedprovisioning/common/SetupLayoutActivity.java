/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static com.android.internal.logging.MetricsProto.MetricsEvent.VIEW_UNKNOWN;

import android.app.Activity;
import android.app.ActivityManager.TaskDescription;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.setupwizardlib.GlifLayout;

/**
 * Base class for setting up the layout.
 */
public abstract class SetupLayoutActivity extends Activity {
    protected final Utils mUtils = new Utils();

    private TimeLogger mTimeLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTimeLogger = new TimeLogger(this, getMetricsCategory());
        mTimeLogger.start();
    }

    @Override
    public void onDestroy() {
        mTimeLogger.stop();
        super.onDestroy();
    }

    protected int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    protected Utils getUtils() {
        return mUtils;
    }

    protected void initializeLayoutParams(int layoutResourceId, int headerResourceId,
            boolean showProgressBar) {
        setContentView(layoutResourceId);
        GlifLayout layout = (GlifLayout) findViewById(R.id.setup_wizard_layout);
        layout.setHeaderText(headerResourceId);
        if (showProgressBar) {
            layout.setProgressBarShown(true);
        }
    }

    protected void maybeSetLogoAndMainColor(Integer mainColor) {
        // null means the default value
        if (mainColor == null) {
            mainColor = getResources().getColor(R.color.orange);
        }
        // We should always use a value of 255 for the alpha.
        mainColor = Color.argb(255, Color.red(mainColor), Color.green(mainColor),
                Color.blue(mainColor));

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(mainColor);
        GlifLayout layout = (GlifLayout) findViewById(R.id.setup_wizard_layout);
        Drawable logo = LogoUtils.getOrganisationLogo(this);
        layout.setIcon(logo);
        layout.setPrimaryColor(ColorStateList.valueOf(mainColor));
        View decorView = window.getDecorView();
        int visibility = decorView.getSystemUiVisibility();
        if (getUtils().isBrightColor(mainColor)) {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decorView.setSystemUiVisibility(visibility);
        setTaskDescription(new TaskDescription(null /* label */, null /* icon */,
                mainColor));
    }

    /**
     * Constructs and shows a {@link DialogFragment} unless it is already displayed.
     * @param dialogBuilder Lightweight builder, that it is inexpensive to discard it if dialog
     * already shown.
     * @param tag The tag for this dialog, as per {@link FragmentTransaction#add(Fragment, String)}.
     */
    protected void showDialog(DialogBuilder dialogBuilder, String tag) {
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager.findFragmentByTag(tag) == null) {
            dialogBuilder.build().show(fragmentManager, tag);
        }
    }
}
