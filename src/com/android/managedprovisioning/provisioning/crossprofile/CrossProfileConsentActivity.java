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
 * limitations under the License.
 */

package com.android.managedprovisioning.provisioning.crossprofile;

import static com.android.managedprovisioning.common.ProvisionLogger.logw;

import android.annotation.Nullable;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.LogoUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;

import com.google.android.setupdesign.GlifLayout;

import java.util.List;

/**
 * Stand-alone activity that presents the user with the default OEM cross-profile apps for them to
 * accept or deny. Can be launched independently outside of the setup flow.
 *
 * <p>Sets the result code to {@link AppCompatActivity#RESULT_OK} if the user successfully sets
 * their cross-profile preferences.
 */
public class CrossProfileConsentActivity extends AppCompatActivity {

    /**
     * Boolean extra used internally within this application to specify that this activity is the
     * final screen, not part of a setup flow.
     */
    public static final String EXTRA_FINAL_SCREEN = "android.app.extra.CROSS_PROFILE_FINAL_SCREEN";

    @VisibleForTesting
    static final String CROSS_PROFILE_SUMMARY_META_DATA = "android.app.cross_profile_summary";

    private final Observer<List<CrossProfileItem>> mItemsObserver = this::onItemsChanged;

    private RecyclerView mCrossProfileItems;
    private CrossProfileConsentViewModel mModel;

    /**
     * Flag to determine whether the UI should stop responding to clicks. Required since Android
     * does not effectively handle 'spam clicking'; each click gets registered and acted upon.
     */
    private boolean mSuppressClicks = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeLayoutParams();
        initializeCrossProfileItems();
        addButton();
        mModel = findViewModel();
        observeItems();
        if (savedInstanceState == null) {
            onFirstCreate();
        }
    }

    private CrossProfileConsentViewModel findViewModel() {
        final CrossProfileConsentViewModel.Factory factory =
                new CrossProfileConsentViewModel.Factory(this, getApplication());
        return new ViewModelProvider(this, factory).get(CrossProfileConsentViewModel.class);
    }

    protected void initializeLayoutParams() {
        setContentView(R.layout.cross_profile_consent_activity);
        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        final CustomizationParams params = CustomizationParams.createInstance(this, new Utils());
        layout.setPrimaryColor(ColorStateList.valueOf(params.mainColor));
        layout.setIcon(LogoUtils.getOrganisationLogo(this, params.mainColor));
    }

    private void initializeCrossProfileItems() {
        mCrossProfileItems = findViewById(R.id.cross_profile_items);
        mCrossProfileItems.setLayoutManager(new LinearLayoutManager(this));
    }

    private void addButton() {
        if (isFinalScreen()) {
            Utils.addDoneButton(findViewById(R.id.setup_wizard_layout), v -> onButtonClicked());
        } else {
            Utils.addNextButton(findViewById(R.id.setup_wizard_layout), v -> onButtonClicked());
        }
    }

    private boolean isFinalScreen() {
        final Intent intent = getIntent();
        if (intent == null) {
            return false;
        }
        return intent.getBooleanExtra(EXTRA_FINAL_SCREEN, /* defValue= */ false);
    }

    private void observeItems() {
        // Remove before re-observing to avoid a known issue.
        mModel.getItems().removeObserver(mItemsObserver);
        mModel.getItems().observe(this, mItemsObserver);
    }

    private void onFirstCreate() {
        mModel.findItems();
    }

    @Override
    public void onStart() {
        super.onStart();
        mSuppressClicks = false;
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resId, boolean first) {
        theme.applyStyle(R.style.SetupWizardPartnerResource, /* force= */ true);
        super.onApplyThemeResource(theme, resId, first);
    }

    private void onItemsChanged(@Nullable List<CrossProfileItem> crossProfileItems) {
        if (crossProfileItems == null) {
            return;
        }
        mCrossProfileItems.setAdapter(new CrossProfileAdapter(crossProfileItems));
    }

    private void onButtonClicked() {
        if (mSuppressClicks) {
            logw("Double-click detected on button.");
            return;
        }
        mSuppressClicks = true;
        mModel.onButtonClicked();
        setResult(RESULT_OK);
        finish();
    }
}
