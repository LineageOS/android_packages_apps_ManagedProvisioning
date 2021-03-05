/*
 * Copyright 2016, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.terms;

import static android.view.View.TEXT_ALIGNMENT_TEXT_START;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TERMS_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArraySet;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.Toolbar;

import com.android.car.ui.core.CarUi;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.terms.adapters.TermsListAdapter;
import com.android.managedprovisioning.preprovisioning.terms.adapters.TermsListAdapterCar;

import com.google.android.setupdesign.GlifLayout;

import java.util.List;
import java.util.Set;

/**
 * Activity responsible for displaying the Terms screen
 */
public class TermsActivity extends SetupGlifLayoutActivity {
    private final TermsProvider mTermsProvider;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private final Set<Integer> mExpandedGroupsPosition = new ArraySet<>();
    private final SettingsFacade mSettingsFacade;
    private ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;

    @SuppressWarnings("unused")
    public TermsActivity() {
        this(StoreUtils::readString, null, new SettingsFacade());
    }

    @VisibleForTesting
    TermsActivity(StoreUtils.TextFileReader textFileReader,
            AccessibilityContextMenuMaker contextMenuMaker, SettingsFacade settingsFacade) {
        super(new Utils(), settingsFacade,
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));
        mTermsProvider = new TermsProvider(this, textFileReader, mUtils);
        mContextMenuMaker =
                contextMenuMaker != null ? contextMenuMaker : new AccessibilityContextMenuMaker(
                        this);
        mSettingsFacade = settingsFacade;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            setTheme(R.style.Theme_CarUi_WithToolbar);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.terms_screen);
        setTitle(R.string.terms);

        ProvisioningParams params = checkNotNull(
                getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS));
        List<TermsDocument> terms = mTermsProvider.getTerms(params);
        setUpTermsList(terms);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            ToolbarController toolbar = CarUi.requireToolbar(this);
            toolbar.setTitle(R.string.terms);
            toolbar.setState(com.android.car.ui.toolbar.Toolbar.State.SUBPAGE);
        } else {
            initializeUiForHandhelds(params);
        }

        mProvisioningAnalyticsTracker = new ProvisioningAnalyticsTracker(
                MetricsWriterFactory.getMetricsWriter(this, mSettingsFacade),
                new ManagedProvisioningSharedPreferences(getApplicationContext()));
        mProvisioningAnalyticsTracker.logNumberOfTermsDisplayed(this, terms.size());
    }

    private void initializeUiForHandhelds(ProvisioningParams params) {
        setupGlifLayout(params);
        setupToolbar();
    }

    private void setupGlifLayout(ProvisioningParams params) {
        GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        layout.setDescriptionText(mTermsProvider.getGeneralDisclaimer(params).getContent());
        layout.findViewById(R.id.suc_layout_title)
                .setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
        layout.findViewById(R.id.sud_layout_subtitle)
                .setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
        layout.setIcon(getDrawable(R.drawable.ic_enterprise_blue_24dp));
        layout.findViewById(R.id.sud_layout_icon).setVisibility(View.INVISIBLE);
    }

    private void setupToolbar() {
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.sud_scroll_view).getParent();
        Toolbar toolbar = new Toolbar(this);
        toolbar.setNavigationIcon(getDrawable(R.drawable.ic_arrow_back_24dp));
        toolbar.setNavigationOnClickListener(v -> TermsActivity.this.finish());
        viewGroup.addView(toolbar, /* index= */ 0);
    }

    private void setUpTermsList(List<TermsDocument> terms) {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            CarUiRecyclerView listView = findViewById(R.id.terms_container);
            listView.setAdapter(new TermsListAdapterCar(getApplicationContext(), terms, mUtils));

        } else {
            ExpandableListView container = findViewById(R.id.terms_container);
            container.setAdapter(
                    new TermsListAdapter(getApplicationContext(), terms,
                            getLayoutInflater(),
                            new AccessibilityContextMenuMaker(this),
                            container::isGroupExpanded,
                            mUtils));
            if (terms.size() > 0) {
                container.expandGroup(/* groupPos= */ 0);
            }
            // Add default open terms to the expanded groups set.
            for (int i = 0; i < terms.size(); i++) {
                if (container.isGroupExpanded(i)) mExpandedGroupsPosition.add(i);
            }

            // keep at most one group expanded at a time
            container.setOnGroupExpandListener((int groupPosition) -> {
                mExpandedGroupsPosition.add(groupPosition);
                for (int i = 0; i < terms.size(); i++) {
                    if (i != groupPosition && container.isGroupExpanded(i)) {
                        container.collapseGroup(i);
                    }
                }
            });
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v instanceof TextView) {
            mContextMenuMaker.populateMenuContent(menu, (TextView) v);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        mProvisioningAnalyticsTracker.logNumberOfTermsRead(this, mExpandedGroupsPosition.size());
        super.onDestroy();
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_TERMS_ACTIVITY_TIME_MS;
    }
}
