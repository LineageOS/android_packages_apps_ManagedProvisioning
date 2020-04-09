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

import static com.android.managedprovisioning.common.ProvisionLogger.loge;
import static com.android.managedprovisioning.common.ProvisionLogger.logw;
import static com.android.managedprovisioning.model.ProvisioningParams.EXTRA_PROVISIONING_PARAMS;

import android.annotation.Nullable;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
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
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.crossprofile.CrossProfileAdapter.CrossProfileViewHolder;

import com.google.android.setupdesign.GlifLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stand-alone activity that presents the user with the default OEM cross-profile apps for them to
 * accept or deny. Can be launched independently outside of the setup flow.
 *
 * <p>Sets the result code to {@link AppCompatActivity#RESULT_OK} if the user successfully sets
 * their cross-profile preferences.
 *
 * <p>Accepts provisioning parameters provided via the {@link ProvisioningParams
 * #EXTRA_PROVISIONING_PARAMS} extra. If these are not provided, it is assumed that the screen is
 * being displayed outside of provisioning.
 */
public class CrossProfileConsentActivity extends AppCompatActivity {

    @VisibleForTesting
    static final String CROSS_PROFILE_SUMMARY_META_DATA = "android.app.cross_profile_summary";

    private static final String TOGGLE_STATES_KEY = "TOGGLE_STATES";

    private final Observer<List<CrossProfileItem>> mItemsObserver = this::onItemsChanged;

    private RecyclerView mCrossProfileItems;
    private CrossProfileConsentViewModel mModel;
    private boolean mAddedButton = false;

    /**
     * The restored toggle states from inside the {@link RecyclerView} prior to the activity being
     * recreated. Reset back to null after being used.
     */
    @Nullable private Map<CrossProfileItem, Boolean> mRestoredToggleStates;

    // Provisioning state can be stored here since it is activity-scoped. If the activity is
    // recreated, this state will be retrieved from the intent again in the same way.
    @Nullable private ProvisioningParams mProvisioningParams;

    @Nullable private CrossProfileAdapter mCrossProfileAdapter;

    /**
     * Flag to determine whether the UI should stop responding to clicks. Required since Android
     * does not effectively handle 'spam clicking'; each click gets registered and acted upon.
     */
    private boolean mSuppressClicks = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        maybeInitializeProvisioningParams();
        initializeLayoutParams();
        maybeForceOrientation();
        initializeCrossProfileItems();
        mAddedButton = false;
        mModel = findViewModel();
        observeItems();
        if (savedInstanceState == null) {
            onFirstCreate();
        } else {
            restoreInstanceState(savedInstanceState);
        }
    }

    @Nullable
    private void maybeInitializeProvisioningParams() {
        final Intent intent = getIntent();
        if (intent == null) {
            mProvisioningParams = null;
            return;
        }
        if (!intent.hasExtra(EXTRA_PROVISIONING_PARAMS)) {
            mProvisioningParams = null;
            return;
        }
        mProvisioningParams = intent.getParcelableExtra(EXTRA_PROVISIONING_PARAMS);
    }

    protected void initializeLayoutParams() {
        setContentView(R.layout.cross_profile_consent_activity);
        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        final CustomizationParams params = createCustomizationParams();
        layout.setPrimaryColor(ColorStateList.valueOf(params.mainColor));
        layout.setIcon(LogoUtils.getOrganisationLogo(this, params.mainColor));
    }

    private void maybeForceOrientation() {
        if (!isDuringProvisioning() || !getResources().getBoolean(R.bool.lock_to_portrait)) {
            return;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private boolean isDuringProvisioning() {
        return mProvisioningParams != null;
    }

    private CustomizationParams createCustomizationParams() {
        if (mProvisioningParams == null) {
            return CustomizationParams.createInstance(this, new Utils());
        } else {
            return CustomizationParams.createInstance(mProvisioningParams, this, new Utils());
        }
    }

    private void initializeCrossProfileItems() {
        mCrossProfileItems = findViewById(R.id.cross_profile_items);
        mCrossProfileItems.setLayoutManager(new LinearLayoutManager(this));
    }

    private CrossProfileConsentViewModel findViewModel() {
        final CrossProfileConsentViewModel.Factory factory =
                new CrossProfileConsentViewModel.Factory(this, getApplication());
        return new ViewModelProvider(this, factory).get(CrossProfileConsentViewModel.class);
    }

    private void observeItems() {
        // Remove before re-observing to avoid a known issue.
        mModel.getItems().removeObserver(mItemsObserver);
        mModel.getItems().observe(this, mItemsObserver);
    }

    private void onFirstCreate() {
        mModel.findItems();
    }

    @SuppressWarnings("unchecked")
    private void restoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey(TOGGLE_STATES_KEY)) {
            mRestoredToggleStates =
                    (Map<CrossProfileItem, Boolean>)
                            savedInstanceState.getSerializable(TOGGLE_STATES_KEY);
        }
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        saveToggleInstanceState(savedInstanceState);
    }

    /**
     * Saved the toggle states into the given instance state bundle. Required since the toggles in
     * the {@link RecyclerView} are not restoring themselves.
     */
    private void saveToggleInstanceState(Bundle savedInstanceState) {
        if (mCrossProfileAdapter == null) {
            return;
        }
        final Map<CrossProfileItem, Boolean> toggleStates = findToggleStates();
        if (toggleStates.isEmpty()) {
            return;
        }
        // Wrap in a HashMap since it is serializable.
        savedInstanceState.putSerializable(TOGGLE_STATES_KEY, new HashMap<>(toggleStates));
    }

    private void onItemsChanged(@Nullable List<CrossProfileItem> crossProfileItems) {
        if (crossProfileItems == null) {
            return;
        }
        if (crossProfileItems.isEmpty()) {
            logw("No provisioning cross-profile consent apps. Skipping.");
            mModel.onConsentComplete(new HashMap<>());
            finishWithResult();
            return;
        }
        if (mRestoredToggleStates != null) {
            mCrossProfileAdapter =
                    new CrossProfileAdapter(this, crossProfileItems, mRestoredToggleStates);
            mRestoredToggleStates = null;
        } else {
            mCrossProfileAdapter = new CrossProfileAdapter(this, crossProfileItems);
        }
        mCrossProfileItems.setAdapter(mCrossProfileAdapter);
        maybeAddButton();
        if (isSilentProvisioning()) {
            onButtonClicked();
        }
    }

    private void maybeAddButton() {
        if (mAddedButton) {
            return;
        }
        mAddedButton = true;
        if (isFinalScreen()) {
            Utils.addDoneButton(findViewById(R.id.setup_wizard_layout), v -> onButtonClicked());
        } else {
            Utils.addNextButton(findViewById(R.id.setup_wizard_layout), v -> onButtonClicked());
        }
    }

    private boolean isFinalScreen() {
        return mProvisioningParams == null;
    }

    /**
     * Handles all logic from the click of the 'next' or 'done' button. Since this could have
     * multiple entry points, click listeners should call this directly without preceding logic.
     */
    private void onButtonClicked() {
        if (mSuppressClicks) {
            logw("Double-click detected on button");
            return;
        }
        if (mCrossProfileAdapter == null) {
            loge("Button clicked before items found");
            return;
        }
        final List<CrossProfileItem> crossProfileItems = mModel.getItems().getValue();
        if (crossProfileItems == null) {
            loge("Button clicked before items found");
            return;
        }
        final Map<CrossProfileItem, Boolean> toggleStates = findToggleStates();
        for (CrossProfileItem crossProfileItem : toggleStates.keySet()) {
            if (!crossProfileItems.contains(crossProfileItem)) {
                loge("Unexpected race condition: unknown cross-profile item selected from UI: "
                        + crossProfileItem);
                return;
            }
        }
        mSuppressClicks = true;
        mModel.onConsentComplete(toggleStates);
        finishWithResult();
    }

    /** Assumes {@link #mCrossProfileAdapter} is non-null. */
    private Map<CrossProfileItem, Boolean> findToggleStates() {
        if (isSilentProvisioning()) {
            return findSilentProvisioningToggleStates();
        }
        final Map<CrossProfileItem, Boolean> toggleStates = new HashMap<>();
        final List<CrossProfileItem> crossProfileItems =
                mCrossProfileAdapter.getCrossProfileItems();
        for (int i = 0; i < crossProfileItems.size(); i++) {
            final CrossProfileViewHolder viewHolder =
                    (CrossProfileViewHolder) mCrossProfileItems.findViewHolderForAdapterPosition(i);
            toggleStates.put(crossProfileItems.get(i), viewHolder.toggle().isChecked());
        }
        return toggleStates;
    }

    private Map<CrossProfileItem, Boolean> findSilentProvisioningToggleStates() {
        return mModel.getItems()
                .getValue()
                .stream()
                .collect(Collectors.toMap(Function.identity(), c -> true));
    }

    private void finishWithResult() {
        setResult(RESULT_OK);
        finish();
        // Override the animation to avoid the transition jumping back and forth (b/149463287).
        overridePendingTransition(/* enterAnim= */ 0, /* exitAnim= */ 0);
    }

    private boolean isSilentProvisioning() {
        return mProvisioningParams != null && Utils.isSilentProvisioning(this, mProvisioningParams);
    }
}
